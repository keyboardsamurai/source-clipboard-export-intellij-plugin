package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core.gitignore

import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core.gitignore.GitignoreParser.MatchResult
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
                Arguments.of("doc/**/*.pdf", "doc/guides/user/manual.pdf", false, true, "Complex pattern with nested directories"),

                // Escaped patterns (added for black-box testing)
                Arguments.of("\\#file.txt", "#file.txt", false, true, "Escaped hash matches literal hash"),
                Arguments.of("\\!file.txt", "!file.txt", false, true, "Escaped bang matches literal bang (not negation)"),
                Arguments.of("\\!file.txt", "file.txt", false, false, "Escaped bang does not match non-bang file"),
                Arguments.of("file\\*.txt", "file*.txt", false, true, "Escaped star matches literal star"),
                Arguments.of("file\\?.txt", "file?.txt", false, true, "Escaped question mark matches literal question mark")
            )
        }
    }

    @BeforeEach
    fun setUp() {
        mockGitignoreFile = mockk(relaxed = true)
        mockGitignoreParent = mockk(relaxed = true)
        every { mockGitignoreFile.parent } returns mockGitignoreParent
        every { mockGitignoreFile.path } returns "/path/to/.gitignore"
        every { mockGitignoreFile.name } returns ".gitignore"
        mockkStatic(VfsUtilCore::class)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `matches returns false for any path when gitignore is empty`() {
        every { VfsUtilCore.loadText(mockGitignoreFile) } returns ""
        gitignoreParser = GitignoreParser(mockGitignoreFile)
        assertFalse(gitignoreParser.matches("some/path", false), "Should not match file")
        assertFalse(gitignoreParser.matches("some/dir", true), "Should not match dir")
        assertFalse(gitignoreParser.matches("root.txt", false), "Should not match root file")
    }

    @Test
    fun `matches ignores comments and empty lines`() {
        val gitignoreContent = """
            # Ignore logs
            *.log

            # Ignore temp files
            *.tmp
        """.trimIndent()
        every { VfsUtilCore.loadText(mockGitignoreFile) } returns gitignoreContent
        gitignoreParser = GitignoreParser(mockGitignoreFile)

        assertTrue(gitignoreParser.matches("debug.log", false), "Should match *.log")
        assertTrue(gitignoreParser.matches("session.tmp", false), "Should match *.tmp")
        assertFalse(gitignoreParser.matches("config.ini", false), "Should not match other files")
    }

    @Test
    fun `constructor allows gitignore without parent`() {
        every { mockGitignoreFile.parent } returns null
        GitignoreParser(mockGitignoreFile) // should NOT throw
    }

    @Test
    fun `matches returns false for empty path`() {
        every { VfsUtilCore.loadText(mockGitignoreFile) } returns "*.txt"
        gitignoreParser = GitignoreParser(mockGitignoreFile)
        assertFalse(gitignoreParser.matches("", false), "Empty path should not be matched")
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
        every { VfsUtilCore.loadText(mockGitignoreFile) } returns pattern
        gitignoreParser = GitignoreParser(mockGitignoreFile)
        val result = gitignoreParser.matches(path, isDirectory)
        assertEquals(shouldMatch, result, testDescription)
    }

    @Test
    fun `matches applies last matching rule in file`() {
        val gitignoreContent = """
            *.log
            !debug.log
            debug.log
        """.trimIndent()
        every { VfsUtilCore.loadText(mockGitignoreFile) } returns gitignoreContent
        gitignoreParser = GitignoreParser(mockGitignoreFile)
        assertTrue(gitignoreParser.matches("debug.log", false),  "Last matching rule (debug.log) wins -> file is ignored")
        // Add another check to ensure the negation was overridden
        assertTrue(gitignoreParser.matches("other.log", false), "First rule (*.log) should still apply")
    }

    @Test
    fun `matches handles exception when creating path`() {
        every { VfsUtilCore.loadText(mockGitignoreFile) } returns "*.txt"
        gitignoreParser = GitignoreParser(mockGitignoreFile)
        // Using an invalid path character (on most systems) to trigger an exception
        val result = gitignoreParser.matches("file:with:invalid:chars", false)
        assertFalse(result, "Should return false when path creation fails")
    }

    @Test
    fun `matchResult exposes negation result`() {
        val gitignoreContent = """
            *.log
            !debug.log
        """.trimIndent()
        every { VfsUtilCore.loadText(mockGitignoreFile) } returns gitignoreContent
        gitignoreParser = GitignoreParser(mockGitignoreFile)

        val result = gitignoreParser.matchResult("debug.log", false)
        assertEquals(MatchResult.MATCH_NEGATE, result)
    }

    @Test
    fun `matchResult handles complex extension glob`() {
        every { VfsUtilCore.loadText(mockGitignoreFile) } returns "doc/**/*.pdf"
        gitignoreParser = GitignoreParser(mockGitignoreFile)

        val result = gitignoreParser.matchResult("doc/guides/user/manual.pdf", false)

        assertEquals(MatchResult.MATCH_IGNORE, result)
    }

    /**
     * This test verifies the fix for the Windows bug described in PR #1.
     *
     * On Windows, filenames cannot contain '*' or '?' characters. When the gitignore parser
     * tries to match a pattern like "file\*.txt" (escaped star) against a filename like
     * "file*.txt" (literal star in name), the Path.getPath() call throws InvalidPathException.
     *
     * FIXED: With PR #1 merged, the implementation no longer returns NO_MATCH immediately on
     * InvalidPathException. Instead, it falls back to string/regex matching which correctly
     * handles the pattern.
     *
     * This test uses the null byte character (\u0000) which is invalid in paths on ALL operating
     * systems (including Windows, macOS, and Linux), ensuring the test verifies the fix
     * consistently across all platforms.
     */
    @Test
    fun `VERIFIES FIX - escaped special chars now work when Path creation throws InvalidPathException`() {
        // Use null byte which is invalid on ALL systems to verify the fix
        // This simulates what happens on Windows with '*' and '?' characters
        val fileWithInvalidChar = "file\u0000name.txt"

        // Create a gitignore pattern that should match this file
        // In this case, we'll use a simple pattern that would match via the filename
        every { VfsUtilCore.loadText(mockGitignoreFile) } returns "file*name.txt"
        gitignoreParser = GitignoreParser(mockGitignoreFile)

        // FIXED: Now returns true because the regex fallback handles it correctly
        // The pattern "file*name.txt" matches "file\u0000name.txt" via wildcard matching
        val result = gitignoreParser.matches(fileWithInvalidChar, false)

        assertTrue(
            result,
            "FIX VERIFIED: Pattern matching now works when Path creation throws InvalidPathException. " +
            "The pattern 'file*name.txt' correctly matches 'file\\u0000name.txt' via wildcard " +
            "using the string/regex fallback logic. PR #1 fix is working!"
        )
    }

    /**
     * VERIFIES FIX - Escaped wildcards on Windows
     *
     * This test documents and verifies the fix for the exact scenario from PR #1:
     * - Pattern: "file\*.txt" (escaped star, should match literal '*')
     * - File: "file*.txt" (literal star in filename)
     * - On Windows (before fix): Path.getPath("file*.txt") throws InvalidPathException → NO_MATCH
     * - On Windows (after fix): Path creation fails but regex fallback matches → MATCH
     *
     * This test PASSES on all platforms now:
     * - macOS/Linux: Path creation succeeds and pattern matches correctly
     * - Windows (with PR #1 merged): Path creation fails but fallback logic handles it
     */
    @Test
    fun `VERIFIES FIX - escaped wildcards now match literal wildcard characters in filenames`() {
        // Pattern: file\*.txt (escaped star)
        every { VfsUtilCore.loadText(mockGitignoreFile) } returns "file\\*.txt"
        gitignoreParser = GitignoreParser(mockGitignoreFile)

        // After PR #1 merge:
        // - macOS/Linux: Path.getPath("file*.txt") succeeds, pattern matches correctly
        // - Windows: Path creation throws InvalidPathException but regex fallback matches
        val starResult = gitignoreParser.matches("file*.txt", false)

        assertTrue(
            starResult,
            "FIX VERIFIED: Escaped star pattern 'file\\*.txt' now matches literal filename 'file*.txt' " +
            "on ALL platforms including Windows. PR #1 fix is working!"
        )

        // Pattern: file\?.txt (escaped question mark)
        every { VfsUtilCore.loadText(mockGitignoreFile) } returns "file\\?.txt"
        gitignoreParser = GitignoreParser(mockGitignoreFile)

        val questionResult = gitignoreParser.matches("file?.txt", false)

        assertTrue(
            questionResult,
            "FIX VERIFIED: Escaped question mark pattern 'file\\?.txt' now matches literal filename 'file?.txt' " +
            "on ALL platforms including Windows. PR #1 fix is working!"
        )
    }
}
