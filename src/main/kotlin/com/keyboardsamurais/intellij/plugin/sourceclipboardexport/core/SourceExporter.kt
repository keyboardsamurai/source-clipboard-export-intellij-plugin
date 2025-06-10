package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.config.SourceClipboardExportSettings
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core.gitignore.HierarchicalGitignoreParser
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.AppConstants
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.FileUtils
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.StringUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min

class SourceExporter(
    private val project: Project,
    private val settings: SourceClipboardExportSettings.State,
    private val indicator: ProgressIndicator
) {
    private val logger = Logger.getInstance(SourceExporter::class.java)
    // Use a thread-safe list for collecting results from multiple coroutines
    // We'll use a more efficient approach with local buffers and merging at the end
    private val fileContents = Collections.synchronizedList(mutableListOf<String>())
    // Use AtomicIntegers for thread-safe counting from multiple coroutines
    private val fileCount = AtomicInteger(0)
    private val processedFileCount = AtomicInteger(0)
    private val excludedByFilterCount = AtomicInteger(0)
    private val excludedBySizeCount = AtomicInteger(0)
    private val excludedByBinaryContentCount = AtomicInteger(0)
    private val excludedByIgnoredNameCount = AtomicInteger(0)
    private val excludedByGitignoreCount = AtomicInteger(0)
    private val excludedExtensions = Collections.synchronizedSet(mutableSetOf<String>())

    // Thread-safe counter for generating unique task identifiers
    private val taskCounter = AtomicInteger(0)

    // Store the hierarchical gitignore parser instance
    private val hierarchicalGitignoreParser = HierarchicalGitignoreParser(project)

    data class ExportResult(
        val content: String,
        val processedFileCount: Int,
        val excludedByFilterCount: Int,
        val excludedBySizeCount: Int,
        val excludedByBinaryContentCount: Int,
        val excludedByIgnoredNameCount: Int,
        val excludedByGitignoreCount: Int,
        val excludedExtensions: Set<String>,
        val limitReached: Boolean
    )

    /**
     * Adds line numbers to the file content.
     * Each line will be prefixed with its line number.
     *
     * @param content The file content to add line numbers to
     * @return The file content with line numbers added
     */
    private fun addLineNumbers(content: String): String {
        val lines = content.lines()
        val lineNumberWidth = lines.size.toString().length
        val result = StringBuilder()

        for ((index, line) in lines.withIndex()) {
            val lineNumber = (index + 1).toString().padStart(lineNumberWidth, ' ')
            result.append("$lineNumber: $line\n")
        }

        // Remove the last newline if the original content didn't end with one
        if (!content.endsWith("\n") && result.isNotEmpty()) {
            result.setLength(result.length - 1)
        }

        return result.toString()
    }


    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun exportSources(selectedFiles: Array<VirtualFile>): ExportResult {
        logger.info("Starting source export process.")
        logger.info("Settings: Max Files=${settings.fileCount}, Max Size KB=${settings.maxFileSizeKb}, Filters Enabled=${settings.areFiltersEnabled}, Filters=${settings.filenameFilters.joinToString()}, Ignored=${settings.ignoredNames.joinToString()}, Include Prefix=${settings.includePathPrefix}, Include Line Numbers=${settings.includeLineNumbers}")

        // Clear the hierarchical parser's cache to ensure fresh .gitignore parsing
        hierarchicalGitignoreParser.clearCache()

        indicator.isIndeterminate = false
        indicator.text = "Scanning files..."

        // Use a map to store local buffers for each coroutine
        val localBuffers = ConcurrentHashMap<Long, MutableList<String>>()

        coroutineScope {
            val scopeJob = SupervisorJob()
            // Limit parallelism to avoid spawning too many coroutines
            // This prevents excessive thread creation and context switching
            val limitedDispatcher = Dispatchers.IO.limitedParallelism(Runtime.getRuntime().availableProcessors()) // Use system CPU count

            selectedFiles.forEach { file ->
                launch(scopeJob + limitedDispatcher) {
                    ensureActive()
                    // Create a local buffer for this coroutine
                    val localBuffer = mutableListOf<String>()
                    // Store the buffer in the map using a unique task ID as the key
                    val taskId = taskCounter.incrementAndGet().toLong()
                    localBuffers[taskId] = localBuffer
                    // Process the file with the local buffer
                    processEntry(file, this, localBuffer)
                }
            }
            scopeJob.children.forEach { it.join() }
            scopeJob.complete()
            scopeJob.join()
        }

        // Merge all local buffers into the shared list
        fileContents.clear()
        localBuffers.values.forEach { buffer ->
            fileContents.addAll(buffer)
        }

        val finalProcessedCount = processedFileCount.get()
        val finalFileCount = fileCount.get()
        logger.info("Export process finished. Processed: $finalProcessedCount, Total Considered: $finalFileCount, Excluded (Filter: ${excludedByFilterCount.get()}, Size: ${excludedBySizeCount.get()}, Binary: ${excludedByBinaryContentCount.get()}, IgnoredName: ${excludedByIgnoredNameCount.get()}, Gitignore: ${excludedByGitignoreCount.get()})")

        // Generate directory structure if enabled
        val contentBuilder = StringBuilder()

        if (settings.includeDirectoryStructure) {
            // Extract relative paths from file contents (they are prefixed with "// filename: ")
            val filePaths = mutableListOf<String>()
            for (i in 0 until fileContents.size) {
                val content = fileContents[i]
                if (content.startsWith(AppConstants.FILENAME_PREFIX)) {
                    // Extract the path from the first line (before the first newline)
                    val firstLine = content.substringBefore('\n')
                    val path = firstLine.substring(AppConstants.FILENAME_PREFIX.length).trim()
                    filePaths.add(path)
                }
            }

            // Generate and add the directory tree
            val directoryTree = FileUtils.generateDirectoryTree(filePaths, settings.includeFilesInStructure)
            if (directoryTree.isNotEmpty()) {
                contentBuilder.append(directoryTree)
                contentBuilder.append("\n\n")
            }
        }

        // Add repository summary if enabled
        if (settings.includeRepositorySummary) {
            val repositorySummary = RepositorySummary(
                project = project,
                selectedFiles = selectedFiles,
                fileContents = fileContents,
                processedFileCount = processedFileCount.get(),
                excludedByFilterCount = excludedByFilterCount.get(),
                excludedBySizeCount = excludedBySizeCount.get(),
                excludedByBinaryContentCount = excludedByBinaryContentCount.get(),
                excludedByIgnoredNameCount = excludedByIgnoredNameCount.get(),
                excludedByGitignoreCount = excludedByGitignoreCount.get()
            )
            val summary = repositorySummary.generateSummary(settings.outputFormat)
            contentBuilder.append(summary)
        }

        // Format and add file contents based on the selected output format
        when (settings.outputFormat) {
            AppConstants.OutputFormat.PLAIN_TEXT -> {
                // Current default format - no changes needed
                contentBuilder.append(fileContents.joinToString("\n"))
            }
            AppConstants.OutputFormat.MARKDOWN -> {
                // Format as Markdown with code blocks
                for (content in fileContents) {
                    if (content.startsWith(AppConstants.FILENAME_PREFIX)) {
                        // Extract the path from the first line
                        val firstLine = content.substringBefore('\n')
                        val path = firstLine.substring(AppConstants.FILENAME_PREFIX.length).trim()

                        // Extract the file extension to use as language hint
                        val extension = path.substringAfterLast('.', "").lowercase()
                        // Use the markdown language hint mapping to get the proper language name
                        val languageHint = if (extension.isNotEmpty()) {
                            AppConstants.MARKDOWN_LANGUAGE_HINTS[extension] ?: "text"
                        } else {
                            "text"
                        }

                        // Add markdown heading for the file
                        contentBuilder.append("### $path\n\n")

                        // Add the content in a code block with language hint
                        val fileContent = content.substringAfter('\n')
                        contentBuilder.append("```$languageHint\n")
                        contentBuilder.append(fileContent)
                        contentBuilder.append("\n```\n\n")
                    } else {
                        // If there's no filename prefix, just add the content in a code block
                        contentBuilder.append("```\n")
                        contentBuilder.append(content)
                        contentBuilder.append("\n```\n\n")
                    }
                }
            }
            AppConstants.OutputFormat.XML -> {
                // Format as XML for machine consumption
                contentBuilder.append("<files>\n")
                for (content in fileContents) {
                    if (content.startsWith(AppConstants.FILENAME_PREFIX)) {
                        // Extract the path from the first line
                        val firstLine = content.substringBefore('\n')
                        val path = firstLine.substring(AppConstants.FILENAME_PREFIX.length).trim()

                        // Extract the file content (everything after the first line)
                        val fileContent = content.substringAfter('\n')

                        // Add XML tags for the file
                        contentBuilder.append("  <file path=\"${StringUtils.escapeXml(path)}\">\n")
                        contentBuilder.append("    <content><![CDATA[\n")
                        contentBuilder.append(fileContent)
                        contentBuilder.append("\n    ]]></content>\n")
                        contentBuilder.append("  </file>\n")
                    }
                }
                contentBuilder.append("</files>")
            }
        }

        val finalContent = contentBuilder.toString()
        return ExportResult(
            content = finalContent,
            processedFileCount = finalProcessedCount,
            excludedByFilterCount = excludedByFilterCount.get(),
            excludedBySizeCount = excludedBySizeCount.get(),
            excludedByBinaryContentCount = excludedByBinaryContentCount.get(),
            excludedByIgnoredNameCount = excludedByIgnoredNameCount.get(),
            excludedByGitignoreCount = excludedByGitignoreCount.get(),
            excludedExtensions = excludedExtensions.toSet(),
            limitReached = finalFileCount >= settings.fileCount
        )
    }

    /**
     * Processes a single entry (file or directory), performing all exclusion checks.
     * This function is recursive for directories.
     *
     * @param file The VirtualFile to process.
     * @param scope The CoroutineScope for cooperative cancellation.
     * @param localBuffer The local buffer to add file contents to.
     */
    private suspend fun processEntry(file: VirtualFile, scope: CoroutineScope, localBuffer: MutableList<String>) {
        scope.ensureActive() // Check cancellation at the start

        // --- Basic Checks ---
        if (fileCount.get() >= settings.fileCount) {
            logger.debug("File limit reached before processing entry: ${file.path}. Skipping.")
            return
        }
        if (!file.isValid || !file.exists()) {
            logger.warn("Skipping invalid or non-existent file entry: ${file.path}")
            return
        }

        // Check cancellation again after basic checks
        scope.ensureActive()

        // --- Calculate Relative Path (relative to project root for the single parser) ---
        val relativePath = try {
            FileUtils.getRelativePath(file, project) // Assumes this gives path relative to project root
        } catch (e: Exception) {
            logger.error("Error calculating relative path for: ${file.path}", e)
            null // Handle error case
        }

        if (relativePath == null) {
            logger.warn("Could not determine relative path for ${file.path}. Skipping gitignore check.")
            // Decide whether to proceed without gitignore check or skip entirely
            // Let's proceed for now, but log it clearly.
        }

        // Check cancellation again after calculating relative path
        scope.ensureActive()

        // --- Logging for Gitignore Debugging ---
        logger.info("Processing entry: '${file.name}' | Relative Path: '$relativePath' | isDirectory: ${file.isDirectory}")
        // --- End Logging ---


        // --- Exclusion Checks ---

        // 1. Explicit Ignored Names (Fastest check)
        if (file.name in settings.ignoredNames) {
            logger.info("Skipping ignored file/directory by name: ${file.path}")
            excludedByIgnoredNameCount.incrementAndGet()
            return
        }

        // Check cancellation again after ignored names check
        scope.ensureActive()

        // 2. Gitignore Check (using hierarchical parser)
        try {
            val isIgnoredByGit = hierarchicalGitignoreParser.isIgnored(file)
            if (isIgnoredByGit) {
                logger.info(">>> Gitignore Match: YES. Skipping '${file.path}' based on hierarchical .gitignore rules.")
                excludedByGitignoreCount.incrementAndGet()
                return // Skip this entry entirely
            } else {
                logger.info(">>> Gitignore Match: NO. Proceeding with '${file.path}'.")
            }
        } catch (e: Exception) {
            logger.warn(">>> Gitignore Check: ERROR checking status for '${file.path}'. File will be processed.", e)
        }

        // Check cancellation again after gitignore check
        scope.ensureActive()

        // --- Process Valid, Non-Ignored Entry ---
        if (file.isDirectory) {
            // Process directory contents recursively
            logger.debug("Processing directory contents: ${file.path}")
            processDirectoryChildren(file, scope, localBuffer)
        } else {
            // Process a single file (pass the already calculated relative path)
            logger.debug("Processing file: ${file.path}")
            processSingleFile(file, relativePath ?: file.name, scope, localBuffer) // Pass relative path or fallback
        }
    }

    /**
     * Iterates over directory children and calls processEntry for each.
     * 
     * @param directory The directory to process.
     * @param scope The coroutine scope.
     * @param localBuffer The local buffer to add file contents to.
     */
    /**
     * Iterates over directory children and calls processEntry for each.
     * 
     * @param directory The directory to process.
     * @param scope The coroutine scope.
     * @param localBuffer The local buffer to add file contents to.
     */
    private suspend fun processDirectoryChildren(directory: VirtualFile, scope: CoroutineScope, localBuffer: MutableList<String>) {
        try {
            val children = directory.children ?: return // No children or error reading them
            for (child in children) {
                scope.ensureActive() // Check cancellation before processing each child

                // Check limit again before launching recursive call
                if (fileCount.get() >= settings.fileCount) {
                    logger.debug("File limit reached within directory ${directory.path}. Stopping recursion.")
                    return // Stop processing this directory further
                }

                // Recursively process children - processEntry will handle all checks for the child
                processEntry(child, scope, localBuffer)

                // Check cancellation after processing each child
                scope.ensureActive()
            }
        } catch (ce: CancellationException) {
            logger.info("Directory processing cancelled: ${directory.path}")
            throw ce // Re-throw cancellation to propagate up
        } catch (e: Exception) {
            logger.error("Error processing directory children: ${directory.path}", e)
        }
    }

    /**
     * Processes a single file after basic ignore checks have passed.
     * Performs size, binary, filter checks, reads content, and adds to results.
     * @param file The file VirtualFile.
     * @param relativePath The pre-calculated relative path (or fallback).
     * @param scope The coroutine scope.
     * @param localBuffer The local buffer to add file contents to.
     */
    private suspend fun processSingleFile(file: VirtualFile, relativePath: String, scope: CoroutineScope, localBuffer: MutableList<String>) {
        // Note: .gitignore and ignoredNames checks are done in processEntry

        scope.ensureActive() // Check cancellation

        // Check file limit again before doing expensive checks/reads
        if (fileCount.get() >= settings.fileCount) {
            logger.warn("File limit reached just before processing file details: ${file.name}. Skipping.")
            return
        }

        // --- Further Exclusion Checks (Size, Binary, Filter) ---
        if (FileUtils.isKnownBinaryExtension(file)) {
            logger.info("Skipping known binary file type: $relativePath")
            excludedByBinaryContentCount.incrementAndGet()
            return
        }

        val maxSizeInBytes = settings.maxFileSizeKb * 1024L
        if (file.length > maxSizeInBytes) {
            logger.info("Skipping file due to size limit (> ${settings.maxFileSizeKb} KB): $relativePath")
            excludedBySizeCount.incrementAndGet()
            return
        }

        // Deeper binary check
        val isBinary = try {
            FileUtils.isLikelyBinaryContent(file)
        } catch (e: Exception) {
            logger.warn("Failed deep binary check for $relativePath, assuming binary.", e)
            true // Assume binary if check fails
        }
        if (isBinary) {
            logger.info("Skipping likely binary file (content check): $relativePath")
            excludedByBinaryContentCount.incrementAndGet()
            return
        }


        // Filter check
        if (settings.areFiltersEnabled && settings.filenameFilters.isNotEmpty()) {
            val matchesFilter = settings.filenameFilters.any { filter ->
                val actualFilter = if (filter.startsWith(".")) filter else ".$filter"
                file.name.endsWith(actualFilter, ignoreCase = true)
            }
            if (!matchesFilter) {
                logger.info("Skipping file due to filename filter: $relativePath")
                val fileExtension = file.extension ?: "no_extension"
                excludedExtensions.add(fileExtension)
                excludedByFilterCount.incrementAndGet()
                return
            }
        }

        // --- Read File Content ---
        scope.ensureActive() // Check cancellation before reading content
        var fileContent = try {
            FileUtils.readFileContent(file)
        } catch (e: Exception) {
            logger.error("Error reading file content for $relativePath", e)
            "// Error reading file: ${file.path} (${e.message})" // Indicate error in output
        }

        // Add line numbers if enabled
        if (settings.includeLineNumbers && !fileContent.startsWith("// Error reading file:")) {
            fileContent = addLineNumbers(fileContent)
        }


        if (fileContent.isEmpty() || fileContent.startsWith("// Error reading file:")) {
            logger.warn("File content is empty or unreadable, skipping file: $relativePath")
            return
        }

        // --- Add to Results (Using Local Buffer) ---
        var limitReachedAfterAdd = false

        // Double-check limit before adding to local buffer
        if (fileCount.get() < settings.fileCount) {
            // Combine filename prefix and content into a single entry to prevent interleaving
            val contentToAdd = if (settings.includePathPrefix) {
                // Check if the file content already starts with any filename prefix
                if (FileUtils.hasFilenamePrefix(fileContent)) {
                    fileContent
                } else {
                    // Get the appropriate comment prefix for this file type
                    val commentPrefix = FileUtils.getCommentPrefix(file)
                    // For HTML-style comments, insert the path before the closing tag
                    val formattedPrefix = if (commentPrefix.endsWith("-->")) {
                        commentPrefix.replace("-->", "$relativePath -->")
                    } else {
                        "$commentPrefix$relativePath"
                    }
                    "$formattedPrefix\n$fileContent"
                }
            } else {
                fileContent
            }

            // Add to local buffer - no synchronization needed
            localBuffer.add(contentToAdd)

            val currentFileCount = fileCount.incrementAndGet()
            val currentProcessedCount = processedFileCount.incrementAndGet()

            logger.info("Added file content ($currentProcessedCount/$currentFileCount): $relativePath")

            // Update progress indicator
            indicator.checkCanceled() // Use checkCanceled() instead of !isCanceled
            val targetCount = settings.fileCount.toDouble()
            if (targetCount > 0) {
                indicator.fraction = min(1.0, currentFileCount.toDouble() / targetCount)
            }
            indicator.text = "Processed $currentProcessedCount files..."

            // Check if limit is now reached *after* adding
            if (currentFileCount >= settings.fileCount) {
                logger.info("File count limit (${settings.fileCount}) reached after adding $relativePath.")
                limitReachedAfterAdd = true
            }
        } else {
            logger.warn("File limit reached just before adding file: $relativePath")
            limitReachedAfterAdd = true
        }

        // If limit was reached, cancel the coroutine scope
        if (limitReachedAfterAdd && scope.isActive) {
            logger.info("Requesting cancellation of processing scope due to file limit.")
            scope.cancel("File limit reached")
        }
    }
}
