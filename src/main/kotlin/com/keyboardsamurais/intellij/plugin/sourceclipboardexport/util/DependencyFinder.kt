package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

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
     * @return A set of files that are verified to depend on the input files.
     */
    suspend fun findDependents(files: Array<VirtualFile>, project: Project): Set<VirtualFile> = withContext(Dispatchers.IO) {
        LOG.warn("Starting hybrid dependency search for ${files.size} files.")
        val startTime = System.currentTimeMillis()

        val cacheKey = files.map { it.path }.sorted().joinToString(";")
        if (DependencyFinderConfig.enableCaching && dependentsCache.containsKey(cacheKey)) {
            LOG.warn("Returning cached dependents for selection in ${System.currentTimeMillis() - startTime}ms.")
            return@withContext dependentsCache[cacheKey]!!
        }

        // --- Phase 1: Fast Text Search to find Candidate Files ---
        val candidateFiles = findCandidateFilesByText(files, project)
        if (candidateFiles.isEmpty()) {
            LOG.warn("Phase 1 (Text Search) found no candidate files.")
            return@withContext emptySet()
        }
        LOG.info("Phase 1 (Text Search) found ${candidateFiles.size} candidate files in ${System.currentTimeMillis() - startTime}ms.")

        // --- Phase 2: Accurate PSI Search on the Candidate Set ---
        val finalDependents = ConcurrentHashMap<VirtualFile, Boolean>()
        val psiManager = PsiManager.getInstance(project)

        // Create a specific, narrow search scope from the candidate files. This is the key optimization.
        val searchScope = ReadAction.compute<GlobalSearchScope, Exception> {
            GlobalSearchScope.filesScope(project, candidateFiles)
        }

        coroutineScope {
            val jobs = files.mapNotNull { file ->
                if (file.isDirectory) return@mapNotNull null
                async {
                    try {
                        // Get referenceable elements from the source file. This is slow but done only on the small input set.
                        val elementsToSearch = ReadAction.compute<List<com.intellij.psi.PsiElement>, Exception> {
                            val psiFile = psiManager.findFile(file)
                            if (psiFile != null) findReferenceableElements(psiFile) else emptyList()
                        }

                        if (elementsToSearch.isEmpty()) return@async

                        // Now run the expensive search, but only on the tiny `searchScope`.
                        for (element in elementsToSearch) {
                            val references = ReadAction.compute<List<VirtualFile>, Exception> {
                                ReferencesSearch.search(element, searchScope, false)
                                    .mapNotNull { it.element.containingFile?.virtualFile }
                                    .filter { it.isValid && it != file }
                            }
                            references.forEach { finalDependents[it] = true }
                        }
                    } catch (e: Exception) {
                        LOG.warn("Error during PSI search phase for file ${file.name}", e)
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
    private suspend fun findCandidateFilesByText(sourceFiles: Array<VirtualFile>, project: Project): Set<VirtualFile> {
        val projectRoot = project.baseDir ?: return emptySet()
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
                    if (file !in inputFilesSet) filesToScan.add(file)
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
}