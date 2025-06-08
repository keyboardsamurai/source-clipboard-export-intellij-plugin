package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

object SmartExportUtils {
    
    fun exportFiles(project: Project, files: Array<VirtualFile>) {
        val action = ActionManager.getInstance()
            .getAction("SourceClipboardExport.DumpFolderContents")
        val event = AnActionEvent.createFromDataContext(
            "SmartExport",
            null,
            DataContext { dataId ->
                when (dataId) {
                    CommonDataKeys.PROJECT.name -> project
                    CommonDataKeys.VIRTUAL_FILE_ARRAY.name -> files
                    else -> null
                }
            }
        )
        action.actionPerformed(event)
    }
}