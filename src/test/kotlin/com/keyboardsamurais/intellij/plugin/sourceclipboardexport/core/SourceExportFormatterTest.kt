package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.config.SourceClipboardExportSettings
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.AppConstants
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class SourceExportFormatterTest {

    private lateinit var settings: SourceClipboardExportSettings.State
    private val project = mockk<Project>(relaxed = true)
    private val selectedFiles = emptyArray<VirtualFile>()

    @BeforeEach
    fun setup() {
        settings = SourceClipboardExportSettings.State().apply {
            includeDirectoryStructure = false
            includeRepositorySummary = false
            includeFilesInStructure = true
            includePathPrefix = false
            outputFormat = AppConstants.OutputFormat.PLAIN_TEXT
        }
    }

    @Test
    fun `buildContent joins plain text entries`() {
        val formatter = SourceExportFormatter(project, settings)
        val stats = SourceExportFormatter.ExportStats(2, 0, 0, 0, 0, 0)

        val entries = listOf(
            SourceExportFormatter.FileEntry("src/A.kt", "A content"),
            SourceExportFormatter.FileEntry("src/B.kt", "B content")
        )

        val content = formatter.buildContent(selectedFiles, entries, stats)

        assertTrue(content.contains("A content"))
        assertTrue(content.contains("B content"))
    }

    @Test
    fun `buildContent emits markdown with trimmed prefixes`() {
        settings.apply {
            outputFormat = AppConstants.OutputFormat.MARKDOWN
            includePathPrefix = true
        }
        val formatter = SourceExportFormatter(project, settings)
        val stats = SourceExportFormatter.ExportStats(1, 0, 0, 0, 0, 0)

        val entries = listOf(
            SourceExportFormatter.FileEntry("src/Demo.kt", "// filename: src/Demo.kt\nfun main() = Unit")
        )

        val content = formatter.buildContent(selectedFiles, entries, stats)

        assertTrue(content.contains("```kotlin"))
        assertTrue(content.contains("fun main() = Unit"))
        assertTrue(content.contains("### src/Demo.kt"))
    }
}
