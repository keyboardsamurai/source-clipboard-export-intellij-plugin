package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions

import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.SelectionModel
import com.intellij.openapi.project.Project
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class FoldAndCopyStackTraceActionTest {
    
    private lateinit var action: FoldAndCopyStackTraceAction
    private lateinit var event: AnActionEvent
    private lateinit var project: Project
    private lateinit var editor: Editor
    private lateinit var consoleView: ConsoleView
    private lateinit var selectionModel: SelectionModel
    
    @BeforeEach
    fun setUp() {
        action = FoldAndCopyStackTraceAction()
        event = mockk(relaxed = true)
        project = mockk(relaxed = true)
        editor = mockk(relaxed = true)
        consoleView = mockk(relaxed = true)
        selectionModel = mockk(relaxed = true)
        
        every { editor.selectionModel } returns selectionModel
    }
    
    @Test
    fun `test update enables action when all conditions met`() {
        // Given
        every { event.project } returns project
        every { event.getData(CommonDataKeys.EDITOR) } returns editor
        every { event.getData(LangDataKeys.CONSOLE_VIEW) } returns consoleView
        every { selectionModel.selectedText } returns "Exception stack trace text"
        
        // When
        action.update(event)
        
        // Then
        verify { event.presentation.isEnabledAndVisible = true }
    }
    
    @Test
    fun `test update disables action when no project`() {
        // Given
        every { event.project } returns null
        every { event.getData(CommonDataKeys.EDITOR) } returns editor
        every { event.getData(LangDataKeys.CONSOLE_VIEW) } returns consoleView
        every { selectionModel.selectedText } returns "Exception stack trace text"
        
        // When
        action.update(event)
        
        // Then
        verify { event.presentation.isEnabledAndVisible = false }
    }
    
    @Test
    fun `test update disables action when no editor`() {
        // Given
        every { event.project } returns project
        every { event.getData(CommonDataKeys.EDITOR) } returns null
        every { event.getData(LangDataKeys.CONSOLE_VIEW) } returns consoleView
        
        // When
        action.update(event)
        
        // Then
        verify { event.presentation.isEnabledAndVisible = false }
    }
    
    @Test
    fun `test update disables action when no console view`() {
        // Given
        every { event.project } returns project
        every { event.getData(CommonDataKeys.EDITOR) } returns editor
        every { event.getData(LangDataKeys.CONSOLE_VIEW) } returns null
        every { selectionModel.selectedText } returns "Exception stack trace text"
        
        // When
        action.update(event)
        
        // Then
        verify { event.presentation.isEnabledAndVisible = false }
    }
    
    @Test
    fun `test update disables action when no text selected`() {
        // Given
        every { event.project } returns project
        every { event.getData(CommonDataKeys.EDITOR) } returns editor
        every { event.getData(LangDataKeys.CONSOLE_VIEW) } returns consoleView
        every { selectionModel.selectedText } returns null
        
        // When
        action.update(event)
        
        // Then
        verify { event.presentation.isEnabledAndVisible = false }
    }
    
    @Test
    fun `test update disables action when empty text selected`() {
        // Given
        every { event.project } returns project
        every { event.getData(CommonDataKeys.EDITOR) } returns editor
        every { event.getData(LangDataKeys.CONSOLE_VIEW) } returns consoleView
        every { selectionModel.selectedText } returns ""
        
        // When
        action.update(event)
        
        // Then
        verify { event.presentation.isEnabledAndVisible = false }
    }
    
    @Test
    fun `test update disables action when blank text selected`() {
        // Given
        every { event.project } returns project
        every { event.getData(CommonDataKeys.EDITOR) } returns editor
        every { event.getData(LangDataKeys.CONSOLE_VIEW) } returns consoleView
        every { selectionModel.selectedText } returns "   "
        
        // When
        action.update(event)
        
        // Then
        verify { event.presentation.isEnabledAndVisible = false }
    }
    
    @Test
    fun `test getActionUpdateThread returns EDT`() {
        assert(action.actionUpdateThread == com.intellij.openapi.actionSystem.ActionUpdateThread.EDT)
    }
} 