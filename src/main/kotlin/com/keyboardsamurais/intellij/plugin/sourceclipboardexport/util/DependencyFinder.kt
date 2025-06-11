package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Utility class for finding dependencies and reverse dependencies between files
 */
object DependencyFinder {
    private val LOG = Logger.getInstance(DependencyFinder::class.java)
    
    // Cache for referenceable elements to avoid re-parsing files
    private val referenceableElementsCache = ConcurrentHashMap<String, List<com.intellij.psi.PsiElement>>()
    
    // Cache for dependent files to avoid redundant searches
    private val dependentsCache = ConcurrentHashMap<String, Set<VirtualFile>>()
    
    /**
     * Find all files that depend on (import/use) the given files
     * @param files The files to find dependents for
     * @param project The current project
     * @return Set of files that depend on the input files
     */
    suspend fun findDependents(files: Array<VirtualFile>, project: Project): Set<VirtualFile> = withContext(Dispatchers.Default) {
        LOG.warn("DependencyFinder.findDependents STARTED for ${files.size} files")
        LOG.info("Starting findDependents for ${files.size} files: ${files.map { it.name }}")
        val dependents = ConcurrentHashMap<VirtualFile, Boolean>()
        val projectScope = GlobalSearchScope.projectScope(project)
        
        // Process files concurrently for better performance
        val jobs = files.mapNotNull { file ->
            if (!file.isValid || file.isDirectory) {
                LOG.debug("Skipping invalid or directory file: ${file.name}")
                return@mapNotNull null
            }
            
            // Check cache first
            val cacheKey = file.path
            val cachedDependents = dependentsCache[cacheKey]
            if (cachedDependents != null && DependencyFinderConfig.enableCaching) {
                LOG.info("Using cached dependents for ${file.name}")
                cachedDependents.forEach { dependents[it] = true }
                return@mapNotNull null
            }
            
            async {
                val fileDependents = mutableSetOf<VirtualFile>()
                
                ReadAction.compute<Unit, Exception> {
                    val psiManager = PsiManager.getInstance(project)
                    val psiFile = psiManager.findFile(file)
                    if (psiFile == null) {
                        LOG.warn("Could not find PSI file for: ${file.name}")
                        return@compute
                    }

                    if (DependencyFinderConfig.logDetailedSearchInfo) {
                        LOG.info("Processing file: ${file.name} (language: ${psiFile.language.id})")
                    }
                    
                    // Find all referenceable elements in this file (with caching)
                    val elementsToSearch = findReferenceableElements(psiFile)
                    if (DependencyFinderConfig.enablePerformanceLogging) {
                        LOG.info("Found ${elementsToSearch.size} referenceable elements in ${file.name}")
                    }
                    
                    // Batch process elements for better performance
                    val elementBatches = elementsToSearch.chunked(DependencyFinderConfig.elementBatchSize)
                    
                    for (batch in elementBatches) {
                        for (element in batch) {
                            val elementName = when (element) {
                                is PsiNameIdentifierOwner -> element.name ?: "unnamed"
                                is PsiFile -> element.name.substringBeforeLast('.')
                                else -> element.javaClass.simpleName
                            }
                            
                            if (DependencyFinderConfig.logDetailedSearchInfo) {
                                LOG.warn("Searching references for element: $elementName (type: ${element.javaClass.simpleName})")
                            }
                            
                            // OPTIMIZED HYBRID SEARCH STRATEGY
                            val isTypeScriptReactFile = file.name.endsWith(".tsx") || file.name.endsWith(".jsx") || 
                                file.name.endsWith(".ts") || file.name.endsWith(".js")
                            val isTextMateTypeScript = psiFile.language.javaClass.simpleName == "TextMateLanguage" && isTypeScriptReactFile
                            val isReactComponent = element == psiFile && (file.name.endsWith(".tsx") || file.name.endsWith(".jsx"))
                            
                            var refCount = 0
                            val startTime = System.currentTimeMillis()
                            
                            // Decide search strategy based on file type and parsing quality
                            when {
                                isTextMateTypeScript && isReactComponent -> {
                                    // TextMate parsed React components - use text search only
                                    if (DependencyFinderConfig.logDetailedSearchInfo) {
                                        LOG.warn("Using text-only search for textmate React component: $elementName")
                                    }
                                    val textRefs = findTextReferences(elementName, project, file)
                                    refCount = textRefs.size
                                    fileDependents.addAll(textRefs)
                                }
                                
                                isReactComponent -> {
                                    // Properly parsed React component - try PSI first, text fallback if needed
                                    if (DependencyFinderConfig.logDetailedSearchInfo) {
                                        LOG.warn("Using PSI search with text fallback for React component: $elementName")
                                    }
                                    
                                    // Quick PSI search first
                                    val references = ReferencesSearch.search(element, projectScope, false)
                                    references.forEach { reference ->
                                        refCount++
                                        val containingFile = reference.element.containingFile?.virtualFile
                                        if (containingFile != null && containingFile != file && containingFile.isValid) {
                                            fileDependents.add(containingFile)
                                        }
                                    }
                                    
                                    // Use text search fallback only if PSI found very few results and it's enabled
                                    if (DependencyFinderConfig.enableTextSearchFallback && refCount < DependencyFinderConfig.psiSearchFallbackThreshold) {
                                        if (DependencyFinderConfig.logDetailedSearchInfo) {
                                            LOG.warn("  PSI found only $refCount references (threshold: ${DependencyFinderConfig.psiSearchFallbackThreshold}), using text fallback...")
                                        }
                                        val textRefs = findTextReferences(elementName, project, file)
                                        val newRefs = textRefs.filter { it !in fileDependents }
                                        fileDependents.addAll(newRefs)
                                        refCount += newRefs.size
                                        
                                        if (newRefs.isNotEmpty() && DependencyFinderConfig.logDetailedSearchInfo) {
                                            LOG.warn("  Text fallback found ${newRefs.size} additional references")
                                        }
                                    }
                                }
                                
                                else -> {
                                    // Non-React files - use standard PSI search only
                                    if (DependencyFinderConfig.logDetailedSearchInfo) {
                                        LOG.warn("Using PSI-only search for $elementName")
                                    }
                                    val references = ReferencesSearch.search(element, projectScope, false)
                                    
                                    references.forEach { reference ->
                                        refCount++
                                        val containingFile = reference.element.containingFile?.virtualFile
                                        if (containingFile != null && containingFile != file && containingFile.isValid) {
                                            fileDependents.add(containingFile)
                                        }
                                    }
                                }
                            }
                            
                            val searchTime = System.currentTimeMillis() - startTime
                            
                            if (DependencyFinderConfig.enablePerformanceLogging) {
                                if (refCount > 0) {
                                    LOG.warn("Element $elementName had $refCount references (${searchTime}ms)")
                                } else {
                                    LOG.warn("Element $elementName had NO references found (${searchTime}ms)")
                                }
                            }
                        }
                    }
                }
                
                // Cache the results for this file
                if (DependencyFinderConfig.enableCaching) {
                    dependentsCache[cacheKey] = fileDependents
                }
                
                // Add to concurrent set
                fileDependents.forEach { dependents[it] = true }
            }
        }
        
        // Wait for all concurrent jobs to complete
        jobs.awaitAll()
        
        val result = dependents.keys.toSet()
        LOG.warn("DependencyFinder completed. Found ${result.size} dependent files")
        if (result.isNotEmpty()) {
            LOG.warn("Dependent files: ${result.map { it.name }}")
        }
        result
    }
    
