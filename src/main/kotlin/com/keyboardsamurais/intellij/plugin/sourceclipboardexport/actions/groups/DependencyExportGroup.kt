package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions.groups

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.Separator
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions.ExportBidirectionalDependenciesAction
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions.ExportDependentsAction
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions.ExportWithDirectImportsAction
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions.ExportWithTransitiveImportsAction
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.icons.DependencyIcons

class DependencyExportGroup : ActionGroup("Dependencies", "Export with dependency relationships", null) {
    
    init {
        templatePresentation.setPopupGroup(true)
    }
    
    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        // Update action presentations with better labels and icons
        val directImportsAction = ExportWithDirectImportsAction().apply {
            templatePresentation.text = "Include Direct Dependencies"
            templatePresentation.description = "Export selected files + their direct imports only"
            templatePresentation.icon = DependencyIcons.DIRECT_IMPORTS
        }
        
        val transitiveImportsAction = ExportWithTransitiveImportsAction().apply {
            templatePresentation.text = "Include Transitive Dependencies"
            templatePresentation.description = "Export selected files + complete dependency tree"
            templatePresentation.icon = DependencyIcons.TRANSITIVE_IMPORTS
        }
        
        val dependentsAction = ExportDependentsAction().apply {
            templatePresentation.text = "Include Reverse Dependencies"
            templatePresentation.description = "Export selected files + all files that import/use them"
            templatePresentation.icon = DependencyIcons.DEPENDENTS
        }
        
        val bidirectionalAction = ExportBidirectionalDependenciesAction().apply {
            templatePresentation.text = "Include Bidirectional Dependencies"
            templatePresentation.description = "Export selected files + dependencies + reverse dependencies"
            templatePresentation.icon = DependencyIcons.BIDIRECTIONAL
        }
        
        return arrayOf(
            // Outgoing dependencies
            directImportsAction,
            transitiveImportsAction,
            Separator.getInstance(),
            // Incoming dependencies
            dependentsAction,
            Separator.getInstance(),
            // Combined
            bidirectionalAction
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