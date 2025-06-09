package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.ui

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.JBSplitter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.treeStructure.Tree
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.config.SourceClipboardExportSettings
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core.SourceExporter
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.StringUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import javax.swing.Box
import javax.swing.JCheckBox
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.JTree
import javax.swing.UIManager
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeSelectionModel

class ExportToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val settings = SourceClipboardExportSettings.getInstance()
        if (!settings.state.showExportToolWindow) {
            // Don't create content if disabled
            return
        }
        
        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(ExportToolWindow(project), "", false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project): Boolean {
        // Check if tool window should be available based on settings
        return SourceClipboardExportSettings.getInstance().state.showExportToolWindow
    }
}

class ExportToolWindow(private val project: Project) : SimpleToolWindowPanel(false, true) {
    private val fileTreeModel = DefaultTreeModel(DefaultMutableTreeNode("Project"))
    private val fileTree = Tree(fileTreeModel)
    private val previewArea = JTextArea()
    private val tokenCountLabel = JBLabel("Tokens: 0")
    private val fileSizeLabel = JBLabel("Size: 0 KB")
    private val fileCountLabel = JBLabel("Files: 0")
    
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val selectedFiles = mutableSetOf<VirtualFile>()

    init {
        setupUI()
        setupActions()
    }

    private fun setupUI() {
        // Top toolbar
        val toolbar = createToolbar()
        setToolbar(toolbar.component)

        // Main content with splitter
        val splitter = JBSplitter(true, 0.4f)
        
        // Top panel - file tree with checkboxes
        val treePanel = JPanel(BorderLayout())
        setupFileTree()
        treePanel.add(JBScrollPane(fileTree), BorderLayout.CENTER)
        
        // Stats panel
        val statsPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        statsPanel.add(fileCountLabel)
        statsPanel.add(Box.createHorizontalStrut(20))
        statsPanel.add(fileSizeLabel)
        statsPanel.add(Box.createHorizontalStrut(20))
        statsPanel.add(tokenCountLabel)
        treePanel.add(statsPanel, BorderLayout.SOUTH)
        
        splitter.firstComponent = treePanel
        
        // Bottom panel - preview
        val previewPanel = JPanel(BorderLayout())
        val previewLabel = JBLabel("Preview")
        previewPanel.add(previewLabel, BorderLayout.NORTH)
        
        previewArea.isEditable = false
        previewArea.font = UIManager.getFont("EditorPane.font")
        previewPanel.add(ScrollPaneFactory.createScrollPane(previewArea), BorderLayout.CENTER)
        
        splitter.secondComponent = previewPanel
        
        setContent(splitter)
    }

    private fun setupFileTree() {
        fileTree.cellRenderer = object : DefaultTreeCellRenderer() {
            override fun getTreeCellRendererComponent(
                tree: JTree?,
                value: Any?,
                sel: Boolean,
                expanded: Boolean,
                leaf: Boolean,
                row: Int,
                hasFocus: Boolean
            ): Component {
                val component = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus)
                if (value is DefaultMutableTreeNode) {
                    val userObject = value.userObject
                    if (userObject is FileNode) {
                        val checkbox = JCheckBox(userObject.file.name, userObject.selected)
                        checkbox.isOpaque = false
                        return checkbox
                    }
                }
                return component
            }
        }
        
        fileTree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        
        // Handle checkbox clicks
        fileTree.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                val path = fileTree.getPathForLocation(e.x, e.y) ?: return
                val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
                val fileNode = node.userObject as? FileNode ?: return
                
                fileNode.selected = !fileNode.selected
                if (fileNode.selected) {
                    selectedFiles.add(fileNode.file)
                } else {
                    selectedFiles.remove(fileNode.file)
                }
                
