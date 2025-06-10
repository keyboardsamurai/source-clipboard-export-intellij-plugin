package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions.groups

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions.ExportDependentsAction
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions.ExportWithDirectImportsAction
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions.ExportWithTransitiveImportsAction

class DependencyExportGroup : ActionGroup("Dependencies", "Export with dependency relationships", null) {
    
    init {
        templatePresentation.setPopupGroup(true)
    }
    
    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        return arrayOf(
            ExportWithDirectImportsAction(),
            ExportWithTransitiveImportsAction(),
            ExportDependentsAction()
        )
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