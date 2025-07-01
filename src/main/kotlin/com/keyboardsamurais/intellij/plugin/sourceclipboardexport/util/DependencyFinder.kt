package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Utility class for finding dependencies and reverse dependencies between files.
 * This implementation uses a hybrid approach for optimal performance and accuracy:
 * 1. A fast, text-based search acts as a pre-filter to find "candidate" files.
 * 2. A precise, PSI-based `ReferencesSearch` is then run only on that small set of candidates.
 * This avoids the performance bottleneck of running PSI searches on the entire project.
 */
object DependencyFinder {
    private val LOG = Logger.getInstance(DependencyFinder::class.java)

    // Cache for the final set of dependent files
    private val dependentsCache = ConcurrentHashMap<String, Set<VirtualFile>>()

    /**
     * Finds all files that depend on (import/use) the given files using a hybrid text and PSI search.
     *
     * @param files The files to find dependents for.
     * @param project The current project.
     * @param alreadyIncludedFiles Optional set of files that are already going to be included in the export.
     * @param maxResults Optional maximum number of results to return (for early termination).
     * @return A set of files that are verified to depend on the input files.
     */
    suspend fun findDependents(
        files: Array<VirtualFile>, 
        project: Project,
        alreadyIncludedFiles: Set<VirtualFile> = emptySet(),
        maxResults: Int = DependencyFinderConfig.maxResultsPerSearch
    ): Set<VirtualFile> = withContext(Dispatchers.IO) {
        LOG.warn("Starting hybrid dependency search for ${files.size} files.")
        val startTime = System.currentTimeMillis()

        val cacheKey = files.map { it.path }.sorted().joinToString(";")
        if (DependencyFinderConfig.enableCaching && dependentsCache.containsKey(cacheKey)) {
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
        val concurrencyLimit = DependencyFinderConfig.maxConcurrentPsiSearches
        val semaphore = Semaphore(concurrencyLimit)

        coroutineScope {
            val jobs = files.mapNotNull { file ->
                if (file.isDirectory) return@mapNotNull null
                async {
                    semaphore.acquire()
                    try {
                        // Check for cancellation
                        ProgressManager.checkCanceled()
                        
                        // Early termination if enabled and we've found enough results
                        if (DependencyFinderConfig.enableEarlyTermination && resultsFound.get() >= maxResults) {
                            return@async
                        }
                        
                        // Get referenceable elements from the source file
                        val elementsToSearch = ReadAction.compute<List<com.intellij.psi.PsiElement>, Exception> {
                            val psiFile = psiManager.findFile(file)
                            if (psiFile != null) findReferenceableElementsOptimized(psiFile) else emptyList()
                        }

                        if (elementsToSearch.isEmpty()) return@async

                        // Process elements in batches for better performance
                        elementsToSearch.chunked(DependencyFinderConfig.elementBatchSize).forEach { batch ->
                            // Check cancellation between batches
                            ProgressManager.checkCanceled()
                            
                            if (DependencyFinderConfig.enableEarlyTermination && resultsFound.get() >= maxResults) return@forEach
                            
                            val references = ReadAction.compute<List<VirtualFile>, Exception> {
                                batch.flatMap { element ->
                                    ReferencesSearch.search(element, searchScope, false)
                                        .mapNotNull { it.element.containingFile?.virtualFile }
                                        .filter { it.isValid && it != file && it !in alreadyIncludedFiles }
                                        .let { results ->
                                            if (DependencyFinderConfig.enableEarlyTermination) {
                                                results.take(maxResults - resultsFound.get())
                                            } else {
                                                results
                                            }
                                        }
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

        if (DependencyFinderConfig.enableCaching) {
            dependentsCache[cacheKey] = result
        }

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
            { file -> !file.isDirectory || file.name !in DependencyFinderConfig.skipDirs },
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
                        if (fileToScan.length > DependencyFinderConfig.maxFileSizeBytes) return@async
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
        var elementCount = 0
        val maxElements = DependencyFinderConfig.maxElementsPerFile
        
        class DepthLimitedVisitor(private val maxDepth: Int) : com.intellij.psi.PsiRecursiveElementVisitor() {
            private var currentDepth = 0
            
            override fun visitElement(element: com.intellij.psi.PsiElement) {
                if (currentDepth >= maxDepth || elementCount >= maxElements) return
                
                if (element is PsiNameIdentifierOwner && element.name != null) {
                    elements.add(element)
                    elementCount++
                }
                
                // Only visit children of top-level or near-top-level elements
                if (currentDepth < maxDepth - 1) {
                    currentDepth++
                    super.visitElement(element)
                    currentDepth--
                }
            }
        }
        
        file.accept(DepthLimitedVisitor(DependencyFinderConfig.maxTraversalDepth))
        
        // Always add the file itself
        elements.add(file)
        return elements
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
        if (selectedFilesCount > 10 && DependencyFinderConfig.maxConcurrentPsiSearches > 2) {
            LOG.warn("WARNING: High concurrency (${DependencyFinderConfig.maxConcurrentPsiSearches}) with many files ($selectedFilesCount) may cause IDE freezing")
        }
        
        if (!DependencyFinderConfig.enableEarlyTermination && selectedFilesCount > 5) {
            LOG.warn("WARNING: Early termination disabled with multiple files may cause performance issues")
        }
        
        if (DependencyFinderConfig.maxElementsPerFile > 100) {
            LOG.warn("WARNING: High maxElementsPerFile (${DependencyFinderConfig.maxElementsPerFile}) may cause slow PSI parsing")
        }
    }
}