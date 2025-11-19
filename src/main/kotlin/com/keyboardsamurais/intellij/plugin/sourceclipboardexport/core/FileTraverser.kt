package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core.filter.ExclusionReason
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core.filter.ExportFilter
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core.filter.FileProperties
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.FileUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/** Handles recursive file traversal and filtering. */
class FileTraverser(
        private val project: Project,
        private val stats: ExportStatistics,
        private val traversalFilters: List<ExportFilter>,
        private val inclusionFilters: List<ExportFilter>,
        private val fileCountLimit: Int
) {
    private val logger = Logger.getInstance(FileTraverser::class.java)

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun traverse(
            roots: Array<VirtualFile>,
            onFileFound: suspend (VirtualFile, FileProperties, String) -> Unit
    ) {
        val visitedFiles = ConcurrentHashMap.newKeySet<VirtualFile>()

        coroutineScope {
            val scopeJob = SupervisorJob()
            // Limit parallelism to avoid spawning too many coroutines
            val limitedDispatcher =
                    Dispatchers.IO.limitedParallelism(Runtime.getRuntime().availableProcessors())

            roots.forEach { file ->
                launch(scopeJob + limitedDispatcher) {
                    ensureActive()
                    processEntry(file, this, scopeJob, visitedFiles, onFileFound)
                }
            }
            scopeJob.children.forEach { it.join() }
            scopeJob.complete()
            scopeJob.join()
        }
    }

    private suspend fun processEntry(
            file: VirtualFile,
            scope: CoroutineScope,
            parentJob: Job,
            visitedFiles: MutableSet<VirtualFile>,
            onFileFound: suspend (VirtualFile, FileProperties, String) -> Unit
    ) {
        scope.ensureActive()

        if (!visitedFiles.add(file)) {
            return
        }

        val fileProps = readFileProperties(file)

        if (!fileProps.isValid || !fileProps.exists) {
            return
        }

        if (fileProps.isDirectory) {
            if (hasReachedFileLimit()) {
                if (parentJob.isActive) parentJob.cancel("File limit reached")
                return
            }

            // Apply traversal filters (e.g. Ignored Names, Gitignore)
            for (filter in traversalFilters) {
                val reason = filter.shouldExclude(file, fileProps, null)
                if (reason != null) {
                    recordExclusion(reason)
                    return
                }
            }

            logger.debug("Processing directory: ${fileProps.path}")
            val children = ReadAction.compute<Array<VirtualFile>?, Exception> { file.children }
            children?.sortedBy { it.path }?.forEach { child ->
                if (hasReachedFileLimit()) {
                    if (parentJob.isActive) parentJob.cancel("File limit reached")
                    return@forEach
                }
                scope.ensureActive()
                processEntry(child, scope, parentJob, visitedFiles, onFileFound)
            }
            return
        }

        // It's a file
        if (hasReachedFileLimit()) {
            return
        }

        processFile(file, fileProps, scope, onFileFound)
    }

    private suspend fun processFile(
            file: VirtualFile,
            fileProps: FileProperties,
            scope: CoroutineScope,
            onFileFound: suspend (VirtualFile, FileProperties, String) -> Unit
    ) {
        // Calculate relative path
        val relativePath =
                try {
                    ReadAction.compute<String?, Exception> {
                        FileUtils.getRelativePath(file, project)
                    }
                } catch (e: Exception) {
                    logger.error("Error calculating relative path for: ${fileProps.path}", e)
                    null
                }

        if (relativePath == null) {
            logger.warn("Could not determine relative path for ${fileProps.path}")
        }

        scope.ensureActive()

        // Apply traversal filters to file (e.g. Ignored Names, Gitignore)
        // Note: Some traversal filters might apply to files too
        for (filter in traversalFilters) {
            val reason = filter.shouldExclude(file, fileProps, relativePath)
            if (reason != null) {
                recordExclusion(reason)
                return
            }
        }

        // Apply inclusion filters (Size, Binary, Filename)
        for (filter in inclusionFilters) {
            val reason = filter.shouldExclude(file, fileProps, relativePath)
            if (reason != null) {
                recordExclusion(reason, fileProps.extension)
                return
            }
        }

        scope.ensureActive()
        onFileFound(file, fileProps, relativePath ?: fileProps.name)
    }

    private fun readFileProperties(file: VirtualFile): FileProperties {
        return ReadAction.compute<FileProperties, Exception> {
            FileProperties(
                    name = file.name,
                    path = file.path,
                    isDirectory = file.isDirectory,
                    isValid = file.isValid,
                    exists = file.exists(),
                    length = file.length,
                    extension = file.extension
            )
        }
    }

    private fun hasReachedFileLimit(): Boolean = stats.fileCount.get() >= fileCountLimit

    private fun recordExclusion(reason: ExclusionReason, extension: String? = null) {
        when (reason) {
            ExclusionReason.FILENAME_FILTER -> {
                stats.excludedByFilterCount.incrementAndGet()
                val recordedExtension = extension ?: "no_extension"
                stats.excludedExtensions.add(recordedExtension)
            }
            ExclusionReason.SIZE_LIMIT -> stats.excludedBySizeCount.incrementAndGet()
            ExclusionReason.BINARY_CONTENT -> stats.excludedByBinaryContentCount.incrementAndGet()
            ExclusionReason.IGNORED_NAME -> stats.excludedByIgnoredNameCount.incrementAndGet()
            ExclusionReason.GITIGNORE -> stats.excludedByGitignoreCount.incrementAndGet()
        }
    }
}
