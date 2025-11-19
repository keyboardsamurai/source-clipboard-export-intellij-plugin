package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions

import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.config.SourceClipboardExportSettings
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core.ExportHistory
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core.SourceExporter
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.FileUtils
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.NotificationUtils
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.StringUtils
import kotlinx.coroutines.runBlocking
import java.awt.BorderLayout
import java.awt.datatransfer.StringSelection
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.SwingUtilities
import javax.swing.UIManager

class ExportDiffAction : AnAction("Export Diff", "Show differences from last export", null) {
    
    private val logger = Logger.getInstance(ExportDiffAction::class.java)
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val selectedFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return
        
        val history = ExportHistory.getInstance(project)
        val lastExport = history.getRecentExports().firstOrNull()
        
        if (lastExport == null) {
            NotificationUtils.showNotification(
                project,
                "No Previous Export",
                "No previous export found to compare against",
                NotificationType.INFORMATION
            )
            return
        }
        
        // Generate current export
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Generating Export Diff") {
            override fun run(indicator: ProgressIndicator) {
                val settingsState = SourceClipboardExportSettings.getInstance().state
                val exporter = SourceExporter(project, settingsState, indicator)
                val currentResult = runBlocking {
                    exporter.exportSources(selectedFiles)
                }
                
                SwingUtilities.invokeLater {
                    showDiffDialog(project, lastExport, currentResult, selectedFiles.toList())
                }
            }
        })
    }
    
    private fun showDiffDialog(
        project: Project,
        lastExport: ExportHistory.ExportEntry,
        currentResult: SourceExporter.ExportResult,
        selectedFiles: List<VirtualFile>
    ) {
        val dialog = ExportDiffDialog(project, lastExport, currentResult, selectedFiles)
        dialog.show()
    }
    
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = ActionUpdateSupport.hasProjectAndFiles(e)
    }
    
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}

