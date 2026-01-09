package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import kotlin.math.min

/**
 * File/VFS helpers that need to run inside read actions and are shared by exporter core,
 * notifications, and IntelliJ services. Keeping them centralized simplifies mocking in tests.
 */
object FileUtils {
    private val LOGGER = Logger.getInstance(FileUtils::class.java)

    /**
     * Generates a text-based tree representation of the directory structure for the given file paths.
     * 
     * @param filePaths List of file paths (relative to the repository root)
     * @param includeFiles Whether to include files in the tree representation
     * @return A formatted string representing the directory structure
     */
    fun generateDirectoryTree(filePaths: List<String>, includeFiles: Boolean): String {
        if (filePaths.isEmpty()) return ""

        // Build a tree structure from the file paths
        val root = DirectoryNode("", isRoot = true)

        for (path in filePaths) {
            val parts = path.split('/')
            var currentNode = root

            // Process each part of the path
            for (i in parts.indices) {
                val part = parts[i]
                val isFile = i == parts.lastIndex

                // Skip files if not including them
                if (isFile && !includeFiles) break

                // Find or create child node
                var childNode = currentNode.children.find { it.name == part }
                if (childNode == null) {
                    childNode = DirectoryNode(part, isFile = isFile)
                    currentNode.children.add(childNode)
                }
                currentNode = childNode
            }
        }

        // Generate the tree representation
        val sb = StringBuilder()
        sb.append("// Directory structure\n")
        generateTreeString(root, sb, "", "")
        sb.append("// End of directory structure")

        return sb.toString()
    }

    /**
     * Helper class to represent a node in the directory tree.
     */
    private class DirectoryNode(
        val name: String,
        val isFile: Boolean = false,
        val isRoot: Boolean = false,
        val children: MutableList<DirectoryNode> = mutableListOf()
    )

    /**
     * Recursively generates the string representation of the tree.
     */
    private fun generateTreeString(node: DirectoryNode, sb: StringBuilder, prefix: String, childPrefix: String) {
        if (!node.isRoot) {
            sb.append("// ")
            sb.append(prefix)
            sb.append(node.name)
            sb.append('\n')
        }

        // Sort children: directories first, then files, both alphabetically
        val sortedChildren = node.children.sortedWith(compareBy<DirectoryNode> { it.isFile }.thenBy { it.name })

        for (i in sortedChildren.indices) {
            val child = sortedChildren[i]
            val isLast = i == sortedChildren.lastIndex

            val newPrefix = childPrefix + (if (isLast) "└── " else "├── ")
            val newChildPrefix = childPrefix + (if (isLast) "    " else "│   ")

            generateTreeString(child, sb, newPrefix, newChildPrefix)
        }
    }

    /**
     * Produces a repository-relative path for notifications and headings. Falls back to the file
     * name when no repository root is available (e.g., tests).
     */
    fun getRelativePath(file: VirtualFile, project: Project): String {
        val repositoryRoot = getRepositoryRoot(project)
        return repositoryRoot?.let { VfsUtil.getRelativePath(file, it, '/') } ?: file.name
    }

    /** Returns the first content root for the project, or `null` in tests/headless environments. */
    fun getRepositoryRoot(project: Project): VirtualFile? {
        return try {
            ReadAction.compute<VirtualFile?, Exception> {
                ProjectRootManager.getInstance(project).contentRoots.firstOrNull()
            }
        } catch (e: Exception) {
            // In test environments, the ProjectRootManager service might not be available
            LOGGER.warn("Could not get repository root: ${e.message}")
            null
        }
    }

    /** Checks the extension against [AppConstants.COMMON_BINARY_EXTENSIONS]. */
    fun isKnownBinaryExtension(file: VirtualFile): Boolean {
        return file.extension?.lowercase() in AppConstants.COMMON_BINARY_EXTENSIONS
    }

    /**
     * Performs a UTF-8 aware heuristic on the first kilobyte of the file to detect binary content.
     * Used as a fallback when the extension is unknown.
     *
     * The algorithm:
     * 1. Skips BOM (Byte Order Mark) if present
     * 2. Returns true immediately if null bytes are found (strongest binary indicator)
     * 3. Validates UTF-8 sequences and counts only truly suspicious bytes:
     *    - Control characters (0x00-0x08, 0x0E-0x1F) excluding tab, LF, CR
     *    - Orphan UTF-8 continuation bytes (0x80-0xBF without a lead byte)
     *    - Invalid UTF-8 lead bytes (0xF8-0xFF)
     *    - Truncated/invalid UTF-8 sequences
     * 4. Returns true if suspicious bytes exceed 30% of the sample
     */
    fun isLikelyBinaryContent(file: VirtualFile): Boolean {
        if (file.length == 0L) return false
        val sampleSize = min(file.length, 1024).toInt()
        val bytes = try {
            file.inputStream.use { it.readNBytes(sampleSize) }
        } catch (e: Exception) {
            LOGGER.warn("Could not read file sample for binary check: ${file.path}", e)
            return true // Treat as binary if unreadable
        }

        // Skip BOM if present
        val bomLength = detectBomLength(bytes)
        val startOffset = bomLength

        // Null byte check - strongest binary indicator
        for (i in startOffset until bytes.size) {
            if (bytes[i] == 0x00.toByte()) return true
        }

        // UTF-8 aware suspicious byte counting
        var suspiciousCount = 0
        var i = startOffset
        while (i < bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            when {
                b <= 0x7F -> {
                    // ASCII range: flag control chars except tab (0x09), LF (0x0A), CR (0x0D)
                    if (b < 0x20 && b != 0x09 && b != 0x0A && b != 0x0D) {
                        suspiciousCount++
                    }
                    i++
                }
                b in 0xC0..0xDF -> {
                    // 2-byte UTF-8 sequence lead byte
                    if (isValidUtf8Sequence(bytes, i, 1)) {
                        i += 2 // Skip valid 2-byte sequence
                    } else {
                        suspiciousCount++
                        i++
                    }
                }
                b in 0xE0..0xEF -> {
                    // 3-byte UTF-8 sequence lead byte
                    if (isValidUtf8Sequence(bytes, i, 2)) {
                        i += 3 // Skip valid 3-byte sequence
                    } else {
                        suspiciousCount++
                        i++
                    }
                }
                b in 0xF0..0xF7 -> {
                    // 4-byte UTF-8 sequence lead byte
                    if (isValidUtf8Sequence(bytes, i, 3)) {
                        i += 4 // Skip valid 4-byte sequence
                    } else {
                        suspiciousCount++
                        i++
                    }
                }
                else -> {
                    // Orphan continuation byte (0x80-0xBF) or invalid lead byte (0xF8-0xFF)
                    suspiciousCount++
                    i++
                }
            }
        }

        val effectiveLength = bytes.size - startOffset
        if (effectiveLength <= 0) return false

        val suspiciousRatio = suspiciousCount.toFloat() / effectiveLength
        return suspiciousRatio > 0.30f // 30% threshold for truly suspicious content
    }

