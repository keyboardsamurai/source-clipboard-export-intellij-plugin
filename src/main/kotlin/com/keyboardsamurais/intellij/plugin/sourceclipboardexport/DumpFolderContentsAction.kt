package com.keyboardsamurais.intellij.plugin.sourceclipboardexport

import SourceClipboardExportSettings
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.lang.Math.min

class DumpFolderContentsAction : AnAction() {
    private var fileCount = 0

    override fun actionPerformed(e: AnActionEvent) {
        val settings = SourceClipboardExportSettings.getInstance(e.project!!)
        fileCount = 0

        val project = e.project
        val selectedFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        if (project == null || selectedFiles.isNullOrEmpty()) {
            return
        }

        val fileContents: MutableList<String> = ArrayList()
        for (file in selectedFiles) {
            processFile(file, fileContents, project)
            if (fileCount >= settings.state.fileCount) {
                notifyFileLimitReached(settings.state.fileCount)
                break
            }
        }

        val sb = StringBuilder()
        sb.append(java.lang.String.join("\n", fileContents))
        copyToClipboard(sb.toString())
    }

    private fun processFile(file: VirtualFile, fileContents: MutableList<String>, project: Project) {
        val settings = SourceClipboardExportSettings.getInstance(project)
        val filters = settings.state.filenameFilters
        val repositoryRoot = getRepositoryRoot(project)

        if (file.isDirectory) {
            processDirectory(file, fileContents, project)
        } else {
            val relativePath = repositoryRoot?.let { VfsUtil.getRelativePath(file, it) } ?: file.name
            if (!isBinaryFile(file) && file.length <= 100 * 1024) {
                if (filters.isEmpty() || filters.any { file.name.endsWith(it) }) {
                    fileContents.add("// filename: $relativePath")
                    fileContents.add(fileContentsToString(file))
                    fileCount++
                }
            }
        }
    }

    private fun processDirectory(directory: VirtualFile, fileContents: MutableList<String>, project: Project) {
        for (file in directory.children) {
            if (fileCount >= 50) {
                return
            }
            processFile(file, fileContents, project)
        }
    }

    private fun getRepositoryRoot(project: Project): VirtualFile? {
        val projectRootManager = ProjectRootManager.getInstance(project)
        val contentRoots = projectRootManager.contentRoots
        return contentRoots.firstOrNull()
    }

    private fun isBinaryFile(file: VirtualFile): Boolean {
        val bytes = file.contentsToByteArray()
        // A null byte is often an indicator of a binary file
        val nullByteCount = bytes.count { it == 0x00.toByte() }

        // Check a larger sample size depending on the size of the file
        val sampleSize = min(bytes.size, 1024)
        val nonTextBytes = bytes.take(sampleSize).count {
            (it !in 0x20..0x7E) &&
                    (it !in listOf(0x09.toByte(), 0x0A.toByte(), 0x0D.toByte()))
        }

        // Determine if the file is binary using the presence of null bytes or a high percentage of non-text bytes
        val threshold = 0.30 // Adjusted threshold to 30%
        return nullByteCount > 0 || (nonTextBytes.toFloat() / sampleSize) > threshold
    }


    private fun fileContentsToString(file: VirtualFile): String {
        return try {
            String(file.contentsToByteArray(), file.charset)
        } catch (e: Exception) {
            LOGGER.error("Error reading file contents", e)
            "Error reading file: " + file.path
        }
    }

    private fun copyToClipboard(text: String) {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    }

    private fun notifyFileLimitReached(fileCount: Int) {
        Notifications.Bus.notify(
            Notification(
                "Plugin Error",
                "File Count Limit Reached",
                "Processing stopped after reaching the limit of $fileCount files.",
                NotificationType.WARNING
            )
        )
        LOGGER.info("Reached the limit of $fileCount files, stopping further processing.")
    }

    companion object {
        private val LOGGER = Logger.getInstance(DumpFolderContentsAction::class.java)
    }
}
