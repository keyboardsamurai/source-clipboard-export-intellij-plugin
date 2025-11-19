package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.resources.AngularResourceStrategy
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.resources.NamingConventionResourceStrategy
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.resources.ReactResourceStrategy
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.resources.ResourceDetectionStrategy
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.resources.SpringResourceStrategy
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.resources.StringLiteralResourceStrategy
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.resources.VueResourceStrategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Locates resource files (templates, styles, configs, etc.) associated with a set of source files.
 * The heavy lifting is done by pluggable [ResourceDetectionStrategy] instances so heuristics can
 * evolve independently of the calling code.
 */
object ResourceFinder {

    private val TEMPLATE_EXTENSIONS = setOf("html", "htm", "jsp", "ftl", "vm", "vue", "hbs", "ejs", "pug", "jade")
    private val STYLE_EXTENSIONS = setOf("css", "scss", "sass", "less", "styl", "stylus")
    private val RESOURCE_EXTENSIONS = TEMPLATE_EXTENSIONS + STYLE_EXTENSIONS + setOf("json", "xml", "yaml", "yml", "properties")

    private val strategies: List<ResourceDetectionStrategy> = listOf(
        SpringResourceStrategy(TEMPLATE_EXTENSIONS),
        ReactResourceStrategy(STYLE_EXTENSIONS),
        VueResourceStrategy(STYLE_EXTENSIONS),
        AngularResourceStrategy(),
        StringLiteralResourceStrategy(RESOURCE_EXTENSIONS),
        NamingConventionResourceStrategy(RESOURCE_EXTENSIONS)
    )

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

                strategies.forEach { strategy ->
                    if (strategy.supports(psiFile)) {
                        resources.addAll(strategy.findResources(file, psiFile, project, projectScope))
                    }
                }
            }
        }

        resources
    }
}
