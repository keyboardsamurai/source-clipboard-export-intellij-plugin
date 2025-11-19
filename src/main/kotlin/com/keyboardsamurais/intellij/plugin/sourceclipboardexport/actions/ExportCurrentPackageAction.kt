package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.vfs.VirtualFile
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.ActionRunners
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.RelatedFileFinder

class ExportCurrentPackageAction : AnAction() {
    
    init {
        templatePresentation.text = "Include Package Files"
        templatePresentation.description = "Export all files in the same package"
    }
    
    private val logger = Logger.getInstance(ExportCurrentPackageAction::class.java)
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val selectedFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return

        ActionRunners.runSmartBackground(project, "Finding Package Files") { indicator: ProgressIndicator ->
            logger.info("Finding package files for ${selectedFiles.size} selected files")

            val allFiles = mutableSetOf<VirtualFile>()
            allFiles.addAll(selectedFiles)

            selectedFiles.forEachIndexed { idx, file ->
                indicator.fraction = (idx.toDouble() / selectedFiles.size).coerceIn(0.0, 1.0)
                indicator.text = "Analyzing ${file.name} (${idx + 1}/${selectedFiles.size})"
                val packageFiles = RelatedFileFinder.findCurrentPackageFiles(file)
                allFiles.addAll(packageFiles)
                logger.info("Found ${packageFiles.size} package files for ${file.name}")
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
