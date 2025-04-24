package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.config.SourceClipboardExportSettings
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core.gitignore.GitignoreParser
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.AppConstants
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.FileUtils
import kotlinx.coroutines.*
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min

class SourceExporter(
    private val project: Project,
    private val settings: SourceClipboardExportSettings.State,
    private val indicator: ProgressIndicator
) {
    private val logger = Logger.getInstance(SourceExporter::class.java)
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

    // Store the single parser instance for the project root .gitignore
    private var rootGitignoreParser: GitignoreParser? = null
    private var gitignoreLoadAttempted = false

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

    // Initialize parser in the constructor or an init block
    init {
        loadRootGitignoreParser()
    }

    private fun loadRootGitignoreParser() {
        gitignoreLoadAttempted = true
        val projectBasePath = project.basePath ?: run {
            logger.warn("Project base path is null. Cannot locate .gitignore.")
            return
        }
        // Look for .gitignore directly in the project root using VfsUtil
        val gitignoreVirtualFile = VfsUtil.findRelativeFile(projectBasePath + "/.gitignore", null)

        if (gitignoreVirtualFile != null && gitignoreVirtualFile.exists() && !gitignoreVirtualFile.isDirectory) {
            try {
                // Read content using VirtualFile API directly for simplicity here
                val content = VfsUtil.loadText(gitignoreVirtualFile)
                rootGitignoreParser = GitignoreParser(gitignoreVirtualFile) // Pass the VirtualFile
                logger.info("Successfully loaded and parsed project root .gitignore: ${gitignoreVirtualFile.path}")
            } catch (e: Exception) {
                logger.warn("Failed to read or parse project root .gitignore: ${gitignoreVirtualFile.path}. Gitignore checks disabled.", e)
                rootGitignoreParser = null
            }
        } else {
            logger.info("Project root .gitignore not found or is invalid ($projectBasePath/.gitignore). Gitignore checks disabled.")
            rootGitignoreParser = null
        }
    }


    suspend fun exportSources(selectedFiles: Array<VirtualFile>): ExportResult {
        logger.info("Starting source export process.")
        logger.info("Settings: Max Files=${settings.fileCount}, Max Size KB=${settings.maxFileSizeKb}, Filters Enabled=${settings.areFiltersEnabled}, Filters=${settings.filenameFilters.joinToString()}, Ignored=${settings.ignoredNames.joinToString()}, Include Prefix=${settings.includePathPrefix}")

        // No need to clear caches if we are not using the hierarchical parser's cache
        // GitignoreParser.clearCaches() // Remove this

        indicator.isIndeterminate = false
        indicator.text = "Scanning files..."

        coroutineScope {
            val scopeJob = SupervisorJob()
            selectedFiles.forEach { file ->
                launch(scopeJob + Dispatchers.IO) {
                    ensureActive()
                    processEntry(file, this)
                }
            }
            scopeJob.children.forEach { it.join() }
            scopeJob.complete()
            scopeJob.join()
        }

        val finalProcessedCount = processedFileCount.get()
        val finalFileCount = fileCount.get()
        logger.info("Export process finished. Processed: $finalProcessedCount, Total Considered: $finalFileCount, Excluded (Filter: ${excludedByFilterCount.get()}, Size: ${excludedBySizeCount.get()}, Binary: ${excludedByBinaryContentCount.get()}, IgnoredName: ${excludedByIgnoredNameCount.get()}, Gitignore: ${excludedByGitignoreCount.get()})")

        val finalContent = fileContents.joinToString("\n")
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
     */
    private suspend fun processEntry(file: VirtualFile, scope: CoroutineScope) {
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

        // --- Logging for Gitignore Debugging ---
        logger.info("Processing entry: '${file.name}' | Relative Path: '$relativePath' | isDirectory: ${file.isDirectory}")
        if (!gitignoreLoadAttempted) {
            logger.warn("Root gitignore parser initialization was not attempted!")
        } else if (rootGitignoreParser == null) {
            logger.warn("Root gitignore parser is NULL. Cannot perform check for '$relativePath'.")
        }
        // --- End Logging ---


        // --- Exclusion Checks ---

        // 1. Explicit Ignored Names (Fastest check)
        if (file.name in settings.ignoredNames) {
            logger.info("Skipping ignored file/directory by name: ${file.path}")
            excludedByIgnoredNameCount.incrementAndGet()
            return
        }

        // 2. Gitignore Check (using the single root parser)
        var isIgnoredByGit = false
        if (relativePath != null && rootGitignoreParser != null) {
            try {
                // Use the single parser instance. It expects path relative to its own location (project root).
                isIgnoredByGit = rootGitignoreParser!!.matches(relativePath, file.isDirectory)
                if (isIgnoredByGit) {
                    logger.info(">>> Gitignore Match: YES. Skipping '$relativePath' based on root .gitignore rules.")
                    excludedByGitignoreCount.incrementAndGet()
                    return // Skip this entry entirely
                } else {
                    logger.info(">>> Gitignore Match: NO. Proceeding with '$relativePath'.")
                }
            } catch (e: Exception) {
                logger.warn(">>> Gitignore Check: ERROR checking status for '$relativePath'. File will be processed.", e)
            }
        } else if (relativePath != null) {
            // Log only if parser is null but path is valid
            // logger.info(">>> Gitignore Check: SKIPPED (Parser not loaded). Proceeding with '$relativePath'.")
        }


        // --- Process Valid, Non-Ignored Entry ---
        if (file.isDirectory) {
            // Process directory contents recursively
            logger.debug("Processing directory contents: ${file.path}")
            processDirectoryChildren(file, scope)
        } else {
            // Process a single file (pass the already calculated relative path)
            logger.debug("Processing file: ${file.path}")
            processSingleFile(file, relativePath ?: file.name, scope) // Pass relative path or fallback
        }
    }

    /**
     * Iterates over directory children and calls processEntry for each.
     */
    private suspend fun processDirectoryChildren(directory: VirtualFile, scope: CoroutineScope) {
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
                processEntry(child, scope)
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
     */
    private suspend fun processSingleFile(file: VirtualFile, relativePath: String, scope: CoroutineScope) {
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
        val fileContent = try {
            FileUtils.readFileContent(file)
        } catch (e: Exception) {
            logger.error("Error reading file content for $relativePath", e)
            "// Error reading file: ${file.path} (${e.message})" // Indicate error in output
        }


        if (fileContent.isEmpty() || fileContent.startsWith("// Error reading file:")) {
            logger.warn("File content is empty or unreadable, skipping file: $relativePath")
            return
        }

        // --- Add to Results (Synchronized) ---
        var limitReachedAfterAdd = false
        synchronized(fileContents) {
            // Double-check limit within synchronized block
            if (fileCount.get() < settings.fileCount) {
                if (settings.includePathPrefix) {
                    // Use the passed relativePath
                    fileContents.add("${AppConstants.FILENAME_PREFIX}$relativePath")
                }
                fileContents.add(fileContent)

                val currentFileCount = fileCount.incrementAndGet()
                val currentProcessedCount = processedFileCount.incrementAndGet()

                logger.info("Added file content ($currentProcessedCount/$currentFileCount): $relativePath")

                // Update progress indicator
                if (!indicator.isCanceled) {
                    val targetCount = settings.fileCount.toDouble()
                    if (targetCount > 0) {
                        indicator.fraction = min(1.0, currentFileCount.toDouble() / targetCount)
                    }
                    indicator.text = "Processed $currentProcessedCount files..."
                }

                // Check if limit is now reached *after* adding
                if (currentFileCount >= settings.fileCount) {
                    logger.info("File count limit (${settings.fileCount}) reached after adding $relativePath.")
                    limitReachedAfterAdd = true
                }
            } else {
                logger.warn("File limit reached just before adding file in synchronized block: $relativePath")
                limitReachedAfterAdd = true
            }
        } // End synchronized block

        // If limit was reached, cancel the coroutine scope
        if (limitReachedAfterAdd && scope.isActive) {
            logger.info("Requesting cancellation of processing scope due to file limit.")
            scope.cancel("File limit reached")
        }
    }
}