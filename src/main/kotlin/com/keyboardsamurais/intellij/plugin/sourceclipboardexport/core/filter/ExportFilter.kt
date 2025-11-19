package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core.filter

import com.intellij.openapi.vfs.VirtualFile

/**
 * Data class to hold file properties read within a read action. Extracted from SourceExporter to
 * allow sharing with filters.
 */
data class FileProperties(
        val name: String,
        val path: String,
        val isDirectory: Boolean,
        val isValid: Boolean,
        val exists: Boolean,
        val length: Long,
        val extension: String?
)

/** Reason for excluding a file from export. */
enum class ExclusionReason {
    FILENAME_FILTER,
    SIZE_LIMIT,
    BINARY_CONTENT,
    IGNORED_NAME,
    GITIGNORE
}

/** Interface for file filters. */
interface ExportFilter {
    /**
     * Checks if the file should be excluded.
     *
     * @param file The VirtualFile (use carefully, might require ReadAction for some operations)
     * @param properties Pre-read properties of the file
     * @param relativePath The relative path of the file from the export root
     * @return The reason for exclusion, or null if the file should be included.
     */
    fun shouldExclude(
            file: VirtualFile,
            properties: FileProperties,
            relativePath: String?
    ): ExclusionReason?
}
