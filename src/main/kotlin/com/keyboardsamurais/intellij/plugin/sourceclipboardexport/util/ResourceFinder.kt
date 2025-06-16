package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Utility class for finding related resources like HTML templates, CSS files, and static assets
 */
object ResourceFinder {
    
    private val TEMPLATE_EXTENSIONS = setOf("html", "htm", "jsp", "ftl", "vm", "vue", "hbs", "ejs", "pug", "jade")
    private val STYLE_EXTENSIONS = setOf("css", "scss", "sass", "less", "styl", "stylus")
    private val RESOURCE_EXTENSIONS = TEMPLATE_EXTENSIONS + STYLE_EXTENSIONS + setOf("json", "xml", "yaml", "yml", "properties")
    
    /**
     * Find all related resource files for the given source files
     * @param files The source files to find resources for
     * @param project The current project
     * @return Set of related resource files
     */
    suspend fun findRelatedResources(files: Array<VirtualFile>, project: Project): Set<VirtualFile> = withContext(Dispatchers.Default) {
        val resources = mutableSetOf<VirtualFile>()
        val projectScope = GlobalSearchScope.projectScope(project)
        
        ReadAction.compute<Unit, Exception> {
            val psiManager = PsiManager.getInstance(project)
            
            for (file in files) {
                if (!file.isValid || file.isDirectory) continue
                
                val psiFile = psiManager.findFile(file) ?: continue
                
                // Framework-specific resource detection
                when {
                    isSpringController(psiFile) -> {
                        resources.addAll(findSpringResources(psiFile, project, projectScope))
                    }
                    isReactComponent(psiFile) -> {
                        resources.addAll(findReactResources(file, project, projectScope))
                    }
                    isVueComponent(psiFile) -> {
                        resources.addAll(findVueResources(file, project, projectScope))
                    }
                    isAngularComponent(psiFile) -> {
                        resources.addAll(findAngularResources(psiFile, project, projectScope))
                    }
                }
                
                // Generic resource detection via string literals
                resources.addAll(findResourcesByStringLiterals(psiFile, project, projectScope))
                
                // Pattern-based resource detection
                resources.addAll(findResourcesByNamingConvention(file, project, projectScope))
            }
        }
        
        resources
    }
    
    private fun isSpringController(psiFile: PsiFile): Boolean {
        val text = psiFile.text
        return psiFile.language.id in setOf("JAVA", "kotlin") &&
               (text.contains("@Controller") || text.contains("@RestController"))
    }
    
    private fun findSpringResources(psiFile: PsiFile, project: Project, scope: GlobalSearchScope): Set<VirtualFile> {
        val resources = mutableSetOf<VirtualFile>()
        
        // Find view names in return statements
        val viewNamePattern = Regex("""return\s+["']([^"']+)["']""")
        val matches = viewNamePattern.findAll(psiFile.text)
        
        for (match in matches) {
            val viewName = match.groupValues[1]
            
            // Search for matching template files using modern API
            for (ext in TEMPLATE_EXTENSIONS) {
                val templateFiles = FilenameIndex.getVirtualFilesByName("$viewName.$ext", scope)
                resources.addAll(templateFiles)
            }
        }
        
        return resources
    }
    
    private fun isReactComponent(psiFile: PsiFile): Boolean {
        val text = psiFile.text
        val ext = psiFile.virtualFile.extension?.lowercase() ?: ""
        return ext in setOf("jsx", "tsx", "js", "ts") &&
               (text.contains("import React") || text.contains("from 'react'") || 
                text.contains("from \"react\"") || text.contains("React.Component"))
    }
    
    private fun findReactResources(file: VirtualFile, project: Project, scope: GlobalSearchScope): Set<VirtualFile> {
        val resources = mutableSetOf<VirtualFile>()
        val baseName = file.nameWithoutExtension
        
        // Look for CSS modules using modern API
        for (ext in STYLE_EXTENSIONS) {
            val styleFiles = FilenameIndex.getVirtualFilesByName("$baseName.$ext", scope)
            resources.addAll(styleFiles)
            
            // Also check for .module.css pattern
            val moduleFiles = FilenameIndex.getVirtualFilesByName("$baseName.module.$ext", scope)
            resources.addAll(moduleFiles)
        }
        
        return resources
    }
    
