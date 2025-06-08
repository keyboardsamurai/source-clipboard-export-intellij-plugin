package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.RelatedFileFinder

class ExportRecentChangesAction : AnAction("Export Recent Changes", "Export files modified in the last 24 hours", null) {
    
    private val logger = Logger.getInstance(ExportRecentChangesAction::class.java)
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        logger.info("Finding recent changes in project")
        
        val recentFiles = RelatedFileFinder.findRecentChanges(project, 24)
        logger.info("Found ${recentFiles.size} recently changed files")
        
        if (recentFiles.isEmpty()) {
            // Show notification that no recent changes found
            com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.NotificationUtils.showNotification(
                project,
                "No Recent Changes",
                "No files have been modified in the last 24 hours",
                com.intellij.notification.NotificationType.INFORMATION
            )
            return
        }
        
        // Trigger export with recent files
        SmartExportUtils.exportFiles(project, recentFiles.toTypedArray())
    }
    
    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null
    }
    
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}