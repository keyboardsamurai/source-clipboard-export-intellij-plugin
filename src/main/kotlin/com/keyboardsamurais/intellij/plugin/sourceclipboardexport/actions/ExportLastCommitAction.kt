package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.ActionRunners
import git4idea.history.GitHistoryUtils
import git4idea.repo.GitRepositoryManager

/**
 * Exports every file touched by the most recent Git commit in the current project. Useful when
 * summarizing work for code review or assembling change descriptions.
 *
 * This action sits in the *Version History* group and relies on `git4idea` APIs to query commit
 * metadata before feeding the files to [SmartExportUtils].
 */
class ExportLastCommitAction : AnAction() {
    
    init {
        templatePresentation.text = "Last Commit Files"
        templatePresentation.description = "Export files from the most recent commit"
    }
    
    private val logger = Logger.getInstance(ExportLastCommitAction::class.java)
    
    /**
     * Reads the last commit via [GitHistoryUtils] and exports all `afterRevision` files. The heavy
     * lifting executes inside [ActionRunners.runSmartBackground] so it respects dumb mode and
     * reports progress.
     */
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        ActionRunners.runSmartBackground(project, "Exporting Last Commit") { _: ProgressIndicator ->
            logger.info("Finding files from last commit")
            val files = getFilesFromLastCommit(project)
            if (files.isEmpty()) {
                com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.NotificationUtils.showNotification(
                    project,
                    "No Files Found",
                    "No files found in the last commit",
                    com.intellij.notification.NotificationType.INFORMATION
                )
                return@runSmartBackground
            }

            logger.info("Found ${files.size} files from last commit")
            val fileNames = files.take(5).joinToString(", ") { it.name } + if (files.size > 5) " and ${files.size - 5} more..." else ""
            com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.NotificationUtils.showNotification(
                project,
                "Exporting Last Commit",
                "Exporting ${files.size} files: $fileNames",
                com.intellij.notification.NotificationType.INFORMATION
            )
            SmartExportUtils.exportFiles(project, files.sortedBy { it.path }.toTypedArray())
        }
    }
    
    /**
     * Queries every Git repository in the project for the most recent commit and returns the
     * unioned list of changed files.
     *
     * @return list of valid, non-directory virtual files sorted by repo order
     */
    private fun getFilesFromLastCommit(project: Project): List<VirtualFile> {
        val repositoryManager = GitRepositoryManager.getInstance(project)
        
        if (repositoryManager.repositories.isEmpty()) {
            logger.warn("No Git repositories found in project")
            return emptyList()
        }
        
        return repositoryManager.repositories.flatMap { repo ->
            try {
                // Get the last commit - only need 1 commit, not 2
                val commits = GitHistoryUtils.history(project, repo.root, "-n", "1")
                
                commits.firstOrNull()?.changes?.mapNotNull { change ->
                    // The change object holds the virtual file directly
                    change.afterRevision?.file?.virtualFile?.takeIf { it.exists() && !it.isDirectory }
                } ?: emptyList()
            } catch (e: Exception) {
                logger.error("Could not retrieve history for repository ${repo.root.path}", e)
                emptyList()
            }
        }.distinct()
    }
    
    /**
     * Only shows the command when a project is open and at least one Git repository is present.
     * Keeps the popup tidy inside non-VCS projects.
     */
    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        
        // Check if project has Git repositories
        val hasGit = try {
            val repositoryManager = GitRepositoryManager.getInstance(project)
            repositoryManager.repositories.isNotEmpty()
        } catch (e: Exception) {
            false
        }
        
        e.presentation.isEnabledAndVisible = hasGit
        
        if (hasGit) {
            e.presentation.text = "Last Commit Files"
            e.presentation.description = "Export files changed in the most recent commit"
        }
    }
    
    /** Requires BGT because `update` checks repository info. */
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}