    private fun isVueComponent(psiFile: PsiFile): Boolean {
        val ext = psiFile.virtualFile.extension?.lowercase() ?: ""
        return ext == "vue" || (psiFile.text.contains("Vue.component") || psiFile.text.contains("export default {"))
    }
    
    private fun findVueResources(file: VirtualFile, project: Project, scope: GlobalSearchScope): Set<VirtualFile> {
        val resources = mutableSetOf<VirtualFile>()
        
        // Vue single-file components include everything, but check for external files
        val baseName = file.nameWithoutExtension
        
        // Look for separate style files using modern API
        for (ext in STYLE_EXTENSIONS) {
            val styleFiles = FilenameIndex.getVirtualFilesByName("$baseName.$ext", scope)
            resources.addAll(styleFiles)
        }
        
        return resources
    }
    
    private fun isAngularComponent(psiFile: PsiFile): Boolean {
        val text = psiFile.text
        return psiFile.language.id == "TypeScript" &&
               text.contains("@Component")
    }
    
    private fun findAngularResources(psiFile: PsiFile, project: Project, scope: GlobalSearchScope): Set<VirtualFile> {
        val resources = mutableSetOf<VirtualFile>()
        
        // Extract templateUrl and styleUrls from @Component decorator
        val templateUrlPattern = Regex("""templateUrl\s*:\s*['"]([^'"]+)['"]""")
        val styleUrlsPattern = Regex("""styleUrls\s*:\s*\[\s*['"]([^'"]+)['"]""")
        
        val templateMatch = templateUrlPattern.find(psiFile.text)
        if (templateMatch != null) {
            val templatePath = templateMatch.groupValues[1]
            val templateFile = ReadAction.compute<VirtualFile?, Exception> {
                psiFile.virtualFile.parent.findFileByRelativePath(templatePath)
            }
            if (templateFile != null && templateFile.isValid) {
                resources.add(templateFile)
            }
        }
        
        val styleMatches = styleUrlsPattern.findAll(psiFile.text)
        for (match in styleMatches) {
            val stylePath = match.groupValues[1]
            val styleFile = ReadAction.compute<VirtualFile?, Exception> {
                psiFile.virtualFile.parent.findFileByRelativePath(stylePath)
            }
            if (styleFile != null && styleFile.isValid) {
                resources.add(styleFile)
            }
        }
        
        return resources
    }
    
    private fun findResourcesByStringLiterals(psiFile: PsiFile, project: Project, scope: GlobalSearchScope): Set<VirtualFile> {
        val resources = mutableSetOf<VirtualFile>()
        
        // Find string literals that look like resource paths
        val resourcePattern = Regex("""["']([^"']+\.(?:${RESOURCE_EXTENSIONS.joinToString("|")}))["']""")
        val matches = resourcePattern.findAll(psiFile.text)
        
        for (match in matches) {
            val resourcePath = match.groupValues[1]
            
            // Try to resolve relative to current file
            val resourceFile = ReadAction.compute<VirtualFile?, Exception> {
                psiFile.virtualFile.parent.findFileByRelativePath(resourcePath)
            }
            if (resourceFile != null && resourceFile.isValid) {
                resources.add(resourceFile)
            } else {
                // Try to find by name using modern API
                val fileName = resourcePath.substringAfterLast('/')
                val foundFiles = ReadAction.compute<Collection<VirtualFile>, Exception> {
                    FilenameIndex.getVirtualFilesByName(fileName, scope)
                }
                resources.addAll(foundFiles)
            }
        }
        
        return resources
    }
    
    private fun findResourcesByNamingConvention(file: VirtualFile, project: Project, scope: GlobalSearchScope): Set<VirtualFile> {
        val resources = mutableSetOf<VirtualFile>()
        val baseName = file.nameWithoutExtension
        
        // Look for resources with same base name using modern API
        for (ext in RESOURCE_EXTENSIONS) {
            if (ext != file.extension) {
                val relatedFiles = ReadAction.compute<Collection<VirtualFile>, Exception> {
                    FilenameIndex.getVirtualFilesByName("$baseName.$ext", scope)
                }
                resources.addAll(relatedFiles)
            }
        }
        
        return resources
    }
}