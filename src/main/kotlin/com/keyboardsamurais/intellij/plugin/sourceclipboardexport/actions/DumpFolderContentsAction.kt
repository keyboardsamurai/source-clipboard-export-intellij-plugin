package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions

import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.config.SourceClipboardExportSettings
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core.ExportHistory
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core.SourceExporter
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.ui.ExportNotificationPresenter
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

        val presenter = ExportNotificationPresenter(project)
        val settingsState = SourceClipboardExportSettings.getInstance().state

        // Estimate the number of files that might be processed
        val estimatedFileCount = estimateFileCount(selectedFiles)

        // Show warning for large operations
        if (estimatedFileCount > 100) {
            val result =
                    JOptionPane.showConfirmDialog(
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

        ProgressManager.getInstance()
                .run(
                        object : Task.Backgroundable(project, "Exporting Source to Clipboard") {
                            override fun run(indicator: ProgressIndicator) {
                                try {
                                    val exporter = SourceExporter(project, settingsState, indicator)
                                    val result = runBlocking {
                                        exporter.exportSources(selectedFiles)
                                    }

                                    indicator.text = "Finalizing..."
                                    if (result.content.isEmpty()) {
                                        logger.warn("No file contents were collected for clipboard operation.")
                                        presenter.showEmptyContentWarning(settingsState)
                                    } else {
                                        copyToClipboard(result.content, result, project, presenter)
                                    }

                                    logger.info("Action completed: DumpFolderContentsAction")

                                    if (result.limitReached) {
                                        presenter.showLimitReachedNotification(settingsState.fileCount)
                                    }
                                } catch (
                                        pce:
                                                com.intellij.openapi.progress.ProcessCanceledException) {
                                    // This is a normal cancellation. Rethrow it so the platform can
                                    // handle it.
                                    logger.info("Export operation was cancelled")
                                    presenter.showCancelledNotification()
                                    throw pce // Important to rethrow it!
                                } catch (e: Exception) {
                                    // Now, this block will only catch UNEXPECTED exceptions.
                                    logger.error("Error during export operation", e)
                                    presenter.showErrorNotification(e.message ?: "Unknown error")
                                }
                            }
                        }
                )
    }

    private fun copyToClipboard(
            text: String,
            fileCount: Int,
            project: Project?,
            includedPaths: List<String>,
            presenter: ExportNotificationPresenter?
    ) {
        val charCount = text.length
        val approxTokens = StringUtils.estimateTokensWithSubwordHeuristic(text)
        val sizeInBytes = text.toByteArray(Charsets.UTF_8).size
        val sizeInMB = sizeInBytes / (1024.0 * 1024.0)

        logger.info("Copying to clipboard. Files: $fileCount, Chars: $charCount, Approx Tokens: $approxTokens")
        try {
            CopyPasteManager.getInstance().setContents(StringSelection(text))
            // display approxTokens in a notification with thousand separators
            val formattedApproxTokens = String.format("%,d", approxTokens)
            val formattedSize =
                    if (sizeInMB >= 1.0) {
                        String.format("%.1fMB", sizeInMB)
                    } else {
                        String.format("%.1fKB", sizeInBytes / 1024.0)
                    }

            presenter?.showSimpleSuccessNotification(
                    fileCount,
                    formattedSize,
                    formattedApproxTokens
            )

            // Record in export history
            project?.let { proj ->
                val history = ExportHistory.getInstance(proj)
                history.addExport(fileCount, sizeInBytes, approxTokens, includedPaths)
            }
        } catch (e: Exception) {
            logger.error("Failed to set clipboard contents", e)
            presenter?.showClipboardErrorNotification(e.message ?: "Unknown error")
        }
    }

    // Overload that accepts full result to include a richer summary in notification
    private fun copyToClipboard(
            text: String,
            result: SourceExporter.ExportResult,
            project: Project?,
            presenter: ExportNotificationPresenter
    ) {
        try {
            val stringSelection = StringSelection(text)
            CopyPasteManager.getInstance().setContents(stringSelection)

            val sizeInBytes = text.toByteArray(Charsets.UTF_8).size
            val sizeInMB = sizeInBytes / (1024.0 * 1024.0)
            val formattedSize =
                    if (sizeInMB >= 1.0) {
                        String.format("%.1fMB", sizeInMB)
                    } else {
                        String.format("%.1fKB", sizeInBytes / 1024.0)
                    }
            val approxTokens = StringUtils.estimateTokensWithSubwordHeuristic(text)
            val formattedApproxTokens = String.format("%,d", approxTokens)

            presenter.showSuccessNotification(result, formattedSize, formattedApproxTokens)

            // Record in export history
            project?.let { proj ->
                val history = ExportHistory.getInstance(proj)
                history.addExport(
                        result.processedFileCount,
                        sizeInBytes,
                        approxTokens,
                        result.includedPaths
                )
            }
        } catch (e: Exception) {
            logger.error("Failed to set clipboard contents", e)
            presenter.showClipboardErrorNotification(e.message ?: "Unknown error")
        }
    }

    // Backward-compatible overload for tests expecting the old signature
    // Note: Tests should be updated to mock the presenter or use the new structure if possible.
    // But for now, we keep this and instantiate a temporary presenter if needed, or just use
    // NotificationUtils directly if project is null (unlikely in tests)
    @Suppress("unused")
    private fun copyToClipboard(
            text: String,
            fileCount: Int,
            project: Project?,
            selectedFiles: Array<VirtualFile>
    ) {
        val paths = selectedFiles.map { it.path }
        val presenter = project?.let { ExportNotificationPresenter(it) }
        copyToClipboard(text, fileCount, project, paths, presenter)
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val selectedFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)

        // Keep the original logic for visibility based on VIRTUAL_FILE_ARRAY
        val isVisible = project != null && !selectedFiles.isNullOrEmpty()
        e.presentation.isEnabledAndVisible = isVisible
    }

    /**
     * Specifies that the update method should run on a background thread (BGT). This is required
     * because accessing CommonDataKeys.VIRTUAL_FILE_ARRAY is considered potentially slow by the
     * platform and is discouraged on the EDT. Running on BGT allows the platform to compute the
     * data beforehand.
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
