package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Utility class for finding class implementations and subclasses across multiple languages
 * Supports Java, Kotlin, and TypeScript/JavaScript class hierarchies
 */
object InheritanceFinder {
    
    /**
     * Find all implementations of interfaces and subclasses of classes in the given files
     * @param files The files to find implementations for
     * @param project The current project
     * @param includeAnonymous Whether to include anonymous inner classes
     * @param includeTest Whether to include test implementations
     * @return Set of files containing implementations/subclasses
     */
    suspend fun findImplementations(
        files: Array<VirtualFile>, 
        project: Project,
        includeAnonymous: Boolean = true,
        includeTest: Boolean = true
    ): Set<VirtualFile> = withContext(Dispatchers.Default) {
        val implementations = mutableSetOf<VirtualFile>()
        val projectScope = GlobalSearchScope.projectScope(project)
        
        ReadAction.compute<Unit, Exception> {
            val psiManager = PsiManager.getInstance(project)
            
            for (file in files) {
                if (!file.isValid || file.isDirectory) continue
                
                val psiFile = psiManager.findFile(file) ?: continue
                val language = detectLanguage(file)
                
                when (language) {
                    Language.JAVA, Language.KOTLIN -> {
                        // Use IntelliJ's built-in PSI analysis for Java/Kotlin
                        findJavaKotlinImplementations(psiFile, implementations, projectScope, includeAnonymous, includeTest)
                    }
                    Language.TYPESCRIPT, Language.JAVASCRIPT, Language.JSX, Language.TSX -> {
                        // Use text-based analysis for TypeScript/JavaScript
                        findTypeScriptImplementations(project, psiFile, implementations, includeTest)
                    }
                    else -> {
                        // Skip files that don't support class hierarchies (HTML, CSS, etc.)
                        continue
                    }
                }
            }
        }
        
        implementations
    }
    
    private fun findJavaKotlinImplementations(
        psiFile: PsiFile,
        implementations: MutableSet<VirtualFile>,
        projectScope: GlobalSearchScope,
        includeAnonymous: Boolean,
        includeTest: Boolean
    ) {
        // Find all classes/interfaces in this file
        val classes = findClasses(psiFile)
        
        for (psiClass in classes) {
            // Search for inheritors
            val inheritors = ClassInheritorsSearch.search(psiClass, projectScope, true)
            
            inheritors.forEach { inheritor ->
                val inheritorFile = inheritor.containingFile?.virtualFile
                
                if (inheritorFile != null && inheritorFile.isValid && inheritorFile != psiFile.virtualFile) {
                    // Check filters
                    if (!includeAnonymous && inheritor.name == null) {
                        return@forEach
                    }
                    
                    if (!includeTest && isTestFile(inheritorFile)) {
                        return@forEach
                    }
                    
                    implementations.add(inheritorFile)
                }
            }
        }
    }
    
    private fun findTypeScriptImplementations(
        project: Project,
        psiFile: PsiFile,
        implementations: MutableSet<VirtualFile>,
        includeTest: Boolean
    ) {
        val fileText = psiFile.text
        val inheritableElements = extractInheritableElements(fileText)
        
        if (inheritableElements.isEmpty()) return
        
        // Search for implementations across TypeScript/JavaScript files
        val projectScope = GlobalSearchScope.projectScope(project)
        
        runReadAction {
            val projectFileIndex = com.intellij.openapi.roots.ProjectFileIndex.getInstance(project)
            val psiManager = PsiManager.getInstance(project)
            
            projectFileIndex.iterateContent { file ->
                if (file.isValid && !file.isDirectory && 
                    detectLanguage(file) in listOf(Language.TYPESCRIPT, Language.JAVASCRIPT, Language.JSX, Language.TSX)) {
                    
                    if (!includeTest && isTestFile(file)) {
                        return@iterateContent true
                    }
                    
                    val candidateFile = psiManager.findFile(file)
                    if (candidateFile != null && candidateFile != psiFile) {
                        val candidateText = candidateFile.text
                        
                        // Check if this file implements/extends any of our inheritable elements
                        inheritableElements.forEach { element ->
                            if (isImplementingElement(candidateText, element)) {
                                implementations.add(file)
                            }
                        }
                    }
                }
                true
            }
        }
    }
    
