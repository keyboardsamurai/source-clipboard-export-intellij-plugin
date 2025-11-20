package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util

// Avoid a hard dependency on Kotlin PSI; use optional service instead.
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.kotlin.KotlinImportResolver

/**
 * Heuristic heavy-lifter responsible for locating files related to a selection (tests, configs,
 * imports, recent changes, etc.). Split out so multiple actions can share consistent behavior.
 */
object RelatedFileFinder {
    private val LOG = Logger.getInstance(RelatedFileFinder::class.java)

    object Config {
        private fun listProp(key: String, default: List<String>) =
            System.getProperty(key)?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?: default

        val jsModuleRoots: List<String> = listProp("sce.jsRoots", listOf("src", "app", "pages", "components", "lib", "utils"))
        val indexFileNames: List<String> = listProp("sce.indexFiles", listOf("index.js", "index.ts", "index.jsx", "index.tsx"))
        fun intProp(key: String, default: Int) = System.getProperty(key)?.toIntOrNull() ?: default
        val transitiveMaxDepth: Int = intProp("sce.transitiveDepth", Int.MAX_VALUE)
        val importsMaxPerFile: Int = intProp("sce.imports.maxPerFile", Int.MAX_VALUE)
    }
    
    /**
     * Discovers test files that match the given source file using language-aware naming
     * conventions (JUnit, Jest, pytest, etc.) plus folder heuristics like `__tests__`.
     */
    fun findTestFiles(project: Project, sourceFile: VirtualFile): List<VirtualFile> {
        if (sourceFile.isDirectory) return emptyList()
        
        val testFiles = mutableListOf<VirtualFile>()
        val fileName = sourceFile.nameWithoutExtension
        val extension = sourceFile.extension?.lowercase() ?: ""
        val language = detectLanguage(sourceFile)
        
        // Language-specific test file patterns
        val testPatterns = when (language) {
            Language.JAVA, Language.KOTLIN -> listOf(
                "${fileName}Test.$extension",
                "${fileName}Tests.$extension", 
                "Test$fileName.$extension",
                "${fileName}Spec.$extension",
                "${fileName}IT.$extension",
                "${fileName}IntegrationTest.$extension"
            )
            Language.JAVASCRIPT, Language.TYPESCRIPT, Language.JSX, Language.TSX -> listOf(
                // Jest/Vitest patterns
                "${fileName}.test.js", "${fileName}.test.ts", "${fileName}.test.jsx", "${fileName}.test.tsx",
                "${fileName}.spec.js", "${fileName}.spec.ts", "${fileName}.spec.jsx", "${fileName}.spec.tsx",
                // React Testing Library patterns
                "${fileName}.test.js", "${fileName}.spec.js",
                // __tests__ folder patterns (will be handled separately)
                "$fileName.js", "$fileName.ts", "$fileName.jsx", "$fileName.tsx"
            )
            Language.PYTHON -> listOf(
                "test_$fileName.py",
                "${fileName}_test.py",
                "test$fileName.py"
            )
            Language.HTML -> listOf(
                "${fileName}.test.html",
                "${fileName}.spec.html"
            )
            Language.CSS -> listOf(
                "${fileName}.test.css",
                "${fileName}.spec.css"
            )
            else -> listOf(
                "${fileName}Test.$extension",
                "${fileName}Tests.$extension",
                "Test$fileName.$extension"
            )
        }
        
        // Search for test files with specific patterns
        testPatterns.forEach { pattern ->
            val files = ReadAction.compute<Collection<VirtualFile>, Exception> {
                FilenameIndex.getVirtualFilesByName(pattern, GlobalSearchScope.projectScope(project))
            }
            testFiles.addAll(files)
        }
        
        // Special handling for __tests__ folders (React/Next.js convention)
        if (language in listOf(Language.JAVASCRIPT, Language.TYPESCRIPT, Language.JSX, Language.TSX)) {
            findTestsInTestFolders(sourceFile, testFiles)
        }
        
        return testFiles.distinct()
    }
    
