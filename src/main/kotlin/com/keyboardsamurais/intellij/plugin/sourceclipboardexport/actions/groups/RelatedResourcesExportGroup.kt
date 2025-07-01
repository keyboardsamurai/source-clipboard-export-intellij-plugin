package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions.groups

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions.ExportAllTestsAction
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions.ExportWithConfigsAction
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions.ExportWithResourcesAction

class RelatedResourcesExportGroup : ActionGroup("Related Resources", "Export with related resources", null) {
    
    private val exportWithConfigsAction = ExportWithConfigsAction()
    private val exportWithResourcesAction = ExportWithResourcesAction()
    private val exportAllTestsAction = ExportAllTestsAction()
    
    init {
        templatePresentation.setPopupGroup(true)
    }
    
    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        return arrayOf(
            exportWithConfigsAction,
            exportWithResourcesAction,
            exportAllTestsAction
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