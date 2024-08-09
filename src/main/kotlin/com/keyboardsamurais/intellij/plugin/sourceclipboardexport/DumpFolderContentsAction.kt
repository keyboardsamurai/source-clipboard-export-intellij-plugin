package com.keyboardsamurais.intellij.plugin.sourceclipboardexport

import SourceClipboardExportSettings
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.*
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.lang.Math.min
import java.util.*
import javax.swing.JOptionPane
import javax.swing.Timer

class DumpFolderContentsAction : AnAction() {
    private var fileCount = 0
    private var excludedFileCount = 0
    private var processedFileCount = 0
    private val excludedExtensions = mutableSetOf<String>()

    override fun actionPerformed(e: AnActionEvent) {
        LOGGER.info("Action initiated: DumpFolderContentsAction")
        val project = e.project
        val selectedFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        if (project == null || selectedFiles.isNullOrEmpty()) {
            LOGGER.warn("Action aborted: No project found or no files selected.")
            showNotification("Error", "No files selected", NotificationType.ERROR)
            return
        }

        val settings = SourceClipboardExportSettings.getInstance()
        fileCount = 0
        excludedFileCount = 0
        processedFileCount = 0

        LOGGER.info("Selected files/directories count: ${selectedFiles.size}")

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Processing Files") {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = false
                val fileContents = Collections.synchronizedList(mutableListOf<String>())

                runBlocking {
                    val jobs = selectedFiles.map { file ->
                        launch(Dispatchers.IO) {
                            LOGGER.info("Processing file/directory: ${file.path}")
                            processFile(file, fileContents, project, indicator)
                            if (fileCount >= settings.state.fileCount) {
                                notifyFileLimitReached(settings.state.fileCount)
                                this.coroutineContext.cancelChildren()
                            }
                        }
                    }
                    jobs.joinAll()
                }

                if (fileContents.isEmpty()) {
                    LOGGER.warn("No file contents were collected for clipboard operation.")
                    showNotification("Warning", "No content to copy", NotificationType.WARNING)
                } else {
                    val sb = StringBuilder()
                    sb.append(fileContents.joinToString("\n"))
                    try {
                        val text = sb.toString()
                        copyToClipboard(text)
                        LOGGER.info("Successfully copied contents to clipboard. Content size: ${text.length}")
                    } catch (ex: Exception) {
                        LOGGER.error("Failed to copy contents to clipboard.", ex)
                        showNotification("Error", "Failed to copy to clipboard", NotificationType.ERROR)
                    }
                }

                LOGGER.info("Action completed: DumpFolderContentsAction")
                showOperationSummary(settings.state.filenameFilters, excludedFileCount, processedFileCount)
            }
        })
    }

    private suspend fun processFile(file: VirtualFile, fileContents: MutableList<String>, project: Project, indicator: ProgressIndicator) = withContext(Dispatchers.IO) {
        val settings = SourceClipboardExportSettings.getInstance()
        val filters = settings.state.filenameFilters
        val repositoryRoot = getRepositoryRoot(project)

        if (file.isDirectory) {
            LOGGER.info("Processing directory: ${file.path}")
            processDirectory(file, fileContents, project, indicator)
        } else {
            val relativePath = repositoryRoot?.let { VfsUtil.getRelativePath(file, it) } ?: file.name
            LOGGER.info("Processing individual file: $relativePath")

            if (shouldSkipFile(file, relativePath, filters)) return@withContext

            try {
                val fileContent = fileContentsToString(file)
                if (fileContent.isEmpty()) {
                    LOGGER.warn("File content is empty, skipping file: $relativePath")
                } else {
                    synchronized(fileContents) {
                        fileContents.add("// filename: $relativePath")
                        fileContents.add(fileContent)
                        fileCount++
                        processedFileCount++
                    }
                    LOGGER.info("Added file content to clipboard contents: $relativePath")
                }
            } catch (e: Exception) {
                LOGGER.error("Error processing file: $relativePath", e)
            }

            indicator.fraction = fileCount.toDouble() / settings.state.fileCount
        }
    }

    private fun shouldSkipFile(file: VirtualFile, relativePath: String, filters: List<String>): Boolean {
        if (isBinaryFile(file)) {
            LOGGER.info("Skipping binary file: $relativePath")
            return true
        }
        if (file.length > 100 * 1024) {
            LOGGER.info("Skipping file due to size limit (>100KB): $relativePath")
            return true
        }

        val settings = SourceClipboardExportSettings.getInstance()
        if (settings.state.areFiltersEnabled && filters.isNotEmpty() && filters.none { file.name.endsWith(it) }) {
            LOGGER.info("Skipping file due to filename filter: $relativePath")
            val fileExtension = file.extension ?: "unknown"
            excludedExtensions.add(fileExtension)
            excludedFileCount++
            return true
        }
        return false
    }
    private suspend fun processDirectory(directory: VirtualFile, fileContents: MutableList<String>, project: Project, indicator: ProgressIndicator) {
        val settings = SourceClipboardExportSettings.getInstance()
        for (file in directory.children) {
            if (fileCount >= settings.state.fileCount) {
                return
            }
            processFile(file, fileContents, project, indicator)
        }
    }

    private fun getRepositoryRoot(project: Project): VirtualFile? {
        val projectRootManager = ProjectRootManager.getInstance(project)
        val contentRoots = projectRootManager.contentRoots
        return contentRoots.firstOrNull()
    }

    private fun isBinaryFile(file: VirtualFile): Boolean {
        val bytes = file.contentsToByteArray()
        val nullByteCount = bytes.count { it == 0x00.toByte() }

        val sampleSize = min(bytes.size, 1024)
        val nonTextBytes = bytes.take(sampleSize).count {
            (it !in 0x20..0x7E) &&
                    (it !in listOf(0x09.toByte(), 0x0A.toByte(), 0x0D.toByte()))
        }

        val threshold = 0.30
        return nullByteCount > 0 || (nonTextBytes.toFloat() / sampleSize) > threshold
    }

    private fun fileContentsToString(file: VirtualFile): String {
        return try {
            String(file.contentsToByteArray(), file.charset)
        } catch (e: Exception) {
            LOGGER.error("Error reading file contents", e)
            "Error reading file: ${file.path}"
        }
    }

    private fun copyToClipboard(text: String) {
        LOGGER.info("Copying to clipboard. Content length: ${text.length}")
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
        showNotification(
            "Content Copied",
            "The selected content has been copied to the clipboard.",
            NotificationType.INFORMATION
        )
    }
    private fun notifyFileLimitReached(fileCount: Int) {
        showNotification(
            "File Count Limit Reached",
            "Processing stopped after reaching the limit of $fileCount files.",
            NotificationType.WARNING
        )
        LOGGER.info("Reached the limit of $fileCount files, stopping further processing.")
    }

    private fun showOperationSummary(filters: List<String>, excludedFiles: Int, processedFiles: Int) {
        val filterSummary =
            if (filters.isEmpty()) "" else "Excluded filters: ${filters.joinToString(", ")} \nNumber of excluded files: $excludedFiles"
        val exportedFilesSummary = "Number of exported files: $processedFiles"

        val notificationContent = if (filterSummary.isEmpty()) exportedFilesSummary else "$filterSummary\n$exportedFilesSummary"

        showNotification(
            "Export Operation Summary",
            notificationContent,
            NotificationType.INFORMATION
        )
    }

    private fun showNotification(title: String, content: String, type: NotificationType) {
        val notification = Notification(
            "Execution",
            title,
            content,
            type
        )
        notification.expireAfter(5000)
        Notifications.Bus.notify(notification)
    }

    companion object {
        private val LOGGER = Logger.getInstance(DumpFolderContentsAction::class.java)
    }
}

fun Notification.expireAfter(millis: Int) {
    Timer(millis) { expire() }.apply {
        isRepeats = false
        start()
    }
}
