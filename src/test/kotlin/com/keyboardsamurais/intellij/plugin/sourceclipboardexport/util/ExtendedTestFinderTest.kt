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
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
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

    @Test
    fun `findAllRelatedTests aggregates direct matches resources and utilities`() {
        val project = mockk<Project>(relaxed = true)
        val sourceFile = mockk<VirtualFile>()
        every { sourceFile.isDirectory } returns false
        every { sourceFile.nameWithoutExtension } returns "UserService"
        every { sourceFile.extension } returns "kt"

        mockkStatic(GlobalSearchScope::class)
        val scope = mockk<GlobalSearchScope>(relaxed = true)
        every { GlobalSearchScope.projectScope(project) } returns scope

        mockkStatic(ReadAction::class)
        every { ReadAction.compute(any<ThrowableComputable<Collection<VirtualFile>, Exception>>()) } answers {
            val computable = it.invocation.args[0] as ThrowableComputable<Collection<VirtualFile>, Exception>
            computable.compute()
        }

        mockkStatic(ApplicationManager::class)
        val app = mockk<Application>(relaxed = true)
        every { ApplicationManager.getApplication() } returns app
        every { app.runReadAction(any<Computable<*>>()) } answers {
            val computable = it.invocation.args[0] as Computable<*>
            computable.compute()
        }

        mockkStatic(ProjectFileIndex::class)
        val index = mockk<ProjectFileIndex>()
        every { ProjectFileIndex.getInstance(project) } returns index

        val dirTest = mockFile("/repo/src/test/kotlin/UserServiceSpec.kt", "UserServiceSpec")
        val resourceFile = mockFile("/repo/src/test/resources/test-data/config.yml", "config")
        val otherFile = mockFile("/repo/src/main/java/Other.kt", "Other")
        every { index.iterateContent(any()) } answers {
            val iterator = it.invocation.args[0] as ContentIterator
            listOf(dirTest, resourceFile, otherFile).forEach { vf -> iterator.processFile(vf) }
            true
        }

        mockkStatic(FilenameIndex::class)
        val directTest = mockFile("/repo/src/test/kotlin/UserServiceTest.kt", "UserServiceTest")
        val utilFile = mockFile("/repo/tests/TestUtils.kt", "TestUtils")
        every {
            FilenameIndex.getVirtualFilesByName(any<String>(), eq(scope))
        } answers {
            val name = it.invocation.args[0] as String
            when (name) {
                "UserServiceTest.kt" -> setOf(directTest)
                "TestUtil*.kt" -> setOf(utilFile)
                else -> emptySet()
            }
        }

        val tests = ExtendedTestFinder.findAllRelatedTests(project, arrayOf(sourceFile))

        assertTrue(tests.contains(directTest))
        assertTrue(tests.contains(dirTest))
        assertTrue(tests.contains(resourceFile))
        assertTrue(tests.contains(utilFile))
    }

    @Test
    fun `findAllTestFiles scans entire project index`() {
        val project = mockk<Project>(relaxed = true)
        mockkStatic(ApplicationManager::class)
        val app = mockk<Application>(relaxed = true)
        every { ApplicationManager.getApplication() } returns app
        every { app.runReadAction(any<Computable<*>>()) } answers {
            val computable = it.invocation.args[0] as Computable<*>
            computable.compute()
        }

        mockkStatic(ProjectFileIndex::class)
        val index = mockk<ProjectFileIndex>()
        every { ProjectFileIndex.getInstance(project) } returns index
        val testFile = mockFile("/repo/src/integrationTest/kotlin/SmokeIT.kt", "SmokeIT")
        val prodFile = mockFile("/repo/src/main/kotlin/Service.kt", "Service")
        every { index.iterateContent(any()) } answers {
            val iterator = it.invocation.args[0] as ContentIterator
            iterator.processFile(testFile)
            iterator.processFile(prodFile)
            true
        }

        val allTests = ExtendedTestFinder.findAllTestFiles(project)

        assertEquals(setOf(testFile), allTests)
    }

    private fun mockFile(path: String, nameWithoutExt: String): VirtualFile {
        val vf = mockk<VirtualFile>(relaxed = true)
        every { vf.path } returns path
        every { vf.nameWithoutExtension } returns nameWithoutExt
        every { vf.extension } returns path.substringAfterLast('.', "kt")
        every { vf.isDirectory } returns false
        every { vf.name } returns path.substringAfterLast('/')
        return vf
    }
}
