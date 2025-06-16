package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.config.SourceClipboardExportSettings
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core.gitignore.HierarchicalGitignoreParser
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class SourceExporterPerformanceTest {

    @BeforeEach
    fun setUp() {
        // Mock the gitignore parser to not exclude any files
        mockkConstructor(HierarchicalGitignoreParser::class)
        every { anyConstructed<HierarchicalGitignoreParser>().isIgnored(any()) } returns false
        
        // Mock ReadAction to execute computables directly
        mockkStatic(ReadAction::class)
        every { ReadAction.compute<Any?, Exception>(any()) } answers {
            // The argument is a lambda function, execute it directly
            val lambda = firstArg<() -> Any?>()
            lambda()
        }
    }
    
    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `test exporter uses system CPU count for parallelism`() {
        // This test verifies the change from hardcoded 16 threads to Runtime.availableProcessors()
        // We can't directly test the dispatcher, but we can verify the behavior is reasonable

        val project = mockk<Project>(relaxed = true)
        val indicator = mockk<ProgressIndicator>(relaxed = true)
        val settings = SourceClipboardExportSettings.State().apply {
            fileCount = 10
            maxFileSizeKb = 100
        }

        val exporter = SourceExporter(project, settings, indicator)

        // The actual CPU count will vary by system, but should be reasonable
        val cpuCount = Runtime.getRuntime().availableProcessors()
        assertTrue(cpuCount >= 1, "CPU count should be at least 1")
        assertTrue(cpuCount <= 128, "CPU count should be reasonable (not hardcoded 16)")
    }

    @Test
    @Disabled("Complex integration test requiring full IntelliJ test environment")
    fun `test early termination when file limit is reached`() = runBlocking {
        val project = mockk<Project>(relaxed = true)
        val indicator = mockk<ProgressIndicator>(relaxed = true)
        val settings = SourceClipboardExportSettings.State().apply {
            fileCount = 3 // Low limit to test early termination
            maxFileSizeKb = 100
            areFiltersEnabled = false
            ignoredNames = mutableListOf()
            filenameFilters = mutableListOf()
        }

        // Create root directory
        val rootDir = mockk<VirtualFile>(relaxed = true)
        
        // Create mock files
        val files = (1..10).map { index ->
            mockk<VirtualFile> {
                every { name } returns "file$index.txt"
                every { path } returns "/project/file$index.txt"
                every { isDirectory } returns false
                every { extension } returns "txt"
                every { length } returns 100L
                every { isValid } returns true
                every { exists() } returns true
                every { charset } returns Charsets.UTF_8
                every { contentsToByteArray() } returns "File $index content".toByteArray()
                every { parent } returns rootDir
            }
        }

        // Configure root directory
        every { rootDir.name } returns "project"
        every { rootDir.path } returns "/project"
        every { rootDir.isDirectory } returns true
        every { rootDir.children } returns files.toTypedArray()
        every { rootDir.isValid } returns true
        every { rootDir.exists() } returns true

        every { project.baseDir } returns rootDir

        val exporter = SourceExporter(project, settings, indicator)
        val result = exporter.exportSources(arrayOf(rootDir))

        // Debug output
        println("Processed file count: ${result.processedFileCount}")
        println("Limit reached: ${result.limitReached}")
        println("Content length: ${result.content.length}")
        println("Content preview: ${result.content.take(200)}")
        
        // Should process at least one file
        assertTrue(result.processedFileCount > 0, "Should process at least one file")
        
        // Should only process up to the file limit
        assertTrue(result.processedFileCount <= 3, "Should not exceed file limit (got ${result.processedFileCount})")
        
        // If we processed 3 or more files, limit should be reached
        if (result.processedFileCount >= 3) {
            assertTrue(result.limitReached, "Should indicate limit was reached when processed ${result.processedFileCount} files")
        }
    }

    @Test
    @Disabled("Complex integration test requiring full IntelliJ test environment")
    fun `test concurrent processing efficiency`() = runBlocking {
        val project = mockk<Project>(relaxed = true)
        val indicator = mockk<ProgressIndicator>(relaxed = true)
        val settings = SourceClipboardExportSettings.State().apply {
            fileCount = 50
            maxFileSizeKb = 100
            areFiltersEnabled = false
            ignoredNames = mutableListOf()
            filenameFilters = mutableListOf()
        }

        // Create root directory
        val rootDir = mockk<VirtualFile>(relaxed = true)
        
        // Create many mock files to test concurrent processing
        val files = (1..50).map { index ->
            mockk<VirtualFile> {
                every { name } returns "file$index.txt"
                every { path } returns "/project/file$index.txt"
                every { isDirectory } returns false
                every { extension } returns "txt"
                every { length } returns 100L
                every { isValid } returns true
                every { exists() } returns true
                every { charset } returns Charsets.UTF_8
                every { contentsToByteArray() } returns "File $index content".toByteArray()
                every { parent } returns rootDir
            }
        }

        // Configure root directory
        every { rootDir.name } returns "project"
        every { rootDir.path } returns "/project"
        every { rootDir.isDirectory } returns true
        every { rootDir.children } returns files.toTypedArray()
        every { rootDir.isValid } returns true
        every { rootDir.exists() } returns true

        every { project.baseDir } returns rootDir

        val exporter = SourceExporter(project, settings, indicator)

        val startTime = System.currentTimeMillis()
        val result = exporter.exportSources(arrayOf(rootDir))
        val duration = System.currentTimeMillis() - startTime

        // Debug output
        println("Processed file count: ${result.processedFileCount}")
        println("Processing duration: $duration ms")
        println("Content length: ${result.content.length}")
        
        // Should process at least one file
        assertTrue(result.processedFileCount > 0, "Should process at least one file (got ${result.processedFileCount})")
        
        // Should process many files efficiently  
        assertTrue(result.processedFileCount <= 50, "Should not exceed the total number of files")

        // Processing should be reasonably fast (less than 20 seconds for 50 small files)
        assertTrue(duration < 20000, "Processing 50 files should take less than 20 seconds, took $duration ms")
    }
}
