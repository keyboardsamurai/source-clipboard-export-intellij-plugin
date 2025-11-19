package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VirtualFile
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.NotificationUtils
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.RelatedFileFinder

class ExportWithTestsAction : AnAction() {
    
    init {
        templatePresentation.text = "Include Tests"
        templatePresentation.description = "Export selected files with their test files"
    }
    
    private val logger = Logger.getInstance(ExportWithTestsAction::class.java)
    
    object Config {
        private fun intProp(key: String, default: Int) = System.getProperty(key)?.toIntOrNull() ?: default
        val maxTests: Int = intProp("sce.tests.max", Int.MAX_VALUE)
    }

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
    
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = ActionUpdateSupport.hasProjectAndFiles(e)
    }
    
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}