    /**
     * Finds elements in the given file that are externally visible and can be referenced.
     * PERFORMANCE CRITICAL: Only includes meaningful referenceable elements.
     */
    private fun findReferenceableElements(file: com.intellij.psi.PsiFile): List<com.intellij.psi.PsiElement> {
        // Check cache first
        val cacheKey = "${file.virtualFile?.path}:${file.modificationStamp}"
        if (DependencyFinderConfig.enableCaching) {
            val cached = referenceableElementsCache[cacheKey]
            if (cached != null) {
                if (DependencyFinderConfig.logDetailedSearchInfo) {
                    LOG.warn("Using cached referenceable elements for ${file.name}")
                }
                return cached
            }
        }
        
        val elements = mutableListOf<com.intellij.psi.PsiElement>()
        val fileName = file.name
        val language = file.language?.id ?: "unknown"
        
        if (DependencyFinderConfig.logDetailedSearchInfo) {
            LOG.warn("Finding referenceable elements in $fileName (language: $language)")
        }
        
        // For TypeScript/JSX files that might be detected as textmate, use text parsing
        if ((fileName.endsWith(".tsx") || fileName.endsWith(".jsx") || fileName.endsWith(".ts") || fileName.endsWith(".js")) 
            && (language == "textmate" || language == "TEXT")) {
            if (DependencyFinderConfig.logDetailedSearchInfo) {
                LOG.warn("  - Using text-based parsing for TypeScript/JavaScript file")
            }
            
            val fileContent = file.text
            
            // Look for React component patterns
            val componentPatterns = listOf(
                // export default function ComponentName
                Regex("export\\s+default\\s+function\\s+(\\w+)"),
                // export const ComponentName = 
                Regex("export\\s+const\\s+(\\w+)\\s*="),
                // function ComponentName() ... export default ComponentName
                Regex("(?:function\\s+(\\w+)\\s*\\([^)]*\\)|const\\s+(\\w+)\\s*=).*?export\\s+default\\s+\\1", RegexOption.DOT_MATCHES_ALL),
                // export function ComponentName
                Regex("export\\s+function\\s+(\\w+)"),
                // export { ComponentName }
                Regex("export\\s*\\{[^}]*\\b(\\w+)\\b[^}]*\\}"),
                // Just look for main component based on file name
                Regex("(?:function|const)\\s+(${fileName.substringBeforeLast('.')})\\s*[=\\(]")
            )
            
            for (pattern in componentPatterns) {
                val matches = pattern.findAll(fileContent)
                for (match in matches) {
                    val componentName = match.groupValues.find { it.isNotBlank() && it != match.value } 
                        ?: fileName.substringBeforeLast('.')
                    
                    if (DependencyFinderConfig.logDetailedSearchInfo) {
                        LOG.warn("  - Found component pattern: $componentName")
                    }
                    
                    // Create a synthetic element representing this component
                    // We'll use the file itself but track the component name
                    elements.add(file)
                    break // Only add the file once, even if multiple patterns match
                }
            }
            
            // If no patterns found, but it's a React file, assume the file itself exports something
            if (elements.isEmpty() && (fileName.endsWith(".tsx") || fileName.endsWith(".jsx"))) {
                if (DependencyFinderConfig.logDetailedSearchInfo) {
                    LOG.warn("  - No specific patterns found, adding file as potential component export")
                }
                elements.add(file)
            }
        } else {
            // Use optimized PSI traversal for properly parsed files
            if (DependencyFinderConfig.logDetailedSearchInfo) {
                LOG.warn("  - Using optimized PSI-based traversal")
            }
            
            // Only visit top-level declarations for better performance
            for (child in file.children) {
                processElementForReferences(child, elements, 0)
            }
            
            // For TypeScript/React files, also add the file itself as a potential import target
            // This helps catch cases where the file exports a default component
            if ((fileName.endsWith(".tsx") || fileName.endsWith(".jsx")) && elements.isEmpty()) {
                if (DependencyFinderConfig.logDetailedSearchInfo) {
                    LOG.warn("  - No PSI elements found in React file, adding file itself as potential component export")
                }
                elements.add(file)
            }
        }
        
        if (DependencyFinderConfig.enablePerformanceLogging) {
            LOG.warn("Found ${elements.size} referenceable elements in $fileName")
        }
        
        // Limit the number of elements to prevent excessive processing
        val limitedElements = if (elements.size > DependencyFinderConfig.maxElementsPerFile) {
            if (DependencyFinderConfig.enablePerformanceLogging) {
                LOG.warn("Limiting elements from ${elements.size} to ${DependencyFinderConfig.maxElementsPerFile} for performance")
            }
            elements.take(DependencyFinderConfig.maxElementsPerFile)
        } else {
            elements
        }
        
        // Cache the results
        if (DependencyFinderConfig.enableCaching) {
            referenceableElementsCache[cacheKey] = limitedElements
        }
        
        return limitedElements
    }
    
