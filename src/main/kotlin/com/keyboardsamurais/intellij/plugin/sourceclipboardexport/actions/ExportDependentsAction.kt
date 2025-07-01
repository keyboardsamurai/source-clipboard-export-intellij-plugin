package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions

import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.DependencyFinder
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.NotificationUtils
import kotlinx.coroutines.runBlocking

/**
 * Action to export files that depend on the selected files (reverse dependencies).
 */
class ExportDependentsAction : AnAction() {

    init {
        templatePresentation.text = "Include Reverse Dependencies"
        templatePresentation.description = "Export selected files + all files that import/use them"
        templatePresentation.icon = com.keyboardsamurais.intellij.plugin.sourceclipboardexport.icons.DependencyIcons.DEPENDENTS
    }

    private val logger = Logger.getInstance(ExportDependentsAction::class.java)

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        
        e.presentation.isEnabledAndVisible = project != null && 
            !files.isNullOrEmpty() && 
            files.any { !it.isDirectory }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return
        
        if (files.isEmpty()) {
            NotificationUtils.showNotification(project, "Export Warning", "No files selected for dependent analysis", NotificationType.WARNING)
            return
        }

        // Filter to only non-directory files
        val sourceFiles = files.filter { !it.isDirectory }.toTypedArray()
        if (sourceFiles.isEmpty()) {
            NotificationUtils.showNotification(project, "Export Warning", "No valid source files selected", NotificationType.WARNING)
            return
        }

        // Log the operation
        logger.info("Starting dependent file search for ${sourceFiles.size} files")

        // Show progress dialog
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Finding dependent files...", true) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    indicator.text = "Searching for dependent files..."
                    indicator.isIndeterminate = true
                    
                    // Simple performance configuration based on file count
                    if (sourceFiles.size <= 2) {
                        // Small operation - no special configuration needed
                        logger.info("Small operation: analyzing ${sourceFiles.size} files")
                    } else if (sourceFiles.size <= 10) {
                        // Medium operation - log a warning
                        logger.warn("Medium operation: analyzing ${sourceFiles.size} files")
                    } else {
                        // Large operation - warn user about potential performance impact
                        logger.warn("Large operation: analyzing ${sourceFiles.size} files - this may take a while")
                    }

                    val dependentFiles = runBlocking {
                        DependencyFinder.findDependents(sourceFiles, project)
                    }

                    // Include original files plus their dependents
                    val allFilesToExport = sourceFiles.toSet() + dependentFiles

                    if (dependentFiles.isEmpty()) {
                        NotificationUtils.showNotification(
                            project,
                            "Export Info",
                            "No dependent files found. Exporting only the selected files.",
                            NotificationType.INFORMATION
                        )
                    }

                    indicator.text = "Exporting ${allFilesToExport.size} files..."

                    // Use the centralized export utility for consistent behavior
                    SmartExportUtils.exportFiles(project, allFilesToExport.toTypedArray())
                    
                } catch (e: Exception) {
                    logger.error("Failed to find dependent files", e)
                    NotificationUtils.showNotification(project, "Export Error", "Failed to find dependent files: ${e.message}", NotificationType.ERROR)
                }
            }
        })
    }
}