    private fun findTestsInTestFolders(sourceFile: VirtualFile, testFiles: MutableList<VirtualFile>) {
        val fileName = sourceFile.nameWithoutExtension
        val parentDir = sourceFile.parent ?: return
        
        runReadAction {
            // Look for __tests__ folder in same directory
            val testsDir = parentDir.findChild("__tests__")
            testsDir?.children?.forEach { testFile ->
                if (testFile.nameWithoutExtension.contains(fileName, ignoreCase = true)) {
                    testFiles.add(testFile)
                }
            }
            
            // Look for tests folder in same directory  
            val testsDirAlt = parentDir.findChild("tests")
            testsDirAlt?.children?.forEach { testFile ->
                if (testFile.nameWithoutExtension.contains(fileName, ignoreCase = true)) {
                    testFiles.add(testFile)
                }
            }
            
            // Look for component.test.* pattern in same directory
            parentDir.children.forEach { siblingFile ->
                if (siblingFile.nameWithoutExtension.startsWith(fileName) && 
                    (siblingFile.name.contains(".test.") || siblingFile.name.contains(".spec."))) {
                    testFiles.add(siblingFile)
                }
            }
        }
    }
    
    /**
     * Returns configuration files that are relevant for the source file's language (package.json,
     * tsconfig, application.yml, Dockerfile, etc.). Used by the "Include Configuration" action.
     */
    fun findConfigFiles(project: Project, sourceFile: VirtualFile): List<VirtualFile> {
        val configFiles = mutableListOf<VirtualFile>()
        val projectRoot = ReadAction.compute<VirtualFile?, Exception> {
            ProjectRootManager.getInstance(project).contentRoots.firstOrNull()
        } ?: return emptyList()
        val language = detectLanguage(sourceFile)
        
        // Base config files (always included)
        val baseConfigFiles = listOf(
            "package.json", "pom.xml", "build.gradle", "build.gradle.kts",
            "Cargo.toml", "requirements.txt", "pyproject.toml", "setup.py",
            "composer.json", "Gemfile", "go.mod", "CMakeLists.txt",
            "Dockerfile", "docker-compose.yml", ".env"
        )
        
        // Language-specific config files
        val languageConfigFiles = when (language) {
            Language.JAVASCRIPT, Language.TYPESCRIPT, Language.JSX, Language.TSX -> listOf(
                // Core JS/TS configs
                "tsconfig.json", "jsconfig.json", "webpack.config.js", "vite.config.js", "vite.config.ts",
                // React/Next.js configs
                "next.config.js", "next.config.mjs", "tailwind.config.js", "tailwind.config.ts",
                // Testing configs
                "jest.config.js", "jest.config.ts", "vitest.config.js", "vitest.config.ts",
                // Linting/formatting
                ".eslintrc.js", ".eslintrc.json", ".prettierrc", ".prettierrc.json",
                // Build tools
                "rollup.config.js", "esbuild.config.js", "babel.config.js"
            )
            Language.JAVA, Language.KOTLIN -> listOf(
                "application.properties", "application.yml", "application.yaml"
            )
            Language.PYTHON -> listOf(
                "setup.cfg", "tox.ini", "pytest.ini", ".python-version"
            )
            Language.HTML, Language.CSS -> listOf(
                "postcss.config.js", "stylelint.config.js"
            )
            else -> emptyList()
        }
        
        val allConfigFiles = baseConfigFiles + languageConfigFiles
        
        // Look for config files in project root and source file's directory
        val dirsToSearch = setOf(projectRoot, sourceFile.parent).filterNotNull()
        
        runReadAction {
            dirsToSearch.forEach { dir ->
                allConfigFiles.forEach { configName ->
                    val configFile = dir.findChild(configName)
                    if (configFile != null && configFile.exists()) {
                        configFiles.add(configFile)
                    }
                }
            }
        }
        
        return configFiles.distinct()
    }
    