    /**
     * Process element and its children for referenceable elements.
     * Uses depth limit to avoid deep recursion.
     */
    private fun processElementForReferences(
        element: com.intellij.psi.PsiElement, 
        elements: MutableList<com.intellij.psi.PsiElement>,
        depth: Int
    ) {
        // Stop if we've collected enough elements
        if (elements.size >= DependencyFinderConfig.maxElementsPerFile) {
            return
        }
        
        // Limit depth to avoid excessive recursion
        if (depth > DependencyFinderConfig.maxTraversalDepth) {
            return
        }
        
        // Check if this element is referenceable
        if (isMeaningfulReferenceableElement(element) && isExternallyVisible(element)) {
            elements.add(element)
            if (DependencyFinderConfig.logDetailedSearchInfo) {
                val elementName = when (element) {
                    is PsiNameIdentifierOwner -> element.name ?: "unnamed"
                    else -> element.toString()
                }
                LOG.warn("  + Added referenceable element: $elementName (${element.javaClass.simpleName})")
            }
        }
        
        // For certain container elements, recurse into children
        if (shouldRecurseIntoElement(element)) {
            for (child in element.children) {
                processElementForReferences(child, elements, depth + 1)
            }
        }
    }
    
    /**
     * Determines if we should recurse into an element's children.
     * Only recurse into meaningful containers to avoid processing every token.
     */
    private fun shouldRecurseIntoElement(element: com.intellij.psi.PsiElement): Boolean {
        val className = element.javaClass.simpleName
        
        // Only recurse into class/interface bodies, not into method implementations
        return when {
            className.contains("Class") -> true
            className.contains("Interface") -> true
            className.contains("Object") -> true
            className.contains("Enum") -> true
            className.contains("File") -> true
            className.contains("Package") -> true
            className.contains("Import") -> false // Don't recurse into imports
            className.contains("Method") -> false // Don't recurse into method bodies
            className.contains("Function") -> false // Don't recurse into function bodies
            className.contains("Block") -> false // Don't recurse into code blocks
            else -> false
        }
    }

