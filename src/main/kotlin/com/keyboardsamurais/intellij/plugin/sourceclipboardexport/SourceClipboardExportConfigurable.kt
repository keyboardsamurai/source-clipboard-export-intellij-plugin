package com.keyboardsamurais.intellij.plugin.sourceclipboardexport

import SourceClipboardExportSettings
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.table.DefaultTableModel
import javax.swing.table.TableCellRenderer
import javax.swing.table.TableColumn

class SourceClipboardExportConfigurable : Configurable {
    private var settingsPanel: JPanel? = null
    private var fileCountSpinner: JSpinner? = null
    private var filtersTableModel: DefaultTableModel? = null
    private var filtersTable: JBTable? = null
    private var addFilterTextField: JTextField? = null

    private val project = ProjectManager.getInstance().defaultProject

    override fun createComponent(): JComponent? {
        settingsPanel = JPanel(GridBagLayout())
        val gbc = createGridBagConstraints()

        addFileCountPanel(gbc)
        addFiltersPanel(gbc)
        addFiltersTable(gbc)

        reset()
        return settingsPanel
    }

    private fun createGridBagConstraints() = GridBagConstraints().apply {
        gridx = 0
        gridy = 0
        gridwidth = 2
        fill = GridBagConstraints.HORIZONTAL
        weightx = 1.0
        insets = JBUI.insets(5)
    }

    private fun addFileCountPanel(gbc: GridBagConstraints) {
        val fileCountPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        val fileCountLabel = JLabel("Maximum number of files to process:").apply {
            toolTipText = "Sets the upper limit for the number of files that will be processed and copied to the clipboard."
        }
        fileCountSpinner = JSpinner(SpinnerNumberModel(50, 1, Int.MAX_VALUE, 1))
        fileCountPanel.add(fileCountLabel)
        fileCountPanel.add(fileCountSpinner)
        settingsPanel!!.add(fileCountPanel, gbc)
        gbc.gridy++
    }

    private fun addFiltersPanel(gbc: GridBagConstraints) {
        val filterLabel = JLabel("File extension filters:").apply {
            toolTipText = "Add file extensions to include in the processing. Only files with these extensions will be copied."
        }
        addFilterTextField = createStyledTextField(".fileExtension")
        val addButton = createStyledButton("Include in File Extension Filter")
        addButton.addActionListener { addFilter() }

        val filtersPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        filtersPanel.add(filterLabel)
        filtersPanel.add(addFilterTextField)
        filtersPanel.add(addButton)
        settingsPanel!!.add(filtersPanel, gbc)
        gbc.gridy++
    }

    private fun addFiltersTable(gbc: GridBagConstraints) {
        gbc.gridwidth = 1
        gbc.fill = GridBagConstraints.BOTH
        gbc.weighty = 1.0
        filtersTableModel = DefaultTableModel(arrayOf("Filter", "Action"), 0)
        filtersTable = createFiltersTable()
        val scrollPane = JBScrollPane(filtersTable)
        settingsPanel!!.add(scrollPane, gbc)
    }

