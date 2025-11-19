package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VirtualFile
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.NotificationUtils
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.RelatedFileFinder

/**
 * Adds test sources to a manual selection before delegating to [SmartExportUtils].
 *
 * The action lives under the *Code Structure* export group and inspects the current selection via
 * [RelatedFileFinder.findTestFiles]. PSI work is executed via [RelatedFileExportRunner], ensuring the
 * export pipeline always runs from a consistent orchestration layer.
 */
class ExportWithTestsAction : AnAction() {
    
    init {
        templatePresentation.text = "Include Tests"
        templatePresentation.description = "Export selected files with their test files"
    }
    
    private val logger = Logger.getInstance(ExportWithTestsAction::class.java)
    
    /**
     * System-property backed guardrails for very large repositories. Exposed publicly so automated
     * tests or power users can shrink the blast radius when necessary.
     */
    object Config {
        private fun intProp(key: String, default: Int) = System.getProperty(key)?.toIntOrNull() ?: default
        val maxTests: Int = intProp("sce.tests.max", Int.MAX_VALUE)
    }

    /**
     * Collects related test files for the current selection, enforces optional truncation, and
     * finally hands off to [SmartExportUtils.exportFiles].
     *
     * @param e action context from IDEA; requires both a project and selected files
     */
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val selectedFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return

        logger.info("Finding test files for ${selectedFiles.size} selected files")
        RelatedFileExportRunner.run(
            project = project,
            selectedFiles = selectedFiles,
            progressTitle = "Finding Related Tests",
            collector = { proj, file ->
                val found = RelatedFileFinder.findTestFiles(proj, file)
                logger.info("Found ${found.size} test files for ${file.name}")
                found
            }
        ) { originals, additional ->
            var tests = additional
            if (tests.size > Config.maxTests) {
                NotificationUtils.showNotification(
                    project,
                    "Export Info",
                    "Processing first ${Config.maxTests} of ${tests.size} tests",
                    com.intellij.notification.NotificationType.INFORMATION
                )
                tests = tests.toList().sortedBy { it.path }.take(Config.maxTests).toSet()
            }

            val allFiles = mutableSetOf<VirtualFile>().apply {
                addAll(originals)
                addAll(tests)
            }

            SmartExportUtils.exportFiles(project, allFiles.toTypedArray())
        }
    }
    
    /**
     * Enables the entry only when a project is open and one or more files are selected. Visibility
     * filtering mirrors other actions so the popup menu does not show unusable commands.
     */
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = ActionUpdateSupport.hasProjectAndFiles(e)
    }
    
    /**
     * Runs [update] and collection logic on a background thread because VFS access plus PSI lookups
     * are involved.
     */
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}