    /**
     * PERFORMANCE CRITICAL: Determines if a PSI element is worth searching for references.
     * Filters out infrastructure elements like whitespace, comments, documentation, etc.
     */
    private fun isMeaningfulReferenceableElement(element: com.intellij.psi.PsiElement): Boolean {
        val elementClass = element.javaClass.simpleName
        
        // Exclude infrastructure and non-referenceable elements
        when {
            // Whitespace and basic tokens
            elementClass.contains("WhiteSpace") -> return false
            elementClass.contains("Leaf") && !element.text.matches(Regex("[a-zA-Z_][a-zA-Z0-9_]*")) -> return false
            
            // Documentation elements
            elementClass.startsWith("KDoc") -> return false
            elementClass.contains("Comment") -> return false
            elementClass.contains("Doc") -> return false
            
            // Punctuation and operators
            element.text in listOf("(", ")", "{", "}", "[", "]", ";", ",", ".", ":", "=", "+", "-", "*", "/", "<", ">") -> return false
            
            // Keywords
            element.text in listOf("fun", "class", "interface", "val", "var", "if", "else", "while", "for", "when", "try", "catch", "finally", "return", "import", "package", "public", "private", "protected", "internal") -> return false
            
            // Empty or very short text that's not an identifier
            element.text.isNullOrBlank() -> return false
            element.text.length == 1 && !element.text.matches(Regex("[a-zA-Z_]")) -> return false
        }
        
        // Only include elements that represent named declarations or have meaningful names
        return when (element) {
            is PsiNameIdentifierOwner -> element.name != null && element.name!!.isNotBlank()
            is com.intellij.psi.PsiFile -> true
            else -> {
                // For other elements, only include if they have meaningful text that looks like an identifier
                val text = element.text
                text != null && text.length > 1 && text.matches(Regex("[a-zA-Z_][a-zA-Z0-9_]*"))
            }
        }
    }

