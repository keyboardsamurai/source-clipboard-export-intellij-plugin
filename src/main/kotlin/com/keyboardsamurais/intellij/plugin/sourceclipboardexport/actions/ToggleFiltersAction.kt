package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions

import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.config.SourceClipboardExportSettings
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.NotificationUtils

/**
 * Toggles the global filename-filter flag exposed in [SourceClipboardExportSettings].
 *
 * The action surfaces as a simple enable/disable toggle in the *Smart Export* group and is
 * available from any project. Because it implements [DumbAware], users can still switch filters
 * on/off while indices are rebuilding, ensuring the export pipeline reads the latest setting on
 * its next invocation.
 *
 * @see SourceClipboardExportSettings
 */
class ToggleFiltersAction : AnAction(), DumbAware {

    /**
     * Flips the `areFiltersEnabled` flag and displays a status balloon so users immediately
     * understand how subsequent exports will behave.
     *
     * @param e action context supplied by the platform; must contain a project
     */
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

    /**
     * Updates the toggle text to reflect the latest persisted state and disables the action when
     * there is no project context (e.g., welcome screen).
     */
    override fun update(e: AnActionEvent) {
        super.update(e)
        val settings = SourceClipboardExportSettings.getInstance()
        val status = if (settings.state.areFiltersEnabled) "Enabled" else "Disabled"
        e.presentation.text = "Filters: $status"
        e.presentation.isEnabledAndVisible = e.project != null
    }

    /**
     * Runs [update] on the EDT because it mutates the presentation inside the swing UI tree.
     */
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT
    }
}
