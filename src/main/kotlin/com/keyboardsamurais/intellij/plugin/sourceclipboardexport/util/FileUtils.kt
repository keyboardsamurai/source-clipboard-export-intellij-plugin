package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VfsUtilCore
import java.io.IOException
import java.io.InputStream
import kotlin.math.min

object FileUtils {
    private val LOGGER = Logger.getInstance(FileUtils::class.java)

    fun getRelativePath(file: VirtualFile, project: Project): String {
        val repositoryRoot = getRepositoryRoot(project)
        return repositoryRoot?.let { VfsUtil.getRelativePath(file, it, '/') } ?: file.name
    }

    fun getRepositoryRoot(project: Project): VirtualFile? {
        return ProjectRootManager.getInstance(project).contentRoots.firstOrNull()
    }

    fun isKnownBinaryExtension(file: VirtualFile): Boolean {
        return file.extension?.lowercase() in AppConstants.COMMON_BINARY_EXTENSIONS
    }

    fun isLikelyBinaryContent(file: VirtualFile): Boolean {
        if (file.length == 0L) return false
        val sampleSize = min(file.length, 1024).toInt()
        val bytes = try {
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

    fun readFileContent(file: VirtualFile): String {
        return try {
            VfsUtil.loadText(file)
        } catch (e: Exception) {
            LOGGER.error("Error reading file contents: ${file.path}", e)
            "// Error reading file: ${file.path} (${e.message})"
        }
    }
} 