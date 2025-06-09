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
        val extension = sourceFile.extension
        
        // Common test file naming patterns
        val testPatterns = listOf(
            "${fileName}Test.$extension",
            "${fileName}Tests.$extension", 
            "Test$fileName.$extension",
            "${fileName}Spec.$extension",
            "${fileName}IT.$extension", // Integration tests
            "${fileName}IntegrationTest.$extension"
        )
        
        testPatterns.forEach { pattern ->
            val files = FilenameIndex.getVirtualFilesByName(pattern, GlobalSearchScope.projectScope(project))
            testFiles.addAll(files)
        }
        
        return testFiles.distinct()
    }
    
    fun findConfigFiles(project: Project, sourceFile: VirtualFile): List<VirtualFile> {
        val configFiles = mutableListOf<VirtualFile>()
        val projectRootManager = ProjectRootManager.getInstance(project)
        val projectRoot = projectRootManager.contentRoots.firstOrNull() ?: return emptyList()
        
        // Common config file names
        val configFileNames = listOf(
            "package.json", "pom.xml", "build.gradle", "build.gradle.kts",
            "Cargo.toml", "requirements.txt", "pyproject.toml", "setup.py",
            "composer.json", "Gemfile", "go.mod", "CMakeLists.txt",
            "Dockerfile", "docker-compose.yml", ".env", "application.properties",
            "application.yml", "config.json", "tsconfig.json", "webpack.config.js"
        )
        
        // Look for config files in project root and source file's directory
        val dirsToSearch = setOf(projectRoot, sourceFile.parent).filterNotNull()
        
        dirsToSearch.forEach { dir ->
            configFileNames.forEach { configName ->
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
        
        // Get all files in the same directory
        parent.children.forEach { file ->
            if (!file.isDirectory && file != sourceFile) {
                packageFiles.add(file)
            }
        }
        
        return packageFiles
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
        val projectRootManager = ProjectRootManager.getInstance(project)
        val projectRoot = projectRootManager.contentRoots.firstOrNull() ?: return emptyList()
        val fileText = psiFile.text
        
        // Extract import statements based on file type
        val importPatterns = when (psiFile.fileType.name.lowercase()) {
            "java", "kotlin" -> listOf(
                Regex("""import\s+(?:static\s+)?([a-zA-Z_][a-zA-Z0-9_.]*)\s*;"""),
                Regex("""import\s+([a-zA-Z_][a-zA-Z0-9_.]*)\s*""")
            )
            "javascript", "typescript" -> listOf(
                Regex("""import\s+.*?from\s+['"]([^'"]+)['"]"""),
                Regex("""import\s+['"]([^'"]+)['"]"""),
                Regex("""require\s*\(\s*['"]([^'"]+)['"]\s*\)""")
            )
            "python" -> listOf(
                Regex("""from\s+([a-zA-Z_][a-zA-Z0-9_.]*)\s+import"""),
                Regex("""import\s+([a-zA-Z_][a-zA-Z0-9_.]*(?:\s*,\s*[a-zA-Z_][a-zA-Z0-9_.]*)*)""")
            )
            else -> emptyList()
        }
        
        importPatterns.forEach { pattern ->
            pattern.findAll(fileText).forEach { match ->
                val importPath = match.groupValues[1]
                val resolvedFile = resolveImportToFile(project, psiFile.virtualFile, importPath)
                if (resolvedFile != null) {
                    imports.add(resolvedFile)
                }
            }
        }
        
        return imports
    }
    
    private fun resolveImportToFile(project: Project, sourceFile: VirtualFile, importPath: String): VirtualFile? {
        val projectRootManager = ProjectRootManager.getInstance(project)
        val projectRoot = projectRootManager.contentRoots.firstOrNull() ?: return null
        
        // Try different resolution strategies based on import type
        
        // 1. Relative imports (./file, ../file)
        if (importPath.startsWith("./") || importPath.startsWith("../")) {
            val resolvedPath = sourceFile.parent.findFileByRelativePath(importPath)
            if (resolvedPath?.exists() == true) return resolvedPath
            
            // Try with common extensions
            val extensions = listOf(".js", ".ts", ".jsx", ".tsx", ".java", ".kt", ".py")
            extensions.forEach { ext ->
                val withExt = sourceFile.parent.findFileByRelativePath("$importPath$ext")
                if (withExt?.exists() == true) return withExt
            }
        }
        
        // 2. Package/module imports - convert to file path
        val pathVariations = listOf(
            importPath.replace(".", "/"),
            importPath.replace(".", "/").replace("_", ""),
            importPath
        )
        
        pathVariations.forEach { path ->
            // Try in source directories
            val srcDirs = listOf("src/main/java", "src/main/kotlin", "src", "lib", "")
            srcDirs.forEach { srcDir ->
                val fullPath = if (srcDir.isEmpty()) path else "$srcDir/$path"
                val extensions = listOf("", ".java", ".kt", ".js", ".ts", ".py", ".tsx", ".jsx")
                
                extensions.forEach { ext ->
                    val candidate = projectRoot.findFileByRelativePath("$fullPath$ext")
                    if (candidate?.exists() == true) return candidate
                }
            }
        }
        
        return null
    }
}