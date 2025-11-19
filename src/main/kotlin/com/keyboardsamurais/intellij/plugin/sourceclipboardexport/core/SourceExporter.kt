package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.config.SourceClipboardExportSettings
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core.filter.ContentBinaryFilter
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core.filter.ExportFilter
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core.filter.FileProperties
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core.filter.FilenameFilter
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core.filter.GitignoreFilter
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core.filter.IgnoredNameFilter
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core.filter.KnownBinaryFilter
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core.filter.SizeFilter
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core.gitignore.HierarchicalGitignoreParser
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.FileUtils
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

/**
 * Orchestrates the source export process. Delegates traversal to [FileTraverser], filtering to
 * [ExportFilter]s, and formatting to [SourceExportFormatter].
 */
class SourceExporter(
        private val project: Project,
        private val settings: SourceClipboardExportSettings.State,
        private val indicator: ProgressIndicator
) {
    private val logger = Logger.getInstance(SourceExporter::class.java)

    // Use the project-scoped gitignore parser service
    private val hierarchicalGitignoreParser =
            project.getService(HierarchicalGitignoreParser::class.java)
                    ?: throw IllegalStateException(
                            "HierarchicalGitignoreParser service not found. Ensure it is registered in plugin.xml and tests."
                    )

    private val formatter = SourceExportFormatter(project, settings)

    data class ExportResult(
            val content: String,
            val processedFileCount: Int,
            val excludedByFilterCount: Int,
            val excludedBySizeCount: Int,
            val excludedByBinaryContentCount: Int,
            val excludedByIgnoredNameCount: Int,
            val excludedByGitignoreCount: Int,
            val excludedExtensions: Set<String>,
            val limitReached: Boolean,
            val includedPaths: List<String>
    )

    suspend fun exportSources(selectedFiles: Array<VirtualFile>): ExportResult {
        logger.info("Starting source export process.")
        logger.info("Settings: Max Files=${settings.fileCount}, Max Size KB=${settings.maxFileSizeKb}, Filters Enabled=${settings.areFiltersEnabled}, Filters=${settings.filenameFilters.joinToString()}, Ignored=${settings.ignoredNames.joinToString()}, Include Prefix=${settings.includePathPrefix}, Include Line Numbers=${settings.includeLineNumbers}")

        // Clear the hierarchical parser's cache to ensure fresh .gitignore parsing
        hierarchicalGitignoreParser.clearCache()

        indicator.isIndeterminate = false
        indicator.text = "Scanning files..."

        val stats = ExportStatistics()
        val explicitTopLevelFiles = selectedFiles.toSet()

        // Initialize Filters
        val traversalFilters = mutableListOf<ExportFilter>()
        traversalFilters.add(IgnoredNameFilter(settings.ignoredNames))
        traversalFilters.add(GitignoreFilter(hierarchicalGitignoreParser, explicitTopLevelFiles))

        val inclusionFilters = mutableListOf<ExportFilter>()
        inclusionFilters.add(KnownBinaryFilter())
        inclusionFilters.add(SizeFilter(settings.maxFileSizeKb))
        // ContentBinaryFilter is expensive, so it's usually last or done separately,
        // but FileTraverser applies them in order.
        inclusionFilters.add(ContentBinaryFilter())

        if (settings.areFiltersEnabled) {
            inclusionFilters.add(FilenameFilter(settings.filenameFilters))
        }

        val traverser =
                FileTraverser(
                        project = project,
                        stats = stats,
                        traversalFilters = traversalFilters,
                        inclusionFilters = inclusionFilters,
                        fileCountLimit = settings.fileCount
                )

        // Use a map to store local (path, content) entries for each coroutine (simulated by unique
        // ID or just thread safe collection)
        // Since FileTraverser uses coroutines, we need a thread-safe way to collect results.
        // We can use a ConcurrentHashMap where key is path (unique) and value is content.
        // Or better, a list of entries.
        val collectedEntries = ConcurrentHashMap<String, String>()

        traverser.traverse(selectedFiles) { file, fileProps, relativePath ->
            // This callback is invoked for files that passed all filters
            processFileContent(file, fileProps, relativePath, stats, collectedEntries)
        }

        // Merge and sort
        val mergedEntries =
                collectedEntries
                        .map { SourceExportFormatter.FileEntry(it.key, it.value) }
                        .sortedBy { it.path }
        val includedPaths = mergedEntries.map { it.path }

        val finalProcessedCount = stats.processedFileCount.get()
        val finalFileCount = stats.fileCount.get()

        logger.info("Export process finished. Processed: $finalProcessedCount, Total Considered: $finalFileCount, Excluded (Filter: ${stats.excludedByFilterCount.get()}, Size: ${stats.excludedBySizeCount.get()}, Binary: ${stats.excludedByBinaryContentCount.get()}, IgnoredName: ${stats.excludedByIgnoredNameCount.get()}, Gitignore: ${stats.excludedByGitignoreCount.get()})")

        val exportStats =
                SourceExportFormatter.ExportStats(
                        processedFileCount = finalProcessedCount,
                        excludedByFilterCount = stats.excludedByFilterCount.get(),
                        excludedBySizeCount = stats.excludedBySizeCount.get(),
                        excludedByBinaryContentCount = stats.excludedByBinaryContentCount.get(),
                        excludedByIgnoredNameCount = stats.excludedByIgnoredNameCount.get(),
                        excludedByGitignoreCount = stats.excludedByGitignoreCount.get()
                )

        val finalContent = formatter.buildContent(selectedFiles, mergedEntries, exportStats)

        return ExportResult(
                content = finalContent,
                processedFileCount = finalProcessedCount,
                excludedByFilterCount = stats.excludedByFilterCount.get(),
                excludedBySizeCount = stats.excludedBySizeCount.get(),
                excludedByBinaryContentCount = stats.excludedByBinaryContentCount.get(),
                excludedByIgnoredNameCount = stats.excludedByIgnoredNameCount.get(),
                excludedByGitignoreCount = stats.excludedByGitignoreCount.get(),
                excludedExtensions = stats.excludedExtensions.toSet(),
                limitReached = finalFileCount >= settings.fileCount,
                includedPaths = includedPaths
        )
    }

    private suspend fun processFileContent(
            file: VirtualFile,
            fileProps: FileProperties,
            relativePath: String,
            stats: ExportStatistics,
            collectedEntries: ConcurrentHashMap<String, String>
    ) {
        if (stats.fileCount.get() >= settings.fileCount) {
            return
        }

        val fileContent = readFileContentSafely(file, fileProps, relativePath) ?: return
        val preparedContent = applyPathPrefixIfNeeded(fileContent, file, relativePath)

        val currentFileCount = incrementFileCountWithinLimit(stats) ?: return

        collectedEntries[relativePath] = preparedContent
        val currentProcessedCount = stats.processedFileCount.incrementAndGet()

        indicator.checkCanceled()
        val targetCount = settings.fileCount.toDouble()
        if (targetCount > 0) {
            indicator.fraction = min(1.0, currentFileCount.toDouble() / targetCount)
        }
        indicator.text = "Processed $currentProcessedCount files..."
    }

    private fun readFileContentSafely(
            file: VirtualFile,
            fileProps: FileProperties,
            relativePath: String
    ): String? {
        val content =
                try {
                    ReadAction.compute<String, Exception> { FileUtils.readFileContent(file) }
                } catch (e: Exception) {
                    logger.error("Error reading file content for $relativePath", e)
                    "// Error reading file: ${fileProps.path} (${e.message})"
                }

        val prepared =
                if (settings.includeLineNumbers && !content.startsWith("// Error")) {
                    addLineNumbers(content)
                } else {
                    content
                }

        if (prepared.isEmpty() || prepared.startsWith("// Error")) {
            logger.warn("File content is empty or unreadable, skipping file: $relativePath")
            return null
        }
        return prepared
    }

    private fun applyPathPrefixIfNeeded(
            content: String,
            file: VirtualFile,
            relativePath: String
    ): String {
        if (!settings.includePathPrefix) return content
        if (FileUtils.hasFilenamePrefix(content)) return content

        val commentPrefix =
                ReadAction.compute<String, Exception> { FileUtils.getCommentPrefix(file) }
        val formattedPrefix =
                if (commentPrefix.endsWith("-->")) {
                    commentPrefix.replace("-->", "$relativePath -->")
                } else {
                    "$commentPrefix$relativePath"
                }
        return "$formattedPrefix\n$content"
    }

    private fun addLineNumbers(content: String): String {
        val lines = content.lines()
        val lineNumberWidth = lines.size.toString().length
        val result = StringBuilder()

        for ((index, line) in lines.withIndex()) {
            val lineNumber = (index + 1).toString().padStart(lineNumberWidth, ' ')
            result.append("$lineNumber: $line\n")
        }

        // Remove the last newline if the original content didn't end with one
        if (!content.endsWith("\n") && result.isNotEmpty()) {
            result.setLength(result.length - 1)
        }

        return result.toString()
    }

    private fun incrementFileCountWithinLimit(stats: ExportStatistics): Int? {
        while (true) {
            val current = stats.fileCount.get()
            if (current >= settings.fileCount) {
                logger.debug("File limit reached, skipping additional files.")
                return null
            }
            val next = current + 1
            if (stats.fileCount.compareAndSet(current, next)) {
                return next
            }
        }
    }
}
