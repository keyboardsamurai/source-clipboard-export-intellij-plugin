package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions.groups

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ActionGroupsTest {
    
    private lateinit var event: AnActionEvent
    private lateinit var project: Project
    private lateinit var sourceFile: VirtualFile
    
    @BeforeEach
    fun setUp() {
        event = mockk(relaxed = true)
        project = mockk(relaxed = true)
        sourceFile = mockk(relaxed = true)
        
        every { sourceFile.isDirectory } returns false
    }
    
    // CodeStructureExportGroup Tests
    @Test
    fun `test CodeStructureExportGroup update enables when project and files exist`() {
        // Given
        val group = CodeStructureExportGroup()
        every { event.project } returns project
        every { event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) } returns arrayOf(sourceFile)
        
        // When
        group.update(event)
        
        // Then
        verify { event.presentation.isEnabledAndVisible = true }
    }
    
    @Test
    fun `test CodeStructureExportGroup update disables when no project`() {
        // Given
        val group = CodeStructureExportGroup()
        every { event.project } returns null
        every { event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) } returns arrayOf(sourceFile)
        
        // When
        group.update(event)
        
        // Then
        verify { event.presentation.isEnabledAndVisible = false }
    }
    
    @Test
    fun `test CodeStructureExportGroup update disables when no files`() {
        // Given
        val group = CodeStructureExportGroup()
        every { event.project } returns project
        every { event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) } returns null
        
        // When
        group.update(event)
        
        // Then
        verify { event.presentation.isEnabledAndVisible = false }
    }
    
    @Test
    fun `test CodeStructureExportGroup update disables when empty files`() {
        // Given
        val group = CodeStructureExportGroup()
        every { event.project } returns project
        every { event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) } returns emptyArray()
        
        // When
        group.update(event)
        
        // Then
        verify { event.presentation.isEnabledAndVisible = false }
    }
    
    @Test
    fun `test CodeStructureExportGroup getChildren returns correct actions`() {
        // Given
        val group = CodeStructureExportGroup()
        
        // When
        val children = group.getChildren(event)
        
        // Then
        assert(children.size == 3)
        assert(children.any { it.javaClass.simpleName == "ExportWithTestsAction" })
        assert(children.any { it.javaClass.simpleName == "ExportWithImplementationsAction" })
        assert(children.any { it.javaClass.simpleName == "ExportCurrentPackageAction" })
    }
    
    @Test
    fun `test CodeStructureExportGroup getActionUpdateThread returns BGT`() {
        val group = CodeStructureExportGroup()
        assert(group.actionUpdateThread == com.intellij.openapi.actionSystem.ActionUpdateThread.BGT)
    }
    
    // DependencyExportGroup Tests
    @Test
    fun `test DependencyExportGroup update enables when project and files exist`() {
        // Given
        val group = DependencyExportGroup()
        every { event.project } returns project
        every { event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) } returns arrayOf(sourceFile)
        
        // When
        group.update(event)
        
        // Then
        verify { event.presentation.isEnabledAndVisible = true }
    }
    
    @Test
    fun `test DependencyExportGroup update disables when no project`() {
        // Given
        val group = DependencyExportGroup()
        every { event.project } returns null
        every { event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) } returns arrayOf(sourceFile)
        
        // When
        group.update(event)
        
        // Then
        verify { event.presentation.isEnabledAndVisible = false }
    }
    
    @Test
    fun `test DependencyExportGroup getChildren returns correct actions`() {
        // Given
        val group = DependencyExportGroup()
        
        // When
        val children = group.getChildren(event)
        
        // Then
        assert(children.size == 3)
        assert(children.any { it.javaClass.simpleName == "ExportWithDirectImportsAction" })
        assert(children.any { it.javaClass.simpleName == "ExportWithTransitiveImportsAction" })
        assert(children.any { it.javaClass.simpleName == "ExportDependentsAction" })
    }
    
    // RelatedResourcesExportGroup Tests
    @Test
    fun `test RelatedResourcesExportGroup update enables when project and files exist`() {
        // Given
        val group = RelatedResourcesExportGroup()
        every { event.project } returns project
        every { event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) } returns arrayOf(sourceFile)
        
        // When
        group.update(event)
        
        // Then
        verify { event.presentation.isEnabledAndVisible = true }
    }
    
    @Test
    fun `test RelatedResourcesExportGroup update disables when no files`() {
        // Given
        val group = RelatedResourcesExportGroup()
        every { event.project } returns project
        every { event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) } returns null
        
        // When
        group.update(event)
        
        // Then
        verify { event.presentation.isEnabledAndVisible = false }
    }
    
    @Test
    fun `test RelatedResourcesExportGroup getChildren returns correct actions`() {
        // Given
        val group = RelatedResourcesExportGroup()
        
        // When
        val children = group.getChildren(event)
        
        // Then
        assert(children.size == 3)
        assert(children.any { it.javaClass.simpleName == "ExportWithResourcesAction" })
        assert(children.any { it.javaClass.simpleName == "ExportWithConfigsAction" })
        assert(children.any { it.javaClass.simpleName == "ExportAllTestsAction" })
    }
    
    // VersionHistoryExportGroup Tests
    @Test
    fun `test VersionHistoryExportGroup update enables when project and files exist`() {
        // Given
        val group = VersionHistoryExportGroup()
        every { event.project } returns project
        every { event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) } returns arrayOf(sourceFile)
        
        // When
        group.update(event)
        
        // Then
        verify { event.presentation.isEnabledAndVisible = true }
    }
    
    @Test
    fun `test VersionHistoryExportGroup update disables when no project`() {
        // Given
        val group = VersionHistoryExportGroup()
        every { event.project } returns null
        every { event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) } returns arrayOf(sourceFile)
        
        // When
        group.update(event)
        
        // Then
        verify { event.presentation.isEnabledAndVisible = false }
    }
    
    @Test
    fun `test VersionHistoryExportGroup getChildren returns correct actions`() {
        // Given
        val group = VersionHistoryExportGroup()
        
        // When
        val children = group.getChildren(event)
        
        // Then
        assert(children.size == 2)
        assert(children.any { it.javaClass.simpleName == "ExportLastCommitAction" })
        assert(children.any { it.javaClass.simpleName == "ExportRecentChangesAction" })
    }
} 