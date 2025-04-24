package com.keyboardsamurais.intellij.plugin.sourceclipboardexport

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.awt.datatransfer.StringSelection
import java.lang.Math.min
import java.util.*
import javax.swing.Timer

class DumpFolderContentsAction : AnAction() {
    private var fileCount = 0
    private var excludedFileCount = 0
    private var processedFileCount = 0
    private val excludedExtensions = Collections.synchronizedSet(mutableSetOf<String>())

    override fun actionPerformed(e: AnActionEvent) {
        LOGGER.info("Action initiated: DumpFolderContentsAction")
        val project = e.project
        val selectedFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        if (project == null || selectedFiles.isNullOrEmpty()) {
            LOGGER.warn("Action aborted: No project found or no files selected.")
            showNotification(project, "Error", "No files selected", NotificationType.ERROR)
            return
        }

        val settings = SourceClipboardExportSettings.getInstance()
        fileCount = 0
        excludedFileCount = 0
        processedFileCount = 0
        excludedExtensions.clear()

        LOGGER.info("Selected files/directories count: ${selectedFiles.size}")
        LOGGER.info("Settings: Max Files=${settings.state.fileCount}, Max Size KB=${settings.state.maxFileSizeKb}, Filters Enabled=${settings.state.areFiltersEnabled}, Filters=${settings.state.filenameFilters.joinToString()}, Ignored=${settings.state.ignoredNames.joinToString()}, Include Prefix=${settings.state.includePathPrefix}")

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Exporting Source to Clipboard") {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = false
                indicator.text = "Processing files..."
                val fileContents = Collections.synchronizedList(mutableListOf<String>())

                runBlocking {
                    val processingScope = this

                    val jobs = selectedFiles.map { file ->
                        launch(Dispatchers.IO) {
                            ensureActive()
                            LOGGER.info("Processing file/directory: ${file.path}")
                            processFile(file, fileContents, project, indicator, settings, processingScope)
                            if (fileCount >= settings.state.fileCount) {
                                if (processingScope.isActive) {
                                    LOGGER.info("File count limit (${settings.state.fileCount}) reached or exceeded. Requesting cancellation.")
                                    notifyFileLimitReached(project, settings.state.fileCount)
                                    processingScope.cancel()
                                }
                            }
                        }
                    }
                    try {
                        jobs.joinAll()
                    } catch (ce: CancellationException) {
                        LOGGER.info("Processing cancelled, likely due to file limit.")
                    } catch (e: Exception) {
                        LOGGER.error("Error waiting for processing jobs", e)
                    }
                }

                indicator.text = "Finalizing..."
                if (fileContents.isEmpty()) {
                    LOGGER.warn("No file contents were collected for clipboard operation.")
                    showNotification(project, "Warning", "No content to copy (check filters, size limits, and ignored files)", NotificationType.WARNING)
                } else {
                    val sb = StringBuilder()
                    sb.append(fileContents.joinToString("\n"))
                    try {
                        val text = sb.toString()
                        copyToClipboard(project, text)
                        LOGGER.info("Successfully copied contents to clipboard. Content size: ${text.length} characters")
                    } catch (ex: Exception) {
                        LOGGER.error("Failed to copy contents to clipboard.", ex)
                        showNotification(project, "Error", "Failed to copy to clipboard: ${ex.message}", NotificationType.ERROR)
                    }
                }

                LOGGER.info("Action completed: DumpFolderContentsAction")
                showOperationSummary(project, settings.state.filenameFilters, excludedFileCount, processedFileCount, excludedExtensions, settings.state.areFiltersEnabled)
            }
        })
    }

    private suspend fun processFile(
        file: VirtualFile,
        fileContents: MutableList<String>,
        project: Project,
        indicator: ProgressIndicator,
        settings: SourceClipboardExportSettings,
        scope: CoroutineScope
    ) {
        scope.ensureActive()

        val repositoryRoot = getRepositoryRoot(project)

        if (file.name in settings.state.ignoredNames) {
            LOGGER.info("Skipping ignored file/directory: ${file.name}")
            return
        }

        if (file.isDirectory) {
            LOGGER.info("Processing directory: ${file.path}")
            processDirectory(file, fileContents, project, indicator, settings, scope)
        } else {
            val relativePath = repositoryRoot?.let { VfsUtil.getRelativePath(file, it, '/') } ?: file.name
            LOGGER.info("Processing individual file: $relativePath")

            if (shouldSkipFile(file, relativePath, settings)) {
                return
            }

            scope.ensureActive()

            try {
                val fileContent = fileContentsToString(file)
                if (fileContent.isEmpty()) {
                    LOGGER.warn("File content is empty, skipping file: $relativePath")
                } else {
                    synchronized(fileContents) {
                        if (fileCount < settings.state.fileCount) {
                            if (settings.state.includePathPrefix) {
                                fileContents.add("$FILENAME_PREFIX$relativePath")
                            }
                            fileContents.add(fileContent)
                            fileCount++
                            processedFileCount++
                            LOGGER.info("Added file content ($processedFileCount/$fileCount): $relativePath")
                        } else {
                            LOGGER.warn("File limit reached before adding file: $relativePath. Stopping addition.")
                            if (scope.isActive) scope.cancel()
                            return
                        }
                    }
                }
            } catch (e: Exception) {
                LOGGER.error("Error processing file: $relativePath", e)
            }

            val targetCount = settings.state.fileCount.toDouble()
            if (targetCount > 0) {
                indicator.fraction = min(1.0, fileCount.toDouble() / targetCount)
            }
            indicator.text = "Processed $processedFileCount files..."
        }
    }

    private fun shouldSkipFile(file: VirtualFile, relativePath: String, settings: SourceClipboardExportSettings): Boolean {
        if (file.extension?.lowercase() in COMMON_BINARY_EXTENSIONS) {
            LOGGER.info("Skipping known binary file type: $relativePath")
            return true
        }

        val maxSizeInBytes = settings.state.maxFileSizeKb * 1024L
        if (file.length > maxSizeInBytes) {
            LOGGER.info("Skipping file due to size limit (> ${settings.state.maxFileSizeKb} KB): $relativePath")
            return true
        }

        if (isBinaryFile(file)) {
            LOGGER.info("Skipping binary file (content check): $relativePath")
            return true
        }

        val filters = settings.state.filenameFilters
        if (settings.state.areFiltersEnabled && filters.isNotEmpty()) {
            val matchesFilter = filters.any { filter ->
                val actualFilter = if (filter.startsWith(".")) filter else ".$filter"
                file.name.endsWith(actualFilter, ignoreCase = true)
            }
            if (!matchesFilter) {
                LOGGER.info("Skipping file due to filename filter (filters enabled): $relativePath")
                val fileExtension = file.extension ?: "unknown"
                synchronized(excludedExtensions) {
                    excludedExtensions.add(fileExtension)
                }
                excludedFileCount++
                return true
            }
        }
        return false
    }

    private suspend fun processDirectory(
        directory: VirtualFile,
        fileContents: MutableList<String>,
        project: Project,
        indicator: ProgressIndicator,
        settings: SourceClipboardExportSettings,
        scope: CoroutineScope
    ) {
        if (directory.name in settings.state.ignoredNames) {
            LOGGER.info("Skipping ignored directory: ${directory.name}")
            return
        }

        try {
            for (file in directory.children) {
                scope.ensureActive()

                if (fileCount >= settings.state.fileCount) {
                    LOGGER.debug("File limit reached within directory loop: ${directory.path}. Stopping further processing in this directory.")
                    return
                }
                processFile(file, fileContents, project, indicator, settings, scope)
            }
        } catch (ce: CancellationException) {
            LOGGER.info("Directory processing cancelled: ${directory.path}")
            throw ce
        } catch (e: Exception) {
            LOGGER.error("Error processing directory children: ${directory.path}", e)
        }
    }

    private fun getRepositoryRoot(project: Project): VirtualFile? {
        val projectRootManager = ProjectRootManager.getInstance(project)
        val contentRoots = projectRootManager.contentRoots
        return contentRoots.firstOrNull()
    }

    private fun isBinaryFile(file: VirtualFile): Boolean {
        if (file.length == 0L) return false

        val sampleSize = min(file.length, 1024).toInt()
        val bytes = try {
            file.inputStream.use { it.readNBytes(sampleSize) }
        } catch (e: Exception) {
            LOGGER.warn("Could not read file sample for binary check: ${file.path}", e)
            return true
        }

        val nullByteCount = bytes.count { it == 0x00.toByte() }
        if (nullByteCount > 0) {
            return true
        }

        val nonTextBytes = bytes.count {
            val byteVal = it.toInt() and 0xFF
            (byteVal < 0x20 && byteVal !in listOf(0x09, 0x0A, 0x0D)) ||
            (byteVal > 0x7E)
        }

        val threshold = 0.10
        val nonTextRatio = if (sampleSize > 0) nonTextBytes.toFloat() / sampleSize else 0f

        if (nonTextRatio > threshold) {
            return true
        }

        return false
    }

    private fun fileContentsToString(file: VirtualFile): String {
        return try {
            VfsUtil.loadText(file)
        } catch (e: Exception) {
            LOGGER.error("Error reading file contents: ${file.path}", e)
            "// Error reading file: ${file.path} (${e.message})"
        }
    }

    private fun copyToClipboard(project: Project?, text: String) {
        LOGGER.info("Copying to clipboard. Content length: ${text.length} characters")
        try {
            CopyPasteManager.getInstance().setContents(StringSelection(text))
            showNotification(
                project,
                "Content Copied",
                "Selected content (${processedFileCount} files, ${text.length} chars) copied to clipboard.",
                NotificationType.INFORMATION
            )
        } catch (e: Exception) {
            LOGGER.error("Failed to set clipboard contents", e)
            showNotification(project, "Error", "Failed to copy to clipboard: ${e.message}", NotificationType.ERROR)
        }
    }

    private fun notifyFileLimitReached(project: Project?, limit: Int) {
        showNotification(
            project,
            "File Limit Reached",
            "Processing stopped after reaching the limit of $limit files.",
            NotificationType.WARNING
        )
    }

    private fun showOperationSummary(
        project: Project?,
        filters: List<String>,
        excludedFiles: Int,
        processedFiles: Int,
        excludedTypes: Set<String>,
        filtersWereEnabled: Boolean
    ) {
        val summaryLines = mutableListOf<String>()
        summaryLines.add("Processed files: $processedFiles")

        if (excludedFiles > 0) {
            summaryLines.add("Excluded files (due to filters): $excludedFiles")
            if (excludedTypes.isNotEmpty()) {
                val topTypes = excludedTypes.take(5).joinToString(", ")
                val moreTypes = if (excludedTypes.size > 5) ", ..." else ""
                summaryLines.add("Excluded types: $topTypes$moreTypes")
            }
        } else if (filtersWereEnabled && filters.isNotEmpty()) {
            summaryLines.add("Filters were active, but no files excluded by them.")
        }

        if (filtersWereEnabled && filters.isNotEmpty()) {
            summaryLines.add("Active filters: ${filters.joinToString(", ")}")
        }

        showNotification(
            project,
            "Export Operation Summary",
            summaryLines.joinToString("\n"),
            NotificationType.INFORMATION
        )
    }

    private fun showNotification(project: Project?, title: String, content: String, type: NotificationType) {
        val formattedContent = content.replace("\n", "<br>")
        val notification = Notification(
            NOTIFICATION_GROUP_ID,
            title,
            formattedContent,
            type
        )
        Notifications.Bus.notify(notification, project)
    }

    companion object {
        private val LOGGER = Logger.getInstance(DumpFolderContentsAction::class.java)
        private const val FILENAME_PREFIX = "// filename: "
        private const val NOTIFICATION_GROUP_ID = "SourceClipboardExport"

        private val COMMON_BINARY_EXTENSIONS = setOf(
            "png", "jpg", "jpeg", "gif", "bmp", "tiff", "ico",
            "mp3", "wav", "ogg", "flac", "aac",
            "mp4", "avi", "mov", "wmv", "mkv",
            "zip", "rar", "7z", "tar", "gz", "bz2",
            "exe", "dll", "so", "dylib", "app",
            "o", "obj", "lib", "a",
            "class",
            "pyc",
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "jar", "war", "ear",
            "woff", "woff2", "ttf", "otf", "eot",
            "db", "sqlite", "mdb",
            "iso", "img",
            "swf"
        )
    }
}

fun Notification.expireAfter(millis: Int) {
    Timer(millis) { expire() }.apply {
        isRepeats = false
        start()
    }
}
