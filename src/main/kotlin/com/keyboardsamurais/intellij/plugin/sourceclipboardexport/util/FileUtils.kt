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
     * Performs a lightweight heuristic on the first kilobyte of the file to detect binary content.
     * Used as a fallback when the extension is unknown.
     */
    fun isLikelyBinaryContent(file: VirtualFile): Boolean {
        if (file.length == 0L) return false
        val sampleSize = min(file.length, 1024).toInt()
        val bytes = try {
            // Always read only a small prefix of the file to avoid loading large files fully
            file.inputStream.use { it.readNBytes(sampleSize) }
        } catch (e: Exception) {
            LOGGER.warn("Could not read file sample for binary check: ${file.path}", e)
            return true // Treat as binary if unreadable
        }

        // Simple check for null bytes
        if (bytes.any { it == 0x00.toByte() }) return true

        // Check for high proportion of non-printable ASCII or control characters (excluding tab, LF, CR)
        val nonTextBytes = bytes.count {
            val byteVal = it.toInt() and 0xFF
            (byteVal < 0x20 && byteVal !in listOf(0x09, 0x0A, 0x0D)) || (byteVal > 0x7E)
        }
        val threshold = 0.10 // 10% non-text characters
        val nonTextRatio = if (sampleSize > 0) nonTextBytes.toFloat() / sampleSize else 0f
        return nonTextRatio > threshold
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
