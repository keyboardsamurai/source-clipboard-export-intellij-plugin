package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions

import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.config.SourceClipboardExportSettings
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core.SourceExporter
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.AppConstants
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.NotificationUtils
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.StringUtils
import kotlinx.coroutines.runBlocking
import java.awt.datatransfer.StringSelection

class DumpFolderContentsAction : AnAction() {

    private val logger = Logger.getInstance(DumpFolderContentsAction::class.java)

    override fun actionPerformed(e: AnActionEvent) {
        logger.info("Action initiated: DumpFolderContentsAction")
        val project = e.project
        val selectedFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)

        if (project == null || selectedFiles.isNullOrEmpty()) {
            logger.warn("Action aborted: No project found or no files/directories selected.")
            NotificationUtils.showNotification(
                project,
                "Error",
                "No files or directories selected",
                NotificationType.ERROR
            )
            return
        }

        val settingsState = SourceClipboardExportSettings.getInstance().state

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Exporting Source to Clipboard") {
            override fun run(indicator: ProgressIndicator) {
                val exporter = SourceExporter(project, settingsState, indicator)
                val result = runBlocking {
                    exporter.exportSources(selectedFiles)
                }

                indicator.text = "Finalizing..."
                if (result.content.isEmpty()) {
                    logger.warn("No file contents were collected for clipboard operation.")
                    NotificationUtils.showNotification(
                        project,
                        "Warning",
                        "No content to copy (check filters, size limits, ignored files, and selection)",
                        NotificationType.WARNING
                    )
                } else {
                    copyToClipboard(result.content, result.processedFileCount, project)
                }

                logger.info("Action completed: DumpFolderContentsAction")
                showOperationSummary(result, settingsState, project)

                if (result.limitReached) {
                    notifyFileLimitReached(settingsState.fileCount, project)
                }
            }
        })
    }

    private fun copyToClipboard(text: String, fileCount: Int, project: Project?) {
        val charCount = text.length
        val approxTokens = StringUtils.estimateTokensWithSubwordHeuristic(text)

        logger.info("Copying to clipboard. Files: $fileCount, Chars: $charCount, Approx Tokens: $approxTokens")
        try {
            CopyPasteManager.getInstance().setContents(StringSelection(text))
            NotificationUtils.showNotification(
                project,
                "Content Copied",
                "Selected content ($fileCount files, $charCount chars, ~$approxTokens tokens) copied.",
                NotificationType.INFORMATION
            )
        } catch (e: Exception) {
            logger.error("Failed to set clipboard contents", e)
            NotificationUtils.showNotification(
                project,
                "Error",
                "Failed to copy to clipboard: ${e.message}",
                NotificationType.ERROR
            )
        }
    }

    private fun notifyFileLimitReached(limit: Int, project: Project?) {
        NotificationUtils.showNotification(
            project,
            "File Limit Reached",
            "Processing stopped after reaching the limit of $limit files.",
            NotificationType.WARNING
        )
    }

    private fun showOperationSummary(
        result: SourceExporter.ExportResult,
        settings: SourceClipboardExportSettings.State,
        project: Project?
    ) {
        val summaryLines = mutableListOf<String>()
        summaryLines.add("<b>Processed files: ${result.processedFileCount}</b>")

        val totalExcluded = result.excludedByFilterCount + result.excludedBySizeCount +
                            result.excludedByBinaryContentCount + result.excludedByIgnoredNameCount +
                            result.excludedByGitignoreCount
        if (totalExcluded > 0) {
            summaryLines.add("Excluded files: $totalExcluded")
            if (result.excludedByFilterCount > 0) {
                 summaryLines.add("&nbsp;&nbsp;- By filter: ${result.excludedByFilterCount}")
                 if (result.excludedExtensions.isNotEmpty()) {
                     val topTypes = result.excludedExtensions.take(5).joinToString(", ")
                     val moreTypes = if (result.excludedExtensions.size > 5) ", ..." else ""
                     summaryLines.add("&nbsp;&nbsp;&nbsp;&nbsp;<i>Types: $topTypes$moreTypes</i>")
                 }
            }
            if (result.excludedBySizeCount > 0) summaryLines.add("&nbsp;&nbsp;- By size (> ${settings.maxFileSizeKb} KB): ${result.excludedBySizeCount}")
            if (result.excludedByBinaryContentCount > 0) summaryLines.add("&nbsp;&nbsp;- Binary content: ${result.excludedByBinaryContentCount}")
            if (result.excludedByIgnoredNameCount > 0) summaryLines.add("&nbsp;&nbsp;- Ignored name: ${result.excludedByIgnoredNameCount}")
            if (result.excludedByGitignoreCount > 0) summaryLines.add("&nbsp;&nbsp;- By .gitignore: ${result.excludedByGitignoreCount}")
        }

        if (settings.areFiltersEnabled && settings.filenameFilters.isNotEmpty()) {
            summaryLines.add("Active filters: ${settings.filenameFilters.joinToString(", ")}")
        } else if (settings.areFiltersEnabled && settings.filenameFilters.isEmpty()) {
             summaryLines.add("Filters enabled, but list is empty (all non-binary allowed).")
        } else {
             summaryLines.add("Filters disabled.")
        }

        NotificationUtils.showNotification(
            project,
            "Export Operation Summary",
            summaryLines.joinToString("<br>"),
            NotificationType.INFORMATION
        )
    }

     override fun update(e: AnActionEvent) {
        val project = e.project
        val selectedFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        e.presentation.isEnabledAndVisible = project != null && !selectedFiles.isNullOrEmpty()
    }
} 