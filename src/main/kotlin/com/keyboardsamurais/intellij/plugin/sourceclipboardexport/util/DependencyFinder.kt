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
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Utility class for finding dependencies and reverse dependencies between files
 */
object DependencyFinder {
    private val LOG = Logger.getInstance(DependencyFinder::class.java)

    /**
     * Find all files that depend on (import/use) the given files
     * @param files The files to find dependents for
     * @param project The current project
     * @return Set of files that depend on the input files
     */
    suspend fun findDependents(files: Array<VirtualFile>, project: Project): Set<VirtualFile> = withContext(Dispatchers.Default) {
        LOG.warn("DependencyFinder.findDependents STARTED for ${files.size} files")
        LOG.info("Starting findDependents for ${files.size} files: ${files.map { it.name }}")
        val dependents = mutableSetOf<VirtualFile>()
        val projectScope = GlobalSearchScope.projectScope(project)

        ReadAction.compute<Unit, Exception> {
            val psiManager = PsiManager.getInstance(project)

            for (file in files) {
                if (!file.isValid || file.isDirectory) {
                    LOG.debug("Skipping invalid or directory file: ${file.name}")
                    continue
                }

                val psiFile = psiManager.findFile(file)
                if (psiFile == null) {
                    LOG.warn("Could not find PSI file for: ${file.name}")
                    continue
                }

                LOG.warn("Processing file: ${file.name} (language: ${psiFile.language.id})")
                LOG.warn("  - Language display name: ${psiFile.language.displayName}")
                LOG.warn("  - Language class: ${psiFile.language.javaClass.simpleName}")
                LOG.warn("  - Is TypeScript/React file: ${file.name.endsWith(".tsx") || file.name.endsWith(".jsx") || file.name.endsWith(".ts") || file.name.endsWith(".js")}")
                LOG.warn("  - Is textmate parsed: ${psiFile.language.javaClass.simpleName == "TextMateLanguage"}")

                // Find all referenceable elements in this file
                val elementsToSearch = findReferenceableElements(psiFile)
                LOG.warn("📋 Found ${elementsToSearch.size} referenceable elements in ${file.name}")

                for (element in elementsToSearch) {
                    val elementName = when (element) {
                        is PsiNameIdentifierOwner -> element.name ?: "unnamed"
                        is PsiFile -> element.name.substringBeforeLast('.')
                        else -> element.javaClass.simpleName
                    }
                    
                    LOG.warn("Searching references for element: $elementName (type: ${element.javaClass.simpleName})")
                    
                    // Check if this is a TypeScript/React file that might benefit from text-based search
                    val isTypeScriptReactFile = file.name.endsWith(".tsx") || file.name.endsWith(".jsx") || 
                        file.name.endsWith(".ts") || file.name.endsWith(".js")
                    val isTextMateTypeScript = psiFile.language.javaClass.simpleName == "TextMateLanguage" && isTypeScriptReactFile
                    
                    var refCount = 0
                    
                    if (isTextMateTypeScript && element == psiFile) {
                        // For textmate files, search for text occurrences of the component name
                        LOG.warn("Using text-based search for textmate TypeScript component: $elementName")
                        val textRefs = findTextReferences(elementName, project, file)
                        refCount = textRefs.size
                        dependents.addAll(textRefs)
                        
                        if (refCount > 0) {
                            LOG.warn("Text search found $refCount references to $elementName")
                        }
                    } else {
                        // Use standard PSI reference search first
                        val references = ReferencesSearch.search(element, projectScope, false)
                        
                        references.forEach { reference ->
                            refCount++
                            val containingFile = reference.element.containingFile?.virtualFile
                            if (containingFile != null && containingFile != file && containingFile.isValid) {
                                LOG.warn("Found PSI reference to $elementName in file: ${containingFile.name}")
                                dependents.add(containingFile)
                            } else {
                                // Log why references were filtered out
                                val reason = when {
                                    containingFile == null -> "no containing file"
                                    containingFile == file -> "same file"
                                    !containingFile.isValid -> "invalid file"
                                    else -> "unknown reason"
                                }
                                LOG.debug("Filtered PSI reference to $elementName: $reason")
                            }
                        }
                        
                        // For TypeScript/React files, if PSI search found few/no results, try text-based search as fallback
                        if (isTypeScriptReactFile && element == psiFile && refCount < 2) {
                            LOG.warn("PSI search found only $refCount references for React component. Trying text-based fallback...")
                            val textRefs = findTextReferences(elementName, project, file)
                            val newRefs = textRefs.filter { it !in dependents }
                            
                            if (newRefs.isNotEmpty()) {
                                LOG.warn("Text-based fallback found ${newRefs.size} additional references to $elementName")
                                dependents.addAll(newRefs)
                                refCount += newRefs.size
                            } else if (textRefs.isNotEmpty()) {
                                LOG.warn("Text-based fallback found ${textRefs.size} references, but all were already found by PSI search")
                            } else {
                                LOG.warn(" Text-based fallback also found no references")
                            }
                        }
                    }
                    
                    if (refCount > 0) {
                        LOG.warn("🔗 Element $elementName had $refCount references")
                    } else {
                        LOG.warn(" Element $elementName had NO references found")
                    }
                }
            }
        }

        LOG.warn("DependencyFinder completed. Found ${dependents.size} dependent files")
        if (dependents.isNotEmpty()) {
            LOG.warn("Dependent files: ${dependents.map { it.name }}")
        }
        dependents
    }