    /**
     * Find all classes and interfaces in a file
     */
    private fun findClasses(psiFile: PsiFile): List<PsiClass> {
        val classes = mutableListOf<PsiClass>()
        
        psiFile.accept(object : com.intellij.psi.PsiRecursiveElementVisitor() {
            override fun visitElement(element: com.intellij.psi.PsiElement) {
                if (element is PsiClass) {
                    classes.add(element)
                }
                super.visitElement(element)
            }
        })
        
        return classes
    }
    
    /**
     * Check if a file is a test file based on common patterns
     */
    private fun isTestFile(file: VirtualFile): Boolean {
        val path = file.path
        val name = file.nameWithoutExtension
        
        return path.contains("/test/") || 
               path.contains("/tests/") ||
               path.contains("/__tests__/") ||
               name.endsWith("Test") ||
               name.endsWith("Tests") ||
               name.endsWith("Spec") ||
               name.startsWith("Test") ||
               name.contains(".test.") ||
               name.contains(".spec.") ||
               name.contains("Test", ignoreCase = true) && 
               (name.endsWith("IT") || name.endsWith("IntegrationTest") || name.endsWith("E2ETest"))
    }
    
    private fun extractInheritableElements(fileText: String): List<InheritableElement> {
        val elements = mutableListOf<InheritableElement>()
        
        // TypeScript/JavaScript class patterns
        val classPattern = Regex("""(?:export\s+)?(?:abstract\s+)?class\s+([A-Z][a-zA-Z0-9_]*)\s*(?:extends\s+[^{]+)?(?:implements\s+[^{]+)?\s*\{""")
        classPattern.findAll(fileText).forEach { match ->
            elements.add(InheritableElement(match.groupValues[1], ElementType.CLASS))
        }
        
        // TypeScript interface patterns
        val interfacePattern = Regex("""(?:export\s+)?interface\s+([A-Z][a-zA-Z0-9_]*)\s*(?:extends\s+[^{]+)?\s*\{""")
        interfacePattern.findAll(fileText).forEach { match ->
            elements.add(InheritableElement(match.groupValues[1], ElementType.INTERFACE))
        }
        
        // React component patterns (function components that could be extended)
        val reactComponentPattern = Regex("""(?:export\s+)?(?:const|function)\s+([A-Z][a-zA-Z0-9_]*)\s*(?:\([^)]*\))?\s*(?::\s*[^=]+)?\s*=?\s*(?:\([^)]*\))?\s*=>""")
        reactComponentPattern.findAll(fileText).forEach { match ->
            // Only consider it if it looks like a React component (starts with capital letter)
            val name = match.groupValues[1]
            if (name[0].isUpperCase()) {
                elements.add(InheritableElement(name, ElementType.REACT_COMPONENT))
            }
        }
        
        // Abstract class patterns
        val abstractClassPattern = Regex("""(?:export\s+)?abstract\s+class\s+([A-Z][a-zA-Z0-9_]*)\s*(?:extends\s+[^{]+)?\s*\{""")
        abstractClassPattern.findAll(fileText).forEach { match ->
            elements.add(InheritableElement(match.groupValues[1], ElementType.ABSTRACT_CLASS))
        }
        
        return elements
    }
    
    private fun isImplementingElement(candidateText: String, element: InheritableElement): Boolean {
        val elementName = element.name
        
        return when (element.type) {
            ElementType.CLASS, ElementType.ABSTRACT_CLASS -> {
                // Look for "extends ClassName"
                val extendsPattern = Regex("""class\s+[A-Z][a-zA-Z0-9_]*\s+extends\s+$elementName(?:\s|<|,|\{)""")
                extendsPattern.containsMatchIn(candidateText)
            }
            ElementType.INTERFACE -> {
                // Look for "implements InterfaceName" or "extends InterfaceName" (for interface extension)
                val implementsPattern = Regex("""(?:implements|extends)\s+(?:[^,\s{]*,\s*)*$elementName(?:\s|<|,|\{)""")
                implementsPattern.containsMatchIn(candidateText)
            }
            ElementType.REACT_COMPONENT -> {
                // Look for HOC patterns or component wrapping
                val hocPattern = Regex("""(?:withRouter|connect|memo|forwardRef)\s*\(\s*$elementName\s*\)""")
                val wrapperPattern = Regex("""const\s+[A-Z][a-zA-Z0-9_]*\s*=\s*\([^)]*\)\s*=>\s*<$elementName""")
                hocPattern.containsMatchIn(candidateText) || wrapperPattern.containsMatchIn(candidateText)
            }
        }
    }
    
