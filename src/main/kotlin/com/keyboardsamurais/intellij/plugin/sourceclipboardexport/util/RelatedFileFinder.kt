package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope

object RelatedFileFinder {
    
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
            val files = FilenameIndex.getVirtualFilesByName(pattern, GlobalSearchScope.projectScope(project))
            testFiles.addAll(files)
        }
        
        // Special handling for __tests__ folders (React/Next.js convention)
        if (language in listOf(Language.JAVASCRIPT, Language.TYPESCRIPT, Language.JSX, Language.TSX)) {
            findTestsInTestFolders(project, sourceFile, testFiles)
        }
        
        return testFiles.distinct()
    }
    
    private fun findTestsInTestFolders(project: Project, sourceFile: VirtualFile, testFiles: MutableList<VirtualFile>) {
        val fileName = sourceFile.nameWithoutExtension
        val parentDir = sourceFile.parent ?: return
        
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
    
    fun findConfigFiles(project: Project, sourceFile: VirtualFile): List<VirtualFile> {
        val configFiles = mutableListOf<VirtualFile>()
        val projectRootManager = ProjectRootManager.getInstance(project)
        val projectRoot = projectRootManager.contentRoots.firstOrNull() ?: return emptyList()
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
        
        dirsToSearch.forEach { dir ->
            allConfigFiles.forEach { configName ->
                val configFile = dir.findChild(configName)
                if (configFile != null && configFile.exists()) {
                    configFiles.add(configFile)
                }
            }
        }
        
        return configFiles.distinct()
    }
    
    fun findRecentChanges(project: Project, hours: Int = 24): List<VirtualFile> {
        val recentFiles = mutableListOf<VirtualFile>()
        val cutoffTime = System.currentTimeMillis() - (hours * 60 * 60 * 1000)
        
        val projectFileIndex = ProjectFileIndex.getInstance(project)
        
        projectFileIndex.iterateContent { file ->
            if (!file.isDirectory && file.timeStamp > cutoffTime) {
                recentFiles.add(file)
            }
            true
        }
        
        return recentFiles
    }
    
    fun findCurrentPackageFiles(project: Project, sourceFile: VirtualFile): List<VirtualFile> {
        val packageFiles = mutableListOf<VirtualFile>()
        val parent = sourceFile.parent ?: return emptyList()
        val language = detectLanguage(sourceFile)
        
        // Base behavior: all files in same directory
        parent.children.forEach { file ->
            if (!file.isDirectory && file != sourceFile) {
                packageFiles.add(file)
            }
        }
        
        // Language-specific additions
        when (language) {
            Language.JAVASCRIPT, Language.TYPESCRIPT, Language.JSX, Language.TSX -> {
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
            Language.JAVA, Language.KOTLIN -> {
                // Already includes all files in package - no additional logic needed
            }
            else -> {
                // Default behavior already applied above
            }
        }
        
        return packageFiles.distinct()
    }
    
    fun findDirectImports(project: Project, sourceFile: VirtualFile): List<VirtualFile> {
        if (sourceFile.isDirectory) return emptyList()
        
        val psiManager = PsiManager.getInstance(project)
        val psiFile = psiManager.findFile(sourceFile) ?: return emptyList()
        
        return findImportedFiles(project, psiFile, transitive = false)
    }
    
    fun findTransitiveImports(project: Project, sourceFile: VirtualFile): List<VirtualFile> {
        if (sourceFile.isDirectory) return emptyList()
        
        val psiManager = PsiManager.getInstance(project)
        val psiFile = psiManager.findFile(sourceFile) ?: return emptyList()
        
        return findImportedFiles(project, psiFile, transitive = true)
    }
    
    private fun findImportedFiles(project: Project, psiFile: PsiFile, transitive: Boolean): List<VirtualFile> {
        val importedFiles = mutableSetOf<VirtualFile>()
        val processed = mutableSetOf<VirtualFile>()
        val toProcess = mutableListOf(psiFile.virtualFile)
        
        while (toProcess.isNotEmpty()) {
            val currentFile = toProcess.removeAt(0)
            if (currentFile in processed) continue
            processed.add(currentFile)
            
            val currentPsiFile = PsiManager.getInstance(project).findFile(currentFile) ?: continue
            val directImports = extractImportsFromFile(project, currentPsiFile)
            
            directImports.forEach { importedFile ->
                if (importedFile !in importedFiles) {
                    importedFiles.add(importedFile)
                    
                    // Add to processing queue for transitive imports
                    if (transitive && importedFile !in processed) {
                        toProcess.add(importedFile)
                    }
                }
            }
        }
        
        return importedFiles.toList()
    }
    
    private fun extractImportsFromFile(project: Project, psiFile: PsiFile): List<VirtualFile> {
        val imports = mutableListOf<VirtualFile>()
        val fileText = psiFile.text
        val language = detectLanguage(psiFile.virtualFile)
        
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
        
        return imports
    }
    
    private fun resolveImportToFile(
        project: Project, 
        sourceFile: VirtualFile, 
        importPath: String,
        language: Language
    ): VirtualFile? {
        val projectRootManager = ProjectRootManager.getInstance(project)
        val projectRoot = projectRootManager.contentRoots.firstOrNull() ?: return null
        
        // Language-specific resolution strategies
        return when (language) {
            Language.JAVASCRIPT, Language.TYPESCRIPT, Language.JSX, Language.TSX -> 
                resolveJavaScriptImport(projectRoot, sourceFile, importPath)
            Language.JAVA, Language.KOTLIN -> 
                resolveJavaKotlinImport(projectRoot, importPath)
            Language.PYTHON ->
                resolvePythonImport(projectRoot, importPath)
            Language.HTML ->
                resolveHtmlReference(sourceFile, importPath)
            Language.CSS ->
                resolveCssImport(sourceFile, importPath)
            else -> null
        }
    }
    
    private fun resolveJavaScriptImport(projectRoot: VirtualFile, sourceFile: VirtualFile, importPath: String): VirtualFile? {
        // 1. Relative imports (./file, ../file)
        if (importPath.startsWith("./") || importPath.startsWith("../")) {
            val resolvedPath = sourceFile.parent.findFileByRelativePath(importPath)
            if (resolvedPath?.exists() == true) return resolvedPath
            
            // Try with common extensions
            val extensions = listOf(".js", ".ts", ".jsx", ".tsx", ".json", ".mjs")
            extensions.forEach { ext ->
                val withExt = sourceFile.parent.findFileByRelativePath("$importPath$ext")
                if (withExt?.exists() == true) return withExt
            }
            
            // Try index files in directories
            val indexFiles = listOf("/index.js", "/index.ts", "/index.jsx", "/index.tsx")
            indexFiles.forEach { indexFile ->
                val indexPath = sourceFile.parent.findFileByRelativePath("$importPath$indexFile")
                if (indexPath?.exists() == true) return indexPath
            }
        }
        
        // 2. Absolute imports from src
        val srcPaths = listOf("src", "app", "pages", "components", "lib", "utils")
        srcPaths.forEach { srcDir ->
            val srcRoot = projectRoot.findChild(srcDir)
            if (srcRoot != null) {
                val extensions = listOf("", ".js", ".ts", ".jsx", ".tsx", ".json")
                extensions.forEach { ext ->
                    val candidate = srcRoot.findFileByRelativePath("$importPath$ext")
                    if (candidate?.exists() == true) return candidate
                }
                
                // Try index files
                val indexFiles = listOf("/index.js", "/index.ts", "/index.jsx", "/index.tsx")
                indexFiles.forEach { indexFile ->
                    val indexPath = srcRoot.findFileByRelativePath("$importPath$indexFile")
                    if (indexPath?.exists() == true) return indexPath
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
                if (candidate?.exists() == true) return candidate
            }
        }
        
        return null
    }
    
    private fun resolveJavaKotlinImport(projectRoot: VirtualFile, importPath: String): VirtualFile? {
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
                    if (candidate?.exists() == true) return candidate
                }
            }
        }
        
        return null
    }
    
    private fun resolvePythonImport(projectRoot: VirtualFile, importPath: String): VirtualFile? {
        val pathVariations = listOf(
            importPath.replace(".", "/") + ".py",
            "$importPath.py",
            "${importPath.replace(".", "/")}/__init__.py"
        )
        
        pathVariations.forEach { path ->
            val candidate = projectRoot.findFileByRelativePath(path)
            if (candidate?.exists() == true) return candidate
        }
        
        return null
    }
    
    private fun resolveHtmlReference(sourceFile: VirtualFile, referencePath: String): VirtualFile? {
        // Relative path resolution for HTML
        return if (referencePath.startsWith("/")) {
            // Absolute path from project root
            sourceFile.fileSystem.findFileByPath(referencePath)
        } else {
            // Relative path from current file
            sourceFile.parent.findFileByRelativePath(referencePath)
        }
    }
    
    private fun resolveCssImport(sourceFile: VirtualFile, importPath: String): VirtualFile? {
        // CSS @import resolution
        val extensions = listOf("", ".css", ".scss", ".sass")
        
        extensions.forEach { ext ->
            val candidate = sourceFile.parent.findFileByRelativePath("$importPath$ext")
            if (candidate?.exists() == true) return candidate
        }
        
        return null
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