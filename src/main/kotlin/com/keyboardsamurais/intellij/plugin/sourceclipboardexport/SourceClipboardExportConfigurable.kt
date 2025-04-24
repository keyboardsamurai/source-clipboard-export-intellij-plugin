package com.keyboardsamurais.intellij.plugin.sourceclipboardexport

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.GridLayout
import java.awt.RenderingHints
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.DefaultCellEditor
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.JTable
import javax.swing.JTextField
import javax.swing.SpinnerNumberModel
import javax.swing.table.DefaultTableModel
import javax.swing.table.TableCellRenderer
import javax.swing.table.TableColumn

class SourceClipboardExportConfigurable : Configurable {
    private var settingsPanel: JPanel? = null
    private var fileCountSpinner: JSpinner? = null
    private var maxFileSizeSpinner: JSpinner? = null
    private var filtersTableModel: DefaultTableModel? = null
    private var filtersTable: JBTable? = null
    private var addFilterTextField: JTextField? = null
    private var ignoredNamesTextArea: JBTextArea? = null
    private var includePathPrefixCheckBox: JBCheckBox? = null

    private val project = ProjectManager.getInstance().defaultProject

    override fun createComponent(): JComponent? {
        settingsPanel = JPanel(GridBagLayout())
        val gbc = createGridBagConstraints()

        addFileLimitsPanel(gbc)
        addPathPrefixToggle(gbc)
        addFiltersPanel(gbc)
        addFiltersTable(gbc)
        addIgnoredNamesPanel(gbc)

        gbc.weighty = 0.0

        reset()
        return settingsPanel
    }

    private fun createGridBagConstraints() = GridBagConstraints().apply {
        gridx = 0
        gridy = 0
        gridwidth = 2
        fill = GridBagConstraints.HORIZONTAL
        anchor = GridBagConstraints.NORTHWEST
        weightx = 1.0
        weighty = 0.0
        insets = JBUI.insets(5)
    }

    private fun addFileLimitsPanel(gbc: GridBagConstraints) {
        val limitsPanel = JPanel(GridLayout(2, 1, 0, 5))

        val fileCountPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        val fileCountLabel = JLabel("Maximum number of files to process:").apply {
            toolTipText = "Sets the upper limit for the number of files that will be processed and copied."
        }
        fileCountSpinner = JSpinner(SpinnerNumberModel(50, 1, Int.MAX_VALUE, 1))
        fileCountPanel.add(fileCountLabel)
        fileCountPanel.add(fileCountSpinner)
        limitsPanel.add(fileCountPanel)

        val fileSizePanel = JPanel(FlowLayout(FlowLayout.LEFT))
        val fileSizeLabel = JLabel("Maximum file size to process (KB):").apply {
            toolTipText = "Skips files larger than this size (in Kilobytes)."
        }
        maxFileSizeSpinner = JSpinner(SpinnerNumberModel(100, 1, Int.MAX_VALUE, 1))
        fileSizePanel.add(fileSizeLabel)
        fileSizePanel.add(maxFileSizeSpinner)
        limitsPanel.add(fileSizePanel)

        settingsPanel!!.add(limitsPanel, gbc)
        gbc.gridy++
    }

    private fun addPathPrefixToggle(gbc: GridBagConstraints) {
        includePathPrefixCheckBox = JBCheckBox("Include '// filename: path' prefix in output").apply {
            toolTipText = "If checked, each file's content will be preceded by a comment with its relative path."
        }
        settingsPanel!!.add(includePathPrefixCheckBox, gbc)
        gbc.gridy++
    }

    private fun addFiltersPanel(gbc: GridBagConstraints) {
        val filterLabel = JLabel("Include files matching extensions (e.g., java, .kt):").apply {
            toolTipText = "Add file extensions (with or without leading dot) to include. If list is empty, all non-binary files are considered (respecting size/ignore limits)."
        }
        addFilterTextField = createStyledTextField("java or .kt")
        val addButton = createStyledButton("Add Filter")
        addButton.addActionListener { addFilter() }

        val filtersPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        filtersPanel.add(filterLabel)
        filtersPanel.add(addFilterTextField)
        filtersPanel.add(addButton)
        settingsPanel!!.add(filtersPanel, gbc)
        gbc.gridy++
    }

    private fun addFiltersTable(gbc: GridBagConstraints) {
        gbc.gridwidth = 2
        gbc.fill = GridBagConstraints.BOTH
        gbc.weighty = 0.3
        filtersTableModel = DefaultTableModel(arrayOf("Filter", "Action"), 0)
        filtersTable = createFiltersTable()
        val scrollPane = JBScrollPane(filtersTable)
        scrollPane.preferredSize = Dimension(450, 100)
        settingsPanel!!.add(scrollPane, gbc)
        gbc.gridy++
        gbc.weighty = 0.0
    }