    /**
     * Scans the project VFS for files whose last-modified timestamp is within the past [hours].
     * Does not require VCS data, making it fast.
     */
    fun findRecentChanges(project: Project, hours: Int = 24): List<VirtualFile> {
        val recentFiles = mutableListOf<VirtualFile>()
        val cutoffTime = System.currentTimeMillis() - (hours * 60 * 60 * 1000)
        
        runReadAction {
            val projectFileIndex = ProjectFileIndex.getInstance(project)
            
            projectFileIndex.iterateContent { file ->
                if (!file.isDirectory && file.timeStamp > cutoffTime) {
                    recentFiles.add(file)
                }
                true
            }
        }
        
        return recentFiles
    }
    
    /**
     * Returns other files that live in the same directory/package as [sourceFile]. For JS/TS
     * components we include CSS modules or barrel files as well.
     */
    fun findCurrentPackageFiles(sourceFile: VirtualFile): List<VirtualFile> {
        val packageFiles = mutableListOf<VirtualFile>()
        val parent = sourceFile.parent ?: return emptyList()
        val language = detectLanguage(sourceFile)
        
        // Base behavior: all files in same directory
        runReadAction {
            parent.children.forEach { file ->
                if (!file.isDirectory && file != sourceFile) {
                    packageFiles.add(file)
                }
            }
        }
        
        // Language-specific additions
        when (language) {
            Language.JAVASCRIPT, Language.TYPESCRIPT, Language.JSX, Language.TSX -> {
                runReadAction {
                    // Include barrel exports (index.js/ts files)
                    val indexFiles = listOf("index.js", "index.ts", "index.jsx", "index.tsx")
                    indexFiles.forEach { indexFile ->
                        val indexVirtualFile = parent.findChild(indexFile)
                        if (indexVirtualFile != null && indexVirtualFile != sourceFile) {
                            packageFiles.add(indexVirtualFile)
                        }
                    }
                    
                    // Include related CSS/SCSS files for components
                    if (sourceFile.extension in listOf("jsx", "tsx")) {
                        val baseName = sourceFile.nameWithoutExtension
                        val styleExtensions = listOf("css", "scss", "sass", "module.css", "module.scss")
                        styleExtensions.forEach { ext ->
                            val styleFile = parent.findChild("$baseName.$ext")
                            if (styleFile != null) {
                                packageFiles.add(styleFile)
                            }
                        }
                    }
                }
            }
            Language.JAVA, Language.KOTLIN -> {
                // Already includes all files in package - no additional logic needed
            }
            else -> {
                // Default behavior already applied above
            }
        }
        
        return packageFiles.distinct()
    }
    
    /** Resolves direct imports/requires for a file via PSI + text heuristics. */
    fun findDirectImports(project: Project, sourceFile: VirtualFile): List<VirtualFile> {
        if (sourceFile.isDirectory) return emptyList()
        
        val psiFile = ReadAction.compute<PsiFile?, Exception> {
            PsiManager.getInstance(project).findFile(sourceFile)
        } ?: return emptyList()
        
        return findImportedFiles(project, psiFile, transitive = false)
    }
    
    /**
     * Computes the transitive closure of imports starting from [sourceFile], bounded by the
     * configured max depth and max results per file.
     */
    fun findTransitiveImports(project: Project, sourceFile: VirtualFile): List<VirtualFile> {
        if (sourceFile.isDirectory) return emptyList()
        
        val psiFile = ReadAction.compute<PsiFile?, Exception> {
            PsiManager.getInstance(project).findFile(sourceFile)
        } ?: return emptyList()
        
        return findImportedFiles(project, psiFile, transitive = true)
    }
    
