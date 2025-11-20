package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.threading

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions.SmartExportUtils
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.config.SourceClipboardExportSettings
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core.SourceExporter
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core.gitignore.HierarchicalGitignoreParser
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.AppConstants
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

/** Tests to ensure proper threading and read action handling */
class ThreadingTest {

    private lateinit var project: Project
    private lateinit var mockFile: VirtualFile
    private lateinit var mockPsiFile: PsiFile

    @BeforeEach
    fun setUp() {
        project = mockk(relaxed = true)
        every { project.getService(HierarchicalGitignoreParser::class.java) } returns
                mockk(relaxed = true)
        mockFile = mockk(relaxed = true)
        mockPsiFile = mockk(relaxed = true)

        // Setup basic file properties
        every { mockFile.name } returns "Test.java"
        every { mockFile.path } returns "/test/Test.java"
        every { mockFile.isDirectory } returns false
        every { mockFile.isValid } returns true
        every { mockFile.length } returns 100
        every { mockFile.extension } returns "java"

        // Mock ReadAction for tests
        mockkStatic(ReadAction::class)
        every { ReadAction.compute<String, Exception>(any()) } answers
                {
                    val compute = firstArg<() -> String>()
                    compute()
                }
        every { ReadAction.compute<Boolean, Exception>(any()) } answers
                {
                    val compute = firstArg<() -> Boolean>()
                    compute()
                }
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    @Disabled("Complex integration test - requires full IntelliJ test environment")
    fun `test export utils does not throw read action exception`() {
        // This test would require a full IntelliJ test fixture
        // For now, we'll test the basic functionality with mocks

        // Verify that SmartExportUtils can be called without throwing
        try {
            SmartExportUtils.exportFiles(project, arrayOf(mockFile))
            assert(true) { "Export should not throw exception" }
        } catch (e: Exception) {
            // In test environment, some operations might fail due to missing services
            // That's OK as long as it's not a read action exception
            assert(e.message?.contains("Read access") != true) {
                "Should not throw read action exception: ${e.message}"
            }
        }
    }

    @Test
    @Disabled("Complex integration test requiring full IntelliJ test environment")
    fun `test source exporter handles read actions correctly`() {
        // Mock file content reading
        every { mockFile.contentsToByteArray() } returns "public class Test { }".toByteArray()
        every { mockFile.charset } returns Charsets.UTF_8

        val mockFile2 =
                mockk<VirtualFile>(relaxed = true) {
                    every { name } returns "Test2.java"
                    every { path } returns "/test/Test2.java"
                    every { isDirectory } returns false
                    every { isValid } returns true
                    every { length } returns 100
                    every { extension } returns "java"
                    every { contentsToByteArray() } returns "public class Test2 { }".toByteArray()
                    every { charset } returns Charsets.UTF_8
                }

        val virtualFiles = arrayOf(mockFile, mockFile2)

        // Mock settings
        val settings =
                SourceClipboardExportSettings.State().apply {
                    fileCount = 100
                    maxFileSizeKb = 500
                    includePathPrefix = true
                    outputFormat = AppConstants.OutputFormat.PLAIN_TEXT
                }

        mockkObject(SourceClipboardExportSettings)
        every { SourceClipboardExportSettings.getInstance() } returns
                mockk { every { state } returns settings }

        val indicator = EmptyProgressIndicator()
        val exporter = SourceExporter(project, settings, indicator)

        // This should not throw read action exceptions even when run from a coroutine
        val result = runBlocking { exporter.exportSources(virtualFiles) }

        assert(result.processedFileCount > 0) { "Should process at least one file" }
        assert(result.content.isNotEmpty()) { "Result should not be empty" }
    }

    @Test
    @Disabled("Complex integration test requiring full IntelliJ test environment")
    fun `test virtual file access in coroutines`() {
        // Test that we can access file properties safely in coroutines
        runBlocking {
            // This would throw without proper read action handling
            val name = ReadAction.compute<String, Exception> { mockFile.name }
            val path = ReadAction.compute<String, Exception> { mockFile.path }
            val isDirectory = ReadAction.compute<Boolean, Exception> { mockFile.isDirectory }

            assert(name == "Test.java")
            assert(!isDirectory)
            assert(path.contains("Test.java"))
        }
    }

    @Test
    @Disabled("Complex integration test requiring full IntelliJ test environment")
    fun `test concurrent file access`() {
        // Create multiple test files
        val files =
                (1..10)
                        .map { i ->
                            mockk<VirtualFile>(relaxed = true) {
                                every { name } returns "Test$i.java"
                                every { path } returns "/test/Test$i.java"
                                every { isDirectory } returns false
                                every { isValid } returns true
                                every { length } returns 100
                                every { extension } returns "java"
                                every { contentsToByteArray() } returns
                                        "public class Test$i { }".toByteArray()
                                every { charset } returns Charsets.UTF_8
                            }
                        }
                        .toTypedArray()

        // Mock settings
        val settings =
                SourceClipboardExportSettings.State().apply {
                    fileCount = 100
                    maxFileSizeKb = 500
                    includePathPrefix = true
                    outputFormat = AppConstants.OutputFormat.PLAIN_TEXT
                }

        mockkObject(SourceClipboardExportSettings)
        every { SourceClipboardExportSettings.getInstance() } returns
                mockk { every { state } returns settings }

        val indicator = EmptyProgressIndicator()
        val exporter = SourceExporter(project, settings, indicator)

        val result = runBlocking { exporter.exportSources(files) }

        assert(result.processedFileCount == 10) { "Should process all 10 files" }
        files.forEach { file ->
            val fileName = ReadAction.compute<String, Exception> { file.name }
            assert(result.content.contains(fileName)) { "Result should contain $fileName" }
        }
    }
}
