package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicInteger

/**
 * Utility for finding dependencies (imports/references) and dependents (reverse dependencies).
 * 
 * This uses a hybrid approach:
 * 1. Fast text-based pre-filtering to find candidate files
 * 2. Accurate PSI-based search on the filtered candidate set
 * 
 * This is much faster than doing PSI search on all project files.
 */
object DependencyFinder {

    private val LOG = Logger.getInstance(DependencyFinder::class.java)
    private val dependentsCache = ConcurrentHashMap<String, Set<VirtualFile>>()
    
    // Configuration constants
    private const val MAX_RESULTS_PER_SEARCH = 100
    private const val MAX_CONCURRENT_PSI_SEARCHES = 2
    private const val ELEMENT_BATCH_SIZE = 20
    private const val MAX_FILE_SIZE_BYTES = 500_000
    private const val MAX_ELEMENTS_PER_FILE = 50
    private const val MAX_TRAVERSAL_DEPTH = 3
    private val SKIP_DIRS = setOf("node_modules", ".git", "build", "dist", "target", "out", ".idea")

    /**
     * Finds files that depend on the given source files.
     * Uses hybrid search for better performance.
     */
    suspend fun findDependents(
        files: Array<VirtualFile>,
        project: Project,
        alreadyIncludedFiles: Set<VirtualFile> = emptySet(),
        maxResults: Int = MAX_RESULTS_PER_SEARCH
    ): Set<VirtualFile> = withContext(Dispatchers.IO) {
        LOG.warn("Starting hybrid dependency search for ${files.size} files.")
        val startTime = System.currentTimeMillis()

        val cacheKey = files.map { it.path }.sorted().joinToString(";")
        if (dependentsCache.containsKey(cacheKey)) {
            LOG.warn("Returning cached dependents for selection in ${System.currentTimeMillis() - startTime}ms.")
            return@withContext dependentsCache[cacheKey]!!
        }

        // --- Phase 1: Fast Text Search to find Candidate Files ---
        val candidateFiles = findCandidateFilesByText(files, project, alreadyIncludedFiles)
        if (candidateFiles.isEmpty()) {
            LOG.warn("Phase 1 (Text Search) found no candidate files.")
            return@withContext emptySet()
        }
        
        // Filter out files that are already included to avoid unnecessary PSI parsing
        val candidatesToProcess = candidateFiles - alreadyIncludedFiles
        if (candidatesToProcess.isEmpty()) {
            LOG.warn("All candidate files are already included in export.")
            return@withContext emptySet()
        }
        
        LOG.info("Phase 1 (Text Search) found ${candidateFiles.size} candidates, ${candidatesToProcess.size} to process in ${System.currentTimeMillis() - startTime}ms.")

        // --- Phase 2: Accurate PSI Search on the Candidate Set ---
        val finalDependents = ConcurrentHashMap<VirtualFile, Boolean>()
        val resultsFound = AtomicInteger(0)
        val psiManager = PsiManager.getInstance(project)

        // Create a specific, narrow search scope from the candidate files to process
        val searchScope = ReadAction.compute<GlobalSearchScope, Exception> {
            GlobalSearchScope.filesScope(project, candidatesToProcess)
        }
        
        // Limit concurrent PSI operations to prevent IDE freeze
        val concurrencyLimit = MAX_CONCURRENT_PSI_SEARCHES
        val semaphore = Semaphore(concurrencyLimit)

        coroutineScope {
            val jobs = files.mapNotNull { file ->
                if (file.isDirectory) return@mapNotNull null
                async {
                    semaphore.acquire()
                    try {
                        // Check for cancellation
                        ProgressManager.checkCanceled()
                        
                        // Early termination if we've found enough results
                        if (resultsFound.get() >= maxResults) {
                            return@async
                        }
                        
                        // Get referenceable elements from the source file
                        val elementsToSearch = ReadAction.compute<List<com.intellij.psi.PsiElement>, Exception> {
                            val psiFile = psiManager.findFile(file)
                            if (psiFile != null) findReferenceableElementsOptimized(psiFile) else emptyList()
                        }

                        if (elementsToSearch.isEmpty()) return@async

                        // Process elements in batches for better performance
                        elementsToSearch.chunked(ELEMENT_BATCH_SIZE).forEach { batch ->
                            // Check cancellation between batches
                            ProgressManager.checkCanceled()
                            
                            if (resultsFound.get() >= maxResults) return@forEach
                            
                            val references = ReadAction.compute<List<VirtualFile>, Exception> {
                                batch.flatMap { element ->
                                    ReferencesSearch.search(element, searchScope, false)
                                        .mapNotNull { it.element.containingFile?.virtualFile }
                                        .filter { it.isValid && it != file && it !in alreadyIncludedFiles }
                                        .take(maxResults - resultsFound.get())
                                }
                            }
                            
                            references.forEach { 
                                if (finalDependents.putIfAbsent(it, true) == null) {
                                    resultsFound.incrementAndGet()
                                }
                            }
                        }
                    } catch (e: ProcessCanceledException) {
                        throw e // Re-throw to allow proper cancellation
                    } catch (e: CancellationException) {
                        throw e // Re-throw to allow proper cancellation
                    } catch (e: Exception) {
                        LOG.warn("Error during PSI search phase for file ${file.name}", e)
                    } finally {
                        semaphore.release()
                    }
                }
            }
            jobs.awaitAll()
        }

        val result = finalDependents.keys.toSet()
        val totalTime = System.currentTimeMillis() - startTime
        LOG.warn("Phase 2 (PSI Search) completed. Found ${result.size} verified dependent files. Total time: ${totalTime}ms.")

        dependentsCache[cacheKey] = result

        return@withContext result
    }

