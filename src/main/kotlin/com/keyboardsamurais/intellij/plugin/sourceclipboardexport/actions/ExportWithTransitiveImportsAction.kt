package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.vfs.VirtualFile
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.ActionRunners
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.NotificationUtils
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.RelatedFileFinder

class ExportWithTransitiveImportsAction : AnAction() {
    
    init {
        templatePresentation.text = "Include Transitive Dependencies"
        templatePresentation.description = "Export selected files + complete dependency tree"
        templatePresentation.icon = com.keyboardsamurais.intellij.plugin.sourceclipboardexport.icons.DependencyIcons.TRANSITIVE_IMPORTS
    }
    
    private val logger = Logger.getInstance(ExportWithTransitiveImportsAction::class.java)
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val selectedFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return

        ActionRunners.runSmartBackground(project, "Finding Transitive Dependencies") { indicator: ProgressIndicator ->
            logger.info("Finding transitive imports for ${selectedFiles.size} selected files")

            val allFiles = mutableSetOf<VirtualFile>()
            allFiles.addAll(selectedFiles)

            var truncated = false

            selectedFiles.forEachIndexed { idx, file ->
                indicator.fraction = (idx.toDouble() / selectedFiles.size).coerceIn(0.0, 1.0)
                indicator.text = "Analyzing ${file.name} (${idx + 1}/${selectedFiles.size})"
                val importFiles = RelatedFileFinder.findTransitiveImports(project, file)
                allFiles.addAll(importFiles)
                logger.info("Found ${importFiles.size} transitive import files for ${file.name}")
                if (importFiles.size >= RelatedFileFinder.Config.importsMaxPerFile) {
                    truncated = true
                }
            }

            if (truncated) {
                NotificationUtils.showNotification(
                    project,
                    "Export Info",
                    "Dependency traversal reached configured limit for some files",
                    com.intellij.notification.NotificationType.INFORMATION
                )
            }

            SmartExportUtils.exportFiles(project, allFiles.toTypedArray())
        }
    }
    
    override fun update(e: AnActionEvent) {
        val project = e.project
        val selectedFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        e.presentation.isEnabledAndVisible = project != null && !selectedFiles.isNullOrEmpty()
    }
    
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}