    private fun findImportedFiles(project: Project, psiFile: PsiFile, transitive: Boolean): List<VirtualFile> {
        val allFoundFiles = mutableSetOf<VirtualFile>()
        data class Node(val file: VirtualFile, val depth: Int)
        val queue = ArrayDeque<Node>()
        
        // Initial file
        queue.add(Node(psiFile.virtualFile, 0))
        // Use a single set to track visited files to prevent cycles and redundant processing
        val visited = mutableSetOf(psiFile.virtualFile)

        while (queue.isNotEmpty()) {
            val (currentFile, currentDepth) = queue.removeFirst()
            
            // Don't add the initial file to the results, only its dependencies
            if (currentFile != psiFile.virtualFile) {
                allFoundFiles.add(currentFile)
                if (allFoundFiles.size >= Config.importsMaxPerFile) {
                    // Cap reached for this file's import traversal
                    break
                }
            }

            // If not transitive, we only process the initial file
            if (!transitive && currentFile != psiFile.virtualFile) {
                continue
            }

            // Respect transitive max depth (depth 0 is the starting file)
            if (transitive && currentDepth >= Config.transitiveMaxDepth) {
                continue
            }

            val currentPsiFile = ReadAction.compute<PsiFile?, Exception> {
                PsiManager.getInstance(project).findFile(currentFile)
            } ?: continue

            val directImports = extractImportsFromFile(project, currentPsiFile)
            
            directImports.forEach { importedFile ->
                // Add to queue only if it has never been visited before
                if (visited.add(importedFile)) {
                    queue.add(Node(importedFile, currentDepth + 1))
                }
            }
        }
        
        return allFoundFiles.toList()
    }
    
    private fun extractImportsFromFile(project: Project, psiFile: PsiFile): List<VirtualFile> {
        val imports = mutableSetOf<VirtualFile>()
        // Accessing PSI text must be done under a read action
        val fileText = ReadAction.compute<String, Exception> { psiFile.text }
        val language = detectLanguage(psiFile.virtualFile)

        // Prefer PSI-level import resolution when possible
        when (language) {
            Language.JAVA -> imports.addAll(resolveJavaImportsPsi(psiFile))
            Language.KOTLIN -> imports.addAll(resolveKotlinImportsIfAvailable(project, psiFile))
            else -> {}
        }
        
        // Enhanced language-specific import patterns
        val importPatterns = when (language) {
            Language.JAVA, Language.KOTLIN -> listOf(
                Regex("""import\s+(?:static\s+)?([a-zA-Z_][a-zA-Z0-9_.]*)\s*;?"""),
                Regex("""from\s+([a-zA-Z_][a-zA-Z0-9_.]*)\s+import""") // Kotlin multiplatform
            )
            Language.JAVASCRIPT, Language.TYPESCRIPT, Language.JSX, Language.TSX -> listOf(
                // ES6 imports
                Regex("""import\s+.*?from\s+['"]([^'"]+)['"]"""),
                Regex("""import\s+['"]([^'"]+)['"]"""),
                // CommonJS
                Regex("""require\s*\(\s*['"]([^'"]+)['"]\s*\)"""),
                // Dynamic imports
                Regex("""import\s*\(\s*['"]([^'"]+)['"]\s*\)"""),
                // Re-exports
                Regex("""export\s+.*?from\s+['"]([^'"]+)['"]"""),
                // CSS imports in JS
                Regex("""import\s+['"]([^'"]+\.css)['"]"""),
                Regex("""import\s+['"]([^'"]+\.scss)['"]"""),
                Regex("""import\s+['"]([^'"]+\.sass)['"]""")
            )
            Language.PYTHON -> listOf(
                Regex("""from\s+([a-zA-Z_][a-zA-Z0-9_.]*)\s+import"""),
                Regex("""import\s+([a-zA-Z_][a-zA-Z0-9_.]*(?:\s*,\s*[a-zA-Z_][a-zA-Z0-9_.]*)*?)(?:\s|$)""")
            )
            Language.HTML -> listOf(
                // Script tags
                Regex("""<script[^>]+src\s*=\s*['"]([^'"]+)['"]"""),
                // Link tags for CSS
                Regex("""<link[^>]+href\s*=\s*['"]([^'"]+\.css)['"]"""),
                // Import maps
                Regex(""""([^"]+)":\s*['"]([^'"]+)['"]""")
            )
            Language.CSS -> listOf(
                // CSS @import
                Regex("""@import\s+['"]([^'"]+)['"]"""),
                Regex("""@import\s+url\s*\(\s*['"]?([^'"]+)['"]?\s*\)""")
            )
            else -> emptyList()
        }
        
        importPatterns.forEach { pattern ->
            pattern.findAll(fileText).forEach { match ->
                val importPath = match.groupValues[1]
                val resolvedFile = resolveImportToFile(project, psiFile.virtualFile, importPath, language)
                if (resolvedFile != null) {
                    imports.add(resolvedFile)
                }
            }
        }

        return imports.toList()
    }

