package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.config.ui

import com.intellij.ui.JBColor
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import javax.swing.JTextField

/**
 * Simple text field that paints gray placeholder text whenever the field is empty and unfocused.
 * Keeps the settings UI readable without pulling in a heavyweight component.
 */
class PlaceholderTextField(private val placeholder: String) : JTextField() {
    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        if (text.isEmpty() && !hasFocus()) {
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.color = JBColor.GRAY // Use JBColor for theme compatibility
            val fm = g.fontMetrics
            // Use text field's insets to position placeholder correctly
            val x = insets.left
            val y = (height - fm.height) / 2 + fm.ascent
            g.drawString(placeholder, x, y)
        }
    }

    init {
        // Repaint on focus change to show/hide placeholder
        addFocusListener(object : FocusAdapter() {
            override fun focusGained(e: FocusEvent?) {
                repaint()
            }
            override fun focusLost(e: FocusEvent?) {
                repaint()
            }
        })
    }
} 