    /**
     * Phase 1: Scans the project using a fast text-based regex to find any file that *might*
     * contain a reference to the selected source files.
     */
    private suspend fun findCandidateFilesByText(
        sourceFiles: Array<VirtualFile>, 
        project: Project,
        alreadyIncludedFiles: Set<VirtualFile> = emptySet()
    ): Set<VirtualFile> {
        val projectRoot = ProjectRootManager.getInstance(project).contentRoots.firstOrNull() ?: return emptySet()
        val candidates = ConcurrentHashMap.newKeySet<VirtualFile>()

        val searchTerms = ReadAction.compute<Set<String>, Exception> {
            sourceFiles.filter { !it.isDirectory }.mapTo(mutableSetOf()) { it.nameWithoutExtension }
        }
        if (searchTerms.isEmpty()) return emptySet()

        val combinedPattern = try {
            val termsPattern = searchTerms.joinToString("|") { Regex.escape(it) }
            Regex("""\b($termsPattern)\b""") // Simple word boundary search is sufficient for a pre-filter.
        } catch (e: Exception) {
            LOG.error("Failed to create regex for candidate search", e)
            return emptySet()
        }

        val filesToScan = mutableListOf<VirtualFile>()
        val inputFilesSet = sourceFiles.toSet()
        VfsUtil.iterateChildrenRecursively(projectRoot,
            { file -> !file.isDirectory || file.name !in SKIP_DIRS },
            { file ->
                if (!file.isDirectory && file.extension in setOf("js", "jsx", "ts", "tsx", "kt", "java", "py", "vue", "svelte", "xml", "yml", "yaml")) {
                    // Skip files that are already in the input set or already included
                    if (file !in inputFilesSet && file !in alreadyIncludedFiles) {
                        filesToScan.add(file)
                    }
                }
                true
            })

        coroutineScope {
            val jobs = filesToScan.map { fileToScan ->
                async(Dispatchers.IO) {
                    try {
                        if (fileToScan.length > MAX_FILE_SIZE_BYTES) return@async
                        val content = VfsUtil.loadText(fileToScan)
                        if (combinedPattern.containsMatchIn(content)) {
                            candidates.add(fileToScan)
                        }
                    } catch (e: Exception) {
                        // Ignore errors reading individual files
                    }
                }
            }
            jobs.awaitAll()
        }
        return candidates
    }