    private fun resolveJavaImportsPsi(psiFile: PsiFile): List<VirtualFile> {
        val jf = psiFile as? PsiJavaFile ?: return emptyList()
        return runReadAction {
            jf.importList?.allImportStatements?.mapNotNull { stmt ->
                stmt.importReference?.resolve()?.containingFile?.virtualFile
            } ?: emptyList()
        }
    }

    private fun resolveKotlinImportsIfAvailable(project: Project, psiFile: PsiFile): List<VirtualFile> {
        return try {
            // Be defensive: consider historical IDs in case of changes across IDE versions.
            val kotlinPluginIds = listOf("org.jetbrains.kotlin", "com.intellij.kotlin")
            val isKotlinEnabled = kotlinPluginIds.any { id ->
                PluginManagerCore.getPlugin(PluginId.getId(id))?.isEnabled == true
            }
            if (!isKotlinEnabled) return emptyList()

            // Service is registered only when Kotlin plugin is present (optional dependency)
            val resolver = try {
                // Use service() in a try-catch to avoid exceptions when not registered
                service<KotlinImportResolver>()
            } catch (_: Throwable) {
                null
            }
            if (resolver != null) resolver.resolveImports(project, psiFile) else emptyList()
        } catch (t: Throwable) {
            // Guard against any unexpected issues; fall back silently
            LOG.debug("Kotlin PSI resolution unavailable, falling back: ", t)
            emptyList()
        }
    }
    
    private fun resolveImportToFile(
        project: Project, 
        sourceFile: VirtualFile, 
        importPath: String,
        language: Language
    ): VirtualFile? {
        val projectRoots = ReadAction.compute<Array<VirtualFile>, Exception> {
            ProjectRootManager.getInstance(project).contentRoots
        }
        if (projectRoots.isEmpty()) return null

        return when (language) {
            Language.JAVASCRIPT, Language.TYPESCRIPT, Language.JSX, Language.TSX -> {
                projectRoots.firstNotNullOfOrNull { root ->
                    resolveJavaScriptImport(root, sourceFile, importPath)
                }
            }
            Language.JAVA, Language.KOTLIN -> {
                projectRoots.firstNotNullOfOrNull { root ->
                    resolveJavaKotlinImport(root, importPath)
                }
            }
            Language.PYTHON -> {
                projectRoots.firstNotNullOfOrNull { root ->
                    resolvePythonImport(root, importPath)
                }
            }
            Language.HTML -> resolveHtmlReference(sourceFile, importPath)
            Language.CSS -> resolveCssImport(sourceFile, importPath)
            else -> null
        }
    }
    
