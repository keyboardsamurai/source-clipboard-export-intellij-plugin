package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.testutils.ActionRunnersTestSetup
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.NotificationUtils
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.RelatedFileFinder
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SmartExportActionsTest {

    private lateinit var project: Project
    private lateinit var event: AnActionEvent
    private lateinit var file1: VirtualFile
    private lateinit var file2: VirtualFile

    @BeforeEach
    fun setup() {
        project = mockk(relaxed = true)
        event = mockk(relaxed = true)
        file1 = mockk(relaxed = true)
        file2 = mockk(relaxed = true)

        // Set up ActionRunners mocks
        ActionRunnersTestSetup.setupMocks(project)

        every { event.project } returns project
        every { event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) } returns arrayOf(file1)
        
        every { file1.name } returns "Example.java"
        every { file1.path } returns "/project/src/Example.java"
        every { file1.isDirectory } returns false
        
        every { file2.name } returns "ExampleTest.java"
        every { file2.path } returns "/project/test/ExampleTest.java"
        every { file2.isDirectory } returns false
    }
    
    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `test smart export group update enables when files selected`() {
        val group = SmartExportGroup()
        
        group.update(event)
        
        verify { event.presentation.isEnabledAndVisible = true }
    }

    @Test
    fun `test smart export group update disables when no files selected`() {
        every { event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) } returns null
        
        val group = SmartExportGroup()
        group.update(event)
        
        verify { event.presentation.isEnabledAndVisible = false }
    }

    @Test
    fun `test export with tests action`() {
        mockkObject(RelatedFileFinder)
        every { RelatedFileFinder.findTestFiles(project, file1) } returns listOf(file2)
        
        mockkObject(SmartExportUtils)
        every { SmartExportUtils.exportFiles(project, any()) } returns Unit
        
        val action = ExportWithTestsAction()
        action.actionPerformed(event)
        
        verify { SmartExportUtils.exportFiles(project, match { files ->
            files.contains(file1) && files.contains(file2)
        }) }
        
        unmockkObject(RelatedFileFinder)
        unmockkObject(SmartExportUtils)
    }

    @Test
    fun `test export with configs action`() {
        val configFile = mockk<VirtualFile>(relaxed = true)
        every { configFile.name } returns "pom.xml"
        every { configFile.path } returns "/project/pom.xml"
        
        mockkObject(RelatedFileFinder)
        every { RelatedFileFinder.findConfigFiles(project, file1) } returns listOf(configFile)
        
        mockkObject(SmartExportUtils)
        every { SmartExportUtils.exportFiles(project, any()) } returns Unit
        
        val action = ExportWithConfigsAction()
        action.actionPerformed(event)
        
        verify { SmartExportUtils.exportFiles(project, match { files ->
            files.contains(file1) && files.contains(configFile)
        }) }
        
        unmockkObject(RelatedFileFinder)
        unmockkObject(SmartExportUtils)
    }

    @Test
    fun `test export recent changes action with files found`() {
        mockkObject(RelatedFileFinder)
        every { RelatedFileFinder.findRecentChanges(project, 24) } returns listOf(file1, file2)
        
        mockkObject(SmartExportUtils)
        every { SmartExportUtils.exportFiles(project, any()) } returns Unit
        
        val action = ExportRecentChangesAction()
        action.actionPerformed(event)
        
        verify { SmartExportUtils.exportFiles(project, match { files ->
            files.contains(file1) && files.contains(file2)
        }) }
        
        unmockkObject(RelatedFileFinder)
        unmockkObject(SmartExportUtils)
    }

    @Test
    fun `test export recent changes action with no files found`() {
        mockkObject(RelatedFileFinder)
        every { RelatedFileFinder.findRecentChanges(project, 24) } returns emptyList()
        
        // Mock notification utils to avoid actual notifications
        mockkObject(NotificationUtils)
        
        val action = ExportRecentChangesAction()
        action.actionPerformed(event)
        
        // Should show notification about no recent changes
        verify { 
            NotificationUtils.showNotification(
                project, 
                "No Recent Changes", 
                any(), 
                any(),
                any()
            )
        }
        
        unmockkObject(RelatedFileFinder)
        unmockkObject(NotificationUtils)
    }

    @Test
    fun `test export with direct imports action`() {
        val importFile = mockk<VirtualFile>(relaxed = true)
        every { importFile.name } returns "ImportedClass.java"
        every { importFile.path } returns "/project/src/ImportedClass.java"
        
        mockkObject(RelatedFileFinder)
        every { RelatedFileFinder.findDirectImports(project, file1) } returns listOf(importFile)
        
        mockkObject(SmartExportUtils)
        every { SmartExportUtils.exportFiles(project, any()) } returns Unit
        
        val action = ExportWithDirectImportsAction()
        action.actionPerformed(event)
        
        verify { SmartExportUtils.exportFiles(project, match { files ->
            files.contains(file1) && files.contains(importFile)
        }) }
        
        unmockkObject(RelatedFileFinder)
        unmockkObject(SmartExportUtils)
    }

    @Test
    fun `test export with transitive imports action`() {
        val transitiveFile = mockk<VirtualFile>(relaxed = true)
        every { transitiveFile.name } returns "TransitiveClass.java"
        every { transitiveFile.path } returns "/project/src/TransitiveClass.java"
        
        mockkObject(RelatedFileFinder)
        every { RelatedFileFinder.findTransitiveImports(project, file1) } returns listOf(transitiveFile)
        
        mockkObject(SmartExportUtils)
        every { SmartExportUtils.exportFiles(project, any()) } returns Unit
        
        val action = ExportWithTransitiveImportsAction()
        action.actionPerformed(event)
        
        verify { SmartExportUtils.exportFiles(project, match { files ->
            files.contains(file1) && files.contains(transitiveFile)
        }) }
        
        unmockkObject(RelatedFileFinder)
        unmockkObject(SmartExportUtils)
    }
}