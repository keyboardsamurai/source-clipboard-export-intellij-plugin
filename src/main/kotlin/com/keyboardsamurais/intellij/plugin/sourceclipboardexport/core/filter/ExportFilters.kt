package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core.filter

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VirtualFile
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core.gitignore.HierarchicalGitignoreParser
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.FileUtils

/** Rejects files whose size exceeds the configured kilobyte limit. */
class SizeFilter(private val maxFileSizeKb: Int) : ExportFilter {
    private val logger = Logger.getInstance(SizeFilter::class.java)

    override fun shouldExclude(
            file: VirtualFile,
            properties: FileProperties,
            relativePath: String?
    ): ExclusionReason? {
        val maxSizeInBytes = maxFileSizeKb * 1024L
        return if (properties.length > maxSizeInBytes) {
            logger.info("Skipping file due to size limit (> ${maxFileSizeKb} KB): $relativePath")
            ExclusionReason.SIZE_LIMIT
        } else {
            null
        }
    }
}

/** Skips files/directories whose names appear in the user-maintained ignore list. */
class IgnoredNameFilter(private val ignoredNames: List<String>) : ExportFilter {
    private val logger = Logger.getInstance(IgnoredNameFilter::class.java)

    override fun shouldExclude(
            file: VirtualFile,
            properties: FileProperties,
            relativePath: String?
    ): ExclusionReason? {
        return if (properties.name in ignoredNames) {
            logger.info("Skipping ignored file/directory by name: ${properties.path}")
            ExclusionReason.IGNORED_NAME
        } else {
            null
        }
    }
}

/** Includes only files whose extension matches one of the configured filters. */
class FilenameFilter(private val filters: List<String>) : ExportFilter {
    private val logger = Logger.getInstance(FilenameFilter::class.java)

    override fun shouldExclude(
            file: VirtualFile,
            properties: FileProperties,
            relativePath: String?
    ): ExclusionReason? {
        if (filters.isEmpty()) return null

        val matchesFilter =
                filters.any { filter ->
                    val actualFilter = if (filter.startsWith(".")) filter else ".$filter"
                    properties.name.endsWith(actualFilter, ignoreCase = true)
                }

        return if (!matchesFilter) {
            logger.info("Skipping file due to filename filter: $relativePath")
            ExclusionReason.FILENAME_FILTER
        } else {
            null
        }
    }
}

/** Applies repository `.gitignore` rules via [HierarchicalGitignoreParser]. */
class GitignoreFilter(
        private val parser: HierarchicalGitignoreParser,
        private val explicitFiles: Set<VirtualFile>
) : ExportFilter {
    private val logger = Logger.getInstance(GitignoreFilter::class.java)

    override fun shouldExclude(
            file: VirtualFile,
            properties: FileProperties,
            relativePath: String?
    ): ExclusionReason? {
        // Override for explicitly selected files
        if (!properties.isDirectory && file in explicitFiles) {
            logger.info(">>> Gitignore Override: File was explicitly selected. Including '${properties.path}'")
            return null
        }

        return try {
            val isIgnored = ReadAction.compute<Boolean, Exception> { parser.isIgnored(file) }
            if (isIgnored) {
                logger.info(">>> Gitignore Match: YES. Skipping '${properties.path}'")
                ExclusionReason.GITIGNORE
            } else {
                logger.info(">>> Gitignore Match: NO. Proceeding with '${properties.path}'")
                null
            }
        } catch (e: Exception) {
            logger.warn("Error checking gitignore status for '${properties.path}'. Proceeding.", e)
            null
        }
    }
}

/** Fast extension-based binary detector used early in the pipeline. */
class KnownBinaryFilter : ExportFilter {
    private val logger = Logger.getInstance(KnownBinaryFilter::class.java)

    override fun shouldExclude(
            file: VirtualFile,
            properties: FileProperties,
            relativePath: String?
    ): ExclusionReason? {
        val isBinary =
                ReadAction.compute<Boolean, Exception> { FileUtils.isKnownBinaryExtension(file) }
        return if (isBinary) {
            logger.info("Skipping known binary file type: $relativePath")
            ExclusionReason.BINARY_CONTENT
        } else {
            null
        }
    }
}

/** Slower heuristic that inspects file content to detect binary data. */
class ContentBinaryFilter : ExportFilter {
    private val logger = Logger.getInstance(ContentBinaryFilter::class.java)

    override fun shouldExclude(
            file: VirtualFile,
            properties: FileProperties,
            relativePath: String?
    ): ExclusionReason? {
        val isBinary =
                try {
                    ReadAction.compute<Boolean, Exception> { FileUtils.isLikelyBinaryContent(file) }
                } catch (e: Exception) {
                    logger.warn("Failed deep binary check for $relativePath, assuming binary.", e)
                    true
                }

        return if (isBinary) {
            logger.info("Skipping likely binary file (content check): $relativePath")
            ExclusionReason.BINARY_CONTENT
        } else {
            null
        }
    }
}
