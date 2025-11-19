package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core.gitignore

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.config.SourceClipboardExportSettings
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core.SourceExporter
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class HierarchicalGitignoreParserLifecycleTest {

    @Test
    fun `SourceExporter must retrieve HierarchicalGitignoreParser from project service`() {
        // Arrange
        val project = mockk<Project>(relaxed = true)
        val settings = mockk<SourceClipboardExportSettings.State>(relaxed = true)
        val indicator = mockk<ProgressIndicator>(relaxed = true)
        val mockParser = mockk<HierarchicalGitignoreParser>(relaxed = true)

        every { project.getService(HierarchicalGitignoreParser::class.java) } returns mockParser

        // Act
        val exporter = SourceExporter(project, settings, indicator)

        // Assert
        // Verify that getService was called
        verify(exactly = 1) { project.getService(HierarchicalGitignoreParser::class.java) }
    }

    @Test
    fun `SourceExporter throws IllegalStateException if HierarchicalGitignoreParser service is missing`() {
        // Arrange
        val project = mockk<Project>(relaxed = true)
        val settings = mockk<SourceClipboardExportSettings.State>(relaxed = true)
        val indicator = mockk<ProgressIndicator>(relaxed = true)

        // Simulate service not found (returns null)
        every { project.getService(HierarchicalGitignoreParser::class.java) } returns null

        // Act & Assert
        assertThrows(IllegalStateException::class.java) {
            SourceExporter(project, settings, indicator)
        }
    }
}
