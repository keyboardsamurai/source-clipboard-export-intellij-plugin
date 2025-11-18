package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util

import com.intellij.openapi.vfs.VirtualFile
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExtendedTestFinderTest {

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
}
