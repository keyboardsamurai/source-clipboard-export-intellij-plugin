package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.kotlin

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile

/**
 * Provides Kotlin PSI-based import resolution when the Kotlin plugin is available. Registered as an
 * optional service (see `META-INF/kotlin.xml`) so core code can call through without taking a hard
 * dependency on Kotlin PSI classes.
 */
interface KotlinImportResolver {
    /**
     * Resolves import directives in [psiFile] to real files by leveraging Kotlin's light classes.
     *
     * @param project owning project
     * @param psiFile Kotlin PSI file whose imports should be resolved
     * @return list of virtual files backing those imports; may be empty
     */
    fun resolveImports(project: Project, psiFile: PsiFile): List<VirtualFile>
}
