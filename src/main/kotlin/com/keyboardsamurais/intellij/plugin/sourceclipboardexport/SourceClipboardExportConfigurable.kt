package com.keyboardsamurais.intellij.plugin.sourceclipboardexport

import SourceClipboardExportSettings
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
        fileCountSpinner = JSpinner(SpinnerNumberModel(50, 1, Int.MAX_VALUE, 1))
        fileCountPanel.add(JLabel("Maximum number of files to process:"))
        fileCountPanel.add(fileCountSpinner)
        settingsPanel!!.add(fileCountPanel, gbc)
        gbc.gridy++
    }

    private fun addFiltersPanel(gbc: GridBagConstraints) {
        addFilterTextField = PlaceholderTextField(".fileExtension").apply {
            maximumSize = Dimension(Integer.MAX_VALUE, preferredSize.height)
            preferredSize = Dimension(200, preferredSize.height)
        }
        val addButton = JButton("Include in File Extension Filter").apply {
            addActionListener { addFilter() }
        }
        val filtersPanel = JPanel(FlowLayout(FlowLayout.LEFT))
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
        columnModel.getColumn(1).cellEditor = ButtonEditor(JButton())
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
        if (filterText.isNotEmpty() && !filterExists(filterText)) {
            filtersTableModel!!.addRow(arrayOf(filterText, "Remove"))
            addFilterTextField!!.text = ""
        }
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
        val settings = SourceClipboardExportSettings.getInstance(project)
        val filters = (0 until filtersTableModel!!.rowCount).map {
            val value = filtersTableModel!!.getValueAt(it, 0)
            if (value is String) value else ""
        }
        return fileCountSpinner!!.value != settings.state.fileCount || settings.state.filenameFilters != filters
    }

    override fun apply() {
        val settings = SourceClipboardExportSettings.getInstance(project)
        settings.state.fileCount = fileCountSpinner!!.value as Int
        settings.state.filenameFilters =
            (0 until filtersTableModel!!.rowCount).map { filtersTableModel!!.getValueAt(it, 0) as String }
                .toMutableList()
    }

    override fun reset() {
        val settings = SourceClipboardExportSettings.getInstance(project)
        fileCountSpinner!!.value = settings.state.fileCount
        filtersTableModel!!.rowCount = 0
        settings.state.filenameFilters.forEach { filter ->
            filtersTableModel!!.addRow(arrayOf(filter))
        }
    }

    override fun getDisplayName(): String {
        return "Source Clipboard Export"
    }

    inner class ButtonRenderer : JButton(), TableCellRenderer {
        init {
            isOpaque = true
        }

        override fun getTableCellRendererComponent(
            table: JTable, value: Any,
            isSelected: Boolean, hasFocus: Boolean,
            row: Int, column: Int
        ): Component {
            text = (if (value is Boolean) value.toString() else "Remove")
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
