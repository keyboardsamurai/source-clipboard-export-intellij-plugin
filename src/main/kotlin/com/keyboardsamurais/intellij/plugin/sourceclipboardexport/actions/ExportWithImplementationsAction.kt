package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.vfs.VirtualFile
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.ActionRunners
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.DebugTracer
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.InheritanceFinder
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.NotificationUtils
import kotlinx.coroutines.runBlocking

/**
 * Action to export all implementations of selected interfaces/classes
 */
class ExportWithImplementationsAction : AnAction() {
    private val logger = Logger.getInstance(ExportWithImplementationsAction::class.java)
    
    init {
        templatePresentation.text = "Implementations/Subclasses"
        templatePresentation.description = "Export all implementations of selected interfaces/classes"
    }
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val selectedFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return
        logger.info("SCE[FileAction]: selected files=${selectedFiles.joinToString { it.path }}")
        
        if (selectedFiles.isEmpty()) {
            NotificationUtils.showNotification(project, "Export Error", "No files selected", com.intellij.notification.NotificationType.ERROR)
            return
        }
        
        ActionRunners.runSmartBackground(project, "Finding Implementations...") { indicator: ProgressIndicator ->
                try {
                    indicator.isIndeterminate = false
                    indicator.text = "Analyzing class hierarchy..."
                    
                    // First check if there are any inheritable types (Kotlin open/abstract and interfaces).
                    DebugTracer.start("FileAction files=${selectedFiles.map { it.path }}")
                    val stats = runBlocking { InheritanceFinder.getImplementationStats(selectedFiles, project) }
                    logger.info("SCE[FileAction]: stats interfaces=${stats.interfaceCount} abstract/open=${stats.abstractOrOpenClassCount} concrete=${stats.concreteClassCount}")
                    DebugTracer.log("SCE[FileAction]: stats interfaces=${stats.interfaceCount} abstract/open=${stats.abstractOrOpenClassCount} concrete=${stats.concreteClassCount}")
                    
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
                    logger.info("SCE[FileAction]: implCount=${implementations.size} impls=${implementations.map { it.path }}")
                    DebugTracer.log("SCE[FileAction]: implCount=${implementations.size} impls=${implementations.map { it.path }}")
                    
                    if (implementations.isEmpty()) {
                        val message = buildString {
                            append("No implementations found")
                            if (stats.hasInheritableTypes()) {
                                append(" for ")
                                if (stats.interfaceCount > 0) {
                                    append("${stats.interfaceCount} interface${if (stats.interfaceCount > 1) "s" else ""}")
                                }
                                if (stats.abstractOrOpenClassCount > 0) {
                                    if (stats.interfaceCount > 0) append(" and ")
                                    append("${stats.abstractOrOpenClassCount} abstract/open class${if (stats.abstractOrOpenClassCount > 1) "es" else ""}")
                                }
                            }
                            append("; exporting only the selected files.")
                        }
                        NotificationUtils.showNotification(project, "Export Info", message, com.intellij.notification.NotificationType.INFORMATION)
                    }
                    
                    // Combine selected files with their implementations
                    val allFiles = selectedFiles.toMutableSet().apply { addAll(implementations) }
                    logger.info("SCE[FileAction]: exporting ${allFiles.size} files: ${allFiles.map { it.path }}")
                    DebugTracer.log("SCE[FileAction]: exporting ${allFiles.size} files: ${allFiles.map { it.path }}")
                    
                    indicator.text = "Exporting ${allFiles.size} files..."
                    
                    // Export using SmartExportUtils
                    SmartExportUtils.exportFiles(
                        project,
                        allFiles.toTypedArray()
                    )
                    val logText = DebugTracer.dump()
                    javax.swing.SwingUtilities.invokeLater {
                        DebugOutputDialog(project, logText).show()
                    }
                } catch (e: Exception) {
                    val logText = DebugTracer.dump() + "\nSCE[Error]: ${e.message}\n"
                    javax.swing.SwingUtilities.invokeLater {
                        DebugOutputDialog(project, logText).show()
                    }
                    NotificationUtils.showNotification(
                        project, 
                        "Export Error",
                        "Failed to find implementations: ${e.message}",
                        com.intellij.notification.NotificationType.ERROR
                    )
                } finally {
                    DebugTracer.end()
                }
        }
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
