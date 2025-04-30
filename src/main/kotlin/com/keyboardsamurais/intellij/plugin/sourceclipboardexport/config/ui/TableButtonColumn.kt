package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.config.ui

import java.awt.Component
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.AbstractCellEditor
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JTable
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer
import javax.swing.table.TableColumnModel

/**
 * Adds a button column to a JTable that performs an action when clicked.
 */
object TableButtonColumn {

    fun add(table: JTable, column: Int, actionText: String = "Remove", action: (Int) -> Unit) {
        val button = JButton(actionText)
        button.isOpaque = true
        button.border = BorderFactory.createEmptyBorder(2, 5, 2, 5) // Add some padding

        val renderer = ButtonRenderer(actionText)
        val editor = ButtonEditor(button, action)

        val columnModel: TableColumnModel = table.columnModel
        columnModel.getColumn(column).cellRenderer = renderer
        columnModel.getColumn(column).cellEditor = editor

        // Trigger action on mouse click within the button column
        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val clickColumn = table.columnAtPoint(e.point)
                if (clickColumn == column) {
                    editor.actionPerformed(ActionEvent(table, ActionEvent.ACTION_PERFORMED, ""))
                }
            }
        })
    }

    // Inner class for rendering the button
    private class ButtonRenderer(private val text: String) : JButton(), TableCellRenderer {
        init {
            isOpaque = true
            border = BorderFactory.createEmptyBorder(2, 5, 2, 5)
        }
        override fun getTableCellRendererComponent(
            table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
        ): Component {
            background = if (isSelected) table.selectionBackground else table.background
            foreground = if (isSelected) table.selectionForeground else table.foreground
            return this
        }
    }

    // Inner class for editing (handling clicks)
    private class ButtonEditor(
        private val button: JButton,
        private val action: (Int) -> Unit
    ) : AbstractCellEditor(), TableCellEditor, ActionListener {
        private var currentRow: Int = -1

        init {
            button.addActionListener(this)
            button.isFocusPainted = false // Looks better in a table
        }

        override fun getTableCellEditorComponent(
            table: JTable, value: Any?, isSelected: Boolean, row: Int, column: Int
        ): Component {
            currentRow = row
            button.background = table.selectionBackground // Match selection color when active
            button.foreground = table.selectionForeground
            return button
        }

        override fun getCellEditorValue(): Any {
            return button.text // Return the button text
        }

        override fun actionPerformed(e: ActionEvent) {
            if (currentRow != -1) {
                action(currentRow) // Execute the provided action
            }
            fireEditingStopped() // Important to stop editing after action
        }

        // Optional: Stop editing if the user clicks elsewhere
        override fun stopCellEditing(): Boolean {
            currentRow = -1
            return super.stopCellEditing()
        }
    }
} 