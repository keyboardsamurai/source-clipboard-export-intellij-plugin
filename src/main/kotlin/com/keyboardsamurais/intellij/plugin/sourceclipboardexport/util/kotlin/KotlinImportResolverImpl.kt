package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.kotlin

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.psi.KtFile

/**
 * Kotlin PSI implementation of [KotlinImportResolver]. Only loaded when the Kotlin plugin is
 * present via optional dependency wiring.
 */
class KotlinImportResolverImpl : KotlinImportResolver {
    /**
     * Scans Kotlin import directives and resolves them into physical files using [JavaPsiFacade].
     * Wildcard imports are skipped because they would explode the result list.
     */
    override fun resolveImports(project: Project, psiFile: PsiFile): List<VirtualFile> {
        val kf = psiFile as? KtFile ?: return emptyList()

        val fqns = runReadAction {
            kf.importDirectives.mapNotNull { it.importPath?.fqName?.asString() }
        }
        val scope = GlobalSearchScope.allScope(project)
        val facade = JavaPsiFacade.getInstance(project)
        return fqns.mapNotNull { fqn ->
            if (fqn.endsWith(".*")) return@mapNotNull null
            val psiClass = runReadAction { facade.findClass(fqn, scope) }
            psiClass?.containingFile?.virtualFile
        }
    }
}
