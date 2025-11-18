package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util

import com.intellij.openapi.vfs.VirtualFile
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

class FileUtilsTest {

    @AfterEach
    fun cleanup() {
        unmockkAll()
    }

    @Test
    fun `generateDirectoryTree returns empty string for empty list`() {
        assertEquals("", FileUtils.generateDirectoryTree(emptyList(), true))
    }

    @Test
    fun `generateDirectoryTree generates correct tree with files included`() {
        val filePaths = listOf(
            "src/main/kotlin/com/example/App.kt",
            "src/main/resources/config.properties",
            "src/test/kotlin/com/example/AppTest.kt"
        )

        val result = FileUtils.generateDirectoryTree(filePaths, true)

        assertTrue(result.contains("App.kt"))
        assertTrue(result.contains("config.properties"))
        assertTrue(result.contains("AppTest.kt"))
    }

    @Test
    fun `generateDirectoryTree generates correct tree without files`() {
        val filePaths = listOf(
            "src/main/kotlin/com/example/App.kt",
            "src/test/kotlin/com/example/AppTest.kt"
        )

        val result = FileUtils.generateDirectoryTree(filePaths, false)

        assertTrue(result.contains("Directory structure"))
        assertFalse(result.contains("App.kt"))
    }

    @Test
    fun `readFileContent returns decoded text`() {
        val file = mockk<VirtualFile>()
        every { file.contentsToByteArray() } returns "body".toByteArray()
        every { file.charset } returns Charsets.UTF_8

        assertEquals("body", FileUtils.readFileContent(file))
    }

    @Test
    fun `isLikelyBinaryContent samples bytes`() {
        val textFile = mockk<VirtualFile>()
        every { textFile.length } returns 4
        every { textFile.inputStream } returns ByteArrayInputStream("text".toByteArray())

        val binaryFile = mockk<VirtualFile>()
        every { binaryFile.length } returns 4
        every { binaryFile.inputStream } returns ByteArrayInputStream(byteArrayOf(0x00, 0x10, 0x02, 0x03))

        assertFalse(FileUtils.isLikelyBinaryContent(textFile))
        assertTrue(FileUtils.isLikelyBinaryContent(binaryFile))
    }

    @Test
    fun `hasFilenamePrefix correctly identifies content with filename prefixes`() {
        assertTrue(FileUtils.hasFilenamePrefix("// filename: Foo.kt"))
        assertTrue(FileUtils.hasFilenamePrefix("# filename: Foo.py"))
        assertFalse(FileUtils.hasFilenamePrefix("class Example"))
    }

    @Test
    fun `getCommentPrefix returns defaults for unknown types`() {
        val kotlinFile = mockk<VirtualFile>()
        every { kotlinFile.extension } returns "kt"
        val unknownFile = mockk<VirtualFile>()
        every { unknownFile.extension } returns "xyz"

        assertEquals("// filename: ", FileUtils.getCommentPrefix(kotlinFile))
        assertEquals("// filename: ", FileUtils.getCommentPrefix(unknownFile))
    }

    @Test
    fun `isKnownBinaryExtension detects common binary types`() {
        val file = mockk<VirtualFile>()
        every { file.extension } returns "png"
        assertTrue(FileUtils.isKnownBinaryExtension(file))
    }
}
