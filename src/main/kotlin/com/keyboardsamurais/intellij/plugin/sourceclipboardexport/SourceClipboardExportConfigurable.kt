package com.keyboardsamurais.intellij.plugin.sourceclipboardexport

import SourceClipboardExportSettings
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.table.DefaultTableModel
import javax.swing.table.TableCellRenderer
import javax.swing.table.TableColumn


class SourceClipboardExportConfigurable : Configurable {
    private var mySettingsPanel: JPanel? = null
    private var myFileCountSpinner: JSpinner? = null
    private var myFiltersTableModel: DefaultTableModel? = null
    private var myFiltersTable: JBTable? = null
    private var myAddFilterTextField: JTextField? = null

    private val project = ProjectManager.getInstance().defaultProject
    override fun createComponent(): JComponent? {
        mySettingsPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
        }


        // Create a panel for file count with FlowLayout to put elements in the same row
        val fileCountPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        myFileCountSpinner = JSpinner(SpinnerNumberModel(50, 1, Int.MAX_VALUE, 1))

        // Add the label and spinner to the file count panel
        fileCountPanel.add(JLabel("Maximum number of files to process:"))
        fileCountPanel.add(myFileCountSpinner)
        mySettingsPanel!!.add(fileCountPanel);

        // Filter components
        myAddFilterTextField = JTextField().apply {
            maximumSize = Dimension(Integer.MAX_VALUE, preferredSize.height)
        }
        val addButton = JButton("Add Filter").apply {
            addActionListener { addFilter() }
        }
        val filtersPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(myAddFilterTextField)
            add(addButton)
        }
        mySettingsPanel!!.add(filtersPanel)

        // Table for filters
        myFiltersTableModel = DefaultTableModel(arrayOf("Filter"), 0)
        myFiltersTable = JBTable(myFiltersTableModel).apply {
            preferredScrollableViewportSize = Dimension(450, 70)
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    val column = columnAtPoint(e.point)
                    if (column == 1) { // Assuming the "Remove" button is in the second column
                        val row = rowAtPoint(e.point)
                        myFiltersTableModel?.takeIf { it.rowCount > row }?.removeRow(row)
                    }
                }
            })

            // Create a separate remove button column
            columnModel.addColumn(object : TableColumn() {
                init {
                    headerValue = "Action"
                    cellRenderer = RemoveButtonRenderer()
                }
            })
        }

        mySettingsPanel!!.add(JBScrollPane(myFiltersTable))

        reset() // Load initial state

        return mySettingsPanel
    }

    private fun addFilter() {
        val filterText = myAddFilterTextField!!.text.trim()
        if (filterText.isNotEmpty()) {
            myFiltersTableModel!!.addRow(arrayOf(filterText))
            myAddFilterTextField!!.text = ""
        }
    }

    override fun isModified(): Boolean {
        val settings = SourceClipboardExportSettings.getInstance(project)
        val filters = (0 until myFiltersTableModel!!.rowCount).map {
            val value = myFiltersTableModel!!.getValueAt(it, 0)
            if (value is String) value else ""
        }
        return myFileCountSpinner!!.value != settings.state.fileCount || settings.state.filenameFilters != filters
    }

    override fun apply() {
        val settings = SourceClipboardExportSettings.getInstance(project)
        settings.state.fileCount = myFileCountSpinner!!.value as Int
        settings.state.filenameFilters =
            (0 until myFiltersTableModel!!.rowCount).map { myFiltersTableModel!!.getValueAt(it, 0) as String }
                .toMutableList()
    }

    override fun reset() {
        val settings = SourceClipboardExportSettings.getInstance(project)
        myFileCountSpinner!!.value = settings.state.fileCount

        myFiltersTableModel!!.rowCount = 0
        settings.state.filenameFilters.forEach { filter ->
            myFiltersTableModel!!.addRow(arrayOf(filter))
        }
    }

    override fun getDisplayName(): String {
        return "Source Clipboard Export"
    }

    inner class RemoveButtonRenderer : JButton(), TableCellRenderer {
        override fun getTableCellRendererComponent(
            table: JTable,
            value: Any,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int
        ): Component {
            text = "Remove"
            return this
        }
    }
}