    private fun resolveJavaScriptImport(projectRoot: VirtualFile, sourceFile: VirtualFile, importPath: String): VirtualFile? {
        // 1. Relative imports (./file, ../file)
        if (importPath.startsWith("./") || importPath.startsWith("../")) {
            return ReadAction.compute<VirtualFile?, Exception> {
                val resolvedPath = sourceFile.parent.findFileByRelativePath(importPath)
                if (resolvedPath?.exists() == true) return@compute resolvedPath
                
                // Try with common extensions
                val extensions = listOf(".js", ".ts", ".jsx", ".tsx", ".json", ".mjs")
                extensions.forEach { ext ->
                    val withExt = sourceFile.parent.findFileByRelativePath("$importPath$ext")
                    if (withExt?.exists() == true) return@compute withExt
                }
                
                // Try index files in directories
                val indexFiles = listOf("/index.js", "/index.ts", "/index.jsx", "/index.tsx")
                indexFiles.forEach { indexFile ->
                    val indexPath = sourceFile.parent.findFileByRelativePath("$importPath$indexFile")
                    if (indexPath?.exists() == true) return@compute indexPath
                }
                null
            }
        }
        
        // 2. Absolute imports from src
        return ReadAction.compute<VirtualFile?, Exception> {
            val srcPaths = Config.jsModuleRoots
            srcPaths.forEach { srcDir ->
                val srcRoot = projectRoot.findChild(srcDir)
                if (srcRoot != null) {
                    val extensions = listOf("", ".js", ".ts", ".jsx", ".tsx", ".json")
                    extensions.forEach { ext ->
                        val candidate = srcRoot.findFileByRelativePath("$importPath$ext")
                        if (candidate?.exists() == true) return@compute candidate
                    }
                    
                    // Try index files
                    val indexFiles = Config.indexFileNames.map { "/$it" }
                    indexFiles.forEach { indexFile ->
                        val indexPath = srcRoot.findFileByRelativePath("$importPath$indexFile")
                        if (indexPath?.exists() == true) return@compute indexPath
                    }
                }
            }
            
            // 3. Next.js specific patterns
            if (importPath.startsWith("@/")) {
                val cleanPath = importPath.substring(2)
                val srcRoot = projectRoot.findChild("src") ?: projectRoot
                val extensions = listOf("", ".js", ".ts", ".jsx", ".tsx")
                extensions.forEach { ext ->
                    val candidate = srcRoot.findFileByRelativePath("$cleanPath$ext")
                    if (candidate?.exists() == true) return@compute candidate
                }
            }
            
            null
        }
    }
    
    private fun resolveJavaKotlinImport(projectRoot: VirtualFile, importPath: String): VirtualFile? {
        return ReadAction.compute<VirtualFile?, Exception> {
            val pathVariations = listOf(
                importPath.replace(".", "/"),
                importPath.replace(".", "/").replace("_", ""),
                importPath
            )
            
            pathVariations.forEach { path ->
                val srcDirs = listOf("src/main/java", "src/main/kotlin", "src", "")
                srcDirs.forEach { srcDir ->
                    val fullPath = if (srcDir.isEmpty()) path else "$srcDir/$path"
                    val extensions = listOf("", ".java", ".kt")
                    
                    extensions.forEach { ext ->
                        val candidate = projectRoot.findFileByRelativePath("$fullPath$ext")
                        if (candidate?.exists() == true) return@compute candidate
                    }
                }
            }
            
            null
        }
    }
    
    private fun resolvePythonImport(projectRoot: VirtualFile, importPath: String): VirtualFile? {
        return ReadAction.compute<VirtualFile?, Exception> {
            val pathVariations = listOf(
                importPath.replace(".", "/") + ".py",
                "$importPath.py",
                "${importPath.replace(".", "/")}/__init__.py"
            )
            
            pathVariations.forEach { path ->
                val candidate = projectRoot.findFileByRelativePath(path)
                if (candidate?.exists() == true) return@compute candidate
            }
            
            null
        }
    }
    
    private fun resolveHtmlReference(sourceFile: VirtualFile, referencePath: String): VirtualFile? {
        // Relative path resolution for HTML
        return ReadAction.compute<VirtualFile?, Exception> {
            if (referencePath.startsWith("/")) {
                // Absolute path from project root
                sourceFile.fileSystem.findFileByPath(referencePath)
            } else {
                // Relative path from current file
                sourceFile.parent.findFileByRelativePath(referencePath)
            }
        }
    }
    
    private fun resolveCssImport(sourceFile: VirtualFile, importPath: String): VirtualFile? {
        // CSS @import resolution
        return ReadAction.compute<VirtualFile?, Exception> {
            val extensions = listOf("", ".css", ".scss", ".sass")
            
            extensions.forEach { ext ->
                val candidate = sourceFile.parent.findFileByRelativePath("$importPath$ext")
                if (candidate?.exists() == true) return@compute candidate
            }
            
            null
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
            "py" -> Language.PYTHON
            "html", "htm" -> Language.HTML
            "css" -> Language.CSS
            "scss", "sass" -> Language.CSS
            else -> Language.UNKNOWN
        }
    }
    
    private enum class Language {
        JAVA, KOTLIN, JAVASCRIPT, TYPESCRIPT, JSX, TSX, 
        PYTHON, HTML, CSS, UNKNOWN
    }
}
