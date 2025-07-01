package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.config.SourceClipboardExportSettings
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core.ExportHistory
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core.SourceExporter
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.NotificationUtils
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.StringUtils
import java.awt.datatransfer.StringSelection

object SmartExportUtils {
    
    fun exportFiles(project: Project, files: Array<VirtualFile>) {
        // Run the export process with a progress indicator
        ProgressManager.getInstance().runProcessWithProgressSynchronously({
            try {
                val settings = SourceClipboardExportSettings.getInstance().state
                val progressIndicator = ProgressManager.getInstance().progressIndicator
                val exporter = SourceExporter(project, settings, progressIndicator)
                
                // Use runBlocking to execute the suspend function synchronously
                // This is appropriate here since we're already in a background thread
                // from runProcessWithProgressSynchronously
                val result = kotlinx.coroutines.runBlocking {
                    exporter.exportSources(files)
                }
                
                // Copy to clipboard (this doesn't need read action)
                ApplicationManager.getApplication().invokeLater {
                    val stringSelection = StringSelection(result.content)
                    CopyPasteManager.getInstance().setContents(stringSelection)

                    val text = result.content
                    val fileCount = result.processedFileCount
                    val approxTokens = StringUtils.estimateTokensWithSubwordHeuristic(text)
                    val sizeInBytes = text.toByteArray(Charsets.UTF_8).size

                    val formattedApproxTokens = String.format("%,d", approxTokens)
                    val sizeInMB = sizeInBytes / (1024.0 * 1024.0)
                    val formattedSize = if (sizeInMB >= 1.0) {
                        String.format("%.1fMB", sizeInMB)
                    } else {
                        String.format("%.1fKB", sizeInBytes / 1024.0)
                    }

                    // Show success notification with token count and size
                    NotificationUtils.showNotification(
                        project,
                        "Export completed successfully",
                        "Exported $fileCount files ($formattedSize, ~$formattedApproxTokens tokens) to clipboard",
                        com.intellij.notification.NotificationType.INFORMATION
                    )

                    // Record in export history
                    val history = ExportHistory.getInstance(project)
                    val filePaths = files.map { it.path }
                    history.addExport(fileCount, sizeInBytes, approxTokens, filePaths)
                }
            } catch (pce: com.intellij.openapi.progress.ProcessCanceledException) {
                // User cancelled the operation, rethrow to allow the platform to handle it gracefully.
                throw pce
            } catch (e: Exception) {
                // Handle all other unexpected exceptions.
                ApplicationManager.getApplication().invokeLater {
                    NotificationUtils.showNotification(
                        project,
                        "Export failed",
                        "Error: ${e.message}",
                        com.intellij.notification.NotificationType.ERROR
                    )
                }
            }
        }, "Exporting Files", true, project)
    }
}