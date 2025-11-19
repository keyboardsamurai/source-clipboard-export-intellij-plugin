package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.resources

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope

/**
 * Detects resource files related to a given PSI element (framework specific or generic heuristics).
 */
interface ResourceDetectionStrategy {
    fun supports(psiFile: PsiFile): Boolean = true

    fun findResources(
        file: VirtualFile,
        psiFile: PsiFile,
        project: Project,
        scope: GlobalSearchScope
    ): Collection<VirtualFile>
}

class SpringResourceStrategy(
    private val templateExtensions: Set<String>
) : ResourceDetectionStrategy {
    private val viewNamePattern = Regex("""return\s+["']([^"']+)["']""")

    override fun supports(psiFile: PsiFile): Boolean {
        val text = psiFile.text
        return psiFile.language.id in setOf("JAVA", "kotlin") &&
            (text.contains("@Controller") || text.contains("@RestController"))
    }

    override fun findResources(
        file: VirtualFile,
        psiFile: PsiFile,
        project: Project,
        scope: GlobalSearchScope
    ): Collection<VirtualFile> {
        val resources = mutableSetOf<VirtualFile>()
        viewNamePattern.findAll(psiFile.text).forEach { match ->
            val viewName = match.groupValues[1]
            templateExtensions.forEach { ext ->
                resources.addAll(FilenameIndex.getVirtualFilesByName("$viewName.$ext", scope))
            }
        }
        return resources
    }
}

class ReactResourceStrategy(
    private val styleExtensions: Set<String>
) : ResourceDetectionStrategy {
    override fun supports(psiFile: PsiFile): Boolean {
        val ext = psiFile.virtualFile.extension?.lowercase() ?: ""
        val text = psiFile.text
        return ext in setOf("jsx", "tsx", "js", "ts") &&
            (text.contains("import React") || text.contains("from 'react'") ||
                text.contains("from \"react\"") || text.contains("React.Component"))
    }

    override fun findResources(
        file: VirtualFile,
        psiFile: PsiFile,
        project: Project,
        scope: GlobalSearchScope
    ): Collection<VirtualFile> {
        val resources = mutableSetOf<VirtualFile>()
        val baseName = file.nameWithoutExtension
        styleExtensions.forEach { ext ->
            resources.addAll(FilenameIndex.getVirtualFilesByName("$baseName.$ext", scope))
            resources.addAll(FilenameIndex.getVirtualFilesByName("$baseName.module.$ext", scope))
        }
        return resources
    }
}

class VueResourceStrategy(
    private val styleExtensions: Set<String>
) : ResourceDetectionStrategy {
    override fun supports(psiFile: PsiFile): Boolean {
        val ext = psiFile.virtualFile.extension?.lowercase() ?: ""
        val text = psiFile.text
        return ext == "vue" || text.contains("Vue.component") || text.contains("export default {")
    }

    override fun findResources(
        file: VirtualFile,
        psiFile: PsiFile,
        project: Project,
        scope: GlobalSearchScope
    ): Collection<VirtualFile> {
        val resources = mutableSetOf<VirtualFile>()
        val baseName = file.nameWithoutExtension
        styleExtensions.forEach { ext ->
            resources.addAll(FilenameIndex.getVirtualFilesByName("$baseName.$ext", scope))
        }
        return resources
    }
}

class AngularResourceStrategy : ResourceDetectionStrategy {
    private val templateUrlPattern = Regex("""templateUrl\s*:\s*['"]([^'"]+)['"]""")
    private val styleUrlsBlockPattern = Regex("""styleUrls\s*:\s*\[(.*?)\]""", setOf(RegexOption.DOT_MATCHES_ALL))
    private val quotedPathPattern = Regex("""['"]([^'"]+)['"]""")

    override fun supports(psiFile: PsiFile): Boolean {
        val text = psiFile.text
        return psiFile.language.id == "TypeScript" && text.contains("@Component")
    }

    override fun findResources(
        file: VirtualFile,
        psiFile: PsiFile,
        project: Project,
        scope: GlobalSearchScope
    ): Collection<VirtualFile> {
        val resources = mutableSetOf<VirtualFile>()
        val templateMatch = templateUrlPattern.find(psiFile.text)
        if (templateMatch != null) {
            val templatePath = templateMatch.groupValues[1]
            psiFile.virtualFile.parent.findFileByRelativePath(templatePath)?.let { templateFile ->
                if (templateFile.isValid) {
                    resources.add(templateFile)
                }
            }
        }

        val styleBlock = styleUrlsBlockPattern.find(psiFile.text)?.groupValues?.getOrNull(1)
        if (!styleBlock.isNullOrBlank()) {
            quotedPathPattern.findAll(styleBlock).forEach { match ->
                val stylePath = match.groupValues[1]
                psiFile.virtualFile.parent.findFileByRelativePath(stylePath)?.let { styleFile ->
                    if (styleFile.isValid) {
                        resources.add(styleFile)
                    }
                }
            }
        }

        return resources
    }
}

class StringLiteralResourceStrategy(
    private val resourceExtensions: Set<String>
) : ResourceDetectionStrategy {
    override fun findResources(
        file: VirtualFile,
        psiFile: PsiFile,
        project: Project,
        scope: GlobalSearchScope
    ): Collection<VirtualFile> {
        val resources = mutableSetOf<VirtualFile>()
        val pattern = Regex("""["']([^"']+\.(?:${resourceExtensions.joinToString("|")}))["']""")
        pattern.findAll(psiFile.text).forEach { match ->
            val resourcePath = match.groupValues[1]
            val relative = psiFile.virtualFile.parent.findFileByRelativePath(resourcePath)
            if (relative != null && relative.isValid) {
                resources.add(relative)
            } else {
                val fileName = resourcePath.substringAfterLast('/')
                resources.addAll(FilenameIndex.getVirtualFilesByName(fileName, scope))
            }
        }
        return resources
    }
}

class NamingConventionResourceStrategy(
    private val resourceExtensions: Set<String>
) : ResourceDetectionStrategy {
    override fun findResources(
        file: VirtualFile,
        psiFile: PsiFile,
        project: Project,
        scope: GlobalSearchScope
    ): Collection<VirtualFile> {
        val resources = mutableSetOf<VirtualFile>()
        val baseName = file.nameWithoutExtension
        resourceExtensions.forEach { ext ->
            if (ext != file.extension) {
                resources.addAll(FilenameIndex.getVirtualFilesByName("$baseName.$ext", scope))
            }
        }
        return resources
    }
}
