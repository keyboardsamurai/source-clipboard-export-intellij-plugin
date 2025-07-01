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
    
    private val dependencyExportGroup = DependencyExportGroup()
    private val codeStructureExportGroup = CodeStructureExportGroup()
    private val relatedResourcesExportGroup = RelatedResourcesExportGroup()
    private val versionHistoryExportGroup = VersionHistoryExportGroup()
    
    init {
        templatePresentation.setPopupGroup(true)
    }
    
    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        return arrayOf(
            dependencyExportGroup,
            Separator.getInstance(),
            codeStructureExportGroup,
            Separator.getInstance(),
            relatedResourcesExportGroup,
            Separator.getInstance(),
            versionHistoryExportGroup
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