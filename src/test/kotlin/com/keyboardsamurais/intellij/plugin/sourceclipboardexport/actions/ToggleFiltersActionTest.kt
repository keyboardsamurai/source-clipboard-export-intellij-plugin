package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ToggleFiltersActionTest {
    
    private lateinit var action: ToggleFiltersAction
    private lateinit var event: AnActionEvent
    private lateinit var project: Project
    
    @BeforeEach
    fun setUp() {
        action = ToggleFiltersAction()
        event = mockk(relaxed = true)
        project = mockk(relaxed = true)
    }
    
    @Test
    fun `test update disables action when no project`() {
        // Given
        every { event.project } returns null
        
        // When
        action.update(event)
        
        // Then
        verify { event.presentation.isEnabledAndVisible = false }
    }
    
    @Test
    fun `test update enables action when project exists`() {
        // Given
        every { event.project } returns project
        
        // When
        action.update(event)
        
        // Then
        verify { event.presentation.isEnabledAndVisible = true }
    }
    
    @Test
    fun `test getActionUpdateThread returns EDT`() {
        assert(action.actionUpdateThread == com.intellij.openapi.actionSystem.ActionUpdateThread.EDT)
    }
} 