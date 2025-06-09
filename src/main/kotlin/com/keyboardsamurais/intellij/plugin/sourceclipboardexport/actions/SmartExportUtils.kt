package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.config.SourceClipboardExportSettings
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core.SourceExporter
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.NotificationUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

object SmartExportUtils {
    
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    fun exportFiles(project: Project, files: Array<VirtualFile>) {
        // Modern approach: Use SourceExporter directly instead of deprecated action invocation
        coroutineScope.launch {
            try {
                val settings = SourceClipboardExportSettings.getInstance().state
                
                // Run with progress indicator
                ProgressManager.getInstance().runProcessWithProgressSynchronously({
                    val progressIndicator = ProgressManager.getInstance().progressIndicator
                    val exporter = SourceExporter(project, settings, progressIndicator)
                    
                    // This needs to be run in a coroutine context, so we'll use the coroutine version
                    ApplicationManager.getApplication().runReadAction {
                        coroutineScope.launch {
                            try {
                                val result = exporter.exportSources(files)
                                
                                // Copy to clipboard
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
                            } catch (e: Exception) {
                                NotificationUtils.showNotification(
                                    project,
                                    "Export failed",
                                    "Error: ${e.message}",
                                    com.intellij.notification.NotificationType.ERROR
                                )
                            }
                        }
                    }
                }, "Exporting Files", true, project)
                
            } catch (e: Exception) {
                NotificationUtils.showNotification(
                    project,
                    "Export failed",
                    "Error: ${e.message}",
                    com.intellij.notification.NotificationType.ERROR
                )
            }
        }
    }
}