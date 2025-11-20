package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Separator
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions.groups.CodeStructureExportGroup
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions.groups.DependencyExportGroup
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions.groups.RelatedResourcesExportGroup
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions.groups.VersionHistoryExportGroup

/**
 * Root popup group registered in `plugin.xml` that collects all high-level "Smart Export"
 * entrypoints (dependencies, structure, resources, history). When IDEA renders the action group,
 * it lazily instantiates each child group so submenus stay responsive.
 *
 * Grouping everything under a single `ActionGroup` keeps `plugin.xml` tidy and lets us reuse the
 * same enablement check for every child action.
 */
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
        e.presentation.isEnabledAndVisible = ActionUpdateSupport.hasProjectAndFiles(e)
    }
    
    /** BGT ensures IDEA can evaluate selection state without blocking the EDT. */
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}
