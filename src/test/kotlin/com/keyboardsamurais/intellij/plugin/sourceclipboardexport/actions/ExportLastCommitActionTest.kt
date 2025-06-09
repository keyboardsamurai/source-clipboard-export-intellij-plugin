package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions

import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.NotificationUtils
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.unmockkConstructor
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

class ExportLastCommitActionTest {

    private lateinit var action: ExportLastCommitAction
    private lateinit var mockProject: Project
    private lateinit var mockEvent: AnActionEvent
    private lateinit var mockDataContext: DataContext
    private lateinit var mockLogger: Logger
    private lateinit var mockVirtualFileManager: VirtualFileManager
    private lateinit var mockVirtualFile: VirtualFile

    @BeforeEach
    fun setUp() {
        // Mock static components
        mockkStatic(Logger::class)
        mockkStatic(VirtualFileManager::class)
        mockkObject(NotificationUtils)
        mockkObject(SmartExportUtils)

        // Initialize mocks
        mockProject = mockk(relaxed = true)
        mockEvent = mockk(relaxed = true)
        mockDataContext = mockk(relaxed = true)
        mockLogger = mockk(relaxed = true)
        mockVirtualFileManager = mockk(relaxed = true)
        mockVirtualFile = mockk(relaxed = true)

        // Set up default mock behaviors
        every { Logger.getInstance(ExportLastCommitAction::class.java) } returns mockLogger
        every { mockEvent.project } returns mockProject
        every { mockEvent.dataContext } returns mockDataContext
        every { mockDataContext.getData(CommonDataKeys.PROJECT) } returns mockProject
        every { VirtualFileManager.getInstance() } returns mockVirtualFileManager
        every { NotificationUtils.showNotification(any(), any(), any(), any()) } just runs
        every { SmartExportUtils.exportFiles(any(), any()) } just runs

        // Create the action
        action = ExportLastCommitAction()
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `actionPerformed shows notification when no project`() {
        // Arrange
        every { mockEvent.project } returns null

        // Act
        action.actionPerformed(mockEvent)

        // Assert
        verify(exactly = 0) { NotificationUtils.showNotification(any(), any(), any(), any()) }
        verify(exactly = 0) { SmartExportUtils.exportFiles(any(), any()) }
    }

    @Test
    fun `actionPerformed shows notification when no git directory`() {
        // Arrange
        every { mockProject.basePath } returns "/tmp/test-project"
        
        // Mock File to simulate no .git directory
        mockkConstructor(File::class)
        every { anyConstructed<File>().exists() } returns false

        // Act
        action.actionPerformed(mockEvent)

        // Assert
        verify { 
            NotificationUtils.showNotification(
                mockProject,
                "No Files Found",
                "No files found in the last commit",
                NotificationType.INFORMATION
            )
        }
        verify(exactly = 0) { SmartExportUtils.exportFiles(any(), any()) }

        unmockkConstructor(File::class)
    }

    @Test
    fun `update enables action when project has git directory`() {
        // Arrange
        every { mockProject.basePath } returns "/tmp/test-project"
        
        // Mock File to simulate .git directory exists
        mockkConstructor(File::class)
        every { anyConstructed<File>().exists() } returns true
        every { anyConstructed<File>().isDirectory } returns true

        // Act
        action.update(mockEvent)

        // Assert
        verify { 
            mockEvent.presentation.isEnabledAndVisible = true
            mockEvent.presentation.text = "Last Commit Files"
            mockEvent.presentation.description = "Export files changed in the most recent commit"
        }

        unmockkConstructor(File::class)
    }

    @Test
    fun `update disables action when project has no git directory`() {
        // Arrange
        every { mockProject.basePath } returns "/tmp/test-project"
        
        // Mock File to simulate no .git directory
        mockkConstructor(File::class)
        every { anyConstructed<File>().exists() } returns false

        // Act
        action.update(mockEvent)

        // Assert
        verify { mockEvent.presentation.isEnabledAndVisible = false }

        unmockkConstructor(File::class)
    }

    @Test
    fun `update disables action when no project`() {
        // Arrange
        every { mockEvent.project } returns null

        // Act
        action.update(mockEvent)

        // Assert
        verify { mockEvent.presentation.isEnabledAndVisible = false }
    }

    @Test
    fun `update handles exception when checking for git`() {
        // Arrange
        every { mockProject.basePath } throws RuntimeException("Error accessing project path")

        // Act
        action.update(mockEvent)

        // Assert
        verify { mockEvent.presentation.isEnabledAndVisible = false }
    }

    @Test
    fun `getActionUpdateThread returns BGT`() {
        // Act
        val thread = action.actionUpdateThread

        // Assert
        assertEquals(ActionUpdateThread.BGT, thread)
    }
}