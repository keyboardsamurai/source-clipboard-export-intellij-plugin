package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.config

import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import javax.swing.JOptionPane
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

class SourceClipboardExportConfigurableTest {

    private lateinit var configurable: SourceClipboardExportConfigurable

    @BeforeEach
    fun setup() {
        configurable = SourceClipboardExportConfigurable()
    }

    @Test
    fun `test configurable creates component successfully`() {
        val component = configurable.createComponent()
        assertNotNull(component)
    }

    @Test
    fun `test configurable display name`() {
        assertEquals("Source Clipboard Export", configurable.displayName)
    }

    @Test
    fun `test validation rejects negative file count`() {
        // Mock JOptionPane to avoid UI popup during test
        mockkStatic(JOptionPane::class)
        every { JOptionPane.showMessageDialog(any(), any(), any(), any()) } returns Unit
        
        configurable.createComponent()
        
        // Access private field via reflection
        val fileCountField = configurable.javaClass.getDeclaredField("fileCountSpinner")
        fileCountField.isAccessible = true
        val spinner = JSpinner(SpinnerNumberModel(0, 0, Int.MAX_VALUE, 1))
        fileCountField.set(configurable, spinner)
        
        // Access private validation method
        val validateMethod = configurable.javaClass.getDeclaredMethod("validateInput")
        validateMethod.isAccessible = true
        
        val result = validateMethod.invoke(configurable) as Boolean
        assertFalse(result)
        
        // Verify error dialog was shown
        verify { JOptionPane.showMessageDialog(any(), match { it.toString().contains("positive integer") }, eq("Invalid Input"), eq(JOptionPane.ERROR_MESSAGE)) }
        
        unmockkStatic(JOptionPane::class)
    }

    @Test
    fun `test reset loads default values correctly`() {
        configurable.createComponent()
        
        // Get settings instance
        val settings = SourceClipboardExportSettings.getInstance()
        val originalState = settings.state
        
        // Modify settings
        originalState.fileCount = 999
        originalState.maxFileSizeKb = 999
        originalState.includeLineNumbers = false
        
        // Reset
        configurable.reset()
        
        // Verify the UI was updated with the settings values
        val fileCountField = configurable.javaClass.getDeclaredField("fileCountSpinner")
        fileCountField.isAccessible = true
        val spinner = fileCountField.get(configurable) as JSpinner?
        assertEquals(999, spinner?.value)
    }

    @Test
    fun `test isModified detects changes`() {
        configurable.createComponent()
        configurable.reset()
        
        // Initially not modified
        assertFalse(configurable.isModified)
        
        // Change a value
        val fileCountField = configurable.javaClass.getDeclaredField("fileCountSpinner")
        fileCountField.isAccessible = true
        val spinner = fileCountField.get(configurable) as JSpinner?
        spinner?.value = 999
        
        // Should now be modified
        assertTrue(configurable.isModified)
    }

    @Test
    fun `test apply saves changes to settings`() {
        configurable.createComponent()
        
        // Change values
        val fileCountField = configurable.javaClass.getDeclaredField("fileCountSpinner")
        fileCountField.isAccessible = true
        val spinner = fileCountField.get(configurable) as JSpinner?
        spinner?.value = 300
        
        val lineNumbersField = configurable.javaClass.getDeclaredField("includeLineNumbersCheckBox")
        lineNumbersField.isAccessible = true
        val checkbox = lineNumbersField.get(configurable) as com.intellij.ui.components.JBCheckBox?
        checkbox?.isSelected = true
        
        // Apply changes
        configurable.apply()
        
        // Verify settings were updated
        val settings = SourceClipboardExportSettings.getInstance().state
        assertEquals(300, settings.fileCount)
        assertTrue(settings.includeLineNumbers)
    }
}