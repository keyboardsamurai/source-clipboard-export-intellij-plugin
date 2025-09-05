package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.vfs.VirtualFile
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.ActionRunners
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

        ActionRunners.runSmartBackground(project, "Finding Related Tests") { indicator: ProgressIndicator ->
            logger.info("Finding test files for ${selectedFiles.size} selected files")

            val allFiles = mutableSetOf<VirtualFile>()
            allFiles.addAll(selectedFiles)

            var testsCollected = mutableSetOf<VirtualFile>()

            selectedFiles.forEachIndexed { idx, file ->
                indicator.fraction = (idx.toDouble() / selectedFiles.size).coerceIn(0.0, 1.0)
                indicator.text = "Analyzing ${file.name} (${idx + 1}/${selectedFiles.size})"
                val testFiles = RelatedFileFinder.findTestFiles(project, file)
                testsCollected.addAll(testFiles)
                logger.info("Found ${testFiles.size} test files for ${file.name}")
            }

            var truncated = false
            val limitedTests = if (testsCollected.size > Config.maxTests) {
                truncated = true
                testsCollected.toList().sortedBy { it.path }.take(Config.maxTests).toSet()
            } else testsCollected

            if (truncated) {
                NotificationUtils.showNotification(
                    project,
                    "Export Info",
                    "Processing first ${Config.maxTests} of ${testsCollected.size} tests",
                    com.intellij.notification.NotificationType.INFORMATION
                )
            }

            allFiles.addAll(limitedTests)

            SmartExportUtils.exportFiles(project, allFiles.toTypedArray())
        }
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
