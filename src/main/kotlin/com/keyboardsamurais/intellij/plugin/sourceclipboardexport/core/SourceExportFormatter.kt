package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.config.SourceClipboardExportSettings
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.AppConstants
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.FileUtils
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.StringUtils

/**
 * Formats the ordered file entries produced by [SourceExporter] into the requested
 * representation (plain text, Markdown, or XML) and injects optional context such as
 * directory trees or repository summaries.
 */
class SourceExportFormatter(
    private val project: Project,
    private val settings: SourceClipboardExportSettings.State
) {
    data class FileEntry(val path: String, val content: String)

    data class ExportStats(
        val processedFileCount: Int,
        val excludedByFilterCount: Int,
        val excludedBySizeCount: Int,
        val excludedByBinaryContentCount: Int,
        val excludedByIgnoredNameCount: Int,
        val excludedByGitignoreCount: Int
    )

    /**
     * Builds the final export string by optionally injecting directory trees or repository summary
     * text and formatting each file according to [settings.outputFormat].
     *
     * @param selectedFiles original selection (needed for repository stats)
     * @param entries file entries with already-prefixed content
     * @param stats counters returned from [FileTraverser]
     */
    fun buildContent(
        selectedFiles: Array<VirtualFile>,
        entries: List<FileEntry>,
        stats: ExportStats
    ): String {
        val includedPaths = entries.map { it.path }
        val contentBuilder = StringBuilder()

        if (settings.includeDirectoryStructure) {
            val directoryTree = FileUtils.generateDirectoryTree(includedPaths, settings.includeFilesInStructure)
            if (directoryTree.isNotEmpty()) {
                contentBuilder.append(directoryTree)
                contentBuilder.append("\n\n")
            }
        }

        if (settings.includeRepositorySummary) {
            val repositorySummary = RepositorySummary(
                project = project,
                selectedFiles = selectedFiles,
                fileContents = entries.map { it.content },
                processedFileCount = stats.processedFileCount,
                excludedByFilterCount = stats.excludedByFilterCount,
                excludedBySizeCount = stats.excludedBySizeCount,
                excludedByBinaryContentCount = stats.excludedByBinaryContentCount,
                excludedByIgnoredNameCount = stats.excludedByIgnoredNameCount,
                excludedByGitignoreCount = stats.excludedByGitignoreCount
            )
            val summary = repositorySummary.generateSummary(settings.outputFormat)
            contentBuilder.append(summary)
        }

        when (settings.outputFormat) {
            AppConstants.OutputFormat.PLAIN_TEXT -> {
                contentBuilder.append(entries.joinToString("\n") { it.content })
            }
            AppConstants.OutputFormat.MARKDOWN -> {
                entries.forEach { entry ->
                    val fileName = entry.path.substringAfterLast('/')
                    val extension = entry.path.substringAfterLast('.', "").lowercase()
                    val languageHint = when {
                        extension.isNotEmpty() -> AppConstants.MARKDOWN_LANGUAGE_HINTS[extension] ?: "text"
                        else -> AppConstants.MARKDOWN_LANGUAGE_HINTS[fileName]
                            ?: AppConstants.MARKDOWN_LANGUAGE_HINTS[fileName.lowercase()] ?: "text"
                    }

                    contentBuilder.append("### ${entry.path}\n\n")
                    val body = if (settings.includePathPrefix && FileUtils.hasFilenamePrefix(entry.content)) {
                        entry.content.substringAfter('\n')
                    } else {
                        entry.content
                    }
                    contentBuilder.append("```$languageHint\n")
                    contentBuilder.append(body)
                    contentBuilder.append("\n```\n\n")
                }
            }
            AppConstants.OutputFormat.XML -> {
                contentBuilder.append("<files>\n")
                entries.forEach { entry ->
                    val body = if (settings.includePathPrefix && FileUtils.hasFilenamePrefix(entry.content)) {
                        entry.content.substringAfter('\n')
                    } else {
                        entry.content
                    }
                    contentBuilder.append("  <file path=\"${StringUtils.escapeXml(entry.path)}\">\n")
                    contentBuilder.append("    <content><![CDATA[\n")
                    contentBuilder.append(body)
                    contentBuilder.append("\n    ]]></content>\n")
                    contentBuilder.append("  </file>\n")
                }
                contentBuilder.append("</files>")
            }
        }

        return contentBuilder.toString()
    }
}
