package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.DependencyFinder
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.DependencyFinderConfig
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
        ReadAction.run<Exception> {
            selectedFiles.forEachIndexed { index, file ->
                LOG.info("Selected file [$index]: ${file.name} (path: ${file.path})")
            }
        }
        
        if (selectedFiles.isEmpty()) {
            LOG.warn("No files selected for export dependents action")
            NotificationUtils.showNotification(project, "Export Error", "No files selected", com.intellij.notification.NotificationType.ERROR)
            return
        }
        
        // Filter out directories for better logging
        val (fileCount, dirCount) = ReadAction.compute<Pair<Int, Int>, Exception> {
            val files = selectedFiles.count { !it.isDirectory }
            val dirs = selectedFiles.count { it.isDirectory }
            files to dirs
        }
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
                    
                    LOG.warn("Starting dependency analysis in background task")
                    
                    // Configure DependencyFinder based on project size
                    val projectFileCount = selectedFiles.size
                    if (projectFileCount > 50) {
                        LOG.info("Large selection detected ($projectFileCount files), configuring for performance")
                        DependencyFinderConfig.configureForPerformance()
                    } else if (projectFileCount < 10) {
                        LOG.info("Small selection detected ($projectFileCount files), configuring for thoroughness")
                        DependencyFinderConfig.configureForSmallProject()
                    } else {
                        LOG.info("Medium selection detected ($projectFileCount files), using default configuration")
                        DependencyFinderConfig.resetToDefaults()
                    }
                    
                    // Find all dependents
                    val startTime = System.currentTimeMillis()
                    val dependents = runBlocking {
                        DependencyFinder.findDependents(selectedFiles, project)
                    }
                    val duration = System.currentTimeMillis() - startTime
                    
                    LOG.warn("Dependency analysis completed in ${duration}ms")
                    LOG.warn("Found ${dependents.size} dependent files")
                    ReadAction.run<Exception> {
                        dependents.forEachIndexed { index, file ->
                            LOG.warn("Dependent file [$index]: ${file.name} (path: ${file.path})")
                        }
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
                    
                    LOG.warn("Export completed successfully")
                    
                } catch (e: ReadAction.CannotReadException) {
                    // This is a control flow exception - the read action was cancelled
                    // This is normal when a write action needs to run
                    LOG.info("Dependency analysis was cancelled due to write action")
                    NotificationUtils.showNotification(
                        project,
                        "Export Cancelled",
                        "The operation was cancelled. Please try again.",
                        com.intellij.notification.NotificationType.WARNING
                    )
                } catch (e: ProcessCanceledException) {
                    // User cancelled the operation
                    LOG.info("Dependency analysis was cancelled by user")
                    NotificationUtils.showNotification(
                        project,
                        "Export Cancelled", 
                        "The operation was cancelled.",
                        com.intellij.notification.NotificationType.INFORMATION
                    )
                } catch (e: Exception) {
                    // Only log actual errors, not control flow exceptions
                    if (e !is ReadAction.CannotReadException && e !is ProcessCanceledException) {
                        LOG.error("Error during dependency analysis or export", e)
                    }
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
        
        val isEnabled = if (project != null && !selectedFiles.isNullOrEmpty()) {
            ReadAction.compute<Boolean, Exception> {
                selectedFiles.any { !it.isDirectory }
            }
        } else {
            false
        }
        
        e.presentation.isEnabled = isEnabled
        
        LOG.debug("Action update: enabled=$isEnabled, project=${project?.name}, selectedFiles=${selectedFiles?.size}")
        if (selectedFiles != null && LOG.isDebugEnabled) {
            val fileInfo = ReadAction.compute<String, Exception> {
                selectedFiles.joinToString { "${it.name}(dir=${it.isDirectory})" }
            }
            LOG.debug("Selected files for update check: $fileInfo")
        }
    }
    
    override fun getActionUpdateThread() = com.intellij.openapi.actionSystem.ActionUpdateThread.BGT
}