    /**
     * Extracts externally-visible, referenceable elements from a PSI file.
     * This is needed for the accurate search in Phase 2.
     */
    private fun findReferenceableElements(file: PsiFile): List<com.intellij.psi.PsiElement> {
        val elements = mutableListOf<com.intellij.psi.PsiElement>()
        file.accept(object : com.intellij.psi.PsiRecursiveElementVisitor() {
            override fun visitElement(element: com.intellij.psi.PsiElement) {
                super.visitElement(element)
                if (element is PsiNameIdentifierOwner) {
                    // A simple heuristic: if it has a name, it's potentially referenceable.
                    // The PSI search will handle visibility (public, private, etc.) correctly.
                    elements.add(element)
                }
            }
        })
        // Also add the file itself, as it can be a target for imports.
        elements.add(file)
        return elements
    }
    
    /**
     * Optimized version that limits traversal depth and element count
     */
    private fun findReferenceableElementsOptimized(file: PsiFile): List<com.intellij.psi.PsiElement> {
        val elements = mutableListOf<com.intellij.psi.PsiElement>()
        val maxElements = MAX_ELEMENTS_PER_FILE
        
        // First, try to get the top-level elements
        file.children.filterIsInstance<PsiNameIdentifierOwner>().take(maxElements).forEach {
            elements.add(it)
        }
        
        // If we haven't reached the limit, do a limited recursive search
        if (elements.size < maxElements) {
            file.accept(DepthLimitedVisitor(MAX_TRAVERSAL_DEPTH, elements, maxElements))
        }
        
        // Always add the file itself for import references
        elements.add(file)
        
        return elements.take(maxElements)
    }

    /**
     * PSI visitor that limits traversal depth and element count
     */
    private class DepthLimitedVisitor(
        private val maxDepth: Int,
        private val elements: MutableList<com.intellij.psi.PsiElement>,
        private val maxElements: Int
    ) : com.intellij.psi.PsiRecursiveElementVisitor() {
        private var currentDepth = 0
        
        override fun visitElement(element: com.intellij.psi.PsiElement) {
            if (currentDepth >= maxDepth || elements.size >= maxElements) return
            
            if (element is PsiNameIdentifierOwner && element.name != null) {
                elements.add(element)
            }
            
            // Only visit children of top-level or near-top-level elements
            if (currentDepth < maxDepth - 1 && elements.size < maxElements) {
                currentDepth++
                super.visitElement(element)
                currentDepth--
            }
        }
    }

    /**
     * Clear all caches. Useful when project structure changes.
     */
    fun clearCaches() {
        dependentsCache.clear()
        LOG.info("DependencyFinder caches cleared")
    }

    /**
     * Get cache statistics for monitoring.
     */
    fun getCacheStats(): String {
        return "Dependents cache: ${dependentsCache.size} entries"
    }
    
    /**
     * Warn if configuration might cause performance issues
     */
    fun validateConfiguration(project: Project, selectedFilesCount: Int) {
        if (selectedFilesCount > 10 && MAX_CONCURRENT_PSI_SEARCHES > 2) {
            LOG.warn("WARNING: High concurrency ($MAX_CONCURRENT_PSI_SEARCHES) with many files ($selectedFilesCount) may cause IDE freezing")
        }
        
        if (selectedFilesCount > 5) {
            LOG.warn("WARNING: Processing $selectedFilesCount files may be slow")
        }
        
        if (MAX_ELEMENTS_PER_FILE > 100) {
            LOG.warn("WARNING: High maxElementsPerFile ($MAX_ELEMENTS_PER_FILE) may cause slow PSI parsing")
        }
    }
}