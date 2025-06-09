package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import java.io.File

class ExportLastCommitAction : AnAction("Last Commit Files", "Export files from the most recent commit", null) {
    
    private val logger = Logger.getInstance(ExportLastCommitAction::class.java)
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        logger.info("Finding files from last commit")
        
        val files = getFilesFromLastCommit(project)
        
        if (files.isEmpty()) {
            com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.NotificationUtils.showNotification(
                project,
                "No Files Found",
                "No files found in the last commit",
                com.intellij.notification.NotificationType.INFORMATION
            )
            return
        }
        
        logger.info("Found ${files.size} files from last commit")
        
        // Show what we're about to export
        val fileNames = files.take(5).joinToString(", ") { it.name } + 
                       if (files.size > 5) " and ${files.size - 5} more..." else ""
        
        com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.NotificationUtils.showNotification(
            project,
            "Exporting Last Commit",
            "Exporting ${files.size} files: $fileNames",
            com.intellij.notification.NotificationType.INFORMATION
        )
        
        // Trigger export with files from last commit
        SmartExportUtils.exportFiles(project, files.toTypedArray())
    }
    
    private fun getFilesFromLastCommit(project: Project): List<VirtualFile> {
        val files = mutableListOf<VirtualFile>()
        
        try {
            // Get the project base directory
            val projectDir = project.basePath ?: return emptyList()
            
            // Check if this is a git repository
            val gitDir = File(projectDir, ".git")
            if (!gitDir.exists() || !gitDir.isDirectory) {
                logger.warn("No Git repository found in project")
                return emptyList()
            }
            
            // Run git command to get list of files changed in last commit
            val processBuilder = ProcessBuilder()
                .command("git", "diff", "--name-only", "HEAD~1", "HEAD")
                .directory(File(projectDir))
            
            val process = processBuilder.start()
            val result = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            
            if (exitCode == 0) {
                // Process the output - each line is a file path
                result.lines().forEach { line ->
                    if (line.isNotBlank()) {
                        // Convert relative path to VirtualFile
                        val filePath = "$projectDir/${line.trim()}"
                        val virtualFile = VirtualFileManager.getInstance()
                            .findFileByUrl("file://$filePath")
                        
                        if (virtualFile != null && virtualFile.exists() && !virtualFile.isDirectory) {
                            files.add(virtualFile)
                            logger.debug("Added file from last commit: ${virtualFile.path}")
                        }
                    }
                }
            } else {
                logger.warn("Failed to get files from last commit: exit code $exitCode")
            }
            
        } catch (e: Exception) {
            logger.error("Error getting files from last commit", e)
        }
        
        return files
    }
    
    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        
        // Check if project has Git
        val hasGit = try {
            val projectDir = project.basePath
            projectDir != null && File(projectDir, ".git").exists()
        } catch (e: Exception) {
            false
        }
        
        e.presentation.isEnabledAndVisible = hasGit
        
        if (hasGit) {
            e.presentation.text = "Last Commit Files"
            e.presentation.description = "Export files changed in the most recent commit"
        }
    }
    
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}