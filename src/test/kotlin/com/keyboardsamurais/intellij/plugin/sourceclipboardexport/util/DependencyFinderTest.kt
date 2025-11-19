package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util

import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiReference
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.AbstractQuery
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class DependencyFinderTest {

    private val project = mockk<Project>(relaxed = true)

    @BeforeEach
    fun setup() {
        mockkStatic(ReadAction::class)
        every { ReadAction.compute(any<ThrowableComputable<*, *>>()) } answers {
            val computable = it.invocation.args[0] as ThrowableComputable<Any?, Exception>
            computable.compute()
        }
        mockkStatic(ApplicationManager::class)
        val app = mockk<Application>(relaxed = true)
        every { ApplicationManager.getApplication() } returns app
        every { app.isUnitTestMode } returns true

        mockkStatic(GlobalSearchScope::class)
        mockkStatic(PsiManager::class)
        mockkStatic(ReferencesSearch::class)
        mockkStatic(com.intellij.openapi.progress.ProgressManager::class)
        justRun { com.intellij.openapi.progress.ProgressManager.checkCanceled() }
    }

    @AfterEach
    fun tearDown() {
        DependencyFinder.clearCaches()
        unmockkAll()
    }

    @Test
    fun `findDependents returns verified PSI hits`() = runBlocking {
        val sourceFile = mockVirtualFile("Foo.kt").apply { every { nameWithoutExtension } returns "Foo" }
        val candidateFile = mockVirtualFile("Bar.kt").apply {
            every { extension } returns "kt"
            every { length } returns 128L
        }
        val scope = mockk<GlobalSearchScope>(relaxed = true)
        every { GlobalSearchScope.filesScope(project, any<Collection<VirtualFile>>()) } returns scope

        val psiManager = mockk<PsiManager>()
        every { PsiManager.getInstance(project) } returns psiManager

        val psiClassElement = mockk<PsiNameIdentifierOwner>(relaxed = true)
        val sourcePsi = mockk<PsiFile>(relaxed = true)
        every { sourcePsi.virtualFile } returns sourceFile
        every { sourcePsi.children } returns arrayOf(psiClassElement as PsiElement)
        every { sourcePsi.accept(any()) } answers { }
        every { psiManager.findFile(sourceFile) } returns sourcePsi

        val referenceElement = mockk<PsiElement>(relaxed = true)
        val referencePsiFile = mockk<PsiFile>(relaxed = true)
        every { referencePsiFile.virtualFile } returns candidateFile
        every { referenceElement.containingFile } returns referencePsiFile
        val psiReference = mockk<PsiReference>()
        every { psiReference.element } returns referenceElement

        val query = object : AbstractQuery<PsiReference>() {
            override fun processResults(consumer: com.intellij.util.Processor<in PsiReference>): Boolean {
                consumer.process(psiReference)
                return true
            }
        }
        every { ReferencesSearch.search(any(), scope, false) } returns query

        val dependents = DependencyFinder.runPsiVerificationPhase(
                files = arrayOf(sourceFile),
                project = project,
                candidatesToProcess = setOf(candidateFile),
                alreadyIncludedFiles = emptySet(),
                maxResults = 10
        )

        assertEquals(setOf(candidateFile), dependents)
    }

    private fun mockVirtualFile(name: String): VirtualFile {
        val vf = mockk<VirtualFile>(relaxed = true)
        every { vf.name } returns name
        every { vf.extension } returns name.substringAfterLast('.', "kt")
        every { vf.nameWithoutExtension } returns name.substringBeforeLast('.', name)
        every { vf.path } returns "/repo/$name"
        every { vf.isDirectory } returns false
        every { vf.exists() } returns true
        every { vf.isValid } returns true
        every { vf.length } returns 64L
        return vf
    }
}
