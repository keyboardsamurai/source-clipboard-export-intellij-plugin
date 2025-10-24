package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.kotlin

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile

/**
 * Provides Kotlin PSI-based import resolution when the Kotlin plugin is available.
 * This interface does not depend on Kotlin PSI types to keep the base plugin decoupled.
 */
interface KotlinImportResolver {
    fun resolveImports(project: Project, psiFile: PsiFile): List<VirtualFile>
}

