package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.config

import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.AppConstants
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SourceClipboardExportSettingsTest {

    @Test
    fun `test default settings values are user-friendly`() {
        // Create a fresh state
        val state = SourceClipboardExportSettings.State()
        
        // Verify improved defaults
        assertEquals(200, state.fileCount, "Default file count should be 200 for modern projects")
        assertEquals(500, state.maxFileSizeKb, "Default max file size should be 500KB")
        assertFalse(state.areFiltersEnabled, "Filters should be disabled by default when filter list is empty")
        assertTrue(state.includeLineNumbers, "Line numbers should be enabled by default for AI context")
        
        // Verify unchanged defaults
        assertTrue(state.includePathPrefix)
        assertFalse(state.includeDirectoryStructure)
        assertFalse(state.includeFilesInStructure)
        assertFalse(state.includeRepositorySummary)
        assertEquals(AppConstants.OutputFormat.PLAIN_TEXT, state.outputFormat)
        assertEquals(AppConstants.DEFAULT_IGNORED_NAMES, state.ignoredNames)
        assertTrue(state.filenameFilters.isEmpty(), "Filter list should be empty by default")
        assertEquals(3, state.stackTraceSettings.minFramesToFold)
    }

    @Test
    fun `test filters are disabled when filter list is empty`() {
        val state = SourceClipboardExportSettings.State()
        
        // Default state: empty filters, disabled
        assertTrue(state.filenameFilters.isEmpty())
        assertFalse(state.areFiltersEnabled)
    }

    @Test
    fun `test loadState preserves ignoredNames when empty`() {
        val settings = SourceClipboardExportSettings()
        val emptyState = SourceClipboardExportSettings.State()
        emptyState.ignoredNames = mutableListOf() // Simulate empty ignored names
        
        settings.loadState(emptyState)
        
        // Should restore default ignored names
        assertEquals(AppConstants.DEFAULT_IGNORED_NAMES, settings.state.ignoredNames)
    }

    @Test
    fun `test loadState enables filters when legacy state has non-empty filters`() {
        val settings = SourceClipboardExportSettings()
        val legacyState = SourceClipboardExportSettings.State().apply {
            filenameFilters = mutableListOf(".kt", ".java")
            areFiltersEnabled = false // Simulate pre-flag behavior where filters were always applied
            hasMigratedFilterFlag = false
        }

        settings.loadState(legacyState)

        assertTrue(settings.state.areFiltersEnabled, "Filters should be enabled when a legacy state has non-empty filters")
        assertTrue(settings.state.hasMigratedFilterFlag, "Migration flag should be set after running loadState")
    }
}
