package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.vfs.VirtualFile
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.ActionRunners
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.NotificationUtils
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.ResourceFinder
import kotlinx.coroutines.runBlocking

/**
 * Augments the manual selection with related resources (templates, styles, JSON configs, etc.)
 * detected by [ResourceFinder]. Useful when sharing UI features with LLMs where context such as CSS
 * or templates materially changes the output.
 */
class ExportWithResourcesAction : AnAction() {
    
    init {
        templatePresentation.text = "Templates & Styles"
        templatePresentation.description = "Export related HTML, CSS, and resource files"
    }
    
    /**
     * Resolves resource files via [ResourceFinder.findRelatedResources] and exports the union of
     * the original selection plus any matched assets. The work happens in smart mode to ensure PSI
     * indexes are available to the resource strategies.
     */
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val selectedFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return
        
        if (selectedFiles.isEmpty()) {
            NotificationUtils.showNotification(project, "Export Error", "No files selected", com.intellij.notification.NotificationType.ERROR)
            return
        }
        
        ActionRunners.runSmartBackground(project, "Finding Related Resources...") { indicator: ProgressIndicator ->
                try {
                    indicator.isIndeterminate = false
                    indicator.text = "Analyzing resource relationships..."
                    
                    // Find all related resources
                    val resources = runBlocking { ResourceFinder.findRelatedResources(selectedFiles, project) }
                    
                    if (resources.isEmpty()) {
                        NotificationUtils.showNotification(
                            project, 
                            "Export Info",
                            "No related templates, styles, or resources found for the selected files",
                            com.intellij.notification.NotificationType.INFORMATION
                        )
                        return@runSmartBackground
                    }
                    
                    // Combine selected files with their resources
                    val allFiles = selectedFiles.toMutableSet()
                    allFiles.addAll(resources)
                    
                    
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
                        "Failed to find resources: ${e.message}",
                        com.intellij.notification.NotificationType.ERROR
                    )
                }
        }
    }
    
    /**
     * Displays the action only when at least one plausible source file (not folder/binary) is
     * selected because resource heuristics rely on code-to-asset relationships.
     */
    override fun update(e: AnActionEvent) {
        val enabled = ActionUpdateSupport.hasProjectAndFiles(e) { files ->
            files.any { !it.isDirectory && isSourceFile(it) }
        }
        e.presentation.isEnabled = enabled
    }
    
    private fun isSourceFile(file: VirtualFile): Boolean {
        val extension = file.extension?.lowercase() ?: return false
        return extension in setOf(
            // Java/Kotlin
            "java", "kt", "kts",
            // JavaScript/TypeScript
            "js", "jsx", "ts", "tsx",
            // Python
            "py",
            // PHP
            "php",
            // Ruby
            "rb",
            // C#
            "cs",
            // Go
            "go"
        )
    }
    
    /** Must run off the EDT because `update` checks the selection array. */
    override fun getActionUpdateThread() = com.intellij.openapi.actionSystem.ActionUpdateThread.BGT
}
