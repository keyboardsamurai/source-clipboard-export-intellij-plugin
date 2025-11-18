package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util

import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.VirtualFile
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse

class ExtendedTestFinderTest {

    @AfterEach
    fun cleanup() {
        unmockkAll()
    }

    @Test
    fun `isTestFile matches directories and naming patterns`() {
        val method = ExtendedTestFinder::class.java.getDeclaredMethod(
            "isTestFile",
            VirtualFile::class.java
        ).apply { isAccessible = true }

        val testFile = mockk<VirtualFile>()
        every { testFile.nameWithoutExtension } returns "UserServiceTest"
        every { testFile.path } returns "/repo/module/src/test/java/UserServiceTest.kt"

        val prodFile = mockk<VirtualFile>()
        every { prodFile.nameWithoutExtension } returns "UserService"
        every { prodFile.path } returns "/repo/module/src/main/java/UserService.kt"

        assertTrue(method.invoke(ExtendedTestFinder, testFile) as Boolean)
        assertFalse(method.invoke(ExtendedTestFinder, prodFile) as Boolean)
    }

    @Test
    fun `isTestResource requires test directory markers`() {
        val method = ExtendedTestFinder::class.java.getDeclaredMethod(
            "isTestResource",
            VirtualFile::class.java
        ).apply { isAccessible = true }

        val resourceFile = mockk<VirtualFile>()
        every { resourceFile.path } returns "/repo/src/test/resources/test-data/config.yaml"
        every { resourceFile.isDirectory } returns false
        every { resourceFile.name } returns "config.yaml"

        val unrelated = mockk<VirtualFile>()
        every { unrelated.path } returns "/repo/src/main/resources/application.yaml"
        every { unrelated.isDirectory } returns false
        every { unrelated.name } returns "application.yaml"

        assertTrue(method.invoke(ExtendedTestFinder, resourceFile) as Boolean)
        assertFalse(method.invoke(ExtendedTestFinder, unrelated) as Boolean)
    }

    @Test
    fun `findTestsInTestDirectories adds matching siblings`() {
        val project = mockk<Project>(relaxed = true)
        val baseName = "UserService"
        val extension = "kt"
        val collector = mutableSetOf<VirtualFile>()

        val sibling = mockk<VirtualFile>()
        every { sibling.isDirectory } returns false
        every { sibling.extension } returns "kt"
        every { sibling.nameWithoutExtension } returns "UserServiceHelper"
        every { sibling.path } returns "/repo/src/test/kotlin/UserServiceHelper.kt"

        val parent = mockk<VirtualFile>()
        every { parent.children } returns arrayOf(sibling)

        val source = mockk<VirtualFile>()
        every { source.parent } returns parent

        mockkStatic(ApplicationManager::class)
        val app = mockk<Application>(relaxed = true)
        every { ApplicationManager.getApplication() } returns app
        every { app.runReadAction(any<Computable<*>>()) } answers {
            (it.invocation.args[0] as Computable<*>).compute()
        }

        mockkStatic(ProjectFileIndex::class)
        val index = mockk<ProjectFileIndex>()
        every { ProjectFileIndex.getInstance(project) } returns index
        every { index.iterateContent(any()) } answers {
            val iterator = it.invocation.args[0] as ContentIterator
            iterator.processFile(sibling)
            true
        }

        val method = ExtendedTestFinder::class.java.getDeclaredMethod(
            "findTestsInTestDirectories",
            Project::class.java,
            String::class.java,
            String::class.java,
            MutableSet::class.java
        ).apply { isAccessible = true }

        method.invoke(ExtendedTestFinder, project, baseName, extension, collector)

        assertTrue(collector.contains(sibling))
    }
}
