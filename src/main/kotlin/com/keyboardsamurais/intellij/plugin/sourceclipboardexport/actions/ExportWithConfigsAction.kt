package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VirtualFile
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.RelatedFileFinder

class ExportWithConfigsAction : AnAction("Export with Configs", "Export selected files along with related configuration files", null) {
    
    private val logger = Logger.getInstance(ExportWithConfigsAction::class.java)
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val selectedFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return
        
        logger.info("Finding config files for ${selectedFiles.size} selected files")
        
        val allFiles = mutableSetOf<VirtualFile>()
        allFiles.addAll(selectedFiles)
        
        // Find config files for each selected file
        selectedFiles.forEach { file ->
            val configFiles = RelatedFileFinder.findConfigFiles(project, file)
            allFiles.addAll(configFiles)
            logger.info("Found ${configFiles.size} config files for ${file.name}")
        }
        
        // Trigger export with all files
        SmartExportUtils.exportFiles(project, allFiles.toTypedArray())
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