    /**
     * Finds elements in the given file that are externally visible and can be referenced.
     */
    private fun findReferenceableElements(file: com.intellij.psi.PsiFile): List<com.intellij.psi.PsiElement> {
        val elements = mutableListOf<com.intellij.psi.PsiElement>()
        val fileName = file.name
        val language = file.language?.id ?: "unknown"
        
        LOG.warn("Finding referenceable elements in $fileName (language: $language)")
        
        // For TypeScript/JSX files that might be detected as textmate, use text parsing
        if ((fileName.endsWith(".tsx") || fileName.endsWith(".jsx") || fileName.endsWith(".ts") || fileName.endsWith(".js")) 
            && (language == "textmate" || language == "TEXT")) {
            LOG.warn("  - Using text-based parsing for TypeScript/JavaScript file")
            
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
                    
                    LOG.warn("  - Found component pattern: $componentName")
                    
                    // Create a synthetic element representing this component
                    // We'll use the file itself but track the component name
                    elements.add(file)
                    break // Only add the file once, even if multiple patterns match
                }
            }
            
            // If no patterns found, but it's a React file, assume the file itself exports something
            if (elements.isEmpty() && (fileName.endsWith(".tsx") || fileName.endsWith(".jsx"))) {
                LOG.warn("  - No specific patterns found, adding file as potential component export")
                elements.add(file)
            }
        } else {
            // Use standard PSI traversal for properly parsed files
            LOG.warn("  - Using PSI-based traversal")
            file.accept(object : PsiRecursiveElementVisitor() {
                override fun visitElement(element: com.intellij.psi.PsiElement) {
                    super.visitElement(element)
                    
                    if (isExternallyVisible(element)) {
                        elements.add(element)
                        val elementName = when (element) {
                            is PsiNameIdentifierOwner -> element.name ?: "unnamed"
                            else -> element.toString()
                        }
                        LOG.warn("  + Added referenceable element: $elementName (${element.javaClass.simpleName})")
                    }
                }
            })
            
            // For TypeScript/React files, also add the file itself as a potential import target
            // This helps catch cases where the file exports a default component
            if ((fileName.endsWith(".tsx") || fileName.endsWith(".jsx")) && elements.isEmpty()) {
                LOG.warn("  - No PSI elements found in React file, adding file itself as potential component export")
                elements.add(file)
            }
        }
        
        LOG.warn("Found ${elements.size} referenceable elements in $fileName")
        return elements
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
        
        LOG.warn("Checking visibility for $elementName (${element.javaClass.simpleName})")
        LOG.warn("  - Is PsiModifierListOwner: ${element is PsiModifierListOwner}")
        
        // For languages like Java and Kotlin that use modifier lists.
        // KtDeclaration implements PsiModifierListOwner, so this works for both.
        if (element is PsiModifierListOwner) {
            // An element is considered externally referenceable if it's not private.
            // This covers public, protected, internal (Kotlin), and package-private (Java).
            val isPrivate = element.hasModifierProperty(PsiModifier.PRIVATE)
            val isVisible = !isPrivate
            LOG.warn("PsiModifierListOwner visibility check for $elementName: private=$isPrivate, visible=$isVisible")
            return isVisible
        }

        // Fallback for languages that don't use PsiModifierListOwner, like JavaScript or HTML.
        val language = element.containingFile?.language?.id ?: return false
        val fileName = element.containingFile?.name ?: ""
        LOG.warn("  - Language: '$language'")
        LOG.warn("  - File name: '$fileName'")
        
        // Handle file extension-based detection for TypeScript/JavaScript files
        // that might be incorrectly detected as "textmate" or other generic languages
        val isTypeScriptFile = fileName.endsWith(".ts") || fileName.endsWith(".tsx") || 
                              fileName.endsWith(".js") || fileName.endsWith(".jsx")
        val isActuallyTypeScript = isTypeScriptFile && (language == "textmate" || language == "TEXT")
        
        if (isActuallyTypeScript) {
            LOG.warn("File detected as '$language' but is actually TypeScript/JavaScript based on extension")
        }
        
        val normalizedLanguage = if (isActuallyTypeScript) {
            if (fileName.endsWith(".tsx") || fileName.endsWith(".jsx")) "jsx" else "javascript"
        } else {
            language.lowercase()
        }
        
        LOG.warn("  - Normalized language: '$normalizedLanguage'")
        
        val result = when (normalizedLanguage) {
            "kotlin" -> {
                // Kotlin elements should normally implement PsiModifierListOwner, but if they don't,
                // we'll assume they're visible unless explicitly private
                LOG.warn("Fallback Kotlin visibility check for $elementName - assuming visible")
                true
            }
            "java" -> {
                // Java elements should normally implement PsiModifierListOwner, but fallback
                LOG.warn("Fallback Java visibility check for $elementName - assuming visible")
                true
            }
            "javascript", "typescript", "jsx", "tsx" -> {
                val isExported = isJavaScriptExported(element)
                LOG.warn("JavaScript/TypeScript visibility check for $elementName: exported=$isExported")
                isExported
            }
            "html" -> {
                val isReferenceable = isHtmlReferenceable(element)
                LOG.debug("HTML visibility check for $elementName: referenceable=$isReferenceable")
                isReferenceable
            }
            else -> {
                LOG.warn("Unknown language '$normalizedLanguage' for element $elementName, defaulting to not visible")
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
     */
    private fun findTextReferences(componentName: String, project: Project, sourceFile: VirtualFile): Set<VirtualFile> {
        val referencingFiles = mutableSetOf<VirtualFile>()
        
        try {
            // Search for import statements and component usage
            val searchPatterns = listOf(
                // import ComponentName from './file'
                "import\\s+$componentName\\s+from",
                // import { ComponentName } from './file'
                "import\\s*\\{[^}]*\\b$componentName\\b[^}]*\\}\\s*from",
                // <ComponentName
                "<$componentName\\b",
                // {ComponentName}
                "\\{\\s*$componentName\\s*\\}",
                // ComponentName(
                "\\b$componentName\\s*\\(",
                // const x = ComponentName
                "=\\s*$componentName\\b"
            )
            
            val psiManager = PsiManager.getInstance(project)
            
            for (pattern in searchPatterns) {
                try {
                    val regex = Regex(pattern)
                    LOG.warn("  Searching with pattern: $pattern")
                    
                    // Use VFS to find all TypeScript/JavaScript files in the project
                    VfsUtil.iterateChildrenRecursively(project.baseDir, null) { virtualFile ->
                        if (virtualFile != sourceFile && !virtualFile.isDirectory && 
                            (virtualFile.name.endsWith(".tsx") || virtualFile.name.endsWith(".jsx") ||
                             virtualFile.name.endsWith(".ts") || virtualFile.name.endsWith(".js"))) {
                            
                            try {
                                val psiFile = psiManager.findFile(virtualFile)
                                if (psiFile != null) {
                                    val fileContent = psiFile.text
                                    if (regex.containsMatchIn(fileContent)) {
                                        LOG.warn("  Found text reference in: ${virtualFile.name}")
                                        referencingFiles.add(virtualFile)
                                        return@iterateChildrenRecursively true // Found match, continue to next file
                                    }
                                }
                            } catch (e: Exception) {
                                LOG.warn("  ⚠️ Error reading file ${virtualFile.name}: ${e.message}")
                            }
                        }
                        true // Continue iteration
                    }
                } catch (e: Exception) {
                    LOG.warn("  ⚠️ Error searching with pattern '$pattern': ${e.message}")
                }
            }
            
            if (referencingFiles.isNotEmpty()) {
                LOG.warn("Text search found ${referencingFiles.size} files referencing '$componentName'")
                LOG.warn("Referenced in: ${referencingFiles.map { it.name }}")
            }
            
        } catch (e: Exception) {
            LOG.warn(" Error during text-based reference search: ${e.message}")
        }
        
        return referencingFiles
    }
}