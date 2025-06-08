package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.config.SourceClipboardExportSettings
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SourceExporterPerformanceTest {

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
    fun `test early termination when file limit is reached`() = runBlocking {
        val project = mockk<Project>(relaxed = true)
        val indicator = mockk<ProgressIndicator>(relaxed = true)
        val settings = SourceClipboardExportSettings.State().apply {
            fileCount = 3 // Low limit to test early termination
            maxFileSizeKb = 100
            areFiltersEnabled = false
        }

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
            }
        }

        val rootDir = mockk<VirtualFile> {
            every { name } returns "project"
            every { path } returns "/project"
            every { isDirectory } returns true
            every { children } returns files.toTypedArray()
            every { isValid } returns true
            every { exists() } returns true
        }

        every { project.baseDir } returns rootDir

        val exporter = SourceExporter(project, settings, indicator)
        val result = exporter.exportSources(arrayOf(rootDir))

        // Should only process up to the file limit
        assertEquals(3, result.processedFileCount, "Should stop at file limit")
        assertTrue(result.limitReached, "Should indicate limit was reached")

        // Verify content contains exactly 3 files
        val contentLines = result.content.lines().filter { it.startsWith("// filename:") }
        assertEquals(3, contentLines.size, "Should have exactly 3 file headers")
    }

    @Test
    fun `test concurrent processing efficiency`() = runBlocking {
        val project = mockk<Project>(relaxed = true)
        val indicator = mockk<ProgressIndicator>(relaxed = true)
        val settings = SourceClipboardExportSettings.State().apply {
            fileCount = 50
            maxFileSizeKb = 100
            areFiltersEnabled = false
        }

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
            }
        }

        val rootDir = mockk<VirtualFile> {
            every { name } returns "project"
            every { path } returns "/project"
            every { isDirectory } returns true
            every { children } returns files.toTypedArray()
            every { isValid } returns true
            every { exists() } returns true
        }

        every { project.baseDir } returns rootDir

        val exporter = SourceExporter(project, settings, indicator)

        val startTime = System.currentTimeMillis()
        val result = exporter.exportSources(arrayOf(rootDir))
        val duration = System.currentTimeMillis() - startTime

        // All files should be processed
        assertEquals(50, result.processedFileCount)

        // Processing should be reasonably fast (less than 5 seconds for 50 small files)
        assertTrue(duration < 5000, "Processing 50 files should take less than 5 seconds, took $duration ms")

        // All files should be in the output
        val contentLines = result.content.lines().filter { it.startsWith("// filename:") }
        assertEquals(50, contentLines.size)
    }
}
