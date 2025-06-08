package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VirtualFile
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.RelatedFileFinder

class ExportWithTestsAction : AnAction("Export with Tests", "Export selected files along with their corresponding test files", null) {
    
    private val logger = Logger.getInstance(ExportWithTestsAction::class.java)
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val selectedFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return
        
        logger.info("Finding test files for ${selectedFiles.size} selected files")
        
        val allFiles = mutableSetOf<VirtualFile>()
        allFiles.addAll(selectedFiles)
        
        // Find test files for each selected file
        selectedFiles.forEach { file ->
            val testFiles = RelatedFileFinder.findTestFiles(project, file)
            allFiles.addAll(testFiles)
            logger.info("Found ${testFiles.size} test files for ${file.name}")
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