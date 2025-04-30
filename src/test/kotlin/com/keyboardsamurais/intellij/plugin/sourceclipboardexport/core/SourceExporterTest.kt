package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.config.SourceClipboardExportSettings
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core.gitignore.HierarchicalGitignoreParser
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.FileUtils
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SourceExporterTest {

    // Mocks
    private lateinit var mockProject: Project
    private lateinit var mockSettings: SourceClipboardExportSettings.State
    private lateinit var mockIndicator: ProgressIndicator
    private lateinit var mockHierarchicalGitignoreParser: HierarchicalGitignoreParser
    private lateinit var mockFile: VirtualFile
    private lateinit var mockIgnoredFile: VirtualFile
    private lateinit var mockDirectory: VirtualFile

    // System under test
    private lateinit var sourceExporter: SourceExporter

    @BeforeEach
    fun setUp() {
        // Initialize mocks
        mockProject = mockk(relaxed = true)
        mockSettings = mockk(relaxed = true)
        mockIndicator = mockk(relaxed = true)
        mockHierarchicalGitignoreParser = mockk(relaxed = true)
        mockFile = mockk(relaxed = true)
        mockIgnoredFile = mockk(relaxed = true)
        mockDirectory = mockk(relaxed = true)

        // Mock settings
        every { mockSettings.fileCount } returns 100
        every { mockSettings.maxFileSizeKb } returns 1024
        every { mockSettings.areFiltersEnabled } returns false
        every { mockSettings.filenameFilters } returns mutableListOf<String>()
        every { mockSettings.ignoredNames } returns mutableListOf<String>()
        every { mockSettings.includePathPrefix } returns true

        // Mock ProjectRootManager
        val mockProjectRootManager = mockk<ProjectRootManager>()
        val mockContentRoot = mockk<VirtualFile>()
        every { mockProjectRootManager.contentRoots } returns arrayOf<VirtualFile>(mockContentRoot)

        // Mock static ProjectRootManager.getInstance
        mockkStatic(ProjectRootManager::class)
        every { ProjectRootManager.getInstance(any<Project>()) } returns mockProjectRootManager

        // Mock FileUtils
        mockkObject(FileUtils)
        every { FileUtils.getRepositoryRoot(mockProject) } returns mockContentRoot
        every { FileUtils.getRelativePath(any(), mockProject) } returns "path/to/file.txt"
        every { FileUtils.isKnownBinaryExtension(any()) } returns false
        every { FileUtils.isLikelyBinaryContent(any()) } returns false
        every { FileUtils.readFileContent(any()) } returns "file content"

        // Mock HierarchicalGitignoreParser
        mockkConstructor(HierarchicalGitignoreParser::class)
        every { anyConstructed<HierarchicalGitignoreParser>().clearCache() } just runs
        every { anyConstructed<HierarchicalGitignoreParser>().isIgnored(mockFile) } returns false
        every { anyConstructed<HierarchicalGitignoreParser>().isIgnored(mockIgnoredFile) } returns true

        // Mock file properties
        every { mockFile.isValid } returns true
        every { mockFile.exists() } returns true
        every { mockFile.isDirectory } returns false
        every { mockFile.name } returns "file.txt"
        every { mockFile.path } returns "/path/to/file.txt"
        every { mockFile.length } returns 100
        every { mockFile.extension } returns "txt"

        every { mockIgnoredFile.isValid } returns true
        every { mockIgnoredFile.exists() } returns true
        every { mockIgnoredFile.isDirectory } returns false
        every { mockIgnoredFile.name } returns "ignored.txt"
        every { mockIgnoredFile.path } returns "/path/to/ignored.txt"

        every { mockDirectory.isValid } returns true
        every { mockDirectory.exists() } returns true
        every { mockDirectory.isDirectory } returns true
        every { mockDirectory.name } returns "dir"
        every { mockDirectory.path } returns "/path/to/dir"
        every { mockDirectory.children } returns arrayOf(mockFile)

        // Initialize the exporter
        sourceExporter = SourceExporter(mockProject, mockSettings, mockIndicator)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `exportSources clears gitignore parser cache at start`() = runBlocking {
        // Arrange
        val files = arrayOf(mockFile)

        // Act
        sourceExporter.exportSources(files)

        // Assert
        verify { anyConstructed<HierarchicalGitignoreParser>().clearCache() }
    }

    @Test
    fun `exportSources excludes files ignored by gitignore`() = runBlocking {
        // Arrange
        val files = arrayOf(mockIgnoredFile)

        // Act
        val result = sourceExporter.exportSources(files)

        // Assert
        assertEquals(0, result.processedFileCount, "No files should be processed")
        assertEquals(1, result.excludedByGitignoreCount, "One file should be excluded by gitignore")
        assertEquals("", result.content, "Content should be empty")
    }

    @Test
    fun `exportSources processes valid files`() = runBlocking {
        // Arrange
        val files = arrayOf(mockFile)

        // Act
        val result = sourceExporter.exportSources(files)

        // Assert
        assertEquals(1, result.processedFileCount, "One file should be processed")
        assertEquals(0, result.excludedByGitignoreCount, "No files should be excluded by gitignore")
        assertTrue(result.content.contains("file content"), "Content should contain file content")
    }

    @Test
    fun `exportSources processes directories recursively`() = runBlocking {
        // Arrange
        val files = arrayOf(mockDirectory)

        // Act
        val result = sourceExporter.exportSources(files)

        // Assert
        assertEquals(1, result.processedFileCount, "One file should be processed")
        assertEquals(0, result.excludedByGitignoreCount, "No files should be excluded by gitignore")
        assertTrue(result.content.contains("file content"), "Content should contain file content")
    }

    @Test
    fun `exportSources respects file count limit`() = runBlocking {
        // Arrange
        every { mockSettings.fileCount } returns 1
        val files = arrayOf(mockFile, mockFile) // Two identical files

        // Act
        val result = sourceExporter.exportSources(files)

        // Assert
        assertEquals(1, result.processedFileCount, "Only one file should be processed")
        assertTrue(result.limitReached, "Limit should be reached")
    }

    @Test
    fun `exportSources excludes files by ignored names`() = runBlocking {
        // Arrange
        every { mockSettings.ignoredNames } returns mutableListOf("file.txt")
        val files = arrayOf(mockFile)

        // Act
        val result = sourceExporter.exportSources(files)

        // Assert
        assertEquals(0, result.processedFileCount, "No files should be processed")
        assertEquals(1, result.excludedByIgnoredNameCount, "One file should be excluded by ignored name")
    }

    @Test
    fun `exportSources excludes files by size`() = runBlocking {
        // Arrange
        every { mockSettings.maxFileSizeKb } returns 0 // 0 KB limit
        val files = arrayOf(mockFile)

        // Act
        val result = sourceExporter.exportSources(files)

        // Assert
        assertEquals(0, result.processedFileCount, "No files should be processed")
        assertEquals(1, result.excludedBySizeCount, "One file should be excluded by size")
    }

    @Test
    fun `exportSources excludes binary files`() = runBlocking {
        // Arrange
        every { FileUtils.isKnownBinaryExtension(mockFile) } returns true
        val files = arrayOf(mockFile)

        // Act
        val result = sourceExporter.exportSources(files)

        // Assert
        assertEquals(0, result.processedFileCount, "No files should be processed")
        assertEquals(1, result.excludedByBinaryContentCount, "One file should be excluded as binary")
    }

    @Test
    fun `exportSources excludes files by filter when filters enabled`() = runBlocking {
        // Arrange
        every { mockSettings.areFiltersEnabled } returns true
        every { mockSettings.filenameFilters } returns mutableListOf("java")
        val files = arrayOf(mockFile) // txt file, not matching java filter

        // Act
        val result = sourceExporter.exportSources(files)

        // Assert
        assertEquals(0, result.processedFileCount, "No files should be processed")
        assertEquals(1, result.excludedByFilterCount, "One file should be excluded by filter")
    }

    @Test
    fun `exportSources includes path prefix when enabled`() = runBlocking {
        // Arrange
        every { mockSettings.includePathPrefix } returns true
        val files = arrayOf(mockFile)

        // Act
        val result = sourceExporter.exportSources(files)

        // Assert
        assertTrue(result.content.contains("path/to/file.txt"), "Content should contain path prefix")
    }

    @Test
    fun `exportSources does not include path prefix when disabled`() = runBlocking {
        // Arrange
        every { mockSettings.includePathPrefix } returns false
        val files = arrayOf(mockFile)

        // Act
        val result = sourceExporter.exportSources(files)

        // Assert
        assertFalse(result.content.contains("path/to/file.txt"), "Content should not contain path prefix")
    }
}