    /**
     * Detects the length of a BOM (Byte Order Mark) at the start of the byte array.
     * Supports UTF-8, UTF-16 LE, and UTF-16 BE BOMs.
     *
     * @return The number of bytes to skip (0 if no BOM detected)
     */
    private fun detectBomLength(bytes: ByteArray): Int {
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            return 3 // UTF-8 BOM
        }
        if (bytes.size >= 2) {
            if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
                return 2 // UTF-16 LE BOM
            }
            if (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
                return 2 // UTF-16 BE BOM
            }
        }
        return 0
    }

    /**
     * Validates that a UTF-8 multi-byte sequence starting at [start] has the expected
     * number of valid continuation bytes (0x80-0xBF).
     *
     * @param bytes The byte array to check
     * @param start The index of the lead byte
     * @param expectedContinuation The number of continuation bytes expected (1-3)
     * @return true if all continuation bytes are valid, false otherwise
     */
    private fun isValidUtf8Sequence(bytes: ByteArray, start: Int, expectedContinuation: Int): Boolean {
        if (start + expectedContinuation >= bytes.size) {
            return false // Not enough bytes remaining
        }
        for (j in 1..expectedContinuation) {
            val continuationByte = bytes[start + j].toInt() and 0xFF
            if (continuationByte < 0x80 || continuationByte > 0xBF) {
                return false // Not a valid continuation byte
            }
        }
        return true
    }

    /**
     * Reads file contents taking care to stay inside IntelliJ's read-access constraints. Falls back
     * to `VfsUtil.loadText` so tests that mock `contentsToByteArray` still work.
     */
    fun readFileContent(file: VirtualFile): String {
        return try {
            // Try to use contentsToByteArray first, which is more likely to be mocked in tests
            try {
                String(file.contentsToByteArray(), file.charset)
            } catch (e: Exception) {
                // Fall back to VfsUtil.loadText if contentsToByteArray fails
                VfsUtil.loadText(file)
            }
        } catch (e: Exception) {
            LOGGER.error("Error reading file contents: ${file.path}", e)
            "// Error reading file: ${file.path} (${e.message})"
        }
    }

    /**
     * Gets the appropriate comment prefix for a file based on its extension.
     * Returns the default C-style comment prefix if the extension is not recognized.
     *
     * @param file The file to get the comment prefix for
     * @return The appropriate comment prefix for the file
     */
    fun getCommentPrefix(file: VirtualFile): String {
        val extension = file.extension?.lowercase()
        // Try by extension first
        val byExt = extension?.let { AppConstants.COMMENT_PREFIXES[it] }
        if (byExt != null) return byExt
        // Fallback to special filenames without extensions (e.g., Dockerfile, Makefile)
        val name: String? = try { file.name } catch (_: Exception) { null }
        if (!name.isNullOrBlank()) {
            AppConstants.COMMENT_PREFIXES[name]?.let { return it }
            AppConstants.COMMENT_PREFIXES[name.lowercase()]?.let { return it }
        }
        return AppConstants.FILENAME_PREFIX
    }

    /**
     * Checks if the file content already starts with any of the known filename prefixes.
     *
     * @param fileContent The content of the file to check
     * @return True if the file content already starts with a filename prefix, false otherwise
     */
    fun hasFilenamePrefix(fileContent: String): Boolean {
        // Check for standard prefixes (non-HTML style)
        val standardPrefixMatch = AppConstants.COMMENT_PREFIXES.values
            .filter { !it.endsWith("-->") }
            .any { prefix -> fileContent.startsWith(prefix) }

        if (standardPrefixMatch) return true

        // Special check for HTML-style comments
        val htmlPrefixPattern = """^\s*<!--\s*filename:\s*.*\s*-->""".toRegex()
        if (htmlPrefixPattern.find(fileContent) != null) return true

        // Check default prefix as fallback
        return fileContent.startsWith(AppConstants.FILENAME_PREFIX)
    }
}