    /**
     * Checks if a PSI element is visible outside its own file.
     * This version is more robust, using PsiModifierListOwner for both Java and Kotlin.
     */
    private fun isExternallyVisible(element: com.intellij.psi.PsiElement): Boolean {
        val elementName = when (element) {
            is PsiNameIdentifierOwner -> element.name ?: "unnamed"
            else -> element.javaClass.simpleName
        }
        
        if (DependencyFinderConfig.logDetailedSearchInfo) {
            LOG.debug("Checking visibility for $elementName (${element.javaClass.simpleName})")
        }
        
        // Special handling for Kotlin elements
        val elementClassName = element.javaClass.name
        if (elementClassName.startsWith("org.jetbrains.kotlin.psi")) {
            // For Kotlin PSI elements, check if it has private modifier
            return try {
                // Use reflection to check if element has visibility modifiers
                val hasPrivateModifier = element.text?.contains("private ") == true
                !hasPrivateModifier // If not private, it's visible
            } catch (e: Exception) {
                // If we can't determine, assume visible for Kotlin
                true
            }
        }
        
        // For languages like Java that use modifier lists.
        if (element is PsiModifierListOwner) {
            // An element is considered externally referenceable if it's not private.
            // This covers public, protected, internal (Kotlin), and package-private (Java).
            val isPrivate = element.hasModifierProperty(PsiModifier.PRIVATE)
            val isVisible = !isPrivate
            if (DependencyFinderConfig.logDetailedSearchInfo) {
                LOG.debug("PsiModifierListOwner visibility check for $elementName: private=$isPrivate, visible=$isVisible")
            }
            return isVisible
        }

        // Fallback for languages that don't use PsiModifierListOwner, like JavaScript or HTML.
        val language = element.containingFile?.language?.id ?: return false
        val fileName = element.containingFile?.name ?: ""
        
        if (DependencyFinderConfig.logDetailedSearchInfo) {
            LOG.debug("  - Language: '$language'")
            LOG.debug("  - File name: '$fileName'")
        }
        
        // Handle file extension-based detection for TypeScript/JavaScript files
        // that might be incorrectly detected as "textmate" or other generic languages
        val isTypeScriptFile = fileName.endsWith(".ts") || fileName.endsWith(".tsx") || 
                              fileName.endsWith(".js") || fileName.endsWith(".jsx")
        val isActuallyTypeScript = isTypeScriptFile && (language == "textmate" || language == "TEXT")
        
        if (isActuallyTypeScript && DependencyFinderConfig.logDetailedSearchInfo) {
            LOG.debug("File detected as '$language' but is actually TypeScript/JavaScript based on extension")
        }
        
        val normalizedLanguage = if (isActuallyTypeScript) {
            if (fileName.endsWith(".tsx") || fileName.endsWith(".jsx")) "jsx" else "javascript"
        } else {
            language.lowercase()
        }
        
        if (DependencyFinderConfig.logDetailedSearchInfo) {
            LOG.debug("  - Normalized language: '$normalizedLanguage'")
        }
        
        val result = when (normalizedLanguage) {
            "kotlin" -> {
                // Kotlin elements should normally implement PsiModifierListOwner, but if they don't,
                // we'll assume they're visible unless explicitly private
                if (DependencyFinderConfig.logDetailedSearchInfo) {
                    LOG.debug("Fallback Kotlin visibility check for $elementName - assuming visible")
                }
                true
            }
            "java" -> {
                // Java elements should normally implement PsiModifierListOwner, but fallback
                if (DependencyFinderConfig.logDetailedSearchInfo) {
                    LOG.debug("Fallback Java visibility check for $elementName - assuming visible")
                }
                true
            }
            "javascript", "typescript", "jsx", "tsx" -> {
                val isExported = isJavaScriptExported(element)
                if (DependencyFinderConfig.logDetailedSearchInfo) {
                    LOG.debug("JavaScript/TypeScript visibility check for $elementName: exported=$isExported")
                }
                isExported
            }
            "html" -> {
                val isReferenceable = isHtmlReferenceable(element)
                if (DependencyFinderConfig.logDetailedSearchInfo) {
                    LOG.debug("HTML visibility check for $elementName: referenceable=$isReferenceable")
                }
                isReferenceable
            }
            else -> {
                if (DependencyFinderConfig.logDetailedSearchInfo) {
                    LOG.debug("Unknown language '$normalizedLanguage' for element $elementName, defaulting to not visible")
                }
                false // Default to not visible if we don't know how to check.
            }
        }
        
        return result
    }

