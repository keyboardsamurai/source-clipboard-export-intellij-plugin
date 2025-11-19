package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions

import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.ActionRunners
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.DependencyFinder
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.NotificationUtils
import kotlinx.coroutines.runBlocking

/**
 * Includes reverse dependencies for the current selection before invoking the exporter.
 *
 * The action is part of the *Dependencies* popup and bridges IntelliJ's [AnAction] lifecycle with
 * our PSI-heavy [DependencyFinder] utility. It logs sized buckets for easier troubleshooting and
 * notifies the user when the configured result limit is hit.
 */
class ExportDependentsAction : AnAction() {

    init {
        templatePresentation.text = "Include Reverse Dependencies"
        templatePresentation.description = "Export selected files + all files that import/use them"
        templatePresentation.icon = com.keyboardsamurais.intellij.plugin.sourceclipboardexport.icons.DependencyIcons.DEPENDENTS
    }

    private val logger = Logger.getInstance(ExportDependentsAction::class.java)

    /** Uses the background thread for update calculations because they inspect the PSI selection. */
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    /**
     * Displays the menu item only when at least one non-directory file is selected because reverse
     * dependency search is meaningless for folders.
     */
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = ActionUpdateSupport.hasProjectAndFiles(e) { files ->
            files.any { !it.isDirectory }
        }
    }

    /**
     * Resolves dependent files, merges them with the original selection, and passes the result to
     * [SmartExportUtils]. PSI work happens via [ActionRunners.runSmartBackground] which waits for
     * smart mode so indices are up to date.
     *
     * @param e action context that must contain a project and selected virtual files
     * @throws com.intellij.openapi.progress.ProcessCanceledException when users cancel the search
     */
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return
        
        if (files.isEmpty()) {
            NotificationUtils.showNotification(project, "Export Warning", "No files selected for dependent analysis", NotificationType.WARNING)
            return
        }

        // Filter to only non-directory files
        val sourceFiles = files.filter { !it.isDirectory }.toTypedArray()
        if (sourceFiles.isEmpty()) {
            NotificationUtils.showNotification(project, "Export Warning", "No valid source files selected", NotificationType.WARNING)
            return
        }

        // Log the operation
        logger.info("Starting dependent file search for ${sourceFiles.size} files")

        // Show progress dialog (wait for smart mode)
        ActionRunners.runSmartBackground(project, "Finding dependent files...") { indicator: ProgressIndicator ->
            try {
                indicator.text = "Searching for dependent files..."
                indicator.isIndeterminate = true

                // Simple performance configuration based on file count
                if (sourceFiles.size <= 2) {
                    logger.info("Small operation: analyzing ${sourceFiles.size} files")
                } else if (sourceFiles.size <= 10) {
                    logger.warn("Medium operation: analyzing ${sourceFiles.size} files")
                } else {
                    logger.warn("Large operation: analyzing ${sourceFiles.size} files - this may take a while")
                }

                val dependentFiles = runBlocking {
                    DependencyFinder.findDependents(sourceFiles, project)
                }

                // Include original files plus their dependents
                val allFilesToExport = (sourceFiles.toSet() + dependentFiles)

                if (dependentFiles.isEmpty()) {
                    NotificationUtils.showNotification(
                        project,
                        "Export Info",
                        "No dependent files found. Exporting only the selected files.",
                        NotificationType.INFORMATION
                    )
                } else if (dependentFiles.size >= DependencyFinder.Config.maxResultsPerSearch) {
                    NotificationUtils.showNotification(
                        project,
                        "Export Info",
                        "Dependent search reached configured limit (${DependencyFinder.Config.maxResultsPerSearch})",
                        NotificationType.INFORMATION
                    )
                }

                indicator.text = "Exporting ${allFilesToExport.size} files..."

                // Use the centralized export utility for consistent behavior
                SmartExportUtils.exportFiles(project, allFilesToExport.toTypedArray())
            } catch (e: Exception) {
                logger.error("Failed to find dependent files", e)
                NotificationUtils.showNotification(project, "Export Error", "Failed to find dependent files: ${e.message}", NotificationType.ERROR)
            }
        }
    }
}
