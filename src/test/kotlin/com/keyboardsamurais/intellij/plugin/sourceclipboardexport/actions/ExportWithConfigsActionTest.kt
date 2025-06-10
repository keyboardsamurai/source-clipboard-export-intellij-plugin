package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ExportWithConfigsActionTest {
    
    private lateinit var action: ExportWithConfigsAction
    private lateinit var event: AnActionEvent
    private lateinit var project: Project
    private lateinit var sourceFile: VirtualFile
    
    @BeforeEach
    fun setUp() {
        action = ExportWithConfigsAction()
        event = mockk(relaxed = true)
        project = mockk(relaxed = true)
        sourceFile = mockk(relaxed = true)
    }
    
    @Test
    fun `test action presentation text and description`() {
        assert(action.templatePresentation.text == "Include Configuration")
        assert(action.templatePresentation.description == "Export selected files with configuration files")
    }
    
    @Test
    fun `test update enables action when project and files exist`() {
        // Given
        every { event.project } returns project
        every { event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) } returns arrayOf(sourceFile)
        
        // When
        action.update(event)
        
        // Then
        verify { event.presentation.isEnabledAndVisible = true }
    }
    
    @Test
    fun `test update disables action when no project`() {
        // Given
        every { event.project } returns null
        every { event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) } returns arrayOf(sourceFile)
        
        // When
        action.update(event)
        
        // Then
        verify { event.presentation.isEnabledAndVisible = false }
    }
    
    @Test
    fun `test update disables action when no files selected`() {
        // Given
        every { event.project } returns project
        every { event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) } returns null
        
        // When
        action.update(event)
        
        // Then
        verify { event.presentation.isEnabledAndVisible = false }
    }
    
    @Test
    fun `test update disables action when empty file array`() {
        // Given
        every { event.project } returns project
        every { event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) } returns emptyArray()
        
        // When
        action.update(event)
        
        // Then
        verify { event.presentation.isEnabledAndVisible = false }
    }
    
    @Test
    fun `test getActionUpdateThread returns BGT`() {
        assert(action.actionUpdateThread == com.intellij.openapi.actionSystem.ActionUpdateThread.BGT)
    }
} 