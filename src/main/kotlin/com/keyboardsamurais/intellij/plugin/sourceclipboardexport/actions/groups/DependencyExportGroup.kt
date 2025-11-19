package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions.groups

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Separator
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions.ActionUpdateSupport
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions.ExportBidirectionalDependenciesAction
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions.ExportDependentsAction
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions.ExportWithDirectImportsAction
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions.ExportWithTransitiveImportsAction

class DependencyExportGroup : ActionGroup("Dependencies", "Export with dependency relationships", null) {
    
    private val exportWithDirectImportsAction = ExportWithDirectImportsAction()
    private val exportWithTransitiveImportsAction = ExportWithTransitiveImportsAction()
    private val exportDependentsAction = ExportDependentsAction()
    private val exportBidirectionalDependenciesAction = ExportBidirectionalDependenciesAction()
    
    init {
        templatePresentation.setPopupGroup(true)
    }
    
    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        return arrayOf(
            // Outgoing dependencies
            exportWithDirectImportsAction,
            exportWithTransitiveImportsAction,
            Separator.getInstance(),
            // Incoming dependencies
            exportDependentsAction,
            Separator.getInstance(),
            // Combined
            exportBidirectionalDependenciesAction
        )
    }
    
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = ActionUpdateSupport.hasProjectAndFiles(e)
    }
    
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}