    /**
     * Checks if a JavaScript/TypeScript element is exported and thus visible to other files.
     */
    private fun isJavaScriptExported(element: com.intellij.psi.PsiElement): Boolean {
        val elementName = when (element) {
            is PsiNameIdentifierOwner -> element.name ?: "unnamed"
            else -> "unnamed"
        }
        
        LOG.warn("Checking JavaScript export for element: $elementName (${element.javaClass.simpleName})")
        
        // Get the file content for pattern matching (works even with TextMate parsing)
        val fileContent = element.containingFile?.text ?: ""
        val fileName = element.containingFile?.name ?: ""
        
        // For React components, check if the file exports a component with this name
        if (fileName.endsWith(".tsx") || fileName.endsWith(".jsx")) {
            LOG.warn("  - Checking React component patterns for $elementName")
            
            // Check for various React export patterns
            val exportPatterns = listOf(
                // export default ComponentName
                "export\\s+default\\s+$elementName",
                // export { ComponentName }
                "export\\s*\\{[^}]*\\b$elementName\\b[^}]*\\}",
                // export const ComponentName = 
                "export\\s+const\\s+$elementName\\s*=",
                // export function ComponentName
                "export\\s+function\\s+$elementName\\s*\\(",
                // const ComponentName = ... export default ComponentName
                "const\\s+$elementName\\s*=.*export\\s+default\\s+$elementName",
                // function ComponentName() ... export default ComponentName  
                "function\\s+$elementName\\s*\\([^)]*\\).*export\\s+default\\s+$elementName"
            )
            
            for (pattern in exportPatterns) {
                if (Regex(pattern, RegexOption.DOT_MATCHES_ALL).containsMatchIn(fileContent)) {
                    LOG.warn("Found React export pattern: $pattern")
                    return true
                }
            }
            
            // Check if this is the main component (file name matches component name)
            val baseFileName = fileName.substringBeforeLast('.')
            if (baseFileName.equals(elementName, ignoreCase = true)) {
                LOG.warn("Component name matches file name - likely main export")
                return true
            }
        }
        
        // Check if element or its parent has export modifier (for proper PSI parsing)
        var current: com.intellij.psi.PsiElement? = element
        while (current != null && current != element.containingFile) {
            val text = current.text
            if (text != null && (
                text.startsWith("export ") ||
                text.startsWith("export default ") ||
                text.contains("export {") ||
                text.contains("export const ") ||
                text.contains("export function ") ||
                text.contains("export class ") ||
                text.contains("export interface ") ||
                text.contains("export type ") ||
                text.contains("export enum ")
            )) {
                LOG.warn("  Found direct export in PSI element text")
                return true
            }
            current = current.parent
        }
        
        // Also check if it's a named export in the same line (original logic)
        val elementText = element.text
        if (elementText != null && element is PsiNameIdentifierOwner) {
            val name = element.name
            if (name != null) {
                // Look for export statements that mention this element's name
                val exportPattern = Regex("export\\s*\\{[^}]*\\b${Regex.escape(name)}\\b[^}]*\\}")
                if (exportPattern.containsMatchIn(fileContent)) {
                    LOG.warn("  Found named export in export block")
                    return true
                }
            }
        }
        
        LOG.warn(" No export found for $elementName")
        return false
    }
    
    /**
     * Checks if an HTML element can be referenced from other files (CSS, JS).
     */
    private fun isHtmlReferenceable(element: com.intellij.psi.PsiElement): Boolean {
        val text = element.text
        if (text == null) {
            LOG.debug("HTML element has no text, not referenceable")
            return false
        }
        
        // HTML elements with IDs or classes are often referenced from CSS/JS
        val hasId = text.contains("id=")
        val hasClass = text.contains("class=")
        val isReferenceable = hasId || hasClass
        
        LOG.debug("HTML element referenceable check: hasId=$hasId, hasClass=$hasClass, result=$isReferenceable")
        return isReferenceable
    }
    
