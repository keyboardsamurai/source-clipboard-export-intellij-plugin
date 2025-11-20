package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util

import com.intellij.lang.Language
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class ResourceFinderTest {

    private val project = mockk<Project>(relaxed = true)
    private val scope = mockk<GlobalSearchScope>(relaxed = true)
    private val psiManager = mockk<PsiManager>(relaxed = true)

    @BeforeEach
    fun setup() {
        mockkStatic(ReadAction::class)
        mockkStatic(GlobalSearchScope::class)
        mockkStatic(PsiManager::class)
        mockkStatic(FilenameIndex::class)

        every { GlobalSearchScope.projectScope(project) } returns scope
        every { PsiManager.getInstance(project) } returns psiManager
        every { FilenameIndex.getVirtualFilesByName(any<String>(), any<GlobalSearchScope>()) } answers { emptySet() }
        every { ReadAction.compute(any<ThrowableComputable<Any?, Exception>>()) } answers {
            val computable = firstArg<ThrowableComputable<Any?, Exception>>()
            computable.compute()
        }
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `findRelatedResources detects spring templates`() = runBlocking {
        val controllerFile = mockSourceFile("java")
        val psiFile = mockPsiFile(
            """
            @Controller
            class DemoController {
                fun index(): String {
                    return "home/index"
                }
            }
            """.trimIndent(),
            controllerFile,
            languageId = "JAVA"
        )

        val templateFile = mockk<VirtualFile>(relaxed = true)
        every { templateFile.isValid } returns true
        every { templateFile.extension } returns "html"

        every { psiManager.findFile(controllerFile) } returns psiFile
        every { FilenameIndex.getVirtualFilesByName("home/index.html", scope) } returns setOf(templateFile)

        val resources = ResourceFinder.findRelatedResources(arrayOf(controllerFile), project)

        assertContains(resources, templateFile)
    }

    @Test
    fun `findRelatedResources collects react styles and literal resources`() = runBlocking {
        val reactFile = mockSourceFile("tsx")
        val psiFile = mockPsiFile(
            """
            import React from 'react';
            import './Widget.css';
            export const Widget = () => <div />;
            const dataPath = "static/ui/widget.json";
            """.trimIndent(),
            reactFile,
            languageId = "TypeScript"
        )

        val parentFolder = mockk<VirtualFile>(relaxed = true)
        every { reactFile.parent } returns parentFolder
        every { parentFolder.findFileByRelativePath("static/ui/widget.json") } returns null

        val styleFile = mockk<VirtualFile>(relaxed = true)
        every { styleFile.isValid } returns true
        every { styleFile.extension } returns "css"

        val jsonFile = mockk<VirtualFile>(relaxed = true)
        every { jsonFile.isValid } returns true
        every { jsonFile.extension } returns "json"

        every { psiManager.findFile(reactFile) } returns psiFile
        every { FilenameIndex.getVirtualFilesByName("Widget.css", scope) } returns setOf(styleFile)
        every { FilenameIndex.getVirtualFilesByName("widget.json", scope) } returns setOf(jsonFile)

        val resources = ResourceFinder.findRelatedResources(arrayOf(reactFile), project)

        assertEquals(setOf(styleFile, jsonFile), resources)
    }

    @Test
    fun `findRelatedResources resolves angular templates and styles`() = runBlocking {
        val tsFile = mockSourceFile("ts")
        val psiFile = mockPsiFile(
            """
            @Component({
                templateUrl: 'Widget.html',
                styleUrls: ['Widget.css', './Widget.theme.css']
            })
            export class WidgetComponent {}
            """.trimIndent(),
            tsFile,
            languageId = "TypeScript"
        )

        val parentFolder = mockk<VirtualFile>(relaxed = true)
        every { tsFile.parent } returns parentFolder

        val template = mockk<VirtualFile>(relaxed = true)
        val style1 = mockk<VirtualFile>(relaxed = true)
        val style2 = mockk<VirtualFile>(relaxed = true)
        every { template.isValid } returns true
        every { style1.isValid } returns true
        every { style2.isValid } returns true
        every { parentFolder.findFileByRelativePath("Widget.html") } returns template
        every { parentFolder.findFileByRelativePath("Widget.css") } returns style1
        every { parentFolder.findFileByRelativePath("./Widget.theme.css") } returns style2

        every { psiManager.findFile(tsFile) } returns psiFile

        val resources = ResourceFinder.findRelatedResources(arrayOf(tsFile), project)

        assertEquals(setOf(template, style1, style2), resources)
    }

    private fun mockSourceFile(extension: String): VirtualFile {
        val file = mockk<VirtualFile>(relaxed = true)
        every { file.isDirectory } returns false
        every { file.isValid } returns true
        every { file.extension } returns extension
        every { file.parent } returns mockk(relaxed = true)
        return file
    }

    private fun mockPsiFile(
        text: String,
        backingFile: VirtualFile,
        languageId: String
    ): PsiFile {
        val psiFile = mockk<PsiFile>(relaxed = true)
        val language = mockk<Language>(relaxed = true)
        every { language.id } returns languageId
        every { psiFile.text } returns text
        every { psiFile.language } returns language
        every { psiFile.virtualFile } returns backingFile
        return psiFile
    }
}