    private fun addIgnoredNamesPanel(gbc: GridBagConstraints) {
        val ignoredLabel = JLabel("Ignored file/directory names (one per line):").apply {
            toolTipText = "Files or directories with these exact names will be skipped entirely."
        }
        ignoredNamesTextArea = JBTextArea().apply {
            rows = 4
            lineWrap = true
            wrapStyleWord = true
        }
        val scrollPane = JBScrollPane(ignoredNamesTextArea)
        scrollPane.preferredSize = Dimension(450, 80)

        val ignoredPanel = JPanel(BorderLayout(0, 5))
        ignoredPanel.add(ignoredLabel, BorderLayout.NORTH)
        ignoredPanel.add(scrollPane, BorderLayout.CENTER)

        gbc.fill = GridBagConstraints.BOTH
        gbc.weighty = 0.7
        settingsPanel!!.add(ignoredPanel, gbc)
        gbc.gridy++
    }

    private fun createFiltersTable() = JBTable(filtersTableModel).apply {
        addRemoveActionColumnIfNotExists()
        columnModel.getColumn(1).cellRenderer = ButtonRenderer()
        columnModel.getColumn(1).cellEditor = ButtonEditor(createStyledButton("Remove"))
        columnModel.getColumn(0).preferredWidth = 350
        columnModel.getColumn(1).preferredWidth = 100
        columnModel.getColumn(1).maxWidth = 120
        addMouseListenerForRemoveAction()
    }

    private fun JBTable.addRemoveActionColumnIfNotExists() {
        if (columnModel.columnCount < 2) {
            columnModel.addColumn(TableColumn().apply {
                headerValue = "Action"
            })
        }
    }

