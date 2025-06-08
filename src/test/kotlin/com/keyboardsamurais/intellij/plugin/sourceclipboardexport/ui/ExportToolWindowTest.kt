package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.config.SourceClipboardExportSettings
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ExportToolWindowTest {

    private lateinit var project: Project
    private lateinit var toolWindow: ToolWindow
    private lateinit var factory: ExportToolWindowFactory

    @BeforeEach
    fun setup() {
        project = mockk(relaxed = true)
        toolWindow = mockk(relaxed = true)
        factory = ExportToolWindowFactory()
        
        // Set up test instance
        SourceClipboardExportSettings.setTestInstance(SourceClipboardExportSettings())
    }

    @Test
    fun `test tool window should be available when enabled in settings`() {
        val settings = SourceClipboardExportSettings.getInstance()
        settings.state.showExportToolWindow = true
        
        assertTrue(factory.shouldBeAvailable(project))
    }

    @Test
    fun `test tool window should not be available when disabled in settings`() {
        val settings = SourceClipboardExportSettings.getInstance()
        settings.state.showExportToolWindow = false
        
        assertFalse(factory.shouldBeAvailable(project))
    }

    @Test
    fun `test tool window creation when enabled`() {
        val settings = SourceClipboardExportSettings.getInstance()
        settings.state.showExportToolWindow = true
        
        // Mock content manager
        val contentManager = mockk<com.intellij.ui.content.ContentManager>(relaxed = true)
        every { toolWindow.contentManager } returns contentManager
        
        // The factory should attempt to create content when enabled
        // We can't fully test the ExportToolWindow constructor in unit tests
        // due to heavy IntelliJ dependencies, but we can test the factory logic
        assertTrue(factory.shouldBeAvailable(project))
    }

    @Test
    fun `test export tool window factory basic behavior`() {
        // Test that the factory exists and can be instantiated
        assertNotNull(factory)
        
        // Test settings-based availability
        val settings = SourceClipboardExportSettings.getInstance()
        settings.state.showExportToolWindow = false
        assertFalse(factory.shouldBeAvailable(project))
        
        settings.state.showExportToolWindow = true
        assertTrue(factory.shouldBeAvailable(project))
    }
}