class ExportDiffDialog(
    private val project: Project,
    private val lastExport: ExportHistory.ExportEntry,
    private val currentResult: SourceExporter.ExportResult,
    private val selectedFiles: List<VirtualFile>
) : DialogWrapper(project) {

    // Normalize paths so that legacy absolute paths in history entries are compared
    // consistently with current relative paths from the exporter.
    internal val currentPaths = normalizePaths(currentResult.includedPaths).toSet()
    internal val lastPaths = normalizeHistoricalPaths(project, lastExport.filePaths).toSet()
    
    init {
        title = "Export Diff View"
        setSize(800, 600)
        init()
    }
    
    override fun createCenterPanel(): JComponent? {
        val splitter = JBSplitter(true, 0.3f)
        
        // Top panel - statistics and changes summary
        val topPanel = createSummaryPanel()
        splitter.firstComponent = topPanel
        
        // Bottom panel - detailed diff
        val bottomPanel = createDiffPanel()
        splitter.secondComponent = bottomPanel
        
        return splitter
    }
    
    private fun createSummaryPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        
        val statsPanel = JPanel()
        statsPanel.layout = BoxLayout(statsPanel, BoxLayout.Y_AXIS)
        
        // Statistics comparison
        statsPanel.add(JBLabel("Previous Export: ${lastExport.summary}"))
        
        val currentSizeBytes = currentResult.content.toByteArray(Charsets.UTF_8).size
        val currentTokens = StringUtils.estimateTokensWithSubwordHeuristic(currentResult.content)
        val currentSizeKB = currentSizeBytes / 1024.0
        val currentSizeDisplay = if (currentSizeKB < 1024) {
            String.format("%.1f KB", currentSizeKB)
        } else {
            String.format("%.1f MB", currentSizeKB / 1024.0)
        }
        
        statsPanel.add(JBLabel("Current Export: ${currentResult.processedFileCount} files, $currentSizeDisplay, ~${String.format("%,d", currentTokens)} tokens"))
        
        // Changes summary
        val addedFiles = currentPaths - lastPaths
        val removedFiles = lastPaths - currentPaths
        val unchangedFiles = currentPaths.intersect(lastPaths)
        
        statsPanel.add(Box.createVerticalStrut(10))
        statsPanel.add(JBLabel("Changes:"))
        statsPanel.add(JBLabel("  Added: ${addedFiles.size} files"))
        statsPanel.add(JBLabel("  Removed: ${removedFiles.size} files"))
        statsPanel.add(JBLabel("  Unchanged: ${unchangedFiles.size} files"))
        
        panel.add(statsPanel, BorderLayout.NORTH)
        
        return panel
    }
    
    private fun createDiffPanel(): JComponent {
        val addedFiles = currentPaths - lastPaths.toSet()
        val removedFiles = lastPaths.toSet() - currentPaths
        
        val diffText = StringBuilder()
        
        if (addedFiles.isNotEmpty()) {
            diffText.append("ADDED FILES:\n")
            addedFiles.forEach { path ->
                diffText.append("+ $path\n")
            }
            diffText.append("\n")
        }
        
        if (removedFiles.isNotEmpty()) {
            diffText.append("REMOVED FILES:\n")
            removedFiles.forEach { path ->
                diffText.append("- $path\n")
            }
            diffText.append("\n")
        }
        
        if (addedFiles.isEmpty() && removedFiles.isEmpty()) {
            diffText.append("No file changes detected.\n")
            diffText.append("The same files are being exported as in the previous export.")
        }
        
        val textArea = JTextArea(diffText.toString())
        textArea.isEditable = false
        textArea.font = UIManager.getFont("MonoSpaced.font")
        
        return JBScrollPane(textArea)
    }
    
    override fun createActions(): Array<Action> {
        val copyDiffAction = object : AbstractAction("Copy Diff") {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                copyDiffToClipboard()
            }
        }
        
        val exportChangesAction = object : AbstractAction("Export Changes Only") {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                exportChangesOnly()
                close(OK_EXIT_CODE)
            }
        }
        
        return arrayOf(copyDiffAction, exportChangesAction, cancelAction, okAction)
    }
    
    private fun copyDiffToClipboard() {
        val addedFiles = currentPaths - lastPaths.toSet()
        val removedFiles = lastPaths.toSet() - currentPaths
        
        val diffText = StringBuilder()
        diffText.append("Export Diff Summary\n")
        diffText.append("===================\n\n")
        
        diffText.append("Previous: ${lastExport.summary}\n")
        
        val currentSizeBytes = currentResult.content.toByteArray(Charsets.UTF_8).size
        val currentTokens = StringUtils.estimateTokensWithSubwordHeuristic(currentResult.content)
        val currentSizeKB = currentSizeBytes / 1024.0
        val currentSizeDisplay = if (currentSizeKB < 1024) {
            String.format("%.1f KB", currentSizeKB)
        } else {
            String.format("%.1f MB", currentSizeKB / 1024.0)
        }
        diffText.append("Current: ${currentResult.processedFileCount} files, $currentSizeDisplay, ~${String.format("%,d", currentTokens)} tokens\n\n")
        
        if (addedFiles.isNotEmpty()) {
            diffText.append("Added Files (${addedFiles.size}):\n")
            addedFiles.forEach { path ->
                diffText.append("+ $path\n")
            }
            diffText.append("\n")
        }
        
        if (removedFiles.isNotEmpty()) {
            diffText.append("Removed Files (${removedFiles.size}):\n")
            removedFiles.forEach { path ->
                diffText.append("- $path\n")
            }
        }
        
        CopyPasteManager.getInstance().setContents(StringSelection(diffText.toString()))
        
        NotificationUtils.showNotification(
            project,
            "Diff Copied",
            "Export diff copied to clipboard",
            NotificationType.INFORMATION
        )
    }
    
    private fun exportChangesOnly() {
        val addedFiles = currentPaths - lastPaths.toSet()
        
        if (addedFiles.isEmpty()) {
            NotificationUtils.showNotification(
                project,
                "No Changes",
                "No new files to export",
                NotificationType.INFORMATION
            )
            return
        }
        
        // Resolve relative paths to VirtualFiles under the repository root
        val changedVirtualFiles = resolveVirtualFiles(addedFiles)
        SmartExportUtils.exportFiles(project, changedVirtualFiles.toTypedArray())
    }

    private fun resolveVirtualFiles(paths: Set<String>): List<VirtualFile> {
        val root = com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.FileUtils.getRepositoryRoot(project)
        if (root == null) return emptyList()
        val resolved = mutableListOf<VirtualFile>()
        for (p in paths) {
            try {
                val vf = com.intellij.openapi.vfs.VfsUtil.findRelativeFile(p, root)
                if (vf != null && vf.isValid) resolved.add(vf)
            } catch (_: Exception) {
                // Ignore failures resolving individual files
            }
        }
        return resolved
    }
}

internal fun normalizePaths(paths: List<String>): List<String> {
    return paths.map { it.replace('\\', '/') }
}

internal fun normalizeHistoricalPaths(project: Project, paths: List<String>): List<String> {
    val root = FileUtils.getRepositoryRoot(project) ?: return normalizePaths(paths)
    val rootPath = root.path.replace('\\', '/').trimEnd('/')

    return paths.map { path ->
        val normalizedPath = path.replace('\\', '/')
        if (normalizedPath.startsWith("$rootPath/")) {
            normalizedPath.removePrefix("$rootPath/")
        } else if (normalizedPath == rootPath) {
            ""
        } else {
            normalizedPath
        }
    }
}
