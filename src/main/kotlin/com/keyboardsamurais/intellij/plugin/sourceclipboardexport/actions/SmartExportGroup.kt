package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.Separator
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions.groups.CodeStructureExportGroup
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions.groups.DependencyExportGroup
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions.groups.RelatedResourcesExportGroup
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions.groups.VersionHistoryExportGroup

class SmartExportGroup : ActionGroup("Export with Context", "Smart export with related files", null) {
    
    init {
        templatePresentation.setPopupGroup(true)
    }
    
    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        return arrayOf(
            DependencyExportGroup(),
            Separator.getInstance(),
            CodeStructureExportGroup(),
            Separator.getInstance(),
            RelatedResourcesExportGroup(),
            Separator.getInstance(),
            VersionHistoryExportGroup()
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