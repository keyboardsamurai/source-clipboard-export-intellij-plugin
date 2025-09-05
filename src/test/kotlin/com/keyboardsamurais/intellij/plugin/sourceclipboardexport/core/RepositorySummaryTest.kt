package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.AppConstants
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertContains

class RepositorySummaryTest {
    private lateinit var mockProject: Project
    private lateinit var mockRepositoryManager: GitRepositoryManager
    private lateinit var mockRepository: GitRepository
    private lateinit var mockRemote: GitRemote
    private lateinit var mockVirtualFile: VirtualFile

    @BeforeEach
    fun setUp() {
        mockProject = mockk(relaxed = true)
        mockRepositoryManager = mockk(relaxed = true)
        mockRepository = mockk(relaxed = true)
        mockRemote = mockk(relaxed = true)
        mockVirtualFile = mockk(relaxed = true)

        mockkStatic(GitRepositoryManager::class)
        every { GitRepositoryManager.getInstance(mockProject) } returns mockRepositoryManager
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `should return GitHub URL when repository has origin remote`() {
        // Arrange
        val expectedUrl = "https://github.com/keyboardsamurais/intellij-plugin-export-source"
        every { mockRepositoryManager.repositories } returns listOf(mockRepository)
        every { mockRemote.name } returns "origin"
        every { mockRemote.firstUrl } returns expectedUrl
        every { mockRepository.remotes } returns listOf(mockRemote)
        every { mockRepository.root } returns mockVirtualFile

        val summary = RepositorySummary(
            project = mockProject,
            selectedFiles = arrayOf(mockVirtualFile),
            fileContents = listOf("--- test.txt ---\ntest content"),
            processedFileCount = 1,
            excludedByFilterCount = 0,
            excludedBySizeCount = 0,
            excludedByBinaryContentCount = 0,
            excludedByIgnoredNameCount = 0,
            excludedByGitignoreCount = 0
        )

        // Act
        val result = summary.generateSummary(AppConstants.OutputFormat.PLAIN_TEXT)

        // Assert
        assertContains(result, expectedUrl)
    }

    @Test
    fun `should return first remote URL when no origin remote exists`() {
        // Arrange
        val expectedUrl = "https://gitlab.com/company/project"
        every { mockRepositoryManager.repositories } returns listOf(mockRepository)
        every { mockRemote.name } returns "upstream"
        every { mockRemote.firstUrl } returns expectedUrl
        every { mockRepository.remotes } returns listOf(mockRemote)
        every { mockRepository.root } returns mockVirtualFile

        val summary = RepositorySummary(
            project = mockProject,
            selectedFiles = arrayOf(mockVirtualFile),
            fileContents = listOf("--- test.txt ---\ntest content"),
            processedFileCount = 1,
            excludedByFilterCount = 0,
            excludedBySizeCount = 0,
            excludedByBinaryContentCount = 0,
            excludedByIgnoredNameCount = 0,
            excludedByGitignoreCount = 0
        )

        // Act
        val result = summary.generateSummary(AppConstants.OutputFormat.PLAIN_TEXT)

        // Assert
        assertContains(result, expectedUrl)
    }

    @Test
    fun `should return 'Not a Git repository' when no repositories exist`() {
        // Arrange
        every { mockRepositoryManager.repositories } returns emptyList()

        val summary = RepositorySummary(
            project = mockProject,
            selectedFiles = arrayOf(mockVirtualFile),
            fileContents = listOf("--- test.txt ---\ntest content"),
            processedFileCount = 1,
            excludedByFilterCount = 0,
            excludedBySizeCount = 0,
            excludedByBinaryContentCount = 0,
            excludedByIgnoredNameCount = 0,
            excludedByGitignoreCount = 0
        )

        // Act
        val result = summary.generateSummary(AppConstants.OutputFormat.PLAIN_TEXT)

        // Assert
        assertContains(result, "Not a Git repository")
    }

    @Test
    fun `should return 'No remote configured' when repository has no remotes`() {
        // Arrange
        every { mockRepositoryManager.repositories } returns listOf(mockRepository)
        every { mockRepository.remotes } returns emptyList()
        every { mockRepository.root } returns mockVirtualFile

        val summary = RepositorySummary(
            project = mockProject,
            selectedFiles = arrayOf(mockVirtualFile),
            fileContents = listOf("--- test.txt ---\ntest content"),
            processedFileCount = 1,
            excludedByFilterCount = 0,
            excludedBySizeCount = 0,
            excludedByBinaryContentCount = 0,
            excludedByIgnoredNameCount = 0,
            excludedByGitignoreCount = 0
        )

        // Act
        val result = summary.generateSummary(AppConstants.OutputFormat.PLAIN_TEXT)

        // Assert
        assertContains(result, "No remote configured")
    }

    @Test
    fun `should choose repository with most files in multi-repo project`() {
        // Arrange
        val mockRepo1 = mockk<GitRepository>(relaxed = true)
        val mockRepo2 = mockk<GitRepository>(relaxed = true)
        val mockRoot1 = mockk<VirtualFile>(relaxed = true)
        val mockRoot2 = mockk<VirtualFile>(relaxed = true)
        val mockFile1 = mockk<VirtualFile>(relaxed = true)
        val mockFile2 = mockk<VirtualFile>(relaxed = true)
        val mockFile3 = mockk<VirtualFile>(relaxed = true)

        every { mockRepositoryManager.repositories } returns listOf(mockRepo1, mockRepo2)
        every { mockRepo1.root } returns mockRoot1
        every { mockRepo2.root } returns mockRoot2

        // Setup file hierarchy - 2 files in repo2, 1 file in repo1
        // Mock the parent chain for each file to properly reach the repository root
        var file1Parent: VirtualFile? = mockRoot1
        var file2Parent: VirtualFile? = mockRoot2
        var file3Parent: VirtualFile? = mockRoot2
        
        every { mockFile1.parent } answers { file1Parent }
        every { mockFile2.parent } answers { file2Parent }
        every { mockFile3.parent } answers { file3Parent }
        
        // Mock parent.parent to return null to terminate the loop
        every { mockRoot1.parent } returns null
        every { mockRoot2.parent } returns null

        val expectedUrl = "https://github.com/repo2/project"
        every { mockRemote.name } returns "origin"
        every { mockRemote.firstUrl } returns expectedUrl
        every { mockRepo2.remotes } returns listOf(mockRemote)
        every { mockRepo1.remotes } returns emptyList()

        val summary = RepositorySummary(
            project = mockProject,
            selectedFiles = arrayOf(mockFile1, mockFile2, mockFile3),
            fileContents = listOf("--- test.txt ---\ntest content"),
            processedFileCount = 3,
            excludedByFilterCount = 0,
            excludedBySizeCount = 0,
            excludedByBinaryContentCount = 0,
            excludedByIgnoredNameCount = 0,
            excludedByGitignoreCount = 0
        )

        // Act
        val result = summary.generateSummary(AppConstants.OutputFormat.PLAIN_TEXT)

        // Assert
        assertContains(result, expectedUrl)
    }

    @Test
    fun `should handle markdown format correctly with Git URL`() {
        // Arrange
        val expectedUrl = "https://github.com/test/project"
        every { mockRepositoryManager.repositories } returns listOf(mockRepository)
        every { mockRemote.name } returns "origin"
        every { mockRemote.firstUrl } returns expectedUrl
        every { mockRepository.remotes } returns listOf(mockRemote)
        every { mockRepository.root } returns mockVirtualFile

        val summary = RepositorySummary(
            project = mockProject,
            selectedFiles = arrayOf(mockVirtualFile),
            fileContents = listOf("--- test.txt ---\ntest content"),
            processedFileCount = 1,
            excludedByFilterCount = 0,
            excludedBySizeCount = 0,
            excludedByBinaryContentCount = 0,
            excludedByIgnoredNameCount = 0,
            excludedByGitignoreCount = 0
        )

        // Act
        val result = summary.generateSummary(AppConstants.OutputFormat.MARKDOWN)

        // Assert
        assertContains(result, "# Repository Info")
        assertContains(result, expectedUrl)
    }

    @Test
    fun `should handle XML format correctly with Git URL`() {
        // Arrange
        val expectedUrl = "https://github.com/test/project"
        every { mockRepositoryManager.repositories } returns listOf(mockRepository)
        every { mockRemote.name } returns "origin"
        every { mockRemote.firstUrl } returns expectedUrl
        every { mockRepository.remotes } returns listOf(mockRemote)
        every { mockRepository.root } returns mockVirtualFile

        val summary = RepositorySummary(
            project = mockProject,
            selectedFiles = arrayOf(mockVirtualFile),
            fileContents = listOf("--- test.txt ---\ntest content"),
            processedFileCount = 1,
            excludedByFilterCount = 0,
            excludedBySizeCount = 0,
            excludedByBinaryContentCount = 0,
            excludedByIgnoredNameCount = 0,
            excludedByGitignoreCount = 0
        )

        // Act
        val result = summary.generateSummary(AppConstants.OutputFormat.XML)

        // Assert
        assertContains(result, "<repository-url>")
        assertContains(result, expectedUrl)
        assertContains(result, "</repository-url>")
    }
}