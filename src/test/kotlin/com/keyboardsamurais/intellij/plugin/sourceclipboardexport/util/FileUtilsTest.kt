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

    // ========================================================================
    // UTF-8 Regression Tests - Prevent false positives for valid UTF-8 text
    // ========================================================================

    @Test
    fun `isLikelyBinaryContent returns false for UTF-8 text with accented characters`() {
        // French text with accented chars: é (C3 A9), è (C3 A8), ç (C3 A7)
        val content = "Café crème - une boisson française délicieuse"
        val file = createMockFileWithContent(content.toByteArray(Charsets.UTF_8))
        assertFalse(FileUtils.isLikelyBinaryContent(file))
    }

    @Test
    fun `isLikelyBinaryContent returns false for UTF-8 text with em-dash and smart quotes`() {
        // Em-dash (E2 80 94), smart quotes (E2 80 9C, E2 80 9D)
        val content = "He said \u2014 \u201cHello, World!\u201d \u2014 and smiled."
        val file = createMockFileWithContent(content.toByteArray(Charsets.UTF_8))
        assertFalse(FileUtils.isLikelyBinaryContent(file))
    }

    @Test
    fun `isLikelyBinaryContent returns false for Japanese UTF-8 text`() {
        // Japanese hiragana - all 3-byte UTF-8 sequences
        val content = "こんにちは世界！これはテストです。"
        val file = createMockFileWithContent(content.toByteArray(Charsets.UTF_8))
        assertFalse(FileUtils.isLikelyBinaryContent(file))
    }

    @Test
    fun `isLikelyBinaryContent returns false for Chinese UTF-8 text`() {
        // Chinese characters - 3-byte UTF-8 sequences
        val content = "你好世界！这是一个测试文件。"
        val file = createMockFileWithContent(content.toByteArray(Charsets.UTF_8))
        assertFalse(FileUtils.isLikelyBinaryContent(file))
    }

    @Test
    fun `isLikelyBinaryContent returns false for emoji content`() {
        // 4-byte UTF-8 sequences (emoji)
        val content = "Status: \uD83D\uDE00 Done \uD83D\uDC4D Great work! \uD83C\uDF89"
        val file = createMockFileWithContent(content.toByteArray(Charsets.UTF_8))
        assertFalse(FileUtils.isLikelyBinaryContent(file))
    }

    @Test
    fun `isLikelyBinaryContent returns false for mixed UTF-8 PRD-style document`() {
        // Simulates a PRD document with various UTF-8 characters
        val content = """
            # Product Requirements Document

            ## Overview

            This document describes the "next-generation" features — including:

            • Smart quotes and em-dashes
            • Bullet points (•, ○, ■)
            • Currency symbols: €, £, ¥
            • Accented names: François, José, Müller

            ## Requirements

            1. Support für internationale Zeichen
            2. 日本語サポート
            3. Emoji support 🎉
        """.trimIndent()
        val file = createMockFileWithContent(content.toByteArray(Charsets.UTF_8))
        assertFalse(FileUtils.isLikelyBinaryContent(file))
    }

    @Test
    fun `isLikelyBinaryContent returns false for pure ASCII text`() {
        val content = "Hello, World!\nThis is a test file.\n\tWith tabs and newlines.\r\nAnd CRLF."
        val file = createMockFileWithContent(content.toByteArray())
        assertFalse(FileUtils.isLikelyBinaryContent(file))
    }

    @Test
    fun `isLikelyBinaryContent returns false for empty file`() {
        val file = createMockFileWithContent(ByteArray(0))
        assertFalse(FileUtils.isLikelyBinaryContent(file))
    }

    // ========================================================================
    // BOM Handling Tests
    // ========================================================================

    @Test
    fun `isLikelyBinaryContent handles UTF-8 BOM correctly`() {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val content = "Hello World - text after UTF-8 BOM".toByteArray()
        val file = createMockFileWithContent(bom + content)
        assertFalse(FileUtils.isLikelyBinaryContent(file))
    }

    @Test
    fun `isLikelyBinaryContent detects UTF-16 LE as binary due to null bytes`() {
        // UTF-16 LE BOM + "Hi" in UTF-16 LE (contains null bytes)
        val bom = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
        // UTF-16 LE "Hi" = H(48 00) i(69 00)
        val content = byteArrayOf(0x48, 0x00, 0x69, 0x00)
        val file = createMockFileWithContent(bom + content)
        assertTrue(FileUtils.isLikelyBinaryContent(file)) // Contains null bytes
    }

    @Test
    fun `isLikelyBinaryContent detects UTF-16 BE as binary due to null bytes`() {
        // UTF-16 BE BOM + "Hi" in UTF-16 BE (contains null bytes)
        val bom = byteArrayOf(0xFE.toByte(), 0xFF.toByte())
        // UTF-16 BE "Hi" = H(00 48) i(00 69)
        val content = byteArrayOf(0x00, 0x48, 0x00, 0x69)
        val file = createMockFileWithContent(bom + content)
        assertTrue(FileUtils.isLikelyBinaryContent(file)) // Contains null bytes
    }

    // ========================================================================
    // Binary Detection Tests - Ensure true positives for actual binary content
    // ========================================================================

    @Test
    fun `isLikelyBinaryContent returns true for null bytes in text`() {
        val content = byteArrayOf(0x48, 0x65, 0x6C, 0x00, 0x6F) // "Hel\0o"
        val file = createMockFileWithContent(content)
        assertTrue(FileUtils.isLikelyBinaryContent(file))
    }

    @Test
    fun `isLikelyBinaryContent returns true for PNG header`() {
        // PNG magic bytes: 89 50 4E 47 0D 0A 1A 0A
        val pngHeader = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        )
        val file = createMockFileWithContent(pngHeader + ByteArray(100))
        assertTrue(FileUtils.isLikelyBinaryContent(file))
    }

    @Test
    fun `isLikelyBinaryContent returns true for high ratio of control characters`() {
        // 50% control characters (byte 0x01, not tab/LF/CR)
        val content = ByteArray(100) { i -> if (i % 2 == 0) 0x01 else 0x41 }
        val file = createMockFileWithContent(content)
        assertTrue(FileUtils.isLikelyBinaryContent(file))
    }

    @Test
    fun `isLikelyBinaryContent returns true for many orphan continuation bytes`() {
        // 100 bytes of orphan continuation bytes (0x80) - clearly not valid UTF-8
        val content = ByteArray(100) { 0x80.toByte() }
        val file = createMockFileWithContent(content)
        assertTrue(FileUtils.isLikelyBinaryContent(file))
    }

    @Test
    fun `isLikelyBinaryContent returns true for invalid UTF-8 lead bytes`() {
        // Invalid lead bytes (0xF8-0xFF are not valid UTF-8 lead bytes)
        val content = ByteArray(100) { 0xF8.toByte() }
        val file = createMockFileWithContent(content)
        assertTrue(FileUtils.isLikelyBinaryContent(file))
    }

    // ========================================================================
    // Edge Cases - Invalid UTF-8 sequences that should be handled gracefully
    // ========================================================================

    @Test
    fun `isLikelyBinaryContent handles single orphan continuation byte gracefully`() {
        // Single orphan byte in mostly ASCII - should still be text (below 30% threshold)
        val content = byteArrayOf(0x80.toByte()) + "Hello World, this is valid text!".toByteArray()
        val file = createMockFileWithContent(content)
        assertFalse(FileUtils.isLikelyBinaryContent(file))
    }

    @Test
    fun `isLikelyBinaryContent handles truncated UTF-8 sequence gracefully`() {
        // Truncated 2-byte sequence (C3 without continuation) in mostly ASCII
        val content = byteArrayOf(0xC3.toByte()) + "Hello World, this is valid text!".toByteArray()
        val file = createMockFileWithContent(content)
        assertFalse(FileUtils.isLikelyBinaryContent(file))
    }

    @Test
    fun `isLikelyBinaryContent handles truncated 3-byte UTF-8 sequence`() {
        // Truncated 3-byte sequence (E2 80 without third byte) in mostly ASCII
        val content = byteArrayOf(0xE2.toByte(), 0x80.toByte()) + "Hello World text here".toByteArray()
        val file = createMockFileWithContent(content)
        assertFalse(FileUtils.isLikelyBinaryContent(file)) // Below 30% threshold
    }

    @Test
    fun `isLikelyBinaryContent handles file at end of valid UTF-8 sequence`() {
        // Valid UTF-8 sequence at end of file
        val content = "Hello World".toByteArray() + byteArrayOf(0xC3.toByte(), 0xA9.toByte()) // "é"
        val file = createMockFileWithContent(content)
        assertFalse(FileUtils.isLikelyBinaryContent(file))
    }

    // ========================================================================
    // Helper Functions
    // ========================================================================

    private fun createMockFileWithContent(content: ByteArray): VirtualFile {
        val file = mockk<VirtualFile>()
        every { file.length } returns content.size.toLong()
        every { file.inputStream } returns ByteArrayInputStream(content)
        every { file.path } returns "/mock/test/file.txt"
        return file
    }
}
