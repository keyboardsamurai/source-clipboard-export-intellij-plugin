package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.config.SourceClipboardExportSettings
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core.gitignore.HierarchicalGitignoreParser
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.FileUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min

/**
 * Traverses the selected files/directories, applies ignore/filter rules, and collects
 * normalized file contents that can later be formatted for export.
 *
 * Concurrency concerns (progress indicators, cancellation, gitignore parsing, etc.) stay here,
 * while any layout/formatting decisions are delegated to [SourceExportFormatter].
 */
class SourceExporter(
    private val project: Project,
    private val settings: SourceClipboardExportSettings.State,
    private val indicator: ProgressIndicator
) {
    private val logger = Logger.getInstance(SourceExporter::class.java)
    // Use AtomicIntegers for thread-safe counting from multiple coroutines
    private val fileCount = AtomicInteger(0)
    private val processedFileCount = AtomicInteger(0)
    private val excludedByFilterCount = AtomicInteger(0)
    private val excludedBySizeCount = AtomicInteger(0)
    private val excludedByBinaryContentCount = AtomicInteger(0)
    private val excludedByIgnoredNameCount = AtomicInteger(0)
    private val excludedByGitignoreCount = AtomicInteger(0)
    private val excludedExtensions = Collections.synchronizedSet(mutableSetOf<String>())

    // Thread-safe counter for generating unique task identifiers
    private val taskCounter = AtomicInteger(0)

    // Prefer the project-scoped gitignore parser service; in unit tests fall back to a local instance
    private val hierarchicalGitignoreParser = run {
        val app = try { ApplicationManager.getApplication() } catch (_: Exception) { null }
        if (app == null || app.isUnitTestMode) {
            HierarchicalGitignoreParser(project)
        } else {
            try {
                project.getService(HierarchicalGitignoreParser::class.java)
            } catch (_: Throwable) {
                null
            } ?: HierarchicalGitignoreParser(project)
        }
    }

    private val formatter = SourceExportFormatter(project, settings)

    // Track explicitly selected top-level files to allow .gitignore override for those files only
    private var explicitTopLevelFiles: Set<VirtualFile> = emptySet()

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

    /**
     * Adds line numbers to the file content.
     * Each line will be prefixed with its line number.
     *
     * @param content The file content to add line numbers to
     * @return The file content with line numbers added
     */
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


    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun exportSources(selectedFiles: Array<VirtualFile>): ExportResult {
        logger.info("Starting source export process.")
        logger.info("Settings: Max Files=${settings.fileCount}, Max Size KB=${settings.maxFileSizeKb}, Filters Enabled=${settings.areFiltersEnabled}, Filters=${settings.filenameFilters.joinToString()}, Ignored=${settings.ignoredNames.joinToString()}, Include Prefix=${settings.includePathPrefix}, Include Line Numbers=${settings.includeLineNumbers}")

        // Clear the hierarchical parser's cache to ensure fresh .gitignore parsing
        hierarchicalGitignoreParser.clearCache()

        indicator.isIndeterminate = false
        indicator.text = "Scanning files..."

        // Store explicit selection for .gitignore override on files
        explicitTopLevelFiles = selectedFiles.toSet()

        // Use a map to store local (path, content) entries for each coroutine
        val localEntries = ConcurrentHashMap<Long, MutableList<Pair<String, String>>>()
        val visitedFiles = ConcurrentHashMap.newKeySet<VirtualFile>() // Use a concurrent set for visited tracking

        coroutineScope {
            val scopeJob = SupervisorJob()
            // Limit parallelism to avoid spawning too many coroutines
            // This prevents excessive thread creation and context switching
            val limitedDispatcher = Dispatchers.IO.limitedParallelism(Runtime.getRuntime().availableProcessors()) // Use system CPU count

            // Launch a coroutine for each initially selected file/directory
            selectedFiles.forEach { file ->
                launch(scopeJob + limitedDispatcher) {
                    ensureActive()
                    // Create a local buffer for this coroutine
                    val localBuffer = mutableListOf<Pair<String, String>>()
                    // Store the buffer in the map using a unique task ID as the key
                    val taskId = taskCounter.incrementAndGet().toLong()
                    localEntries[taskId] = localBuffer
                    // processEntry will handle recursion and visited checks
                    processEntry(file, this, scopeJob, localBuffer, visitedFiles)
                }
            }
            scopeJob.children.forEach { it.join() }
            scopeJob.complete()
            scopeJob.join()
        }

        // Merge all local buffers and sort deterministically by relative path
        val mergedEntries = buildMergedEntries(localEntries.values)
        val includedPaths = mergedEntries.map { it.path }

        val finalProcessedCount = processedFileCount.get()
        val finalFileCount = fileCount.get()
        logger.info("Export process finished. Processed: $finalProcessedCount, Total Considered: $finalFileCount, Excluded (Filter: ${excludedByFilterCount.get()}, Size: ${excludedBySizeCount.get()}, Binary: ${excludedByBinaryContentCount.get()}, IgnoredName: ${excludedByIgnoredNameCount.get()}, Gitignore: ${excludedByGitignoreCount.get()})")

        val stats = SourceExportFormatter.ExportStats(
            processedFileCount = finalProcessedCount,
            excludedByFilterCount = excludedByFilterCount.get(),
            excludedBySizeCount = excludedBySizeCount.get(),
            excludedByBinaryContentCount = excludedByBinaryContentCount.get(),
            excludedByIgnoredNameCount = excludedByIgnoredNameCount.get(),
            excludedByGitignoreCount = excludedByGitignoreCount.get()
        )

        val finalContent = formatter.buildContent(selectedFiles, mergedEntries, stats)
        return ExportResult(
            content = finalContent,
            processedFileCount = finalProcessedCount,
            excludedByFilterCount = excludedByFilterCount.get(),
            excludedBySizeCount = excludedBySizeCount.get(),
            excludedByBinaryContentCount = excludedByBinaryContentCount.get(),
            excludedByIgnoredNameCount = excludedByIgnoredNameCount.get(),
            excludedByGitignoreCount = excludedByGitignoreCount.get(),
            excludedExtensions = excludedExtensions.toSet(),
            limitReached = finalFileCount >= settings.fileCount,
            includedPaths = includedPaths
        )
    }

    /**
     * Data class to hold file properties read within a read action
     */
    private data class FileProperties(
        val name: String,
        val path: String,
        val isDirectory: Boolean,
        val isValid: Boolean,
        val exists: Boolean,
        val length: Long,
        val extension: String?
    )

    /**
     * Safely reads VirtualFile properties within a read action
     */
    private fun readFileProperties(file: VirtualFile): FileProperties {
        return ReadAction.compute<FileProperties, Exception> {
            FileProperties(
                name = file.name,
                path = file.path,
                isDirectory = file.isDirectory,
                isValid = file.isValid,
                exists = file.exists(),
                length = file.length,
                extension = file.extension
            )
        }
    }

    /**
     * Processes a single file or directory, performing all exclusion checks.
     * Handles directories recursively in the background to avoid UI freezing.
     *
     * @param file The VirtualFile to process.
     * @param scope The CoroutineScope for cooperative cancellation.
     * @param localBuffer The local buffer to add file contents to.
     * @param visitedFiles The concurrent set to track visited files and prevent duplicates.
     */
    private suspend fun processEntry(
        file: VirtualFile,
        scope: CoroutineScope,
        parentJob: Job,
        localBuffer: MutableList<Pair<String, String>>,
        visitedFiles: MutableSet<VirtualFile>
    ) {
        scope.ensureActive() // Check cancellation at the start

        // --- FIX: Perform the visited check AT THE TOP and make it atomic ---
        // This is the most critical change to prevent race conditions.
        if (!visitedFiles.add(file)) {
            logger.trace("Skipping already visited/claimed entry: ${file.path}")
            return
        }
        
        // Read file properties within a read action
        val fileProps = readFileProperties(file)
        
        if (!fileProps.isValid || !fileProps.exists) {
            logger.warn("Skipping invalid or non-existent file entry: ${fileProps.path}")
            return
        }

        // --- Handle directories recursively ---
        if (fileProps.isDirectory) {
            // Early stop if file limit reached
            if (fileCount.get() >= settings.fileCount) {
                if (parentJob.isActive) parentJob.cancel("File limit reached")
                return
            }
            // Check for ignored directory names (e.g., "node_modules", "build")
            if (fileProps.name in settings.ignoredNames) {
                logger.info("Skipping ignored directory by name: ${fileProps.path}")
                excludedByIgnoredNameCount.incrementAndGet()
                return
            }
            
            // Check .gitignore for the directory itself
            try {
                val isIgnoredByGit = ReadAction.compute<Boolean, Exception> {
                    hierarchicalGitignoreParser.isIgnored(file)
                }
                if (isIgnoredByGit) {
                    logger.info("Skipping ignored directory by .gitignore: ${fileProps.path}")
                    excludedByGitignoreCount.incrementAndGet()
                    return
                }
            } catch (e: Exception) {
                logger.warn("Error checking gitignore status for directory '${fileProps.path}'. Proceeding with traversal.", e)
            }

            logger.debug("Processing directory: ${fileProps.path}")
            val children = ReadAction.compute<Array<VirtualFile>?, Exception> { file.children }
            children
                ?.sortedBy { it.path }
                ?.forEach { child ->
                    // Stop recursing if we've hit the file limit
                    if (fileCount.get() >= settings.fileCount) {
                        if (parentJob.isActive) parentJob.cancel("File limit reached")
                        return@forEach
                    }
                    scope.ensureActive()
                    // The recursive call will handle the visited check for each child
                    processEntry(child, scope, parentJob, localBuffer, visitedFiles)
                }
            return // End processing for this directory entry
        }

        // --- It's a file, proceed with file-specific checks ---
        
        // Check file limit
        if (fileCount.get() >= settings.fileCount) {
            logger.debug("File limit reached. Skipping file: ${fileProps.path}")
            return
        }

        // The rest of the file processing logic (ignored names, gitignore, size, binary, etc.)
        // can now safely assume it's only dealing with files.
        processFileWithChecks(file, fileProps, scope, parentJob, localBuffer)
    }
    
    /**
     * Processes a single file after basic checks have passed.
     * This new helper function contains the logic that was previously in processEntry.
     *
     * @param file The file VirtualFile.
     * @param fileProps The pre-read file properties.
     * @param scope The coroutine scope.
     * @param localBuffer The local buffer to add file contents to.
     */
    private suspend fun processFileWithChecks(
        file: VirtualFile,
        fileProps: FileProperties,
        scope: CoroutineScope,
        parentJob: Job,
        localBuffer: MutableList<Pair<String, String>>
    ) {
        // Calculate relative path for this file
        val relativePath = try {
            ReadAction.compute<String?, Exception> {
                FileUtils.getRelativePath(file, project)
            }
        } catch (e: Exception) {
            logger.error("Error calculating relative path for: ${fileProps.path}", e)
            null
        }

        if (relativePath == null) {
            logger.warn("Could not determine relative path for ${fileProps.path}. Skipping gitignore check.")
        }

        // Check cancellation after calculating relative path
        scope.ensureActive()

        logger.info("Processing file: '${fileProps.name}' | Relative Path: '$relativePath'")

        // --- Exclusion Checks for Files ---
        if (fileProps.name in settings.ignoredNames) {
            logger.info("Skipping ignored file by name: ${fileProps.path}")
            excludedByIgnoredNameCount.incrementAndGet()
            return
        }
        
        // Gitignore Check (override for explicitly selected files only)
        if (file !in explicitTopLevelFiles) {
            try {
                val isIgnoredByGit = ReadAction.compute<Boolean, Exception> {
                    hierarchicalGitignoreParser.isIgnored(file)
                }
                if (isIgnoredByGit) {
                    logger.info(">>> Gitignore Match: YES. Skipping '${fileProps.path}' based on hierarchical .gitignore rules.")
                    excludedByGitignoreCount.incrementAndGet()
                    return
                } else {
                    logger.info(">>> Gitignore Match: NO. Proceeding with '${fileProps.path}'.")
                }
            } catch (e: Exception) {
                logger.warn(">>> Gitignore Check: ERROR checking status for '${fileProps.path}'. File will be processed.", e)
            }
        } else {
            logger.info(">>> Gitignore Override: File was explicitly selected. Including '${fileProps.path}' despite .gitignore matches.")
        }

        // Check cancellation again after gitignore check
        scope.ensureActive()

        // Process the file - delegate to the existing processSingleFile method
        logger.debug("Processing file: ${fileProps.path}")
        processSingleFile(file, fileProps, relativePath ?: fileProps.name, scope, parentJob, localBuffer)
    }


    /**
     * Processes a single file after basic ignore checks have passed.
     * Performs size, binary, filter checks, reads content, and adds to results.
     * @param file The file VirtualFile.
     * @param fileProps The pre-read file properties.
     * @param relativePath The pre-calculated relative path (or fallback).
     * @param scope The coroutine scope.
     * @param localBuffer The local buffer to add file contents to.
     */
    private suspend fun processSingleFile(
        file: VirtualFile,
        fileProps: FileProperties,
        relativePath: String,
        scope: CoroutineScope,
        parentJob: Job,
        localBuffer: MutableList<Pair<String, String>>
    ) {
        // Note: .gitignore and ignoredNames checks are done in processEntry

        scope.ensureActive() // Check cancellation

        if (hasReachedFileLimit()) {
            logger.warn("File limit reached just before processing file details: ${fileProps.name}. Skipping.")
            return
        }

        if (skipKnownBinary(file, relativePath)) return
        if (skipOversizedFile(fileProps, relativePath)) return
        if (skipLikelyBinary(file, relativePath)) return
        if (skipFilteredFile(fileProps, relativePath)) return

        scope.ensureActive()
        val fileContent = readFileContentSafely(file, fileProps, relativePath) ?: return
        val preparedContent = applyPathPrefixIfNeeded(fileContent, file, relativePath)
        recordProcessedFile(relativePath, preparedContent, parentJob, localBuffer)
    }

    private fun buildMergedEntries(
        buffers: Collection<MutableList<Pair<String, String>>>
    ): MutableList<SourceExportFormatter.FileEntry> {
        val mergedEntries = mutableListOf<SourceExportFormatter.FileEntry>()
        buffers.forEach { buffer ->
            buffer.forEach { entry ->
                mergedEntries.add(SourceExportFormatter.FileEntry(entry.first, entry.second))
            }
        }
        mergedEntries.sortBy { it.path }
        return mergedEntries
    }

    private fun hasReachedFileLimit(): Boolean = fileCount.get() >= settings.fileCount

    private fun skipKnownBinary(file: VirtualFile, relativePath: String): Boolean {
        val isBinary = ReadAction.compute<Boolean, Exception> { FileUtils.isKnownBinaryExtension(file) }
        return if (isBinary) {
            logger.info("Skipping known binary file type: $relativePath")
            excludedByBinaryContentCount.incrementAndGet()
            true
        } else {
            false
        }
    }

    private fun skipOversizedFile(fileProps: FileProperties, relativePath: String): Boolean {
        val maxSizeInBytes = settings.maxFileSizeKb * 1024L
        return if (fileProps.length > maxSizeInBytes) {
            logger.info("Skipping file due to size limit (> ${settings.maxFileSizeKb} KB): $relativePath")
            excludedBySizeCount.incrementAndGet()
            true
        } else {
            false
        }
    }

    private fun skipLikelyBinary(file: VirtualFile, relativePath: String): Boolean {
        val isBinary = try {
            ReadAction.compute<Boolean, Exception> { FileUtils.isLikelyBinaryContent(file) }
        } catch (e: Exception) {
            logger.warn("Failed deep binary check for $relativePath, assuming binary.", e)
            true
        }
        if (isBinary) {
            logger.info("Skipping likely binary file (content check): $relativePath")
            excludedByBinaryContentCount.incrementAndGet()
        }
        return isBinary
    }

    private fun skipFilteredFile(fileProps: FileProperties, relativePath: String): Boolean {
        if (!settings.areFiltersEnabled || settings.filenameFilters.isEmpty()) return false
        val matchesFilter = settings.filenameFilters.any { filter ->
            val actualFilter = if (filter.startsWith(".")) filter else ".$filter"
            fileProps.name.endsWith(actualFilter, ignoreCase = true)
        }
        if (!matchesFilter) {
            logger.info("Skipping file due to filename filter: $relativePath")
            val fileExtension = fileProps.extension ?: "no_extension"
            excludedExtensions.add(fileExtension)
            excludedByFilterCount.incrementAndGet()
        }
        return !matchesFilter
    }

    private fun readFileContentSafely(
        file: VirtualFile,
        fileProps: FileProperties,
        relativePath: String
    ): String? {
        val content = try {
            ReadAction.compute<String, Exception> { FileUtils.readFileContent(file) }
        } catch (e: Exception) {
            logger.error("Error reading file content for $relativePath", e)
            "// Error reading file: ${fileProps.path} (${e.message})"
        }

        val prepared = if (settings.includeLineNumbers && !content.startsWith("// Error")) {
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

    private fun applyPathPrefixIfNeeded(content: String, file: VirtualFile, relativePath: String): String {
        if (!settings.includePathPrefix) return content
        if (FileUtils.hasFilenamePrefix(content)) return content

        val commentPrefix = ReadAction.compute<String, Exception> { FileUtils.getCommentPrefix(file) }
        val formattedPrefix = if (commentPrefix.endsWith("-->")) {
            commentPrefix.replace("-->", "$relativePath -->")
        } else {
            "$commentPrefix$relativePath"
        }
        return "$formattedPrefix\n$content"
    }

    private fun recordProcessedFile(
        relativePath: String,
        contentToAdd: String,
        parentJob: Job,
        localBuffer: MutableList<Pair<String, String>>
    ) {
        if (hasReachedFileLimit()) {
            logger.warn("File limit reached just before adding file: $relativePath")
            if (parentJob.isActive) parentJob.cancel("File limit reached")
            return
        }

        localBuffer.add(relativePath to contentToAdd)

        val currentFileCount = fileCount.incrementAndGet()
        val currentProcessedCount = processedFileCount.incrementAndGet()
        logger.info("Added file content ($currentProcessedCount/$currentFileCount): $relativePath")

        indicator.checkCanceled()
        val targetCount = settings.fileCount.toDouble()
        if (targetCount > 0) {
            indicator.fraction = min(1.0, currentFileCount.toDouble() / targetCount)
        }
        indicator.text = "Processed $currentProcessedCount files..."

        if (currentFileCount >= settings.fileCount && parentJob.isActive) {
            logger.info("File count limit (${settings.fileCount}) reached after adding $relativePath.")
            parentJob.cancel("File limit reached")
        }
    }
}