    private fun detectLanguage(file: VirtualFile): Language {
        return when (file.extension?.lowercase()) {
            "java" -> Language.JAVA
            "kt", "kts" -> Language.KOTLIN
            "js", "mjs" -> Language.JAVASCRIPT
            "ts" -> Language.TYPESCRIPT
            "jsx" -> Language.JSX
            "tsx" -> Language.TSX
            else -> Language.UNKNOWN
        }
    }
    
    /**
     * Get implementation statistics for display
     */
    suspend fun getImplementationStats(
        files: Array<VirtualFile>,
        project: Project
    ): ImplementationStats = withContext(Dispatchers.Default) {
        val stats = ImplementationStats()
        
        ReadAction.compute<Unit, Exception> {
            val psiManager = PsiManager.getInstance(project)
            
            for (file in files) {
                if (!file.isValid || file.isDirectory) continue
                
                val psiFile = psiManager.findFile(file) ?: continue
                val language = detectLanguage(file)
                
                when (language) {
                    Language.JAVA, Language.KOTLIN -> {
                        val classes = findClasses(psiFile)
                        for (psiClass in classes) {
                            when {
                                psiClass.isInterface -> stats.interfaceCount++
                                psiClass.hasModifierProperty("abstract") -> stats.abstractClassCount++
                                else -> stats.concreteClassCount++
                            }
                        }
                    }
                    Language.TYPESCRIPT, Language.JAVASCRIPT, Language.JSX, Language.TSX -> {
                        val fileText = psiFile.text
                        val elements = extractInheritableElements(fileText)
                        for (element in elements) {
                            when (element.type) {
                                ElementType.INTERFACE -> stats.interfaceCount++
                                ElementType.ABSTRACT_CLASS -> stats.abstractClassCount++
                                ElementType.CLASS -> stats.concreteClassCount++
                                ElementType.REACT_COMPONENT -> stats.componentCount++
                            }
                        }
                    }
                    else -> {
                        // Skip languages that don't support inheritance
                        continue
                    }
                }
            }
        }
        
        stats
    }
    
    private enum class Language {
        JAVA, KOTLIN, JAVASCRIPT, TYPESCRIPT, JSX, TSX, UNKNOWN
    }
    
    private data class InheritableElement(
        val name: String,
        val type: ElementType
    )
    
    private enum class ElementType {
        CLASS, INTERFACE, ABSTRACT_CLASS, REACT_COMPONENT
    }
    
    data class ImplementationStats(
        var interfaceCount: Int = 0,
        var abstractClassCount: Int = 0,
        var concreteClassCount: Int = 0,
        var componentCount: Int = 0  // For React components
    ) {
        val totalCount: Int
            get() = interfaceCount + abstractClassCount + concreteClassCount + componentCount
            
        fun hasInheritableTypes(): Boolean = interfaceCount > 0 || abstractClassCount > 0 || componentCount > 0
        
        fun getDescription(): String {
            val parts = mutableListOf<String>()
            if (interfaceCount > 0) parts.add("$interfaceCount interface${if (interfaceCount > 1) "s" else ""}")
            if (abstractClassCount > 0) parts.add("$abstractClassCount abstract class${if (abstractClassCount > 1) "es" else ""}")
            if (componentCount > 0) parts.add("$componentCount React component${if (componentCount > 1) "s" else ""}")
            
            return when (parts.size) {
                0 -> "No inheritable types"
                1 -> parts[0]
                2 -> "${parts[0]} and ${parts[1]}"
                else -> "${parts.dropLast(1).joinToString(", ")} and ${parts.last()}"
            }
        }
    }
}