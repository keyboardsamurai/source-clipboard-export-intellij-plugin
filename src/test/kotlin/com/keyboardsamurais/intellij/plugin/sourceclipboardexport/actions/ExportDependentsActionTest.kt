package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions

import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.ActionRunners
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.DependencyFinder
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.NotificationUtils
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ExportDependentsActionTest {
    
    private lateinit var action: ExportDependentsAction
    private lateinit var event: AnActionEvent
    private lateinit var project: Project
    private lateinit var file: VirtualFile
    
    @BeforeEach
    fun setUp() {
        action = ExportDependentsAction()
        event = mockk(relaxed = true)
        project = mockk(relaxed = true)
        file = mockk(relaxed = true)
    }
    
    @AfterEach
    fun tearDown() {
        unmockkAll()
    }
    
    @Test
    fun `test action presentation text and description`() {
        // Verify the action is properly initialized with new values
        assert(action.templatePresentation.text == "Include Reverse Dependencies")
        assert(action.templatePresentation.description == "Export selected files + all files that import/use them")
    }
    
    @Test
    fun `test update enables action when files selected`() {
        // Given
        every { event.project } returns project
        every { event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) } returns arrayOf(file)
        every { file.isDirectory } returns false
        
        // When
        action.update(event)
        
        // Then - now checks isEnabledAndVisible instead of isEnabled
        verify { event.presentation.isEnabledAndVisible = true }
    }
    
    @Test
    fun `test update disables action when no files selected`() {
        // Given
        every { event.project } returns project
        every { event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) } returns emptyArray()
        
        // When
        action.update(event)
        
        // Then
        verify { event.presentation.isEnabledAndVisible = false }
    }
    
    @Test
    fun `test update disables action when no project`() {
        // Given
        every { event.project } returns null
        every { event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) } returns arrayOf(file)
        
        // When
        action.update(event)
        
        // Then
        verify { event.presentation.isEnabledAndVisible = false }
    }
    
    @Test
    fun `test update disables action when only directories selected`() {
        // Given
        every { event.project } returns project
        every { event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) } returns arrayOf(file)
        every { file.isDirectory } returns true
        
        // When
        action.update(event)
        
        // Then
        verify { event.presentation.isEnabledAndVisible = false }
    }
    
    @Test
    fun `test update enables action when at least one file selected`() {
        // Given
        val directory = mockk<VirtualFile>(relaxed = true)
        every { directory.isDirectory } returns true
        every { file.isDirectory } returns false
        
        every { event.project } returns project
        every { event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) } returns arrayOf(directory, file)
        
        // When
        action.update(event)
        
        // Then
        verify { event.presentation.isEnabledAndVisible = true }
    }
    
    @Test
    fun `test getActionUpdateThread returns BGT`() {
        assert(action.actionUpdateThread == com.intellij.openapi.actionSystem.ActionUpdateThread.BGT)
    }

    @Test
    fun `actionPerformed exports dependents union`() {
        val dependent = mockk<VirtualFile>(relaxed = true)
        every { dependent.isDirectory } returns false
        val source = mockk<VirtualFile>(relaxed = true)
        every { source.isDirectory } returns false
        every { source.path } returns "/src/Main.kt"

        every { event.project } returns project
        every { event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) } returns arrayOf(source)

        mockkObject(ActionRunners)
        val indicator = mockk<ProgressIndicator>(relaxed = true)
        every { ActionRunners.runSmartBackground(project, any(), any(), any()) } answers {
            val task = arg<(ProgressIndicator) -> Unit>(3)
            task(indicator)
        }

        mockkObject(DependencyFinder)
        coEvery { DependencyFinder.findDependents(any(), project) } returns setOf(dependent)

        mockkObject(SmartExportUtils)
        io.mockk.justRun { SmartExportUtils.exportFiles(any(), any()) }

        mockkObject(NotificationUtils)
        every { NotificationUtils.showNotification(any(), any(), any(), any()) } just Runs

        action.actionPerformed(event)

        verify {
            SmartExportUtils.exportFiles(
                project,
                match { files -> files.toSet() == setOf(source, dependent) }
            )
        }
        verify(exactly = 0) {
            NotificationUtils.showNotification(project, any(), match { it.contains("No dependent") }, NotificationType.INFORMATION)
        }
    }

    @Test
    fun `actionPerformed informs when no dependents`() {
        val source = mockk<VirtualFile>(relaxed = true)
        every { source.isDirectory } returns false

        every { event.project } returns project
        every { event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) } returns arrayOf(source)

        mockkObject(ActionRunners)
        val indicator = mockk<ProgressIndicator>(relaxed = true)
        every { ActionRunners.runSmartBackground(project, any(), any(), any()) } answers {
            arg<(ProgressIndicator) -> Unit>(3).invoke(indicator)
        }

        mockkObject(DependencyFinder)
        coEvery { DependencyFinder.findDependents(any(), project) } returns emptySet()

        mockkObject(SmartExportUtils)
        io.mockk.justRun { SmartExportUtils.exportFiles(any(), any()) }

        mockkObject(NotificationUtils)
        every { NotificationUtils.showNotification(any(), any(), any(), any()) } just Runs

        action.actionPerformed(event)

        verify {
            NotificationUtils.showNotification(
                project,
                "Export Info",
                match { it.contains("No dependent files found") },
                NotificationType.INFORMATION
            )
        }
    }

    @Test
    fun `actionPerformed warns when hitting dependent limit`() {
        val source = mockk<VirtualFile>(relaxed = true)
        every { source.isDirectory } returns false
        every { event.project } returns project
        every { event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) } returns arrayOf(source)

        mockkObject(ActionRunners)
        val indicator = mockk<ProgressIndicator>(relaxed = true)
        every { ActionRunners.runSmartBackground(project, any(), any(), any()) } answers {
            arg<(ProgressIndicator) -> Unit>(3).invoke(indicator)
        }

        val limit = DependencyFinder.Config.maxResultsPerSearch
        val dependents = (0 until limit).map {
            mockk<VirtualFile>(relaxed = true).also { every { it.isDirectory } returns false }
        }.toSet()

        mockkObject(DependencyFinder)
        coEvery { DependencyFinder.findDependents(any(), project) } returns dependents

        mockkObject(SmartExportUtils)
        io.mockk.justRun { SmartExportUtils.exportFiles(any(), any()) }

        mockkObject(NotificationUtils)
        every { NotificationUtils.showNotification(any(), any(), any(), any()) } just Runs

        action.actionPerformed(event)

        verify {
            NotificationUtils.showNotification(
                project,
                "Export Info",
                match { it.contains("configured limit") },
                NotificationType.INFORMATION
            )
        }
    }
}
