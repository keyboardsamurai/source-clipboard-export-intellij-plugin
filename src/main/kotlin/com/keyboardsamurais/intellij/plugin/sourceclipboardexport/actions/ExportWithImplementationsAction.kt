package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.vfs.VirtualFile
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.InheritanceFinder
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.NotificationUtils
import kotlinx.coroutines.runBlocking

/**
 * Action to export all implementations of selected interfaces/classes
 */
class ExportWithImplementationsAction : AnAction() {
    
    init {
        templatePresentation.text = "Implementations/Subclasses"
        templatePresentation.description = "Export all implementations of selected interfaces/classes"
    }
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val selectedFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return
        
        if (selectedFiles.isEmpty()) {
            NotificationUtils.showNotification(project, "Export Error", "No files selected", com.intellij.notification.NotificationType.ERROR)
            return
        }
        
        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project, 
            "Finding Implementations...", 
            true
        ) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    indicator.isIndeterminate = false
                    indicator.text = "Analyzing class hierarchy..."
                    
                    // First check if there are any inheritable types
                    val stats = runBlocking {
                        InheritanceFinder.getImplementationStats(selectedFiles, project)
                    }
                    
                    if (!stats.hasInheritableTypes()) {
                        NotificationUtils.showNotification(
                            project, 
                            "Export Info",
                            "No interfaces or abstract classes found in the selected files",
                            com.intellij.notification.NotificationType.INFORMATION
                        )
                        return
                    }
                    
                    indicator.text = "Finding implementations..."
                    
                    // Find all implementations
                    val implementations = runBlocking {
                        InheritanceFinder.findImplementations(
                            selectedFiles, 
                            project,
                            includeAnonymous = true,
                            includeTest = true
                        )
                    }
                    
                    if (implementations.isEmpty()) {
                        val message = buildString {
                            append("No implementations found for ")
                            if (stats.interfaceCount > 0) {
                                append("${stats.interfaceCount} interface${if (stats.interfaceCount > 1) "s" else ""}")
                            }
                            if (stats.abstractClassCount > 0) {
                                if (stats.interfaceCount > 0) append(" and ")
                                append("${stats.abstractClassCount} abstract class${if (stats.abstractClassCount > 1) "es" else ""}")
                            }
                        }
                        NotificationUtils.showNotification(project, "Export Info", message, com.intellij.notification.NotificationType.INFORMATION)
                        return
                    }
                    
                    // Combine selected files with their implementations
                    val allFiles = selectedFiles.toMutableSet()
                    allFiles.addAll(implementations)
                    
                    indicator.text = "Exporting ${allFiles.size} files..."
                    
                    // Export using SmartExportUtils
                    SmartExportUtils.exportFiles(
                        project,
                        allFiles.toTypedArray()
                    )
                    
                } catch (e: Exception) {
                    NotificationUtils.showNotification(
                        project, 
                        "Export Error",
                        "Failed to find implementations: ${e.message}",
                        com.intellij.notification.NotificationType.ERROR
                    )
                }
            }
        })
    }
    
    override fun update(e: AnActionEvent) {
        val project = e.project
        val selectedFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        
        // Only enable for files (not directories) in projects with Java/Kotlin support
        e.presentation.isEnabled = project != null && 
                                  !selectedFiles.isNullOrEmpty() &&
                                  selectedFiles.any { !it.isDirectory && isJvmFile(it) }
    }
    
    private fun isJvmFile(file: VirtualFile): Boolean {
        val extension = file.extension?.lowercase() ?: return false
        return extension in setOf("java", "kt", "kts")
    }
    
    override fun getActionUpdateThread() = com.intellij.openapi.actionSystem.ActionUpdateThread.BGT
}