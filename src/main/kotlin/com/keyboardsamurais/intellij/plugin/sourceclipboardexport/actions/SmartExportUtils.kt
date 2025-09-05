package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressIndicatorBase
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
        // Deduplicate and sort for deterministic ordering
        val ordered = files.toList().distinctBy { it.path }.sortedBy { it.path }

        val app = ApplicationManager.getApplication()
        if (app == null || app.isUnitTestMode) {
            // Test-friendly synchronous path without ProgressManager or App services
            try {
                val settings = SourceClipboardExportSettings.getInstance().state
                val exporter = SourceExporter(project, settings, ProgressIndicatorBase())
                val result = kotlinx.coroutines.runBlocking { exporter.exportSources(ordered.toTypedArray()) }

                val text = result.content
                val fileCount = result.processedFileCount
                val approxTokens = StringUtils.estimateTokensWithSubwordHeuristic(text)
                val sizeInBytes = text.toByteArray(Charsets.UTF_8).size
                val sizeInMB = sizeInBytes / (1024.0 * 1024.0)
                val formattedSize = if (sizeInMB >= 1.0) String.format("%.1fMB", sizeInMB) else String.format("%.1fKB", sizeInBytes / 1024.0)
                val formattedApproxTokens = String.format("%,d", approxTokens)

                NotificationUtils.showNotification(
                    project,
                    "Export completed successfully",
                    "Exported $fileCount files ($formattedSize, ~$formattedApproxTokens tokens)",
                    com.intellij.notification.NotificationType.INFORMATION
                )
                // Skip clipboard and export history when App services are unavailable
            } catch (e: Exception) {
                NotificationUtils.showNotification(
                    project,
                    "Export failed",
                    "Error: ${e.message}",
                    com.intellij.notification.NotificationType.ERROR
                )
            }
            return
        }

        // Normal path with progress UI and clipboard/history
        ProgressManager.getInstance().runProcessWithProgressSynchronously({
            try {
                val settings = SourceClipboardExportSettings.getInstance().state
                val progressIndicator = ProgressManager.getInstance().progressIndicator
                val exporter = SourceExporter(project, settings, progressIndicator)

                val result = kotlinx.coroutines.runBlocking {
                    exporter.exportSources(ordered.toTypedArray())
                }

                app.invokeLater {
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

                    NotificationUtils.showNotification(
                        project,
                        "Export completed successfully",
                        "Exported $fileCount files ($formattedSize, ~$formattedApproxTokens tokens) to clipboard",
                        com.intellij.notification.NotificationType.INFORMATION
                    )

                    val history = ExportHistory.getInstance(project)
                    val filePaths = ordered.map { it.path }
                    history.addExport(fileCount, sizeInBytes, approxTokens, filePaths)
                }
            } catch (pce: com.intellij.openapi.progress.ProcessCanceledException) {
                throw pce
            } catch (e: Exception) {
                app.invokeLater {
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
