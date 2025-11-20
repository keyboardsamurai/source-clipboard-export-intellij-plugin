package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions.groups

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions.ActionUpdateSupport
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions.ExportLastCommitAction
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions.ExportRecentChangesAction

/**
 * Groups VCS-aware export actions (recent changes and last commit) so the popup mirrors the mental
 * model users already have for history-based context.
 */
class VersionHistoryExportGroup : ActionGroup("Version History", "Export based on version control", null) {
    
    private val exportRecentChangesAction = ExportRecentChangesAction()
    private val exportLastCommitAction = ExportLastCommitAction()
    
    init {
        templatePresentation.setPopupGroup(true)
    }
    
    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        return arrayOf(
            exportRecentChangesAction,
            exportLastCommitAction
        )
    }
    
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = ActionUpdateSupport.hasProjectAndFiles(e)
    }
    
    /** Runs off the EDT because `update` inspects the current selection. */
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}
