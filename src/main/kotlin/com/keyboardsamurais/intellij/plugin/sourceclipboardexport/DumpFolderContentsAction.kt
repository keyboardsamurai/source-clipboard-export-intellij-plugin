package com.keyboardsamurais.intellij.plugin.sourceclipboardexport


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

class DumpFolderContentsAction : AnAction() {
    private var fileCount = 0

    override fun actionPerformed(e: AnActionEvent) {
        // Reset file count for each action
        fileCount = 0

        // Getting the project and the selected folder
        val project = e.project
        val selectedFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)

        if (project == null || selectedFiles.isNullOrEmpty()) {
            LOGGER.info("No project or selection found")
            return
        }

        val sb = StringBuilder()
        for (file in selectedFiles) {
            if (file.isDirectory) {
                val fileContents: MutableList<String> = ArrayList()
                processDirectory(file, fileContents, "", project)
                sb.append(java.lang.String.join("\n", fileContents))
                if (fileCount >= 50) {
                    LOGGER.info("Reached the limit of 50 files, stopping further processing.")
                    break
                }
            }
        }

        copyToClipboard(sb.toString())
    }

    private fun processDirectory(
        directory: VirtualFile,
        fileContents: MutableList<String>,
        pathPrefix: String,
        project: Project
    ) {
        val repositoryRoot = getRepositoryRoot(project)
        for (file in directory.children) {
            if (fileCount >= 50) {
                return
            }

            val relativePath = repositoryRoot?.let { VfsUtil.getRelativePath(file, it) } ?: file.name
            if (file.isDirectory) {
                processDirectory(file, fileContents, "$relativePath/", project)
            } else if (!isBinaryFile(file) && file.length <= 100 * 1024) {
                fileContents.add("// filename: $relativePath")
                fileContents.add(fileContentsToString(file))
                fileCount++

                if (fileCount >= 50) {
                    return
                }
            }
        }
    }

    private fun getRepositoryRoot(project: Project): VirtualFile? {
        val projectRootManager = ProjectRootManager.getInstance(project)
        val contentRoots = projectRootManager.contentRoots
        return contentRoots.firstOrNull()
    }

    private fun isBinaryFile(file: VirtualFile): Boolean {
        val bytes = file.contentsToByteArray()
        return bytes.size > 1024 && bytes.take(1024).any { it == 0x00.toByte() }
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

    companion object {
        private val LOGGER = Logger.getInstance(DumpFolderContentsAction::class.java)
    }
}
