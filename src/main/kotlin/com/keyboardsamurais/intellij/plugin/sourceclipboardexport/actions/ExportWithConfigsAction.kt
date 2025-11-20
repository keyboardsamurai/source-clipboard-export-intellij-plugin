package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.vfs.VirtualFile
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.ActionRunners
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.RelatedFileFinder

/**
 * Adds build/test/runtime configuration files that are likely relevant to the selected sources.
 * Useful when handing code to a teammate or LLM where the build context matters.
 */
class ExportWithConfigsAction : AnAction() {
    
    init {
        templatePresentation.text = "Include Configuration"
        templatePresentation.description = "Export selected files with configuration files"
    }
    
    private val logger = Logger.getInstance(ExportWithConfigsAction::class.java)
    
    /**
     * For each selected file resolves nearby config files using [RelatedFileFinder.findConfigFiles]
     * and eventually exports the combined list.
     */
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val selectedFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return

        ActionRunners.runSmartBackground(project, "Finding Configuration Files") { indicator: ProgressIndicator ->
            logger.info("Finding config files for ${selectedFiles.size} selected files")

            val allFiles = mutableSetOf<VirtualFile>()
            allFiles.addAll(selectedFiles)

            selectedFiles.forEachIndexed { idx, file ->
                indicator.fraction = (idx.toDouble() / selectedFiles.size).coerceIn(0.0, 1.0)
                indicator.text = "Analyzing ${file.name} (${idx + 1}/${selectedFiles.size})"
                val configFiles = RelatedFileFinder.findConfigFiles(project, file)
                allFiles.addAll(configFiles)
                logger.info("Found ${configFiles.size} config files for ${file.name}")
            }

            SmartExportUtils.exportFiles(project, allFiles.toTypedArray())
        }
    }
    
    /**
     * Requires both a project and at least one selected file so we do not offer the action in
     * irrelevant contexts (Project view empty area, welcome screen, etc.).
     */
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = ActionUpdateSupport.hasProjectAndFiles(e)
    }
    
    /** Background thread ensures `update` can safely inspect selected files. */
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}
