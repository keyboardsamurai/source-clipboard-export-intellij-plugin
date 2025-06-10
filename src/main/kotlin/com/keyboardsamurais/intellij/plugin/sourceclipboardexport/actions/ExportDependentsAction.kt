package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions

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
 * Action to export files that depend on (import/use) the selected files
 */
class ExportDependentsAction : AnAction() {
    
    companion object {
        private val LOG = Logger.getInstance(ExportDependentsAction::class.java)
    }
    
    init {
        templatePresentation.text = "Dependents (What uses this)"
        templatePresentation.description = "Export files that import or depend on the selected files"
    }
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val selectedFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return
        
        LOG.warn("ExportDependentsAction TRIGGERED - This should be visible!")
        LOG.info("ExportDependentsAction triggered")
        LOG.info("Project: ${project.name}")
        LOG.info("Selected files count: ${selectedFiles.size}")
        selectedFiles.forEachIndexed { index, file ->
            LOG.info("Selected file [$index]: ${file.name} (path: ${file.path})")
        }
        
        if (selectedFiles.isEmpty()) {
            LOG.warn("No files selected for export dependents action")
            NotificationUtils.showNotification(project, "Export Error", "No files selected", com.intellij.notification.NotificationType.ERROR)
            return
        }
        
        // Filter out directories for better logging
        val fileCount = selectedFiles.count { !it.isDirectory }
        val dirCount = selectedFiles.count { it.isDirectory }
        LOG.warn("📊 File breakdown: $fileCount files, $dirCount directories")
        
        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project, 
            "Finding Dependents...", 
            true
        ) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    indicator.isIndeterminate = false
                    indicator.text = "Analyzing dependencies..."
                    
                    LOG.warn("🔍 Starting dependency analysis in background task")
                    
                    // Find all dependents
                    val startTime = System.currentTimeMillis()
                    val dependents = runBlocking {
                        DependencyFinder.findDependents(selectedFiles, project)
                    }
                    val duration = System.currentTimeMillis() - startTime
                    
                    LOG.warn("⏱️ Dependency analysis completed in ${duration}ms")
                    LOG.warn("📝 Found ${dependents.size} dependent files")
                    dependents.forEachIndexed { index, file ->
                        LOG.warn("Dependent file [$index]: ${file.name} (path: ${file.path})")
                    }
                    
                    if (dependents.isEmpty()) {
                        LOG.warn("No dependents found, showing notification to user")
                        NotificationUtils.showNotification(
                            project, 
                            "Export Info",
                            "No dependents found for the selected ${if (selectedFiles.size == 1) "file" else "files"}",
                            com.intellij.notification.NotificationType.INFORMATION
                        )
                        return
                    }
                    
                    // Combine selected files with their dependents
                    val allFiles = selectedFiles.toMutableSet()
                    val originalCount = allFiles.size
                    allFiles.addAll(dependents)
                    val totalCount = allFiles.size
                    val addedCount = totalCount - originalCount
                    
                    LOG.warn("Combined files for export:")
                    LOG.warn("  Original selected files: $originalCount")
                    LOG.warn("  Dependent files added: $addedCount")
                    LOG.warn("  Total files to export: $totalCount")
                    
                    indicator.text = "Exporting ${allFiles.size} files..."
                    
                    // Export using SourceExporter
                    LOG.warn("Starting export of combined files")
                    SmartExportUtils.exportFiles(
                        project,
                        allFiles.toTypedArray()
                    )
                    
                    LOG.warn("✅ Export completed successfully")
                    
                } catch (e: Exception) {
                    LOG.error("Error during dependency analysis or export", e)
                    NotificationUtils.showNotification(
                        project, 
                        "Export Error",
                        "Failed to find dependents: ${e.message}",
                        com.intellij.notification.NotificationType.ERROR
                    )
                }
            }
        })
    }
    
    override fun update(e: AnActionEvent) {
        val project = e.project
        val selectedFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        
        val isEnabled = project != null && 
                       !selectedFiles.isNullOrEmpty() &&
                       selectedFiles.any { !it.isDirectory }
        
        e.presentation.isEnabled = isEnabled
        
        LOG.debug("Action update: enabled=$isEnabled, project=${project?.name}, selectedFiles=${selectedFiles?.size}")
        if (selectedFiles != null && LOG.isDebugEnabled) {
            LOG.debug("Selected files for update check: ${selectedFiles.map { "${it.name}(dir=${it.isDirectory})" }}")
        }
    }
    
    override fun getActionUpdateThread() = com.intellij.openapi.actionSystem.ActionUpdateThread.BGT
}