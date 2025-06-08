package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys

class SmartExportGroup : ActionGroup("Export Related Files", "Smart export with related files", null) {
    
    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        return arrayOf(
            ExportWithTestsAction(),
            ExportWithConfigsAction(),
            ExportRecentChangesAction(),
            ExportCurrentPackageAction(),
            ExportWithDirectImportsAction(),
            ExportWithTransitiveImportsAction()
        )
    }
    
    override fun isPopup(): Boolean = true
    
    override fun update(e: AnActionEvent) {
        val project = e.project
        val selectedFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        e.presentation.isEnabledAndVisible = project != null && !selectedFiles.isNullOrEmpty()
    }
    
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}