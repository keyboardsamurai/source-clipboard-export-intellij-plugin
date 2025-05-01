package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core.gitignore

import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class GitignoreParserTest {

    // Mocks
    private lateinit var mockGitignoreFile: VirtualFile
    private lateinit var mockGitignoreParent: VirtualFile

    // System under test
    private lateinit var gitignoreParser: GitignoreParser

    companion object {
        @JvmStatic
        fun providePatternMatchingTestCases(): Stream<Arguments> {
            return Stream.of(
                // Basic patterns
                Arguments.of("*.txt", "file.txt", false, true,  "Simple wildcard pattern"),
                Arguments.of("*.txt", "file.jpg", false, false, "Non-matching extension"),
                Arguments.of("*.txt", "path/to/file.txt", false, true,  "Wildcard pattern in subdirectory"),

                // Directory patterns
                Arguments.of("dir/", "dir", true, true,  "Directory pattern matching directory"),
                Arguments.of("dir/", "dir", false, false, "Directory pattern not matching file"),
                Arguments.of("dir/", "dir/file.txt", false, true,  "Directory pattern matching file in directory"),

                // Negation patterns
                Arguments.of("*.log", "debug.log", false, true,  "Pattern matches log file"),
                Arguments.of("!debug.log", "debug.log", false, false, "Negated pattern excludes specific log file"),

                // Root anchoring
                Arguments.of("/root.txt", "root.txt", false, true, "Root anchored pattern matches at root"),
                Arguments.of("/root.txt", "subdir/root.txt", false, false, "Root anchored pattern doesn't match in subdirectory"),

                // Complex patterns
                Arguments.of("**/build/", "project/build", true, true, "Recursive directory pattern"),
                Arguments.of("**/build/", "project/build/output.txt", false, true, "Recursive directory pattern matches files inside"),
                Arguments.of("doc/**/*.pdf", "doc/manual.pdf", false, true, "Complex pattern with directory and extension"),
                Arguments.of("doc/**/*.pdf", "doc/guides/user/manual.pdf", false, true, "Complex pattern with nested directories")
            )
        }
    }

    @BeforeEach
    fun setUp() {
        // Initialize mocks
        mockGitignoreFile = mockk(relaxed = true)
        mockGitignoreParent = mockk(relaxed = true)

        // Setup basic properties
        every { mockGitignoreFile.parent } returns mockGitignoreParent
        every { mockGitignoreFile.path } returns "/path/to/.gitignore"
        every { mockGitignoreFile.name } returns ".gitignore"

        // Mock file content reading
        mockkStatic(VfsUtilCore::class)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `constructor allows gitignore without parent`() {
        // Arrange
        every { mockGitignoreFile.parent } returns null

        // Act & Assert
        GitignoreParser(mockGitignoreFile) // should NOT throw
    }

    @Test
    fun `constructor parses empty gitignore file`() {
        // Arrange
        every { VfsUtilCore.loadText(mockGitignoreFile) } returns ""

        // Act
        gitignoreParser = GitignoreParser(mockGitignoreFile)

        // Assert - use reflection to check rules list is empty
        val rulesField = GitignoreParser::class.java.getDeclaredField("rules")
        rulesField.isAccessible = true
        val rules = rulesField.get(gitignoreParser) as List<*>
        assertTrue(rules.isEmpty(), "Rules list should be empty for empty gitignore file")
    }

    @Test
    fun `constructor skips comment lines and empty lines`() {
        // Arrange
        val gitignoreContent = """
            # This is a comment

            # Another comment
            *.txt
        """.trimIndent()
        every { VfsUtilCore.loadText(mockGitignoreFile) } returns gitignoreContent

        // Act
        gitignoreParser = GitignoreParser(mockGitignoreFile)

        // Assert - use reflection to check rules list has only one rule
        val rulesField = GitignoreParser::class.java.getDeclaredField("rules")
        rulesField.isAccessible = true
        val rules = rulesField.get(gitignoreParser) as List<*>
        assertEquals(1, rules.size, "Rules list should have one rule after skipping comments and empty lines")
    }

    @Test
    fun `constructor handles escaped comment character`() {
        // Arrange
        val gitignoreContent = """
            \# This is not a comment
            # This is a comment
        """.trimIndent()
        every { VfsUtilCore.loadText(mockGitignoreFile) } returns gitignoreContent

        // Act
        gitignoreParser = GitignoreParser(mockGitignoreFile)

        // Assert - use reflection to check rules list has only one rule
        val rulesField = GitignoreParser::class.java.getDeclaredField("rules")
        rulesField.isAccessible = true
        val rules = rulesField.get(gitignoreParser) as List<*>
        assertEquals(1, rules.size, "Rules list should have one rule for the escaped comment")
        // Check the actual pattern of the single rule (which is the first line due to reversal)
        val rule = rules[0]
        val ruleClass = rule!!.javaClass
        val patternField = ruleClass.getDeclaredField("pattern")
        patternField.isAccessible = true
        assertEquals("# This is not a comment", patternField.get(rule), "Pattern should be the unescaped comment line")
    }

    @Test
    fun `matches returns false for empty path`() {
        // Arrange
        every { VfsUtilCore.loadText(mockGitignoreFile) } returns "*.txt"
        gitignoreParser = GitignoreParser(mockGitignoreFile)

        // Act
        val result = gitignoreParser.matches("", false)

        // Assert
        assertFalse(result, "Empty path should not be matched")
    }

    @ParameterizedTest(name = "{4}: Pattern '{0}' matching '{1}' (isDir={2}) should be {3}")
    @MethodSource("providePatternMatchingTestCases")
    fun `matches correctly applies gitignore patterns`(
        pattern: String,
        path: String,
        isDirectory: Boolean,
        shouldMatch: Boolean,
        testDescription: String
    ) {
        // Arrange
        every { VfsUtilCore.loadText(mockGitignoreFile) } returns pattern
        gitignoreParser = GitignoreParser(mockGitignoreFile)

        // Act
        val result = gitignoreParser.matches(path, isDirectory)

        // Assert
        assertEquals(shouldMatch, result, testDescription)
    }

    @Test
    fun `matches applies last matching rule in file`() {
        // Arrange
        val gitignoreContent = """
            *.log
            !debug.log
            debug.log
        """.trimIndent()
        every { VfsUtilCore.loadText(mockGitignoreFile) } returns gitignoreContent
        gitignoreParser = GitignoreParser(mockGitignoreFile)

        // Act
        val result = gitignoreParser.matches("debug.log", false)

        // Assert
        assertTrue(result,  "Last matching rule (debug.log) wins -> file is ignored")
    }

    @Test
    fun `matches handles exception when creating path`() {
        // Arrange
        every { VfsUtilCore.loadText(mockGitignoreFile) } returns "*.txt"
        gitignoreParser = GitignoreParser(mockGitignoreFile)

        // Act
        // Using an invalid path character (on most systems) to trigger an exception
        val result = gitignoreParser.matches("file:with:invalid:chars", false)

        // Assert
        assertFalse(result, "Should return false when path creation fails")
    }

    @Test
    fun `IgnoreRule correctly parses negation pattern`() {
        // Arrange
        val gitignoreContent = "!*.log"
        every { VfsUtilCore.loadText(mockGitignoreFile) } returns gitignoreContent
        gitignoreParser = GitignoreParser(mockGitignoreFile)

        // Act & Assert - use reflection to check the rule properties
        val rulesField = GitignoreParser::class.java.getDeclaredField("rules")
        rulesField.isAccessible = true
        val rules = rulesField.get(gitignoreParser) as List<*>

        assertEquals(1, rules.size) // Ensure only one rule
        val rule = rules[0]
        val ruleClass = rule!!.javaClass

        val isNegatedField = ruleClass.getDeclaredField("negated")
        isNegatedField.isAccessible = true
        assertTrue(isNegatedField.getBoolean(rule), "Rule should be negated")

        val patternField = ruleClass.getDeclaredField("pattern")
        patternField.isAccessible = true
        assertEquals("*.log", patternField.get(rule), "Pattern should be extracted without negation symbol")
    }

    @Test
    fun `IgnoreRule correctly parses directory-only pattern`() {
        // Arrange
        val gitignoreContent = "build/"
        every { VfsUtilCore.loadText(mockGitignoreFile) } returns gitignoreContent
        gitignoreParser = GitignoreParser(mockGitignoreFile)

        // Act & Assert - use reflection to check the rule properties
        val rulesField = GitignoreParser::class.java.getDeclaredField("rules")
        rulesField.isAccessible = true
        val rules = rulesField.get(gitignoreParser) as List<*>

        assertEquals(1, rules.size) // Ensure only one rule
        val rule = rules[0]
        val ruleClass = rule!!.javaClass

        val matchOnlyDirField = ruleClass.getDeclaredField("dirOnly")
        matchOnlyDirField.isAccessible = true
        assertTrue(matchOnlyDirField.getBoolean(rule), "Rule should match only directories")

        val patternField = ruleClass.getDeclaredField("pattern")
        patternField.isAccessible = true
        assertEquals("build", patternField.get(rule), "Pattern should be extracted without trailing slash")
    }

    @Test
    fun `IgnoreRule correctly parses rooted pattern`() {
        // Arrange
        val gitignoreContent = "/root.txt"
        every { VfsUtilCore.loadText(mockGitignoreFile) } returns gitignoreContent
        gitignoreParser = GitignoreParser(mockGitignoreFile)

        // Act & Assert - use reflection to check the rule properties
        val rulesField = GitignoreParser::class.java.getDeclaredField("rules")
        rulesField.isAccessible = true
        val rules = rulesField.get(gitignoreParser) as List<*>

        assertEquals(1, rules.size) // Ensure only one rule
        val rule = rules[0]
        val ruleClass = rule!!.javaClass

        val isRootedField = ruleClass.getDeclaredField("rooted")
        isRootedField.isAccessible = true
        assertTrue(isRootedField.getBoolean(rule), "Rule should be rooted")

        val patternField = ruleClass.getDeclaredField("pattern")
        patternField.isAccessible = true
        assertEquals("root.txt", patternField.get(rule), "Pattern should be extracted without leading slash")
    }

    @Test
    fun `IgnoreRule correctly handles escaped characters`() {
        // Arrange
        val gitignoreContent = """
            \#not-a-comment.txt
            \!not-negated.txt
        """.trimIndent()
        every { VfsUtilCore.loadText(mockGitignoreFile) } returns gitignoreContent
        gitignoreParser = GitignoreParser(mockGitignoreFile)

        // Act & Assert - use reflection to check the rules
        val rulesField = GitignoreParser::class.java.getDeclaredField("rules")
        rulesField.isAccessible = true
        val rules = rulesField.get(gitignoreParser) as List<*>

        assertEquals(2, rules.size, "Should have two rules")

        // Rule order is reversed in parser init!
        // rules[0] corresponds to the LAST line: \!not-negated.txt
        // rules[1] corresponds to the FIRST line: \#not-a-comment.txt

        // Check first rule (escaped #) - Now at index 1
        val rule1 = rules[1] // <<<< Index adjusted
        val ruleClass1 = rule1!!.javaClass // Use the class from rule1
        val patternField1 = ruleClass1.getDeclaredField("pattern")
        patternField1.isAccessible = true
        // Corrected expected value after unescaping
        assertEquals("#not-a-comment.txt", patternField1.get(rule1), "Pattern should have # without escape character")

        // Check second rule (escaped !) - Now at index 0
        val rule2 = rules[0] // <<<< Index adjusted
        val ruleClass2 = rule2!!.javaClass // Use the class from rule2
        val isNegatedField2 = ruleClass2.getDeclaredField("negated")
        isNegatedField2.isAccessible = true
        assertFalse(isNegatedField2.getBoolean(rule2), "Rule should not be negated despite having !")
        val patternField2 = ruleClass2.getDeclaredField("pattern")
        patternField2.isAccessible = true
        // Corrected expected value after unescaping
        assertEquals("!not-negated.txt", patternField2.get(rule2), "Pattern should have ! without escape character")
    }
}
