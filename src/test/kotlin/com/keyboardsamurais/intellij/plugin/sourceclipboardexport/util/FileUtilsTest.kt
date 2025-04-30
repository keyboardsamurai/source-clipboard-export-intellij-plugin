package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util

import com.intellij.openapi.vfs.VirtualFile
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FileUtilsTest {

    @Test
    fun `generateDirectoryTree returns empty string for empty list`() {
        val result = FileUtils.generateDirectoryTree(emptyList(), true)
        assertEquals("", result)
    }

    @Test
    fun `generateDirectoryTree generates correct tree with files included`() {
        val filePaths = listOf(
            "src/main/kotlin/com/example/App.kt",
            "src/main/kotlin/com/example/utils/StringUtils.kt",
            "src/main/resources/config.properties",
            "src/test/kotlin/com/example/AppTest.kt"
        )

        val result = FileUtils.generateDirectoryTree(filePaths, true)

        println("Generated tree with files: $result")

        // Verify the result contains the expected structure
        assertTrue(result.contains("Directory structure"), "Should contain header")
        assertTrue(result.contains("src"), "Should contain src directory")
        assertTrue(result.contains("main"), "Should contain main directory")
        assertTrue(result.contains("kotlin"), "Should contain kotlin directory")
        assertTrue(result.contains("com"), "Should contain com directory")
        assertTrue(result.contains("example"), "Should contain example directory")
        assertTrue(result.contains("utils"), "Should contain utils directory")
        assertTrue(result.contains("App.kt"), "Should contain App.kt file")
        assertTrue(result.contains("StringUtils.kt"), "Should contain StringUtils.kt file")
        assertTrue(result.contains("resources"), "Should contain resources directory")
        assertTrue(result.contains("config.properties"), "Should contain config.properties file")
        assertTrue(result.contains("test"), "Should contain test directory")
        assertTrue(result.contains("AppTest.kt"), "Should contain AppTest.kt file")

        // Verify the tree structure formatting
        assertTrue(result.contains("├── ") || result.contains("└── "), "Should contain tree branch characters")
    }

    @Test
    fun `generateDirectoryTree generates correct tree without files`() {
        val filePaths = listOf(
            "src/main/kotlin/com/example/App.kt",
            "src/main/kotlin/com/example/utils/StringUtils.kt",
            "src/main/resources/config.properties",
            "src/test/kotlin/com/example/AppTest.kt"
        )

        val result = FileUtils.generateDirectoryTree(filePaths, false)

        println("Generated tree without files: $result")

        // Verify the result contains the expected structure
        assertTrue(result.contains("Directory structure"), "Should contain header")
        assertTrue(result.contains("src"), "Should contain src directory")
        assertTrue(result.contains("main"), "Should contain main directory")
        assertTrue(result.contains("kotlin"), "Should contain kotlin directory")
        assertTrue(result.contains("com"), "Should contain com directory")
        assertTrue(result.contains("example"), "Should contain example directory")
        assertTrue(result.contains("utils"), "Should contain utils directory")
        assertTrue(result.contains("resources"), "Should contain resources directory")
        assertTrue(result.contains("test"), "Should contain test directory")

        // Verify files are not included
        assertTrue(!result.contains("App.kt"), "Should not contain App.kt file")
        assertTrue(!result.contains("StringUtils.kt"), "Should not contain StringUtils.kt file")
        assertTrue(!result.contains("config.properties"), "Should not contain config.properties file")
        assertTrue(!result.contains("AppTest.kt"), "Should not contain AppTest.kt file")

        // Verify the tree structure formatting
        assertTrue(result.contains("├── ") || result.contains("└── "), "Should contain tree branch characters")
    }

    @Test
    fun `generateDirectoryTree handles complex directory structure correctly`() {
        val filePaths = listOf(
            "project/src/main/java/com/example/app/controllers/UserController.java",
            "project/src/main/java/com/example/app/models/User.java",
            "project/src/main/java/com/example/app/services/UserService.java",
            "project/src/main/resources/application.properties",
            "project/src/test/java/com/example/app/controllers/UserControllerTest.java",
            "project/build.gradle",
            "project/README.md"
        )

        val result = FileUtils.generateDirectoryTree(filePaths, true)

        println("Generated complex tree: $result")

        // Verify the result contains the expected structure and files
        assertTrue(result.contains("project"), "Should contain project directory")
        assertTrue(result.contains("src"), "Should contain src directory")
        assertTrue(result.contains("main"), "Should contain main directory")
        assertTrue(result.contains("java"), "Should contain java directory")
        assertTrue(result.contains("com"), "Should contain com directory")
        assertTrue(result.contains("example"), "Should contain example directory")
        assertTrue(result.contains("app"), "Should contain app directory")
        assertTrue(result.contains("controllers"), "Should contain controllers directory")
        assertTrue(result.contains("models"), "Should contain models directory")
        assertTrue(result.contains("services"), "Should contain services directory")
        assertTrue(result.contains("UserController.java"), "Should contain UserController.java file")
        assertTrue(result.contains("User.java"), "Should contain User.java file")
        assertTrue(result.contains("UserService.java"), "Should contain UserService.java file")
        assertTrue(result.contains("resources"), "Should contain resources directory")
        assertTrue(result.contains("application.properties"), "Should contain application.properties file")
        assertTrue(result.contains("test"), "Should contain test directory")
        assertTrue(result.contains("UserControllerTest.java"), "Should contain UserControllerTest.java file")
        assertTrue(result.contains("build.gradle"), "Should contain build.gradle file")
        assertTrue(result.contains("README.md"), "Should contain README.md file")
    }

    @Test
    fun `getCommentPrefix returns correct prefix for known file extensions`() {
        // Create mock files with different extensions
        val kotlinFile = mockk<VirtualFile>()
        val pythonFile = mockk<VirtualFile>()
        val htmlFile = mockk<VirtualFile>()
        val sqlFile = mockk<VirtualFile>()
        val unknownFile = mockk<VirtualFile>()

        // Set up mock file extensions
        every { kotlinFile.extension } returns "kt"
        every { pythonFile.extension } returns "py"
        every { htmlFile.extension } returns "html"
        every { sqlFile.extension } returns "sql"
        every { unknownFile.extension } returns "xyz"

        // Test the getCommentPrefix function
        assertEquals("// filename: ", FileUtils.getCommentPrefix(kotlinFile), "Kotlin files should use C-style comments")
        assertEquals("# filename: ", FileUtils.getCommentPrefix(pythonFile), "Python files should use hash comments")
        assertEquals("<!-- filename: -->", FileUtils.getCommentPrefix(htmlFile), "HTML files should use HTML comments")
        assertEquals("-- filename: ", FileUtils.getCommentPrefix(sqlFile), "SQL files should use SQL comments")
        assertEquals("// filename: ", FileUtils.getCommentPrefix(unknownFile), "Unknown extensions should use default C-style comments")
    }

    @Test
    fun `getCommentPrefix handles null extensions`() {
        val fileWithoutExtension = mockk<VirtualFile>()
        every { fileWithoutExtension.extension } returns null

        assertEquals("// filename: ", FileUtils.getCommentPrefix(fileWithoutExtension), 
            "Files without extensions should use default C-style comments")
    }

    @Test
    fun `hasFilenamePrefix correctly identifies content with filename prefixes`() {
        // Test with various comment styles
        assertTrue(FileUtils.hasFilenamePrefix("// filename: path/to/file.kt"), 
            "Should recognize C-style comment prefix")
        assertTrue(FileUtils.hasFilenamePrefix("# filename: path/to/file.py"), 
            "Should recognize hash comment prefix")
        assertTrue(FileUtils.hasFilenamePrefix("<!-- filename: path/to/file.html -->"), 
            "Should recognize HTML comment prefix")
        assertTrue(FileUtils.hasFilenamePrefix("-- filename: path/to/file.sql"), 
            "Should recognize SQL comment prefix")

        // Test with content that doesn't have a prefix
        assertFalse(FileUtils.hasFilenamePrefix("class Example { }"), 
            "Should not recognize content without a prefix")
        assertFalse(FileUtils.hasFilenamePrefix("// This is a comment but not a filename prefix"), 
            "Should not recognize regular comments")
    }
}
