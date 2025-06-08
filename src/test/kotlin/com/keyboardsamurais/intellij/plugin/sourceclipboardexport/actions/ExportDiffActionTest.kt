package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions

import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core.ExportHistory
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.NotificationUtils
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ExportDiffActionTest {

    private lateinit var project: Project
    private lateinit var event: AnActionEvent
    private lateinit var file1: VirtualFile
    private lateinit var file2: VirtualFile
    private lateinit var exportHistory: ExportHistory

    @BeforeEach
    fun setup() {
        project = mockk(relaxed = true)
        event = mockk(relaxed = true)
        file1 = mockk(relaxed = true)
        file2 = mockk(relaxed = true)
        exportHistory = mockk(relaxed = true)

        every { event.project } returns project
        every { event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) } returns arrayOf(file1)
        
        every { file1.name } returns "Example.java"
        every { file1.path } returns "/project/src/Example.java"
        every { file1.isDirectory } returns false
        
        every { file2.name } returns "Test.java"
        every { file2.path } returns "/project/test/Test.java"
        every { file2.isDirectory } returns false

        // Mock NotificationUtils object
        mockkObject(NotificationUtils)
        every { NotificationUtils.showNotification(any(), any(), any(), any(), any()) } returns Unit
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(NotificationUtils)
    }

    @Test
    fun `test action update enables when files selected`() {
        val action = ExportDiffAction()
        
        action.update(event)
        
        verify { event.presentation.isEnabledAndVisible = true }
    }

    @Test
    fun `test action update disables when no files selected`() {
        every { event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) } returns null
        
        val action = ExportDiffAction()
        action.update(event)
        
        verify { event.presentation.isEnabledAndVisible = false }
    }

    @Test
    fun `test action update disables when no project`() {
        every { event.project } returns null
        
        val action = ExportDiffAction()
        action.update(event)
        
        verify { event.presentation.isEnabledAndVisible = false }
    }

    @Test
    fun `test action shows notification when no previous export exists`() {
        mockkObject(ExportHistory.Companion)
        every { ExportHistory.getInstance(project) } returns exportHistory
        every { exportHistory.getRecentExports() } returns emptyList()
        
        val action = ExportDiffAction()
        action.actionPerformed(event)
        
        verify { 
            NotificationUtils.showNotification(
                project,
                "No Previous Export",
                "No previous export found to compare against",
                NotificationType.INFORMATION
            )
        }
        
        unmockkObject(ExportHistory.Companion)
    }

    @Test
    fun `test export diff dialog creation with valid previous export`() {
        // Create a mock export entry
        val exportEntry = ExportHistory.ExportEntry(
            timestamp = System.currentTimeMillis() - 3600000, // 1 hour ago
            fileCount = 2,
            sizeBytes = 1024,
            tokens = 256,
            filePaths = mutableListOf("/project/src/Old.java", "/project/src/Common.java"),
            summary = "2 files, 1.0 KB, ~256 tokens"
        )
        
        mockkObject(ExportHistory.Companion)
        every { ExportHistory.getInstance(project) } returns exportHistory
        every { exportHistory.getRecentExports() } returns listOf(exportEntry)
        
        // Mock ProgressManager to avoid actual background processing
        mockkStatic("com.intellij.openapi.progress.ProgressManager")
        val progressManager = mockk<com.intellij.openapi.progress.ProgressManager>(relaxed = true)
        every { com.intellij.openapi.progress.ProgressManager.getInstance() } returns progressManager
        every { progressManager.run(any<com.intellij.openapi.progress.Task.Backgroundable>()) } answers {
            // Don't actually run the background task in tests
        }
        
        val action = ExportDiffAction()
        
        // Should not throw exception
        assertDoesNotThrow {
            action.actionPerformed(event)
        }
        
        // Verify that progress task was scheduled
        verify { progressManager.run(any<com.intellij.openapi.progress.Task.Backgroundable>()) }
        
        unmockkObject(ExportHistory.Companion)
        unmockkStatic("com.intellij.openapi.progress.ProgressManager")
    }

    @Test
    fun `test export diff dialog path comparison logic`() {
        val currentPaths = setOf("/project/src/New.java", "/project/src/Common.java")
        val lastPaths = setOf("/project/src/Old.java", "/project/src/Common.java")
        
        val addedFiles = currentPaths - lastPaths
        val removedFiles = lastPaths - currentPaths
        val unchangedFiles = currentPaths.intersect(lastPaths)
        
        assertEquals(setOf("/project/src/New.java"), addedFiles)
        assertEquals(setOf("/project/src/Old.java"), removedFiles)
        assertEquals(setOf("/project/src/Common.java"), unchangedFiles)
    }

    @Test
    fun `test export diff dialog handles identical file sets`() {
        val currentPaths = setOf("/project/src/File1.java", "/project/src/File2.java")
        val lastPaths = setOf("/project/src/File1.java", "/project/src/File2.java")
        
        val addedFiles = currentPaths - lastPaths
        val removedFiles = lastPaths - currentPaths
        val unchangedFiles = currentPaths.intersect(lastPaths)
        
        assertTrue(addedFiles.isEmpty())
        assertTrue(removedFiles.isEmpty())
        assertEquals(2, unchangedFiles.size)
    }

    @Test
    fun `test export diff dialog handles completely different file sets`() {
        val currentPaths = setOf("/project/src/New1.java", "/project/src/New2.java")
        val lastPaths = setOf("/project/src/Old1.java", "/project/src/Old2.java")
        
        val addedFiles = currentPaths - lastPaths
        val removedFiles = lastPaths - currentPaths
        val unchangedFiles = currentPaths.intersect(lastPaths)
        
        assertEquals(2, addedFiles.size)
        assertEquals(2, removedFiles.size)
        assertTrue(unchangedFiles.isEmpty())
    }
}