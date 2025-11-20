package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.ActionRunners
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.RelatedFileFinder

/**
 * Exports files that changed within a configurable number of hours (default 24). Relies on VFS
 * timestamps rather than VCS so it works even when Git data is unavailable.
 */
class ExportRecentChangesAction : AnAction() {
    
    init {
        templatePresentation.text = "Recent Changes"
        templatePresentation.description = "Export files modified in the last 24 hours"
    }
    
    private val logger = Logger.getInstance(ExportRecentChangesAction::class.java)
    
    /**
     * Scans the project index for recent files via [RelatedFileFinder.findRecentChanges] and sends
     * them to [SmartExportUtils]. Users are notified when no changes were detected.
     */
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        ActionRunners.runSmartBackground(project, "Finding Recent Changes") { _: ProgressIndicator ->
            logger.info("Finding recent changes in project")
            val recentFiles = RelatedFileFinder.findRecentChanges(project, 24)
            logger.info("Found ${recentFiles.size} recently changed files")

            if (recentFiles.isEmpty()) {
                com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.NotificationUtils.showNotification(
                    project,
                    "No Recent Changes",
                    "No files have been modified in the last 24 hours",
                    com.intellij.notification.NotificationType.INFORMATION
                )
                return@runSmartBackground
            }
            SmartExportUtils.exportFiles(project, recentFiles.sortedBy { it.path }.toTypedArray())
        }
    }
    
    /** Only requires a project because the action does not depend on selection context. */
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = ActionUpdateSupport.hasProject(e)
    }
    
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}