    private fun JBTable.addMouseListenerForRemoveAction() {
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val column = columnAtPoint(e.point)
                if (column == 1) {
                    val row = rowAtPoint(e.point)
                    filtersTableModel?.takeIf { row >= 0 && it.rowCount > row }?.removeRow(row)
                }
            }
        })
    }

    private fun addFilter() {
        val filterText = addFilterTextField!!.text.trim()
        if (isValidFilter(filterText) && !filterExists(filterText)) {
            filtersTableModel!!.addRow(arrayOf(filterText, "Remove"))
            addFilterTextField!!.text = ""
        } else if (!isValidFilter(filterText)) {
            JOptionPane.showMessageDialog(
                settingsPanel,
                "Invalid filter format. Filters should be like '.java' or 'kt' (alphanumeric, optionally starting with a dot).",
                "Invalid Input",
                JOptionPane.ERROR_MESSAGE
            )
        } else {
            JOptionPane.showMessageDialog(
                settingsPanel,
                "Filter '$filterText' already exists.",
                "Duplicate Filter",
                JOptionPane.WARNING_MESSAGE
            )
        }
    }

    private fun isValidFilter(filter: String): Boolean {
        return filter.matches(Regex("^\\.?\\w+$"))
    }

    private fun filterExists(filterText: String): Boolean {
        for (i in 0 until filtersTableModel!!.rowCount) {
            if (filtersTableModel!!.getValueAt(i, 0) as String == filterText) {
                return true
            }
        }
        return false
    }

    override fun isModified(): Boolean {
        val settings = SourceClipboardExportSettings.getInstance()
        val currentFilters = (0 until filtersTableModel!!.rowCount).map {
            filtersTableModel!!.getValueAt(it, 0) as String
        }
        val currentIgnoredNames = ignoredNamesTextArea?.text?.lines()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toMutableList() ?: mutableListOf()

        return fileCountSpinner!!.value != settings.state.fileCount ||
               maxFileSizeSpinner!!.value != settings.state.maxFileSizeKb ||
               includePathPrefixCheckBox!!.isSelected != settings.state.includePathPrefix ||
               settings.state.filenameFilters != currentFilters ||
               settings.state.ignoredNames != currentIgnoredNames
    }

    override fun apply() {
        if (!validateInput()) return
        val settings = SourceClipboardExportSettings.getInstance()
        settings.state.fileCount = fileCountSpinner!!.value as Int
        settings.state.maxFileSizeKb = maxFileSizeSpinner!!.value as Int
        settings.state.includePathPrefix = includePathPrefixCheckBox!!.isSelected
        settings.state.filenameFilters =
            (0 until filtersTableModel!!.rowCount).map { filtersTableModel!!.getValueAt(it, 0) as String }
                .toMutableList()
        settings.state.ignoredNames = ignoredNamesTextArea?.text?.lines()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toMutableList() ?: mutableListOf()

        LOGGER.debug("Applying settings: File count = ${settings.state.fileCount}, Max Size KB = ${settings.state.maxFileSizeKb}, Include Prefix = ${settings.state.includePathPrefix}, Filters = ${settings.state.filenameFilters.joinToString()}, Ignored = ${settings.state.ignoredNames.joinToString()}")
    }

    override fun reset() {
        val settings = SourceClipboardExportSettings.getInstance()
        fileCountSpinner!!.value = settings.state.fileCount
        maxFileSizeSpinner!!.value = settings.state.maxFileSizeKb
        includePathPrefixCheckBox!!.isSelected = settings.state.includePathPrefix

        filtersTableModel!!.rowCount = 0
        settings.state.filenameFilters.forEach { filter ->
            filtersTableModel!!.addRow(arrayOf(filter, "Remove"))
        }
        ignoredNamesTextArea!!.text = settings.state.ignoredNames.joinToString("\n")

        LOGGER.debug("Resetting settings UI to: File count = ${settings.state.fileCount}, Max Size KB = ${settings.state.maxFileSizeKb}, Include Prefix = ${settings.state.includePathPrefix}, Filters = ${settings.state.filenameFilters.joinToString()}, Ignored = ${settings.state.ignoredNames.joinToString()}")
    }

    override fun getDisplayName(): String {
        return "Source Clipboard Export"
    }

    private fun validateInput(): Boolean {
        val fileCount = fileCountSpinner?.value as? Int ?: 0
        if (fileCount <= 0) {
            JOptionPane.showMessageDialog(settingsPanel, "File count must be a positive integer.", "Invalid Input", JOptionPane.ERROR_MESSAGE)
            return false
        }

        val maxFileSize = maxFileSizeSpinner?.value as? Int ?: 0
        if (maxFileSize <= 0) {
            JOptionPane.showMessageDialog(settingsPanel, "Maximum file size (KB) must be a positive integer.", "Invalid Input", JOptionPane.ERROR_MESSAGE)
            return false
        }

        val invalidFilters = (0 until filtersTableModel!!.rowCount)
            .map { filtersTableModel!!.getValueAt(it, 0) as String }
            .filter { !isValidFilter(it) }

        if (invalidFilters.isNotEmpty()) {
            JOptionPane.showMessageDialog(settingsPanel, "Invalid filters found in table: ${invalidFilters.joinToString(", ")}\nFilters should be like '.java' or 'kt'.", "Invalid Input", JOptionPane.ERROR_MESSAGE)
            return false
        }

        return true
    }

    private fun createStyledTextField(placeholder: String): JTextField {
        return PlaceholderTextField(placeholder).apply {
            preferredSize = Dimension(150, preferredSize.height)
        }
    }

    private fun createStyledButton(text: String): JButton {
        return JButton(text)
    }

    inner class ButtonRenderer : JButton(), TableCellRenderer {
        init {
            isOpaque = true
            border = BorderFactory.createEmptyBorder(2, 5, 2, 5)
        }

        override fun getTableCellRendererComponent(
            table: JTable, value: Any?,
            isSelected: Boolean, hasFocus: Boolean,
            row: Int, column: Int
        ): Component {
            text = value?.toString() ?: "Remove"
            background = if (isSelected) table.selectionBackground else table.background
            foreground = if (isSelected) table.selectionForeground else table.foreground
            return this
        }
    }

    inner class ButtonEditor(private val btn: JButton) : DefaultCellEditor(JCheckBox()) {
        private var clickedRow: Int = -1
        private var clickedColumn: Int = -1

        init {
            btn.isFocusPainted = false
            btn.isContentAreaFilled = true
            btn.isOpaque = true
            btn.border = BorderFactory.createEmptyBorder(2, 5, 2, 5)

            btn.action = object : AbstractAction() {
                override fun actionPerformed(e: ActionEvent) {
                    fireEditingStopped()
                }
            }
        }

        override fun getTableCellEditorComponent(
            table: JTable, value: Any,
            isSelected: Boolean, row: Int, column: Int
        ): Component {
            clickedRow = row
            clickedColumn = column
            btn.text = "Remove"
            btn.background = table.selectionBackground
            btn.foreground = table.selectionForeground
            return btn
        }

        override fun getCellEditorValue(): Any {
            return "Remove"
        }

        override fun stopCellEditing(): Boolean {
            return super.stopCellEditing()
        }

        override fun fireEditingStopped() {
            super.fireEditingStopped()
        }
    }

    companion object {
        private val LOGGER = Logger.getInstance(SourceClipboardExportConfigurable::class.java)
    }
}

class PlaceholderTextField(private val placeholder: String) : JTextField() {
    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        if (text.isEmpty() && !hasFocus()) {
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.color = JBColor.GRAY
            val fm = g.fontMetrics
            val y = (height - fm.height) / 2 + fm.ascent
            g.drawString(placeholder, insets.left, y)
        }
    }

    init {
        addFocusListener(object : java.awt.event.FocusAdapter() {
            override fun focusGained(e: java.awt.event.FocusEvent?) {
                repaint()
            }
            override fun focusLost(e: java.awt.event.FocusEvent?) {
                repaint()
            }
        })
    }
}
