package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.vfs.VirtualFile
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.ActionRunners
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.DependencyFinder
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.NotificationUtils
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.RelatedFileFinder
import kotlinx.coroutines.runBlocking

/**
 * Collects both outgoing dependencies (imports) and incoming dependents for the current selection.
 * The resulting superset is forwarded to [SmartExportUtils] which keeps clipboard handling
 * consistent with the rest of the plugin.
 */
class ExportBidirectionalDependenciesAction : AnAction() {

    companion object {
        private val LOG = Logger.getInstance(ExportBidirectionalDependenciesAction::class.java)
    }

    /**
     * Performance toggles exposed via system properties. Because the action may touch hundreds of
     * files in large mono-repos we let advanced users restrict recursion depth.
     */
    object Config {
        private fun boolProp(key: String, default: Boolean) = System.getProperty(key)?.toBooleanStrictOrNull() ?: default
        private fun intProp(key: String, default: Int) = System.getProperty(key)?.toIntOrNull() ?: default
        val includeTransitive: Boolean = boolProp("sce.includeTransitive", true)
        val dependentsToProcessLimit: Int = intProp("sce.dependentsLimit", 50)
    }

    init {
        templatePresentation.text = "Include Bidirectional Dependencies"
        templatePresentation.description = "Export selected files + dependencies + reverse dependencies"
        templatePresentation.icon = com.keyboardsamurais.intellij.plugin.sourceclipboardexport.icons.DependencyIcons.BIDIRECTIONAL
    }

    /**
     * Executes dependency discovery in multiple phases (dependents, direct dependencies, optional
     * transitive dependencies for dependents) and enforces throttling to keep PSI work bounded.
     * The heavy work is wrapped in [ActionRunners.runSmartBackground] so it waits for up-to-date
     * indices.
     */
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
        
        // Defer heavy PSI work until indices are ready
        ActionRunners.runSmartBackground(project, "Finding bidirectional dependencies...") { indicator: ProgressIndicator ->
                try {
                    val allFiles = mutableSetOf<VirtualFile>()
                    allFiles.addAll(selectedFiles)
                    
                    // Validate configuration for performance
                    DependencyFinder.validateConfiguration(selectedFiles.size)
                    
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
                    indicator.text = if (Config.includeTransitive) {
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
                            if (Config.includeTransitive) {
                                RelatedFileFinder.findTransitiveImports(project, file)
                            } else {
                                RelatedFileFinder.findDirectImports(project, file)
                            }
                        }
                        LOG.info("Found ${dependencies.size} ${if (Config.includeTransitive) "transitive" else "direct"} dependencies for ${file.name}")
                        dependencies
                    }.flatten()
                    
                    allFiles.addAll(fileDependencies)
                    
                    // Phase 3: Optionally find dependencies of reverse dependencies
                    if (Config.includeTransitive && dependents.isNotEmpty()) {
                        indicator.text = "Finding dependencies of reverse dependencies..."
                        indicator.fraction = 0.7
                        
                        // Limit the number of dependents we process to avoid performance issues
                        val limit = Config.dependentsToProcessLimit
                        val dependentsToProcess = dependents.take(limit)
                        if (dependents.size > limit) {
                            LOG.info("Processing first $limit of ${dependents.size} dependents for performance")
                            NotificationUtils.showNotification(
                                project,
                                "Export Info",
                                "Processed first $limit of ${dependents.size} dependents",
                                com.intellij.notification.NotificationType.INFORMATION
                            )
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
                        return@runSmartBackground
                    }
                    
                    indicator.text = "Exporting ${allFiles.size} files..."
                    indicator.fraction = 0.9
                    
                    // Export all files (deterministic order)
                    val ordered = allFiles.toList().sortedBy { it.path }
                    SmartExportUtils.exportFiles(
                        project,
                        ordered.toTypedArray()
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
    }

    /** Makes the action available only when a project exists and at least one file is selected. */
    override fun update(e: AnActionEvent) {
        val project = e.project
        val selectedFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)

        e.presentation.isEnabled = project != null && 
                                  !selectedFiles.isNullOrEmpty() &&
                                  selectedFiles.any { !it.isDirectory }
    }

    /** BGT is required because `update` accesses `CommonDataKeys.VIRTUAL_FILE_ARRAY`. */
    override fun getActionUpdateThread() = com.intellij.openapi.actionSystem.ActionUpdateThread.BGT
}
