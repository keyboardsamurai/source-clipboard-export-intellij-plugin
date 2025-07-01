package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.config.SourceClipboardExportSettings
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core.ExportHistory
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core.SourceExporter
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.NotificationUtils
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.StringUtils
import kotlinx.coroutines.runBlocking
import java.awt.datatransfer.StringSelection
import javax.swing.JOptionPane

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
                "Please select files or folders in the Project view first",
                NotificationType.ERROR
            )
            return
        }

        val settingsState = SourceClipboardExportSettings.getInstance().state

        // Estimate the number of files that might be processed
        val estimatedFileCount = estimateFileCount(selectedFiles)

        // Show warning for large operations
        if (estimatedFileCount > 100) {
            val result = JOptionPane.showConfirmDialog(
                null,
                "This operation will process approximately $estimatedFileCount files.\nThis might take some time. Continue?",
                "Large Export Operation",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
            )

            if (result != JOptionPane.YES_OPTION) {
                logger.info("User cancelled large export operation")
                return
            }
        }

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Exporting Source to Clipboard") {
            override fun run(indicator: ProgressIndicator) {
                try {
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
                            "No content to copy. Check that your selection isn't:\n" +
                            "• Filtered out (Settings → Export Source to Clipboard)\n" +
                            "• Exceeding size limit (${settingsState.maxFileSizeKb}KB)\n" +
                            "• In ignored folders (${settingsState.ignoredNames.take(3).joinToString(", ")}...)\n" +
                            "• Excluded by .gitignore",
                            NotificationType.WARNING
                        )
                    } else {
                        copyToClipboard(result.content, result.processedFileCount, project, selectedFiles)
                    }

                    logger.info("Action completed: DumpFolderContentsAction")
                    showOperationSummary(result, settingsState, project)

                    if (result.limitReached) {
                        notifyFileLimitReached(settingsState.fileCount, project)
                    }
                } catch (pce: com.intellij.openapi.progress.ProcessCanceledException) {
                    // This is a normal cancellation. Rethrow it so the platform can handle it.
                    logger.info("Export operation was cancelled")
                    NotificationUtils.showNotification(
                        project,
                        "Export Cancelled",
                        "The operation was cancelled",
                        NotificationType.WARNING
                    )
                    throw pce // Important to rethrow it!
                } catch (e: Exception) {
                    // Now, this block will only catch UNEXPECTED exceptions.
                    logger.error("Error during export operation", e)
                    NotificationUtils.showNotification(
                        project,
                        "Export Error",
                        "Failed to export source: ${e.message}",
                        NotificationType.ERROR
                    )
                }
            }
        })
    }

    private fun copyToClipboard(text: String, fileCount: Int, project: Project?, selectedFiles: Array<VirtualFile>) {
        val charCount = text.length
        val approxTokens = StringUtils.estimateTokensWithSubwordHeuristic(text)
        val sizeInBytes = text.toByteArray(Charsets.UTF_8).size
        val sizeInMB = sizeInBytes / (1024.0 * 1024.0)

        logger.info("Copying to clipboard. Files: $fileCount, Chars: $charCount, Approx Tokens: $approxTokens")
        try {
            CopyPasteManager.getInstance().setContents(StringSelection(text))
            // display approxTokens in a notification with thousand separators
            val formattedApproxTokens = String.format("%,d", approxTokens)
            val formattedSize = if (sizeInMB >= 1.0) {
                String.format("%.1fMB", sizeInMB)
            } else {
                String.format("%.1fKB", sizeInBytes / 1024.0)
            }

            NotificationUtils.showNotification(
                project,
                "Content Copied",
                "Copied $fileCount files ($formattedSize, ~$formattedApproxTokens tokens)",
                NotificationType.INFORMATION
            )
            
            // Record in export history
            project?.let { proj ->
                val history = ExportHistory.getInstance(proj)
                val filePaths = selectedFiles.map { it.path }
                history.addExport(fileCount, sizeInBytes, approxTokens, filePaths)
            }
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
        val notification = NotificationUtils.createNotification(
            "File Limit Reached",
            "Processing stopped after reaching the limit of $limit files.",
            NotificationType.WARNING
        )
        
        notification.addAction(object : NotificationAction("Open Settings") {
            override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                ShowSettingsUtil.getInstance().showSettingsDialog(
                    project,
                    "com.keyboardsamurais.intellij.plugin.sourceclipboardexport.config.SourceClipboardExportConfigurable"
                )
                notification.expire()
            }
        })
        
        notification.notify(project)
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

        // Keep the original logic for visibility based on VIRTUAL_FILE_ARRAY
        val isVisible = project != null && !selectedFiles.isNullOrEmpty()
        e.presentation.isEnabledAndVisible = isVisible
    }

    /**
     * Specifies that the update method should run on a background thread (BGT).
     * This is required because accessing CommonDataKeys.VIRTUAL_FILE_ARRAY
     * is considered potentially slow by the platform and is discouraged on the EDT.
     * Running on BGT allows the platform to compute the data beforehand.
     */
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    private fun estimateFileCount(files: Array<VirtualFile>): Int {
        var count = 0
        val visited = mutableSetOf<VirtualFile>()

        fun countFiles(file: VirtualFile): Boolean {
            if (file in visited || count >= 1000) return count >= 1000
            visited.add(file)

            if (file.isDirectory) {
                file.children?.forEach { child ->
                    if (countFiles(child)) return true // Stop if we've reached the limit
                }
            } else {
                count++
                if (count >= 1000) return true
            }
            return count >= 1000
        }

        for (file in files) {
            if (countFiles(file)) break // Stop if we've reached the limit
        }

        return count
    }
} 
