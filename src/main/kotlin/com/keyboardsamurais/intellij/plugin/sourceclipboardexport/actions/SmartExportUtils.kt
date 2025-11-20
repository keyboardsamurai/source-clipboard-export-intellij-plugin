package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.progress.ProgressIndicator
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

/**
 * Central coordinator that turns a set of [VirtualFile]s into clipboard-ready text. Handles
 * clipboard interaction, notifications, export history tracking, and background progress dialogs so
 * action classes can stay lean.
 */
object SmartExportUtils {
    
    /**
     * Performs a sorted, de-duplicated export of the provided files. When the IDE is available the
     * work runs under a modal progress indicator and results are copied to the clipboard and
     * recorded in [ExportHistory]. In unit-test mode the export runs synchronously without touching
     * global services.
     *
     * Example:
     * ```
     * val files = arrayOf(fileUnderCaret, relatedTest)
     * SmartExportUtils.exportFiles(project, files)
     * ```
     *
     * @param project owning project; may be used to resolve settings and show notifications
     * @param files array of `VirtualFile`s to export
     */
    fun exportFiles(project: Project, files: Array<VirtualFile>) {
        // Deduplicate and sort for deterministic ordering
        val ordered = files.toList().distinctBy { it.path }.sortedBy { it.path }

        val app = ApplicationManager.getApplication()
        if (app == null || app.isUnitTestMode) {
            // Test-friendly synchronous path without ProgressManager or App services
            try {
                val summary = performExport(project, ordered, ProgressIndicatorBase())

                NotificationUtils.showNotification(
                    project,
                    "Export completed successfully",
                    "Exported ${summary.result.processedFileCount} files (${summary.formattedSize}, ~${summary.formattedApproxTokens} tokens)",
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
                val indicator = ProgressManager.getInstance().progressIndicator ?: ProgressIndicatorBase()
                val summary = performExport(project, ordered, indicator)

                app.invokeLater {
                    val stringSelection = StringSelection(summary.result.content)
                    CopyPasteManager.getInstance().setContents(stringSelection)

                    NotificationUtils.showNotification(
                        project,
                        "Export completed successfully",
                        "Exported ${summary.result.processedFileCount} files (${summary.formattedSize}, ~${summary.formattedApproxTokens} tokens) to clipboard",
                        com.intellij.notification.NotificationType.INFORMATION
                    )

                    val history = ExportHistory.getInstance(project)
                    val filePaths = summary.result.includedPaths
                    history.addExport(summary.result.processedFileCount, summary.sizeInBytes, summary.approxTokens, filePaths)
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

    private data class ExportSummary(
        val result: SourceExporter.ExportResult,
        val approxTokens: Int,
        val sizeInBytes: Int,
        val formattedApproxTokens: String,
        val formattedSize: String
    )

    private fun performExport(
        project: Project,
        files: List<VirtualFile>,
        indicator: ProgressIndicator
    ): ExportSummary {
        val settings = SourceClipboardExportSettings.getInstance().state
        val exporter = SourceExporter(project, settings, indicator)
        val result = kotlinx.coroutines.runBlocking { exporter.exportSources(files.toTypedArray()) }

        val text = result.content
        val sizeInBytes = text.toByteArray(Charsets.UTF_8).size
        val approxTokens = StringUtils.estimateTokensWithSubwordHeuristic(text)
        val formattedApproxTokens = String.format("%,d", approxTokens)
        val formattedSize = formatSize(sizeInBytes)

        return ExportSummary(result, approxTokens, sizeInBytes, formattedApproxTokens, formattedSize)
    }

    private fun formatSize(sizeInBytes: Int): String {
        val sizeInMB = sizeInBytes / (1024.0 * 1024.0)
        return if (sizeInMB >= 1.0) {
            String.format("%.1fMB", sizeInMB)
        } else {
            String.format("%.1fKB", sizeInBytes / 1024.0)
        }
    }
}
