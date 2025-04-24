package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions

import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.config.SourceClipboardExportSettings
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.NotificationUtils

class ToggleFiltersAction : AnAction(), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val settings = SourceClipboardExportSettings.getInstance()
        settings.state.areFiltersEnabled = !settings.state.areFiltersEnabled

        val status = if (settings.state.areFiltersEnabled) "Enabled" else "Disabled"
        val message = "File extension filters are now ${status.lowercase()}."

        NotificationUtils.showNotification(
            e.project,
            "Filters $status",
            message,
            NotificationType.INFORMATION
        )
    }

    override fun update(e: AnActionEvent) {
        super.update(e)
        val settings = SourceClipboardExportSettings.getInstance()
        val status = if (settings.state.areFiltersEnabled) "Enabled" else "Disabled"
        e.presentation.text = "Filters: $status"
        e.presentation.isEnabledAndVisible = e.project != null
    }
} 