package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions.groups

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions.ExportCurrentPackageAction
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions.ExportWithImplementationsAction
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions.ExportWithTestsAction

class CodeStructureExportGroup : ActionGroup("Code Structure", "Export with structural relationships", null) {
    
    private val exportWithTestsAction = ExportWithTestsAction()
    private val exportWithImplementationsAction = ExportWithImplementationsAction()
    private val exportCurrentPackageAction = ExportCurrentPackageAction()
    
    init {
        templatePresentation.setPopupGroup(true)
    }
    
    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        return arrayOf(
            exportWithTestsAction,
            exportWithImplementationsAction,
            exportCurrentPackageAction
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