                fileTreeModel.nodeChanged(node)
                updatePreview()
            }
        })
    }

    private fun createToolbar(): ActionToolbar {
        val group = DefaultActionGroup()
        
        // Refresh action
        group.add(object : AnAction("Refresh", "Refresh file list", null) {
            override fun actionPerformed(e: AnActionEvent) {
                refreshFileTree()
            }
        })
        
        // Export action
        group.add(object : AnAction("Export", "Export selected files", null) {
            override fun actionPerformed(e: AnActionEvent) {
                exportSelected()
            }
        })
        
        // Clear selection
        group.add(object : AnAction("Clear", "Clear selection", null) {
            override fun actionPerformed(e: AnActionEvent) {
                clearSelection()
            }
        })
        
        group.addSeparator()
        
        // Settings action
        group.add(object : AnAction("Settings", "Open plugin settings", null) {
            override fun actionPerformed(e: AnActionEvent) {
                ShowSettingsUtil.getInstance().showSettingsDialog(
                    project,
                    "com.keyboardsamurais.intellij.plugin.sourceclipboardexport.config.SourceClipboardExportConfigurable"
                )
            }
        })
        
        return ActionManager.getInstance().createActionToolbar("ExportToolWindow", group, false)
    }

    private fun setupActions() {
        // Initial refresh
        refreshFileTree()
    }

    private fun refreshFileTree() {
        coroutineScope.launch {
            val root = DefaultMutableTreeNode("Project")
            val projectRootManager = ProjectRootManager.getInstance(project)
            val contentRoots = projectRootManager.contentRoots
            val baseDir = contentRoots.firstOrNull()
            if (baseDir != null) {
                addFilesToTree(root, baseDir)
            }
            
            fileTreeModel.setRoot(root)
            updatePreview()
        }
    }

    private fun addFilesToTree(parentNode: DefaultMutableTreeNode, file: VirtualFile) {
        if (file.isDirectory) {
            val dirNode = DefaultMutableTreeNode(FileNode(file))
            parentNode.add(dirNode)
            file.children?.forEach { child ->
                addFilesToTree(dirNode, child)
            }
        } else {
            parentNode.add(DefaultMutableTreeNode(FileNode(file)))
        }
    }

    private fun updatePreview() {
        coroutineScope.launch {
            if (selectedFiles.isEmpty()) {
                previewArea.text = "No files selected"
                updateStats(0, 0.0, 0)
                return@launch
            }

            val settings = SourceClipboardExportSettings.getInstance().state
            val mockIndicator = object : com.intellij.openapi.progress.ProgressIndicator {
                override fun start() {}
                override fun stop() {}
                override fun isRunning() = true
                override fun cancel() {}
                override fun isCanceled() = false
                override fun setText(text: String?) {}
                override fun getText() = ""
                override fun setText2(text: String?) {}
                override fun getText2() = ""
                override fun getFraction() = 0.0
                override fun setFraction(fraction: Double) {}
                override fun pushState() {}
                override fun popState() {}
                override fun isModal() = false
                override fun getModalityState() = ModalityState.nonModal()
                override fun setModalityProgress(modalityProgress: com.intellij.openapi.progress.ProgressIndicator?) {}
                override fun isIndeterminate() = false
                override fun setIndeterminate(indeterminate: Boolean) {}
                override fun checkCanceled() {}
                override fun isPopupWasShown() = false
                override fun isShowing() = false
            }
            
            val exporter = SourceExporter(project, settings, mockIndicator)
            val result = exporter.exportSources(selectedFiles.toTypedArray())
            
            previewArea.text = result.content.take(5000) + if (result.content.length > 5000) "\n\n... (truncated)" else ""
            
            val sizeInBytes = result.content.toByteArray(Charsets.UTF_8).size
            val sizeInKB = sizeInBytes / 1024.0
            val tokens = StringUtils.estimateTokensWithSubwordHeuristic(result.content)
            
            updateStats(result.processedFileCount, sizeInKB, tokens)
        }
    }

    private fun updateStats(fileCount: Int, sizeKB: Double, tokens: Int) {
        fileCountLabel.text = "Files: $fileCount"
        fileSizeLabel.text = "Size: ${String.format("%.1f", sizeKB)} KB"
        tokenCountLabel.text = "Tokens: ${String.format("%,d", tokens)}"
    }

    private fun exportSelected() {
        if (selectedFiles.isEmpty()) return
        
        coroutineScope.launch {
            val settings = SourceClipboardExportSettings.getInstance().state
            val mockIndicator = object : com.intellij.openapi.progress.ProgressIndicator {
                override fun start() {}
                override fun stop() {}
                override fun isRunning() = true
                override fun cancel() {}
                override fun isCanceled() = false
                override fun setText(text: String?) {}
                override fun getText() = ""
                override fun setText2(text: String?) {}
                override fun getText2() = ""
                override fun getFraction() = 0.0
                override fun setFraction(fraction: Double) {}
                override fun pushState() {}
                override fun popState() {}
                override fun isModal() = false
                override fun getModalityState() = ModalityState.nonModal()
                override fun setModalityProgress(modalityProgress: com.intellij.openapi.progress.ProgressIndicator?) {}
                override fun isIndeterminate() = false
                override fun setIndeterminate(indeterminate: Boolean) {}
                override fun checkCanceled() {}
                override fun isPopupWasShown() = false
                override fun isShowing() = false
            }
            
            val exporter = SourceExporter(project, settings, mockIndicator)
            val result = exporter.exportSources(selectedFiles.toTypedArray())
            
            val stringSelection = StringSelection(result.content)
            val toolkit = Toolkit.getDefaultToolkit()
            toolkit.systemClipboard.setContents(stringSelection, null)
        }
    }

    private fun clearSelection() {
        selectedFiles.clear()
        refreshFileTree()
    }

    private data class FileNode(
        val file: VirtualFile,
        var selected: Boolean = false
    )
}