package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.config.SourceClipboardExportSettings
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core.SourceExporter
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.NotificationUtils
import java.awt.Toolkit
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
                    val toolkit = Toolkit.getDefaultToolkit()
                    toolkit.systemClipboard.setContents(stringSelection, null)
                    
                    // Show success notification
                    NotificationUtils.showNotification(
                        project,
                        "Export completed successfully",
                        "Exported ${result.processedFileCount} files to clipboard",
                        com.intellij.notification.NotificationType.INFORMATION
                    )
                }
            } catch (e: Exception) {
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