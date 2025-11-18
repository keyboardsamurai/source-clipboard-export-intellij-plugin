package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util

import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.ProjectScope
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RelatedFileFinderTest {

    private val project = mockk<Project>(relaxed = true)

    @BeforeEach
    fun setup() {
        mockkStatic(ReadAction::class)
        every { ReadAction.compute(any<ThrowableComputable<Collection<VirtualFile>, Exception>>()) } answers { call ->
            @Suppress("UNCHECKED_CAST")
            (call.invocation.args[0] as ThrowableComputable<Collection<VirtualFile>, Exception>).compute()
        }

        mockkStatic(ApplicationManager::class)
        mockkStatic(ProjectScope::class)
        val app = mockk<Application>(relaxed = true)
        every { ApplicationManager.getApplication() } returns app
        every { app.runReadAction(any<Computable<*>>()) } answers { call ->
            (call.invocation.args[0] as Computable<*>).compute()
        }
        every { ProjectScope.getProjectScope(project) } returns mockk(relaxed = true)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `findTestFiles returns matches from filename index and test folders`() {
        val sourceFile = mockFile("Component.tsx", extension = "tsx")
        val testMatch = mockFile("Component.test.tsx")
        val folderTestFile = mockFile("Component.tsx")
        val testsFolder = mockFolder("__tests__", children = arrayOf(folderTestFile))
        val parent = mockFolder("components", children = arrayOf(sourceFile, testsFolder))
        every { sourceFile.parent } returns parent
        every { parent.findChild("__tests__") } returns testsFolder
        every { testsFolder.children } returns arrayOf(folderTestFile)

        mockkStatic(FilenameIndex::class)
        every {
            FilenameIndex.getVirtualFilesByName(any<String>(), any<GlobalSearchScope>())
        } answers {
            val name = firstArg<String>()
            if (name == "Component.test.js" || name == "Component.test.ts" || name == "Component.test.jsx" || name == "Component.test.tsx") {
                setOf(testMatch)
            } else emptySet()
        }

        val files = RelatedFileFinder.findTestFiles(project, sourceFile)

        assertTrue(files.contains(folderTestFile))
    }

    @Test
    fun `findCurrentPackageFiles includes index and style resources`() {
        val jsxFile = mockFile("Widget.tsx", extension = "tsx")
        val indexFile = mockFile("index.ts")
        val styleFile = mockFile("Widget.module.css")
        val siblingFile = mockFile("Widget.test.tsx")
        val parent = mockFolder("components", children = arrayOf(jsxFile, siblingFile, indexFile, styleFile))
        every { jsxFile.parent } returns parent
        every { parent.findChild("index.js") } returns null
        every { parent.findChild("index.ts") } returns indexFile
        every { parent.findChild("Widget.css") } returns null
        every { parent.findChild("Widget.scss") } returns null
        every { parent.findChild("Widget.sass") } returns null
        every { parent.findChild("Widget.module.css") } returns styleFile

        val result = RelatedFileFinder.findCurrentPackageFiles(project, jsxFile)

        assertTrue(result.contains(indexFile))
        assertTrue(result.contains(styleFile))
        assertTrue(result.contains(siblingFile))
    }

    @Test
    fun `findRecentChanges filters by timestamp`() {
        val recentFile = mockFile("Recent.kt")
        every { recentFile.timeStamp } returns System.currentTimeMillis()
        val oldFile = mockFile("Old.kt")
        every { oldFile.timeStamp } returns System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000

        mockkStatic(ProjectFileIndex::class)
        val index = mockk<ProjectFileIndex>()
        every { ProjectFileIndex.getInstance(project) } returns index
        every { index.iterateContent(any()) } answers {
            val iterator = firstArg<ContentIterator>()
            iterator.processFile(recentFile)
            iterator.processFile(oldFile)
            true
        }

        val files = RelatedFileFinder.findRecentChanges(project, hours = 24)

        assertEquals(listOf(recentFile), files)
    }

    private fun mockFile(name: String, extension: String? = name.substringAfterLast('.', "")): VirtualFile {
        val vf = mockk<VirtualFile>(relaxed = true)
        every { vf.name } returns name
        every { vf.extension } returns extension
        every { vf.nameWithoutExtension } returns name.substringBeforeLast('.', name)
        every { vf.isDirectory } returns false
        every { vf.path } returns "/repo/$name"
        return vf
    }

    private fun mockFolder(name: String, children: Array<VirtualFile>): VirtualFile {
        val vf = mockk<VirtualFile>(relaxed = true)
        every { vf.name } returns name
        every { vf.isDirectory } returns true
        every { vf.children } returns children
        every { vf.path } returns "/repo/$name"
        children.forEach { child ->
            every { child.parent } returns vf
        }
        return vf
    }
}
