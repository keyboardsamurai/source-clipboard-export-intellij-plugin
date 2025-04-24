package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.config.SourceClipboardExportSettings
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.AppConstants
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.FileUtils
import kotlinx.coroutines.*
import java.util.Collections
import kotlin.math.min

class SourceExporter(
    private val project: Project,
    private val settings: SourceClipboardExportSettings.State,
    private val indicator: ProgressIndicator
) {
    private val logger = Logger.getInstance(SourceExporter::class.java)
    private val fileContents = Collections.synchronizedList(mutableListOf<String>())
    private var fileCount = 0
    private var processedFileCount = 0 // Files successfully read and added
    private var excludedByFilterCount = 0
    private var excludedBySizeCount = 0
    private var excludedByBinaryContentCount = 0
    private var excludedByIgnoredNameCount = 0
    private val excludedExtensions = Collections.synchronizedSet(mutableSetOf<String>())

    data class ExportResult(
        val content: String,
        val processedFileCount: Int,
        val excludedByFilterCount: Int,
        val excludedBySizeCount: Int,
        val excludedByBinaryContentCount: Int,
        val excludedByIgnoredNameCount: Int,
        val excludedExtensions: Set<String>,
        val limitReached: Boolean
    )

    suspend fun exportSources(selectedFiles: Array<VirtualFile>): ExportResult {
        logger.info("Starting source export process.")
        logger.info("Settings: Max Files=${settings.fileCount}, Max Size KB=${settings.maxFileSizeKb}, Filters Enabled=${settings.areFiltersEnabled}, Filters=${settings.filenameFilters.joinToString()}, Ignored=${settings.ignoredNames.joinToString()}, Include Prefix=${settings.includePathPrefix}")

        indicator.isIndeterminate = false
        indicator.text = "Scanning files..."

        coroutineScope { // Create a scope for processing
            val jobs = selectedFiles.map { file ->
                launch(Dispatchers.IO) { // Launch processing in IO context
                    ensureActive() // Check for cancellation before starting
                    processEntry(file, this)
                }
            }
            try {
                jobs.joinAll() // Wait for all top-level jobs to complete or be cancelled
            } catch (ce: CancellationException) {
                logger.info("Processing cancelled, likely due to file limit or user action.")
            } catch (e: Exception) {
                logger.error("Error waiting for processing jobs", e)
            }
        } // Scope ends, cancellation propagates

        logger.info("Export process finished. Processed: $processedFileCount, Excluded (Filter: $excludedByFilterCount, Size: $excludedBySizeCount, Binary: $excludedByBinaryContentCount, Ignored: $excludedByIgnoredNameCount)")

        val finalContent = fileContents.joinToString("\n")
        return ExportResult(
            content = finalContent,
            processedFileCount = processedFileCount,
            excludedByFilterCount = excludedByFilterCount,
            excludedBySizeCount = excludedBySizeCount,
            excludedByBinaryContentCount = excludedByBinaryContentCount,
            excludedByIgnoredNameCount = excludedByIgnoredNameCount,
            excludedExtensions = excludedExtensions.toSet(), // Return immutable set
            limitReached = fileCount >= settings.fileCount
        )
    }

    private suspend fun processEntry(file: VirtualFile, scope: CoroutineScope) {
        scope.ensureActive() // Check cancellation at the start of processing each entry

        if (file.name in settings.ignoredNames) {
            logger.info("Skipping ignored file/directory: ${file.name}")
            // We don't count this towards the main excluded counts, but could add another counter if needed
            excludedByIgnoredNameCount++ // Count ignored names
            return
        }

        if (file.isDirectory) {
            logger.debug("Processing directory: ${file.path}")
            processDirectory(file, scope)
        } else {
            logger.debug("Processing file: ${file.path}")
            processSingleFile(file, scope)
        }
    }

    private suspend fun processDirectory(directory: VirtualFile, scope: CoroutineScope) {
        try {
            // Use VfsUtil.collectChildrenRecursively for potentially better performance?
            // Or stick with simple iteration for clarity and cancellation checks.
            for (child in directory.children) {
                scope.ensureActive() // Check cancellation before processing each child
                if (fileCount >= settings.fileCount) {
                    logger.debug("File limit reached within directory ${directory.path}. Stopping recursion.")
                    if (scope.isActive) scope.cancel() // Cancel the scope if limit reached
                    return // Stop processing this directory further
                }
                processEntry(child, scope) // Recursively process children
            }
        } catch (ce: CancellationException) {
            logger.info("Directory processing cancelled: ${directory.path}")
            throw ce // Re-throw cancellation to propagate up
        } catch (e: Exception) {
            logger.error("Error processing directory children: ${directory.path}", e)
            // Decide if you want to continue with other directories or stop
        }
    }

    private suspend fun processSingleFile(file: VirtualFile, scope: CoroutineScope) {
        scope.ensureActive() // Check cancellation before processing file details

        // Check file limit *before* doing expensive checks/reads
        if (fileCount >= settings.fileCount) {
            logger.warn("File limit reached before processing file: ${file.name}. Skipping.")
             if (scope.isActive) scope.cancel() // Ensure scope is cancelled
            return
        }

        val relativePath = FileUtils.getRelativePath(file, project)

        // --- Exclusion Checks ---
        if (FileUtils.isKnownBinaryExtension(file)) {
            logger.info("Skipping known binary file type: $relativePath")
            excludedByBinaryContentCount++
            return
        }

        val maxSizeInBytes = settings.maxFileSizeKb * 1024L
        if (file.length > maxSizeInBytes) {
            logger.info("Skipping file due to size limit (> ${settings.maxFileSizeKb} KB): $relativePath")
            excludedBySizeCount++
            return
        }

        // Deeper binary check (can be slow, do after size check)
        if (FileUtils.isLikelyBinaryContent(file)) {
            logger.info("Skipping likely binary file (content check): $relativePath")
            excludedByBinaryContentCount++
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
                excludedByFilterCount++
                return
            }
        }

        // --- Read File Content ---
        scope.ensureActive() // Check cancellation before reading content
        val fileContent = FileUtils.readFileContent(file)

        if (fileContent.isEmpty() || fileContent.startsWith("// Error reading file:")) {
            logger.warn("File content is empty or unreadable, skipping file: $relativePath")
            // Optionally count this as a specific type of exclusion
            return
        }

        // --- Add to Results (Synchronized) ---
        synchronized(fileContents) {
            // Double-check limit within synchronized block
            if (fileCount < settings.fileCount) {
                if (settings.includePathPrefix) {
                    fileContents.add("${AppConstants.FILENAME_PREFIX}$relativePath")
                }
                fileContents.add(fileContent)
                fileCount++ // Increment the counter *after* successfully adding
                processedFileCount++
                logger.info("Added file content ($processedFileCount/$fileCount): $relativePath")

                // Update progress indicator
                val targetCount = settings.fileCount.toDouble()
                if (targetCount > 0) {
                    indicator.fraction = min(1.0, fileCount.toDouble() / targetCount)
                }
                indicator.text = "Processed $processedFileCount files..."

                // Check if limit is now reached *after* adding
                if (fileCount >= settings.fileCount) {
                    logger.info("File count limit (${settings.fileCount}) reached after adding $relativePath.")
                    if (scope.isActive) scope.cancel() // Request cancellation of the coroutine scope
                }
            } else {
                 // This case should ideally not happen due to earlier checks, but is a safeguard.
                logger.warn("File limit reached just before adding file in synchronized block: $relativePath")
                 if (scope.isActive) scope.cancel()
            }
        }
    }
} 