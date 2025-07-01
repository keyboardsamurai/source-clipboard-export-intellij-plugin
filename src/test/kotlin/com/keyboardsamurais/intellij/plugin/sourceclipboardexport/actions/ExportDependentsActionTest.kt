package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import io.mockk.every
import io.mockk.mockk
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
}