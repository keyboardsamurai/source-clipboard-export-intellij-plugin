package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VirtualFile
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.RelatedFileFinder

class ExportWithDirectImportsAction : AnAction("Export with Direct Imports", "Export selected files along with their direct imports from the same project", null) {
    
    private val logger = Logger.getInstance(ExportWithDirectImportsAction::class.java)
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val selectedFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return
        
        logger.info("Finding direct imports for ${selectedFiles.size} selected files")
        
        val allFiles = mutableSetOf<VirtualFile>()
        allFiles.addAll(selectedFiles)
        
        // Find direct imports for each selected file
        selectedFiles.forEach { file ->
            val importFiles = RelatedFileFinder.findDirectImports(project, file)
            allFiles.addAll(importFiles)
            logger.info("Found ${importFiles.size} direct import files for ${file.name}")
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