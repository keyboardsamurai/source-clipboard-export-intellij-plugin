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

    /**
     * Splits a string by camelCase boundaries.
     * E.g., "myVariableName" -> ["my", "Variable", "Name"]
     * E.g., "URLHandler" -> ["URL", "Handler"]
     * E.g., "Simple" -> ["Simple"]
     */
    private fun splitCamelCase(input: String): List<String> {
        if (input.isEmpty()) return emptyList()

        // Regex Explanation:
        // (?<=\\p{Ll})(?=\\p{Lu}) : Positive lookbehind for a lowercase letter, positive lookahead for an uppercase letter. (e.g., splits between "my" and "Variable")
        // |
        // (?<=\\p{L})(?=\\p{Lu}\\p{Ll}) : Positive lookbehind for any letter, positive lookahead for an uppercase followed by a lowercase. (e.g., splits between "URL" and "Handler")
        // |
        // (?<=\\p{N})(?=\\p{L})   : Positive lookbehind for a number, positive lookahead for a letter. (e.g., splits "var123" and "Name" in "var123Name")
        // |
        // (?<=\\p{L})(?=\\p{N})   : Positive lookbehind for a letter, positive lookahead for a number. (e.g., splits "var" and "123" in "var123")

        val camelCaseSplitRegex = Regex("(?<=\\p{Ll})(?=\\p{Lu})|(?<=\\p{L})(?=\\p{Lu}\\p{Ll})|(?<=\\p{N})(?=\\p{L})|(?<=\\p{L})(?=\\p{N})") // Correctly escaped
        return camelCaseSplitRegex.split(input).filter { it.isNotEmpty() }
    }

    /**
     * Estimates token count using a regex heuristic combined with simulated subword splitting
     * for potential identifiers (camelCase, snake_case).
     *
     * 1. Splits text into potential tokens (words/identifiers vs symbols/punctuation).
     * 2. For identifier-like segments, further splits them by underscores and camelCase.
     * 3. Counts the total number of resulting segments.
     *
     * NOTE: This is STILL an APPROXIMATION and will differ from specific model tokenizers,
     * but it should correlate much better with code token counts than previous methods.
     *
     * @param text The text to estimate tokens for.
     * @return An approximate token count based on segmentation and subword heuristics.
     */
    private fun estimateTokensWithSubwordHeuristic(text: String): Int {
        if (text.isEmpty()) return 0

        // Base regex to separate identifier-like parts from symbols/punctuation and whitespace
        val baseSplitRegex = Regex("([\\p{L}\\p{N}_]+)|([^\\p{L}\\p{N}_\\s]+)|(\\s+)") // Correctly escaped
        // Group 1: Letters, Numbers, Underscore (Potential Identifiers/Keywords/Numbers)
        // Group 2: Non-Letters, Non-Numbers, Non-Underscore, Non-Whitespace (Symbols/Punctuation sequences)
        // Group 3: Whitespace sequences (we will generally ignore these for token count, but capture them)

        var tokenCount = 0
        val matches = baseSplitRegex.findAll(text)

        for (match in matches) {
            when {
                // Group 1: Potential Identifier/Keyword/Number
                match.groups[1] != null -> {
                    val identifierPart = match.value
                    // First, split by underscore for snake_case
                    val snakeParts = identifierPart.split('_').filter { it.isNotEmpty() }
                    var subTokenCount = 0
                    // Then, split each part by camelCase
                    for (part in snakeParts) {
                        subTokenCount += splitCamelCase(part).size
                    }
                    // If splitting resulted in 0 (e.g., input was just "_"), count the original match as 1
                    tokenCount += if (subTokenCount > 0) subTokenCount else 1
                }
                // Group 2: Symbol/Punctuation Sequence
                match.groups[2] != null -> {
                    // Treat each character in the symbol/punctuation sequence as a token
                    tokenCount += match.value.length
                }
                // Group 3: Whitespace - Ignored for token count.
            }
        }

        return tokenCount
    }

    override fun actionPerformed(e: AnActionEvent) {
        LOGGER.info("Action initiated: DumpFolderContentsAction")
        val project = e.project
        val selectedFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        if (project == null || selectedFiles.isNullOrEmpty()) {
            LOGGER.warn("Action aborted: No project found or no files selected.")
            showNotification("Error", "No files selected", NotificationType.ERROR, project)
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
                                    notifyFileLimitReached(settings.state.fileCount, project)
                                    processingScope.cancel()
                                }
                            }
                        }
                    }
                    try { jobs.joinAll() }
                    catch (ce: CancellationException) { LOGGER.info("Processing cancelled, likely due to file limit.") }
                    catch (e: Exception) { LOGGER.error("Error waiting for processing jobs", e) }
                } // End runBlocking

                indicator.text = "Finalizing..."
                if (fileContents.isEmpty()) {
                    LOGGER.warn("No file contents were collected for clipboard operation.")
                    showNotification("Warning", "No content to copy (check filters, size limits, and ignored files)", NotificationType.WARNING, project)
                } else {
                    val sb = StringBuilder()
                    sb.append(fileContents.joinToString("\n"))
                    try {
                        val text = sb.toString()
                        copyToClipboard(text, project)
                    } catch (ex: Exception) {
                        LOGGER.error("Failed to copy contents to clipboard.", ex)
                        showNotification("Error", "Failed to copy to clipboard: ${ex.message}", NotificationType.ERROR, project)
                    }
                }
                LOGGER.info("Action completed: DumpFolderContentsAction")
                showOperationSummary(settings.state.filenameFilters, excludedFileCount, processedFileCount, excludedExtensions, settings.state.areFiltersEnabled, project)
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
        return projectRootManager.contentRoots.firstOrNull()
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
        if (nullByteCount > 0) return true

        val nonTextBytes = bytes.count {
            val byteVal = it.toInt() and 0xFF
            (byteVal < 0x20 && byteVal !in listOf(0x09, 0x0A, 0x0D)) || (byteVal > 0x7E)
        }
        val threshold = 0.10
        val nonTextRatio = if (sampleSize > 0) nonTextBytes.toFloat() / sampleSize else 0f
        return nonTextRatio > threshold
    }

    private fun fileContentsToString(file: VirtualFile): String {
        return try {
            VfsUtil.loadText(file)
        } catch (e: Exception) {
            LOGGER.error("Error reading file contents: ${file.path}", e)
            "// Error reading file: ${file.path} (${e.message})"
        }
    }

    private fun copyToClipboard(text: String, project: Project?) {
        val charCount = text.length
        val approxTokens = estimateTokensWithSubwordHeuristic(text)

        LOGGER.info("Copying to clipboard. Chars: $charCount, Approx Tokens (Subword Heuristic): $approxTokens")
        try {
            CopyPasteManager.getInstance().setContents(StringSelection(text))
            showNotification(
                "Content Copied",
                "Selected content (${processedFileCount} files, $charCount chars, ~$approxTokens tokens) copied.",
                NotificationType.INFORMATION,
                project
            )
        } catch (e: Exception) {
            LOGGER.error("Failed to set clipboard contents", e)
            showNotification("Error", "Failed to copy to clipboard: ${e.message}", NotificationType.ERROR, project)
        }
    }

    private fun notifyFileLimitReached(limit: Int, project: Project? = null) {
        showNotification(
            "File Limit Reached",
            "Processing stopped after reaching the limit of $limit files.",
            NotificationType.WARNING,
            project
        )
    }

    private fun showOperationSummary(
        filters: List<String>,
        excludedFiles: Int,
        processedFiles: Int,
        excludedTypes: Set<String>,
        filtersWereEnabled: Boolean,
        project: Project?
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
            "Export Operation Summary",
            summaryLines.joinToString("<br>"),
            NotificationType.INFORMATION,
            project
        )
    }

    private fun showNotification(title: String, content: String, type: NotificationType, project: Project? = null) {
        val notification = Notification(
            NOTIFICATION_GROUP_ID,
            title,
            content,
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
            "class", "pyc",
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "jar", "war", "ear",
            "woff", "woff2", "ttf", "otf", "eot",
            "db", "sqlite", "mdb",
            "iso", "img", "swf"
        )
    }
}

fun Notification.expireAfter(millis: Int) {
    Timer(millis) { expire() }.apply {
        isRepeats = false
        start()
    }
}
