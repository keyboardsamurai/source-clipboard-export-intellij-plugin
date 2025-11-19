package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions.groups

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions.ActionUpdateSupport
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions.ExportAllTestsAction
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions.ExportWithConfigsAction
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions.ExportWithResourcesAction

/**
 * Collects actions that bring in non-source assets (configs, templates, extra test suites). Makes
 * it obvious that these commands expand beyond raw code.
 */
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
        e.presentation.isEnabledAndVisible = ActionUpdateSupport.hasProjectAndFiles(e)
    }
    
    /** Uses BGT because enablement logic inspects selected files. */
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}
