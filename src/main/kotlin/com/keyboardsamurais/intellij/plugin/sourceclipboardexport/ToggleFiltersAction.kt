package com.keyboardsamurais.intellij.plugin.sourceclipboardexport

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project

class ToggleFiltersAction : AnAction(), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        val settings = SourceClipboardExportSettings.getInstance()
        settings.state.areFiltersEnabled = !settings.state.areFiltersEnabled

        val status = if (settings.state.areFiltersEnabled) "enabled" else "disabled"
        e.presentation.text = "Filters: $status"

        // Notify the user about the change
        showNotification(
            e.project,
            "Filters ${if (settings.state.areFiltersEnabled) "Enabled" else "Disabled"}",
            "File extension filters are now ${if (settings.state.areFiltersEnabled) "active" else "inactive"}.",
            NotificationType.INFORMATION
        )
    }

    override fun update(e: AnActionEvent) {
        super.update(e)
        val settings = SourceClipboardExportSettings.getInstance()
        val status = if (settings.state.areFiltersEnabled) "enabled" else "disabled"
        e.presentation.text = "Filters: $status"
    }

    private fun showNotification(project: Project?, title: String, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("SourceClipboardExport")
            .createNotification(title, content, type)
            .notify(project)
    }
}