    private fun createFiltersTable() = JBTable(filtersTableModel).apply {
        preferredScrollableViewportSize = Dimension(450, 70)
        addRemoveActionColumnIfNotExists()
        columnModel.getColumn(1).cellRenderer = ButtonRenderer()
        columnModel.getColumn(1).cellEditor = ButtonEditor(createStyledButton("Remove"))
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
                    filtersTableModel?.takeIf { it.rowCount > row }?.removeRow(row)
                }
            }
        })
    }

    private fun addFilter() {
        val filterText = addFilterTextField!!.text.trim()
        if (isValidFilter(filterText) && !filterExists(filterText)) {
            filtersTableModel!!.addRow(arrayOf(filterText, "Remove"))
            addFilterTextField!!.text = ""
        } else {
            JOptionPane.showMessageDialog(
                settingsPanel,
                "Invalid or duplicate filter. Filters should start with a dot followed by alphanumeric characters.",
                "Invalid Input",
                JOptionPane.ERROR_MESSAGE
            )
        }
    }

    private fun isValidFilter(filter: String): Boolean {
        return filter.matches(Regex("^\\.[a-zA-Z0-9]+$"))
    }

    private fun filterExists(filterText: String): Boolean {
        for (i in 0 until filtersTableModel!!.rowCount) {
            if (filtersTableModel!!.getValueAt(i, 0) == filterText) {
                return true
            }
        }
        return false
    }

    override fun isModified(): Boolean {
        val settings = SourceClipboardExportSettings.getInstance()
        val filters = (0 until filtersTableModel!!.rowCount).map {
            filtersTableModel!!.getValueAt(it, 0) as String
        }
        return fileCountSpinner!!.value != settings.state.fileCount || settings.state.filenameFilters != filters
    }

    override fun apply() {
        if (!validateInput()) return
        val settings = SourceClipboardExportSettings.getInstance()
        settings.state.fileCount = fileCountSpinner!!.value as Int
        settings.state.filenameFilters =
            (0 until filtersTableModel!!.rowCount).map { filtersTableModel!!.getValueAt(it, 0) as String }
                .toMutableList()
        LOGGER.debug("Applying settings: File count = ${settings.state.fileCount}, Filters = ${settings.state.filenameFilters.joinToString(", ")}")
    }

    override fun reset() {
        val settings = SourceClipboardExportSettings.getInstance()
        fileCountSpinner!!.value = settings.state.fileCount
        filtersTableModel!!.rowCount = 0
        settings.state.filenameFilters.forEach { filter ->
            filtersTableModel!!.addRow(arrayOf(filter, "Remove"))
        }
        LOGGER.debug("Resetting settings UI: Current filters = ${settings.state.filenameFilters.joinToString(", ")}")
    }

    override fun getDisplayName(): String {
        return "Source Clipboard Export"
    }

    private fun validateInput(): Boolean {
        val fileCount = fileCountSpinner?.value as? Int ?: return false
        if (fileCount <= 0) {
            JOptionPane.showMessageDialog(settingsPanel, "File count must be a positive integer.", "Invalid Input", JOptionPane.ERROR_MESSAGE)
            return false
        }

        val invalidFilters = (0 until filtersTableModel!!.rowCount)
            .map { filtersTableModel!!.getValueAt(it, 0) as String }
            .filter { !isValidFilter(it) }

        if (invalidFilters.isNotEmpty()) {
            JOptionPane.showMessageDialog(settingsPanel, "Invalid filters: ${invalidFilters.joinToString(", ")}\nFilters should start with a dot followed by alphanumeric characters.", "Invalid Input", JOptionPane.ERROR_MESSAGE)
            return false
        }

        return true
    }

    private fun createStyledTextField(placeholder: String): JTextField {
        return PlaceholderTextField(placeholder).apply {
            maximumSize = Dimension(Integer.MAX_VALUE, preferredSize.height)
            preferredSize = Dimension(200, preferredSize.height)
        }
    }

    private fun createStyledButton(text: String): JButton {
        return JButton(text).apply {
            background = JBColor.background()
            foreground = JBColor.foreground()
        }
    }

    inner class ButtonRenderer : JButton(), TableCellRenderer {
        init {
            isOpaque = true
        }

        override fun getTableCellRendererComponent(
            table: JTable, value: Any?,
            isSelected: Boolean, hasFocus: Boolean,
            row: Int, column: Int
        ): Component {
            text = value?.toString() ?: "Remove"
            return this
        }
    }

    inner class ButtonEditor(private val btn: JButton) : DefaultCellEditor(JCheckBox()) {
        init {
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
            btn.text = (if (value is Boolean) value.toString() else "Remove")
            return btn
        }

        override fun getCellEditorValue(): Any {
            return btn.text
        }
    }

    companion object {
        private val LOGGER = Logger.getInstance(SourceClipboardExportConfigurable::class.java)
    }
}

class PlaceholderTextField(private val placeholder: String) : JTextField() {
    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        if (text.isEmpty()) {
            g.color = JBColor.GRAY
            g.drawString(placeholder, 5, height / 2 + font.size / 2 - 2)
        }
    }
}
