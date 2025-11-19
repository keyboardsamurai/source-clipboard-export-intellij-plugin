package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VirtualFile
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.NotificationUtils
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.RelatedFileFinder

class ExportWithDirectImportsAction : AnAction() {
    
    init {
        templatePresentation.text = "Include Direct Dependencies"
        templatePresentation.description = "Export selected files + their direct imports only"
        templatePresentation.icon = com.keyboardsamurais.intellij.plugin.sourceclipboardexport.icons.DependencyIcons.DIRECT_IMPORTS
    }
    
    private val logger = Logger.getInstance(ExportWithDirectImportsAction::class.java)
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val selectedFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return

        logger.info("Finding direct imports for ${selectedFiles.size} selected files")
        RelatedFileExportRunner.run(
            project = project,
            selectedFiles = selectedFiles,
            progressTitle = "Finding Direct Dependencies",
            collector = { proj, file ->
                val found = RelatedFileFinder.findDirectImports(proj, file)
                logger.info("Found ${found.size} direct import files for ${file.name}")
                found
            }
        ) { originals, additional ->
            val allFiles = mutableSetOf<VirtualFile>().apply {
                addAll(originals)
                addAll(additional)
            }

            if (additional.isEmpty()) {
                NotificationUtils.showNotification(
                    project,
                    "Export Info",
                    "No direct dependencies found",
                    com.intellij.notification.NotificationType.INFORMATION
                )
            }

            SmartExportUtils.exportFiles(project, allFiles.toTypedArray())
        }
    }
    
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = ActionUpdateSupport.hasProjectAndFiles(e)
    }
    
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}
