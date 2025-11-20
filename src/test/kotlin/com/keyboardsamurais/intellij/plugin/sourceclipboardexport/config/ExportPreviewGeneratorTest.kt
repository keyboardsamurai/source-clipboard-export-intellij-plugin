package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.config

import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.AppConstants
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExportPreviewGeneratorTest {

    @Test
    fun `buildPreview lists settings and sample`() {
        val generator = ExportPreviewGenerator()
        val preview = generator.buildPreview(
            ExportPreviewGenerator.PreviewSettings(
                maxFiles = 10,
                maxFileSizeKb = 256,
                includePathPrefix = true,
                includeLineNumbers = true,
                includeDirectoryStructure = true,
                includeFilesInStructure = true,
                includeRepositorySummary = true,
                outputFormat = AppConstants.OutputFormat.MARKDOWN,
                filters = listOf(".kt", ".java"),
                ignoredNames = listOf("build", "out")
            )
        )

        assertTrue(preview.contains("Maximum files: 10"))
        assertTrue(preview.contains("Filters: .kt, .java"))
        assertTrue(preview.contains("Ignored names: build, out"))
        assertTrue(preview.contains("### src/Example.kt"))
        assertTrue(preview.contains("```kotlin"))
    }

    @Test
    fun `plain text preview reflects directory toggles and summaries`() {
        val generator = ExportPreviewGenerator()
        val preview = generator.buildPreview(
                ExportPreviewGenerator.PreviewSettings(
                        maxFiles = 5,
                        maxFileSizeKb = 128,
                        includePathPrefix = true,
                        includeLineNumbers = true,
                        includeDirectoryStructure = true,
                        includeFilesInStructure = true,
                        includeRepositorySummary = true,
                        outputFormat = AppConstants.OutputFormat.PLAIN_TEXT,
                        filters = emptyList(),
                        ignoredNames = listOf("build", "out")
                )
        )

        assertTrue(preview.contains("Include directory structure"))
        assertTrue(preview.contains("│       └── ExampleTest.kt"))
        assertTrue(preview.contains("Repository Summary:"))
        assertTrue(preview.contains("// filename: src/Example.kt"))
        assertTrue(preview.contains("Ignored names: build, out"))
        assertTrue(preview.contains("Filters: None"))
    }

    @Test
    fun `xml preview omits line numbers when disabled and wraps content`() {
        val generator = ExportPreviewGenerator()
        val preview = generator.buildPreview(
                ExportPreviewGenerator.PreviewSettings(
                        maxFiles = 1,
                        maxFileSizeKb = 64,
                        includePathPrefix = false,
                        includeLineNumbers = false,
                        includeDirectoryStructure = false,
                        includeFilesInStructure = false,
                        includeRepositorySummary = true,
                        outputFormat = AppConstants.OutputFormat.XML,
                        filters = listOf("*.kt"),
                        ignoredNames = emptyList()
                )
        )

        assertTrue(preview.contains("<summary>"))
        assertTrue(preview.contains("<file path=\"src/Example.kt\">"))
        assertTrue(preview.contains("<![CDATA["))
        assertFalse(preview.contains("1: class Example"))
        assertTrue(preview.contains("class Example {"))
        assertTrue(preview.contains("Filters: *.kt"))
    }
}
