package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.config

import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.AppConstants

/**
 * Builds a textual preview that mirrors what the exporter would produce for the
 * currently selected settings, letting users reason about formatting without
 * triggering an actual export.
 */
class ExportPreviewGenerator {
    /** Input snapshot used to build preview text from UI state. */
    data class PreviewSettings(
        val maxFiles: Int,
        val maxFileSizeKb: Int,
        val includePathPrefix: Boolean,
        val includeLineNumbers: Boolean,
        val includeDirectoryStructure: Boolean,
        val includeFilesInStructure: Boolean,
        val includeRepositorySummary: Boolean,
        val outputFormat: AppConstants.OutputFormat,
        val filters: List<String>,
        val ignoredNames: List<String>
    )

    fun buildPreview(settings: PreviewSettings): String {
        val builder = StringBuilder()
        builder.append("Export Preview with Current Settings:\n")
        builder.append("=====================================\n\n")
        appendSettings(builder, settings)
        builder.append("\nSample Output Preview:\n")
        builder.append("---------------------\n")
        appendSample(builder, settings)
        return builder.toString()
    }

    private fun appendSettings(builder: StringBuilder, settings: PreviewSettings) {
        builder.append("Settings:\n")
        builder.append("- Maximum files: ${settings.maxFiles}\n")
        builder.append("- Maximum file size: ${settings.maxFileSizeKb} KB\n")
        builder.append("- Include path prefix: ${settings.includePathPrefix}\n")
        builder.append("- Include line numbers: ${settings.includeLineNumbers}\n")
        builder.append("- Include directory structure: ${settings.includeDirectoryStructure}\n")
        builder.append("- Include files in structure: ${settings.includeFilesInStructure}\n")
        builder.append("- Include repository summary: ${settings.includeRepositorySummary}\n")
        builder.append("- Output format: ${settings.outputFormat.readableName()}\n\n")

        if (settings.filters.isEmpty()) {
            builder.append("Filters: None (all non-binary files will be included)\n")
        } else {
            builder.append("Filters: ${settings.filters.joinToString(", ")}\n")
        }

        if (settings.ignoredNames.isNotEmpty()) {
            builder.append("Ignored names: ${settings.ignoredNames.joinToString(", ")}\n")
        }
    }

    private fun appendSample(builder: StringBuilder, settings: PreviewSettings) {
        when (settings.outputFormat) {
            AppConstants.OutputFormat.PLAIN_TEXT -> appendPlainSample(builder, settings)
            AppConstants.OutputFormat.MARKDOWN -> appendMarkdownSample(builder, settings)
            AppConstants.OutputFormat.XML -> appendXmlSample(builder, settings)
        }
    }

    private fun appendPlainSample(builder: StringBuilder, settings: PreviewSettings) {
        if (settings.includeDirectoryStructure) {
            builder.append("project/\n")
            builder.append("├── src/\n")
            builder.append("│   └── Example.kt\n")
            if (settings.includeFilesInStructure) {
                builder.append("│       └── ExampleTest.kt\n")
            }
            builder.append("└── build.gradle\n\n")
        }
        if (settings.includeRepositorySummary) {
            builder.append("Repository Summary:\n")
            builder.append("Total files processed: 2\n")
            builder.append("Total size: 3.5 KB\n\n")
        }
        if (settings.includePathPrefix) {
            builder.append("// filename: src/Example.kt\n")
        }
        if (settings.includeLineNumbers) {
            builder.append("1: class Example {\n")
            builder.append("2:     fun hello() = \"Hello\"\n")
            builder.append("3: }\n")
        } else {
            builder.append("class Example {\n")
            builder.append("    fun hello() = \"Hello\"\n")
            builder.append("}\n")
        }
    }

    private fun appendMarkdownSample(builder: StringBuilder, settings: PreviewSettings) {
        if (settings.includeDirectoryStructure) {
            builder.append("```\n")
            builder.append("project/\n")
            builder.append("├── src/\n")
            builder.append("│   └── Example.kt\n")
            builder.append("└── build.gradle\n")
            builder.append("```\n\n")
        }
        builder.append("### src/Example.kt\n\n")
        builder.append("```kotlin\n")
        if (settings.includeLineNumbers) {
            builder.append("1: class Example {\n")
            builder.append("2:     fun hello() = \"Hello\"\n")
            builder.append("3: }\n")
        } else {
            builder.append("class Example {\n")
            builder.append("    fun hello() = \"Hello\"\n")
            builder.append("}\n")
        }
        builder.append("```\n")
    }

    private fun appendXmlSample(builder: StringBuilder, settings: PreviewSettings) {
        builder.append("<export>\n")
        if (settings.includeRepositorySummary) {
            builder.append("  <summary>\n")
            builder.append("    <totalFiles>2</totalFiles>\n")
            builder.append("    <totalSize>3584</totalSize>\n")
            builder.append("  </summary>\n")
        }
        builder.append("  <file path=\"src/Example.kt\">\n")
        builder.append("    <content><![CDATA[\n")
        if (settings.includeLineNumbers) {
            builder.append("1: class Example {\n")
            builder.append("2:     fun hello() = \"Hello\"\n")
            builder.append("3: }\n")
        } else {
            builder.append("class Example {\n")
            builder.append("    fun hello() = \"Hello\"\n")
            builder.append("}\n")
        }
        builder.append("    ]]></content>\n")
        builder.append("  </file>\n")
        builder.append("</export>\n")
    }

    private fun AppConstants.OutputFormat.readableName(): String = when (this) {
        AppConstants.OutputFormat.PLAIN_TEXT -> "Plain Text"
        AppConstants.OutputFormat.MARKDOWN -> "Markdown"
        AppConstants.OutputFormat.XML -> "XML"
    }
}
