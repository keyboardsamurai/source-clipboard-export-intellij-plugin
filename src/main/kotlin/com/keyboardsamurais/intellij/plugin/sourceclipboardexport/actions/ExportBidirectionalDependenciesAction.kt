package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.vfs.VirtualFile
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.DependencyFinder
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.NotificationUtils
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.RelatedFileFinder
import kotlinx.coroutines.runBlocking

/**
 * Action to export files with both their dependents and dependencies
 */
class ExportBidirectionalDependenciesAction : AnAction() {

    companion object {
        private val LOG = Logger.getInstance(ExportBidirectionalDependenciesAction::class.java)
    }

    init {
        templatePresentation.text = "Include Bidirectional Dependencies"
        templatePresentation.description = "Export selected files with both dependencies and reverse dependencies"
        // Icon will be set in DependencyExportGroup
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val selectedFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return

        if (selectedFiles.isEmpty()) {
            NotificationUtils.showNotification(
                project, 
                "Export Error", 
                "No files selected", 
                com.intellij.notification.NotificationType.ERROR
            )
            return
        }
        
        // Always include transitive dependencies now that it's fast
        val includeTransitive = true
        
        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            "Finding bidirectional dependencies...",
            true
        ) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    val allFiles = mutableSetOf<VirtualFile>()
                    allFiles.addAll(selectedFiles)
                    
                    // Validate configuration for performance
                    DependencyFinder.validateConfiguration(project, selectedFiles.size)
                    
                    // Phase 1: Find reverse dependencies (what uses these files)
                    indicator.text = "Finding reverse dependencies..."
                    indicator.isIndeterminate = false
                    indicator.fraction = 0.1
                    
                    val dependents = runBlocking {
                        // Pass the already selected files to avoid redundant PSI parsing
                        DependencyFinder.findDependents(selectedFiles, project, allFiles)
                    }
                    LOG.info("Found ${dependents.size} reverse dependencies")
                    allFiles.addAll(dependents)
                    
                    // Phase 2: Find dependencies (what these files use)
                    indicator.text = if (includeTransitive) {
                        "Finding all dependencies (transitive)..."
                    } else {
                        "Finding direct dependencies..."
                    }
                    indicator.fraction = 0.4
                    
                    // Process files in parallel for better performance
                    val fileDependencies = selectedFiles.mapIndexed { index, file ->
                        indicator.checkCanceled()
                        indicator.fraction = 0.4 + (0.3 * index / selectedFiles.size)
                        
                        val dependencies = ReadAction.compute<List<VirtualFile>, Exception> {
                            if (includeTransitive) {
                                RelatedFileFinder.findTransitiveImports(project, file)
                            } else {
                                RelatedFileFinder.findDirectImports(project, file)
                            }
                        }
                        LOG.info("Found ${dependencies.size} ${if (includeTransitive) "transitive" else "direct"} dependencies for ${file.name}")
                        dependencies
                    }.flatten()
                    
                    allFiles.addAll(fileDependencies)
                    
                    // Phase 3: Optionally find dependencies of reverse dependencies
                    if (includeTransitive && dependents.isNotEmpty()) {
                        indicator.text = "Finding dependencies of reverse dependencies..."
                        indicator.fraction = 0.7
                        
                        // Limit the number of dependents we process to avoid performance issues
                        val dependentsToProcess = dependents.take(50)
                        if (dependents.size > 50) {
                            LOG.info("Processing first 50 of ${dependents.size} dependents for performance")
                        }
                        
                        val additionalDeps = dependentsToProcess.mapIndexed { index, dependent ->
                            indicator.checkCanceled()
                            indicator.fraction = 0.7 + (0.2 * index / dependentsToProcess.size)
                            
                            ReadAction.compute<List<VirtualFile>, Exception> {
                                RelatedFileFinder.findDirectImports(project, dependent)
                            }
                        }.flatten().toSet()
                        
                        allFiles.addAll(additionalDeps)
                    }
                    
                    // Summary
                    val originalCount = selectedFiles.size
                    val totalCount = allFiles.size
                    val addedCount = totalCount - originalCount
                    
                    LOG.info("Bidirectional export summary:")
                    LOG.info("  Original files: $originalCount")
                    LOG.info("  Added files: $addedCount")
                    LOG.info("  Total files: $totalCount")
                    
                    if (addedCount == 0) {
                        NotificationUtils.showNotification(
                            project,
                            "Export Info",
                            "No additional dependencies or reverse dependencies found",
                            com.intellij.notification.NotificationType.INFORMATION
                        )
                        return
                    }
                    
                    indicator.text = "Exporting ${allFiles.size} files..."
                    indicator.fraction = 0.9
                    
                    // Export all files
                    SmartExportUtils.exportFiles(
                        project,
                        allFiles.toTypedArray()
                    )
                    
                } catch (pce: com.intellij.openapi.progress.ProcessCanceledException) {
                    // This is a normal cancellation. Rethrow it so the platform can handle it.
                    LOG.info("Bidirectional dependency analysis was cancelled")
                    NotificationUtils.showNotification(
                        project,
                        "Export Cancelled",
                        "The operation was cancelled",
                        com.intellij.notification.NotificationType.WARNING
                    )
                    throw pce // Important to rethrow it!
                } catch (e: Exception) {
                    // Now, this block will only catch UNEXPECTED exceptions.
                    LOG.error("Error during bidirectional dependency analysis", e)
                    NotificationUtils.showNotification(
                        project,
                        "Export Error",
                        "Failed to analyze dependencies: ${e.message}",
                        com.intellij.notification.NotificationType.ERROR
                    )
                }
            }
        })
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val selectedFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)

        e.presentation.isEnabled = project != null && 
                                  !selectedFiles.isNullOrEmpty() &&
                                  selectedFiles.any { !it.isDirectory }
    }

    override fun getActionUpdateThread() = com.intellij.openapi.actionSystem.ActionUpdateThread.BGT
}