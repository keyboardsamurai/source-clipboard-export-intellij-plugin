package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.config

import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.AppConstants
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
}