    /**
     * Finds references by searching for text occurrences of component names in TypeScript/JavaScript files
     * PERFORMANCE OPTIMIZED VERSION
     */
    private fun findTextReferences(componentName: String, project: Project, sourceFile: VirtualFile): Set<VirtualFile> {
        val referencingFiles = mutableSetOf<VirtualFile>()
        
        try {
            val projectBaseDir = project.baseDir ?: return referencingFiles
            
            // Pre-compile regex patterns for better performance
            val searchPatterns = try {
                listOf(
                    // import ComponentName from './file'
                    Regex("import\\s+$componentName\\s+from"),
                    // import { ComponentName } from './file'  
                    Regex("import\\s*\\{[^}]*\\b$componentName\\b[^}]*\\}\\s*from"),
                    // <ComponentName
                    Regex("<$componentName\\b"),
                    // {ComponentName}
                    Regex("\\{\\s*$componentName\\s*\\}"),
                    // ComponentName(
                    Regex("\\b$componentName\\s*\\("),
                    // const x = ComponentName
                    Regex("=\\s*$componentName\\b")
                )
            } catch (e: Exception) {
                LOG.warn("Failed to compile regex patterns for '$componentName': ${e.message}")
                return referencingFiles
            }
            
                         val psiManager = PsiManager.getInstance(project)
             var filesScanned = 0
             var filesSkipped = 0
             val config = DependencyFinderConfig
             
             if (config.enablePerformanceLogging) {
                LOG.warn("Starting optimized text search for '$componentName' (max files: ${config.maxFilesToScan}, max size: ${config.maxFileSizeBytes / 1024}KB)")
             }
             
             // Use configured directories
             val searchDirs = config.searchDirs
             val skipDirs = config.skipDirs
            
            VfsUtil.iterateChildrenRecursively(projectBaseDir, { file ->
                // Skip common build/dependency directories
                val path = file.path
                if (skipDirs.any { skipDir -> path.contains("/$skipDir/") || path.endsWith("/$skipDir") }) {
                    return@iterateChildrenRecursively false // Don't recurse into these directories
                }
                
                // For performance, only recurse into common source directories if they exist
                if (file.isDirectory) {
                    val dirName = file.name
                    // If we're at project root, only recurse into source directories
                    if (file.parent == projectBaseDir) {
                        return@iterateChildrenRecursively searchDirs.contains(dirName) || 
                            // Also allow if no specific source dirs exist (small projects)
                            searchDirs.none { sourceDir -> projectBaseDir.findChild(sourceDir) != null }
                    }
                    return@iterateChildrenRecursively true // Recurse into subdirectories
                }
                
                true // Process files
                         }) { virtualFile ->
                 // Stop if we've scanned enough files
                 if (filesScanned >= config.maxFilesToScan) {
                     if (config.enablePerformanceLogging) {
                         LOG.warn("Reached max files limit (${config.maxFilesToScan}), stopping search")
                     }
                     return@iterateChildrenRecursively false
                 }
                 
                 if (virtualFile != sourceFile && !virtualFile.isDirectory) {
                     val fileName = virtualFile.name
                     
                     // Only process TypeScript/JavaScript files
                     if (fileName.endsWith(".tsx") || fileName.endsWith(".jsx") ||
                         fileName.endsWith(".ts") || fileName.endsWith(".js")) {
                         
                         // Skip if file is too large
                         val fileSize = try { virtualFile.length } catch (e: Exception) { 0L }
                         if (fileSize > config.maxFileSizeBytes) {
                             filesSkipped++
                             if (config.logDetailedSearchInfo) {
                                 LOG.warn("  Skipping large file: $fileName (${fileSize / 1024}KB)")
                             }
                             return@iterateChildrenRecursively true
                         }
                        
                        try {
                            val psiFile = psiManager.findFile(virtualFile)
                            if (psiFile != null) {
                                val fileContent = psiFile.text
                                filesScanned++
                                
                                // Use optimized search - stop at first match
                                val hasMatch = searchPatterns.any { regex ->
                                    try {
                                        regex.containsMatchIn(fileContent)
                                    } catch (e: Exception) {
                                        LOG.warn("Regex error in file $fileName: ${e.message}")
                                        false
                                    }
                                }
                                
                                                                 if (hasMatch) {
                                     if (config.logDetailedSearchInfo) {
                                         LOG.warn("  ✓ Found text reference in: $fileName")
                                     }
                                     referencingFiles.add(virtualFile)
                                 }
                            }
                        } catch (e: Exception) {
                            LOG.warn("  Error reading file $fileName: ${e.message}")
                        }
                    }
                }
                true // Continue iteration
            }
            
                         if (config.enablePerformanceLogging) {
                 LOG.warn("Text search completed: scanned $filesScanned files, skipped $filesSkipped large files, found ${referencingFiles.size} references")
                 if (referencingFiles.isNotEmpty() && config.logDetailedSearchInfo) {
                     LOG.warn("Referenced in: ${referencingFiles.map { it.name }}")
                 }
             }
            
        } catch (e: Exception) {
            LOG.warn("Error during optimized text-based reference search: ${e.message}")
        }
        
        return referencingFiles
    }
    
    /**
     * Clear all caches. Useful when project structure changes.
     */
    fun clearCaches() {
        referenceableElementsCache.clear()
        dependentsCache.clear()
        LOG.info("DependencyFinder caches cleared")
    }
    
    /**
     * Get cache statistics for monitoring
     */
    fun getCacheStats(): String {
        return "ReferenceableElements cache: ${referenceableElementsCache.size} entries, " +
               "Dependents cache: ${dependentsCache.size} entries"
    }
}