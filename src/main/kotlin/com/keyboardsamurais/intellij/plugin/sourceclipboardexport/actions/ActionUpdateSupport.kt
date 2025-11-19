package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.vfs.VirtualFile

/**
 * Shared helper for action `update` methods so that the "project + selection"
 * rules live in one place instead of being duplicated by every action.
 */
object ActionUpdateSupport {
    fun hasProjectAndFiles(
        event: AnActionEvent,
        predicate: (Array<VirtualFile>) -> Boolean = { true }
    ): Boolean {
        if (event.project == null) return false
        val files = event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return false
        if (files.isEmpty()) return false
        return predicate(files)
    }

    fun hasProject(event: AnActionEvent): Boolean = event.project != null
}
