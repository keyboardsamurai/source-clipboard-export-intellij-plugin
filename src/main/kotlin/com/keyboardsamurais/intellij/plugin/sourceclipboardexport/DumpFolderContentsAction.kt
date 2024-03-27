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
import java.util.*
import javax.swing.Timer
import kotlin.collections.ArrayList

class DumpFolderContentsAction : AnAction() {
    private var fileCount = 0
    private var excludedFileCount = 0
    private var processedFileCount = 0
    private val excludedExtensions = mutableSetOf<String>()


    override fun actionPerformed(e: AnActionEvent) {
        LOGGER.info("Action initiated: DumpFolderContentsAction");
        val project = e.project;
        val selectedFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        if (project == null || selectedFiles.isNullOrEmpty()) {
            LOGGER.warn("Action aborted: No project found or no files selected.");
            return;
        }


        val settings = SourceClipboardExportSettings.getInstance();
        fileCount = 0;

        LOGGER.info("Selected files/directories count: " + selectedFiles.size);
        val fileContents: MutableList<String> = ArrayList();
        for (file in selectedFiles) {
            LOGGER.info("Processing file/directory: " + file.getPath());
            processFile(file, fileContents, project);
            if (fileCount >= settings.state.fileCount) {
                notifyFileLimitReached(settings.state.fileCount);
                break;
            }
        }

        if (fileContents.isEmpty()) {
            LOGGER.warn("No file contents were collected for clipboard operation.");
        } else {
            val sb = StringBuilder();
            sb.append(java.lang.String.join("\n", fileContents));
            try {
                val text = sb.toString()
                copyToClipboard(text);
                LOGGER.info("Successfully copied contents to clipboard. Content size: " + text.length);
            } catch (ex: Exception) {
                LOGGER.error("Failed to copy contents to clipboard.", ex);
            }
        }
        LOGGER.info("Action completed: DumpFolderContentsAction");
        showNotification(settings.state.filenameFilters, excludedFileCount, processedFileCount)

    }
    private fun processFile(file: VirtualFile, fileContents: MutableList<String>, project: Project) {
        LOGGER.info("Starting to process file: ${file.path}")
        val settings = SourceClipboardExportSettings.getInstance()
        val filters = settings.state.filenameFilters
        val repositoryRoot = getRepositoryRoot(project)


        if (file.isDirectory) {
            LOGGER.info("File is a directory, processing directory: ${file.path}")
            processDirectory(file, fileContents, project)
        } else {
            val relativePath = repositoryRoot?.let { VfsUtil.getRelativePath(file, it) } ?: file.name
            LOGGER.info("Processing individual file: $relativePath")
            if (isBinaryFile(file)) {
                LOGGER.info("Skipping binary file: $relativePath")
                return
            }
            if (file.length > 100 * 1024) {
                LOGGER.info("Skipping file due to size limit (>100KB): $relativePath")
                return
            }
            if (filters.isNotEmpty() && filters.none { file.name.endsWith(it) }) {
                LOGGER.info("Skipping file due to filename filter: $relativePath")
                val fileExtension = file.extension ?: "unknown"
                excludedExtensions.add(fileExtension)

                excludedFileCount++
                return
            } else {
                // This else branch ensures only non-excluded files are counted as processed
                processedFileCount++
            }

            try {
                val fileContent = fileContentsToString(file)
                if (fileContent.isEmpty()) {
                    LOGGER.warn("File content is empty, skipping file: $relativePath")
                } else {
                    fileContents.add("// filename: $relativePath")
                    fileContents.add(fileContent)
                    fileCount++
                    LOGGER.info("Added file content to clipboard contents: $relativePath")
                }
            } catch (e: Exception) {
                LOGGER.error("Error processing file: $relativePath", e)
            }
        }
        LOGGER.info("Finished processing file: ${file.path}")
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
        LOGGER.info("Copying to clipboard. Content length: ${text.length}")
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
    private fun showNotification(filters: List<String>, excludedFiles: Int, processedFiles: Int) {
        val filterSummary =
            if (filters.isEmpty()) "" else "Excluded filters: ${filters.joinToString(", ")} \nNumber of excluded files: $excludedFiles"
        val exportedFilesSummary =
            """
        Number of exported files: $processedFiles
    """.trimIndent()

        val notificationContent = if (filterSummary.isEmpty()) exportedFilesSummary else "$filterSummary\n$exportedFilesSummary"

        val notification = Notification(
            "Execution",
            "Export Operation Summary",
            notificationContent,
            NotificationType.INFORMATION
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
