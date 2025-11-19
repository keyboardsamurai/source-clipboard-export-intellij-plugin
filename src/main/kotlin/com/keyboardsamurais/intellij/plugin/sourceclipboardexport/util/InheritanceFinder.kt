package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.util.PsiTreeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Utility class for finding class implementations and subclasses across multiple languages
 * Supports Java, Kotlin, and TypeScript/JavaScript class hierarchies
 */
object InheritanceFinder {
    private val logger = Logger.getInstance(InheritanceFinder::class.java)
    private fun trace(msg: String) {
        try { logger.info(msg) } catch (_: Throwable) {}
        DebugTracer.log(msg)
    }
    
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
                trace("SCE[Finder]: scanning file=${file.path} language=$language")
                
                when (language) {
                    Language.JAVA, Language.KOTLIN -> {
                        // Use IntelliJ's built-in PSI analysis for Java/Kotlin
                        val before = implementations.size
                        findJavaKotlinImplementations(psiFile, implementations, projectScope, includeAnonymous, includeTest)
                        val added = implementations.size - before
                        trace("SCE[Finder]: Java/Kotlin pass added=$added total=${implementations.size}")
                    }
                    Language.TYPESCRIPT, Language.JAVASCRIPT, Language.JSX, Language.TSX -> {
                        // Use text-based analysis for TypeScript/JavaScript
                        val before = implementations.size
                        findTypeScriptImplementations(project, psiFile, implementations, includeTest)
                        val added = implementations.size - before
                        trace("SCE[Finder]: TS/JS pass added=$added total=${implementations.size}")
                    }
                    else -> {
                        // Skip files that don't support class hierarchies (HTML, CSS, etc.)
                        continue
                    }
                }
            }
        }
        
        trace("SCE[Finder]: final implementations total=${implementations.size}")
        implementations
    }

    /**
     * Find implementations for a specific set of base classes/interfaces (symbol-scoped search).
     */
    suspend fun findImplementationsFor(
        bases: Collection<PsiClass>,
        project: Project,
        includeAnonymous: Boolean = true,
        includeTest: Boolean = true
    ): Set<VirtualFile> = withContext(Dispatchers.Default) {
        val implementations = mutableSetOf<VirtualFile>()
        val projectScope = GlobalSearchScope.projectScope(project)

        ReadAction.compute<Unit, Exception> {
            bases.forEach { base ->
                val inheritors = ClassInheritorsSearch.search(base, projectScope, true)
                var countPsi = 0

                inheritors.forEach inheritorLoop@ { inheritor ->
                    val inheritorFile = inheritor.containingFile?.virtualFile
                    if (inheritorFile != null && inheritorFile.isValid) {
                        if (!includeAnonymous && inheritor.name == null) return@inheritorLoop
                        if (!includeTest && isTestFile(inheritorFile)) return@inheritorLoop
                        implementations.add(inheritorFile)
                        countPsi++
                    }
                }
                trace("SCE[Finder]: base=${base.qualifiedName ?: base.name} PSI-inheritors=$countPsi")

                // Kotlin-aware fallback using stub index
                val ktFiles = findKotlinInheritorsByIndex(base, project, projectScope)
                trace("SCE[Finder]: base=${base.qualifiedName ?: base.name} Kotlin-index hits=${ktFiles.size}")
                ktFiles.forEach ktLoop@ { vf ->
                    if (vf.isValid) {
                        if (!includeTest && isTestFile(vf)) return@ktLoop
                        implementations.add(vf)
                    }
                }
            }
        }

        trace("SCE[Finder]: symbol-scoped final implementations total=${implementations.size}")
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
        val classes = findClassesIncludingKotlin(psiFile)
        trace("SCE[Finder]: classes in file=${psiFile.virtualFile?.path} count=${classes.size} names=${classes.map { it.qualifiedName ?: it.name }}")
        
        for (psiClass in classes) {
            // Search for inheritors
            val inheritors = ClassInheritorsSearch.search(psiClass, projectScope, true)
            var countPsi = 0
            
            inheritors.forEach javaInheritorLoop@ { inheritor ->
                val inheritorFile = inheritor.containingFile?.virtualFile
                
                if (inheritorFile != null && inheritorFile.isValid && inheritorFile != psiFile.virtualFile) {
                    // Check filters
                    if (!includeAnonymous && inheritor.name == null) {
                        return@javaInheritorLoop
                    }
                    
                    if (!includeTest && isTestFile(inheritorFile)) {
                        return@javaInheritorLoop
                    }
                    
                    implementations.add(inheritorFile)
                    countPsi++
                }
            }
            trace("SCE[Finder]: base=${psiClass.qualifiedName ?: psiClass.name} PSI-inheritors=$countPsi")

            // Kotlin-aware fallback: use Kotlin stub index to find subclasses by super name
            val beforeFallback = implementations.size
            implementations.addAll(
                findKotlinInheritorsByIndex(psiClass, psiFile.project, projectScope)
                    .filter { vf -> vf.isValid && vf != psiFile.virtualFile }
                    .filter { vf -> includeTest || !isTestFile(vf) }
                    .toSet()
            )
            val addedFallback = implementations.size - beforeFallback
            trace("SCE[Finder]: base=${psiClass.qualifiedName ?: psiClass.name} Kotlin-index added=$addedFallback totalNow=${implementations.size}")
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
     * Fallback search for Kotlin inheritors using KotlinSuperClassNameIndex.
     * Uses reflection so the plugin works without the Kotlin plugin.
     */
    private fun findKotlinInheritorsByIndex(base: PsiClass, project: Project, scope: GlobalSearchScope): List<VirtualFile> {
        val baseName = base.name ?: return emptyList()
        return try {
            val indexClass = Class.forName("org.jetbrains.kotlin.idea.stubindex.KotlinSuperClassNameIndex")
            val getInstance = indexClass.methods.firstOrNull { it.name == "getInstance" && it.parameterCount == 0 }
            val instance = getInstance?.invoke(null) ?: return emptyList()
            val getMethod = instance.javaClass.methods.firstOrNull {
                it.name == "get" && it.parameterCount == 3 &&
                        it.parameterTypes[0] == String::class.java &&
                        Project::class.java.isAssignableFrom(it.parameterTypes[1])
            } ?: return emptyList()

            val result = getMethod.invoke(instance, baseName, project, scope)
            val ktList: Collection<*> = when (result) {
                is Collection<*> -> result
                is Array<*> -> result.filterNotNull()
                else -> return emptyList()
            }

            val files = mutableListOf<VirtualFile>()
            for (kt in ktList) {
                // Convert to light classes and verify inheritance relationship to avoid name collisions
                val lightClasses = toLightClassesReflect(kt)
                for (lc in lightClasses) {
                    try {
                        if (lc.isInheritor(base, true)) {
                            lc.containingFile?.virtualFile?.let { files.add(it) }
                        }
                    } catch (_: Throwable) {
                        // Ignore any resolution failures
                    }
                }
            }
            files
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun toLightClassesReflect(ktClassOrObject: Any?): List<PsiClass> {
        if (ktClassOrObject == null) return emptyList()
        return try {
            val utils = Class.forName("org.jetbrains.kotlin.asJava.LightClassUtilsKt")
            val method = utils.methods.firstOrNull { it.name == "toLightClasses" && it.parameterTypes.size == 1 }
            val res = method?.invoke(null, ktClassOrObject)
            when (res) {
                is List<*> -> res.filterIsInstance<PsiClass>()
                is Array<*> -> res.filterIsInstance<PsiClass>()
                else -> emptyList()
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }
    
    /**
     * Find all classes and interfaces in a file
     */
    private fun findClassesIncludingKotlin(psiFile: PsiFile): List<PsiClass> {
        val classes = mutableListOf<PsiClass>()

        // Collect Java PsiClass declarations
        psiFile.accept(object : com.intellij.psi.PsiRecursiveElementVisitor() {
            override fun visitElement(element: com.intellij.psi.PsiElement) {
                if (element is PsiClass) {
                    classes.add(element)
                }
                super.visitElement(element)
            }
        })

        // Attempt to collect Kotlin class/interface declarations via light classes
        try {
            val ktFileClass = Class.forName("org.jetbrains.kotlin.psi.KtFile")
            if (ktFileClass.isInstance(psiFile)) {
                val ktClassOrObjectClass = Class.forName("org.jetbrains.kotlin.psi.KtClassOrObject")

                // Collect all KtClassOrObject elements
                @Suppress("UNCHECKED_CAST")
                val ktElements: Collection<PsiElement> = PsiTreeUtil.collectElementsOfType(psiFile, ktClassOrObjectClass as Class<PsiElement>)
                trace("SCE[Finder]: Kotlin KtClassOrObject count=${ktElements.size}")

                // Use Kotlin's LightClassUtilsKt.toLightClasses via reflection to avoid hard dependency at class load time
                val lightUtils = Class.forName("org.jetbrains.kotlin.asJava.LightClassUtilsKt")
                val toLightClasses = lightUtils.methods.firstOrNull { m ->
                    m.name == "toLightClasses" && m.parameterTypes.size == 1 && m.parameterTypes[0].name == "org.jetbrains.kotlin.psi.KtClassOrObject"
                }

                if (toLightClasses != null) {
                    for (kt in ktElements) {
                        val result = toLightClasses.invoke(null, kt)
                        if (result is List<*>) {
                            result.forEach { lc ->
                                if (lc is PsiClass) classes.add(lc)
                            }
                        } else if (result is Array<*>) {
                            result.forEach { lc -> if (lc is PsiClass) classes.add(lc) }
                        }
                    }
                }
            }
        } catch (_: Throwable) {
            // Kotlin plugin not available or reflection failed; skip Kotlin classes gracefully
        }

        // Deduplicate by qualified name and fallback to name if null
        trace("SCE[Finder]: total classes collected=${classes.size}")
        return classes.distinctBy { it.qualifiedName ?: it.name ?: it.toString() }
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
                        // Include Kotlin via light classes
                        val classes = findClassesIncludingKotlin(psiFile)
                        trace("SCE[Stats]: file=${file.path} classes=${classes.map { it.qualifiedName ?: it.name }}")
                        for (psiClass in classes) {
                            try {
                                if (psiClass.isInterface) {
                                    stats.interfaceCount++
                                } else if (psiClass.hasModifierProperty("abstract")) {
                                    stats.abstractOrOpenClassCount++
                                } else if (isKotlinOpen(psiClass)) {
                                    // Kotlin 'open' classes count as inheritable
                                    stats.abstractOrOpenClassCount++
                                } else {
                                    stats.concreteClassCount++
                                }
                            } catch (_: Throwable) {
                                // Defensive: if anything goes wrong, classify as concrete
                                stats.concreteClassCount++
                            }
                        }
                    }
                    Language.TYPESCRIPT, Language.JAVASCRIPT, Language.JSX, Language.TSX -> {
                        val fileText = psiFile.text
                        val elements = extractInheritableElements(fileText)
                        for (element in elements) {
                            when (element.type) {
                                ElementType.INTERFACE -> stats.interfaceCount++
                                ElementType.ABSTRACT_CLASS -> stats.abstractOrOpenClassCount++
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
        
        trace("SCE[Stats]: total interfaces=${stats.interfaceCount} abstract/open=${stats.abstractOrOpenClassCount} concrete=${stats.concreteClassCount} components=${stats.componentCount}")
        stats
    }

    /**
     * Collect all classes/interfaces declared in the given file (Java and Kotlin via light classes).
     */
    fun collectClasses(psiFile: PsiFile): List<PsiClass> {
        return ReadAction.compute<List<PsiClass>, Exception> {
            findClassesIncludingKotlin(psiFile)
        }
    }

    /**
     * Best-effort check whether a PsiClass originates from a Kotlin 'open' class.
     * Uses reflection to avoid hard dependency when Kotlin plugin is absent.
     */
    private fun isKotlinOpen(psiClass: PsiClass): Boolean {
        return try {
            val originalElement: PsiElement? = psiClass.navigationElement
            if (originalElement == null) return false

            val ktClassOrObjectClass = Class.forName("org.jetbrains.kotlin.psi.KtClassOrObject")
            if (!ktClassOrObjectClass.isInstance(originalElement)) return false

            val ktModifierListOwnerClass = Class.forName("org.jetbrains.kotlin.psi.KtModifierListOwner")
            if (!ktModifierListOwnerClass.isInstance(originalElement)) return false

            val ktTokensClass = Class.forName("org.jetbrains.kotlin.lexer.KtTokens")
            val OPEN = ktTokensClass.getField("OPEN_KEYWORD").get(null)

            val hasModifierMethod = ktModifierListOwnerClass.methods.firstOrNull { it.name == "hasModifier" && it.parameterTypes.size == 1 }
            hasModifierMethod?.invoke(originalElement, OPEN) as? Boolean ?: false
        } catch (_: Throwable) {
            false
        }
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
        var abstractOrOpenClassCount: Int = 0,
        var concreteClassCount: Int = 0,
        var componentCount: Int = 0  // For React components
    ) {
        val totalCount: Int
            get() = interfaceCount + abstractOrOpenClassCount + concreteClassCount + componentCount
            
        fun hasInheritableTypes(): Boolean = interfaceCount > 0 || abstractOrOpenClassCount > 0 || componentCount > 0
        
        fun getDescription(): String {
            val parts = mutableListOf<String>()
            if (interfaceCount > 0) parts.add("$interfaceCount interface${if (interfaceCount > 1) "s" else ""}")
            if (abstractOrOpenClassCount > 0) parts.add("$abstractOrOpenClassCount abstract/open class${if (abstractOrOpenClassCount > 1) "es" else ""}")
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
