package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.ui

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.config.SourceClipboardExportSettings
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core.SourceExporter
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.NotificationUtils

/**
 * Handles the presentation of export results and notifications. Decouples UI logic from Actions.
 */
class ExportNotificationPresenter(private val project: Project) {

        /**
         * Shows a success balloon with size/token stats and a summary of excluded files, ensuring
         * users understand how filters affected the export.
         */
        fun showSuccessNotification(
                result: SourceExporter.ExportResult,
                formattedSize: String,
                formattedApproxTokens: String
        ) {
                val settings = SourceClipboardExportSettings.getInstance().state
                val summaryHtml = buildOperationSummaryHtml(result, settings)
                val message =
                        StringBuilder()
                                .apply {
                                        append(
                                                "Copied ${result.processedFileCount} files ($formattedSize, ~$formattedApproxTokens tokens)"
                                        )
                                        if (summaryHtml.isNotBlank()) {
                                                append("<br>")
                                                append(summaryHtml)
                                        }
                                }
                                .toString()

                NotificationUtils.showNotification(
                        project,
                        "Content Copied",
                        message,
                        NotificationType.INFORMATION
                )
        }

        /** Simpler success message used by tests and legacy code paths. */
        fun showSimpleSuccessNotification(
                fileCount: Int,
                formattedSize: String,
                formattedApproxTokens: String
        ) {
                val message =
                        "Copied $fileCount files ($formattedSize, ~$formattedApproxTokens tokens)"
                NotificationUtils.showNotification(
                        project,
                        "Content Copied",
                        message,
                        NotificationType.INFORMATION
                )
        }

        /**
         * Warns the user when the configured file-count limit stopped the exporter and offers an
         * action to open the settings dialog directly.
         */
        fun showLimitReachedNotification(limit: Int) {
                val notification =
                        NotificationUtils.createNotification(
                                "File Limit Reached",
                                "Processing stopped after reaching the limit of $limit files.",
                                NotificationType.WARNING
                        )

                notification.addAction(
                        object : NotificationAction("Open Settings") {
                                override fun actionPerformed(
                                        e: AnActionEvent,
                                        notification: Notification
                                ) {
                                        ShowSettingsUtil.getInstance()
                                                .showSettingsDialog(
                                                        project,
                                                        "com.keyboardsamurais.intellij.plugin.sourceclipboardexport.config.SourceClipboardExportConfigurable"
                                                )
                                        notification.expire()
                                }
                        }
                )

                notification.notify(project)
        }

        /**
         * Surfaces a warning when no content made it through the filters, highlighting the most
         * likely causes.
         */
        fun showEmptyContentWarning(settings: SourceClipboardExportSettings.State) {
                NotificationUtils.showNotification(
                        project,
                        "Warning",
                        "No content to copy. Check that your selection isn't:\n" +
                                "• Filtered out (Settings → Export Source to Clipboard)\n" +
                                "• Exceeding size limit (${settings.maxFileSizeKb}KB)\n" +
                                "• In ignored folders (${settings.ignoredNames.take(3).joinToString(", ")}...)\n" +
                                "• Excluded by .gitignore",
                        NotificationType.WARNING
                )
        }

        /** Indicates the background progress task was cancelled. */
        fun showCancelledNotification() {
                NotificationUtils.showNotification(
                        project,
                        "Export Cancelled",
                        "The operation was cancelled",
                        NotificationType.WARNING
                )
        }

        /** Fatal error balloon for unexpected exceptions during export. */
        fun showErrorNotification(message: String) {
                NotificationUtils.showNotification(
                        project,
                        "Export Error",
                        "Failed to export source: $message",
                        NotificationType.ERROR
                )
        }

        /** Error balloon used when the IDE cannot write to the clipboard. */
        fun showClipboardErrorNotification(message: String) {
                NotificationUtils.showNotification(
                        project,
                        "Error",
                        "Failed to copy to clipboard: $message",
                        NotificationType.ERROR
                )
        }

        /** Tells the diff action user that there is no previous export to compare against. */
        fun showNoPreviousExportNotification() {
                NotificationUtils.showNotification(
                        project,
                        "No Previous Export",
                        "No previous export found to compare against",
                        NotificationType.INFORMATION
                )
        }

        /** Confirms that the diff summary was copied to the clipboard. */
        fun showDiffCopiedNotification() {
                NotificationUtils.showNotification(
                        project,
                        "Diff Copied",
                        "Export diff copied to clipboard",
                        NotificationType.INFORMATION
                )
        }

        /** Used when "export changes only" doesn't produce any new files. */
        fun showNoChangesNotification() {
                NotificationUtils.showNotification(
                        project,
                        "No Changes",
                        "No new files to export",
                        NotificationType.INFORMATION
                )
        }

        /**
         * Builds an HTML summary list that outlines how many files were processed/excluded and why.
         * Embedded into the main success balloon.
         */
        private fun buildOperationSummaryHtml(
                result: SourceExporter.ExportResult,
                settings: SourceClipboardExportSettings.State
        ): String {
                val summaryLines = mutableListOf<String>()
                summaryLines.add("<b>Processed files: ${result.processedFileCount}</b>")

                val totalExcluded =
                        result.excludedByFilterCount +
                                result.excludedBySizeCount +
                                result.excludedByBinaryContentCount +
                                result.excludedByIgnoredNameCount +
                                result.excludedByGitignoreCount
                if (totalExcluded > 0) {
                        summaryLines.add("Excluded files: $totalExcluded")
                        if (result.excludedByFilterCount > 0) {
                                summaryLines.add(
                                        "&nbsp;&nbsp;- By filter: ${result.excludedByFilterCount}"
                                )
                                if (result.excludedExtensions.isNotEmpty()) {
                                        val topTypes =
                                                result.excludedExtensions.take(5).joinToString(", ")
                                        val moreTypes =
                                                if (result.excludedExtensions.size > 5) ", ..."
                                                else ""
                                        summaryLines.add(
                                                "&nbsp;&nbsp;&nbsp;&nbsp;<i>Types: $topTypes$moreTypes</i>"
                                        )
                                }
                        }
                        if (result.excludedBySizeCount > 0)
                                summaryLines.add(
                                        "&nbsp;&nbsp;- By size (> ${settings.maxFileSizeKb} KB): ${result.excludedBySizeCount}"
                                )
                        if (result.excludedByBinaryContentCount > 0)
                                summaryLines.add(
                                        "&nbsp;&nbsp;- Binary content: ${result.excludedByBinaryContentCount}"
                                )
                        if (result.excludedByIgnoredNameCount > 0)
                                summaryLines.add(
                                        "&nbsp;&nbsp;- Ignored name: ${result.excludedByIgnoredNameCount}"
                                )
                        if (result.excludedByGitignoreCount > 0)
                                summaryLines.add(
                                        "&nbsp;&nbsp;- By .gitignore: ${result.excludedByGitignoreCount}"
                                )
                }

                if (settings.areFiltersEnabled && settings.filenameFilters.isNotEmpty()) {
                        summaryLines.add(
                                "Active filters: ${settings.filenameFilters.joinToString(", ")}"
                        )
                } else if (settings.areFiltersEnabled && settings.filenameFilters.isEmpty()) {
                        summaryLines.add(
                                "Filters enabled, but list is empty (all non-binary allowed)."
                        )
                } else {
                        summaryLines.add("Filters disabled.")
                }

                return summaryLines.joinToString("<br>")
        }
}
