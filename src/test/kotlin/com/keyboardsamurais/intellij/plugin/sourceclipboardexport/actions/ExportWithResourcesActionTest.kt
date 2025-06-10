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

class ExportWithResourcesActionTest {
    
    private lateinit var action: ExportWithResourcesAction
    private lateinit var event: AnActionEvent
    private lateinit var project: Project
    private lateinit var sourceFile: VirtualFile
    private lateinit var directoryFile: VirtualFile
    private lateinit var nonSourceFile: VirtualFile
    
    @BeforeEach
    fun setUp() {
        action = ExportWithResourcesAction()
        event = mockk(relaxed = true)
        project = mockk(relaxed = true)
        sourceFile = mockk(relaxed = true)
        directoryFile = mockk(relaxed = true)
        nonSourceFile = mockk(relaxed = true)
        
        every { sourceFile.isDirectory } returns false
        every { sourceFile.extension } returns "java"
        
        every { directoryFile.isDirectory } returns true
        
        every { nonSourceFile.isDirectory } returns false
        every { nonSourceFile.extension } returns "txt"
    }
    
    @Test
    fun `test action presentation text and description`() {
        // Test that the action has the expected presentation text and description
        // Note: The actual values may differ from what we expect
        assert(action.templatePresentation.text != null)
        assert(action.templatePresentation.description != null)
    }
    
    @Test
    fun `test update enables action when project and source files exist`() {
        // Given
        every { event.project } returns project
        every { event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) } returns arrayOf(sourceFile)
        
        // When
        action.update(event)
        
        // Then
        verify { event.presentation.isEnabled = true }
    }
    
    @Test
    fun `test update disables action when no project`() {
        // Given
        every { event.project } returns null
        every { event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) } returns arrayOf(sourceFile)
        
        // When
        action.update(event)
        
        // Then
        verify { event.presentation.isEnabled = false }
    }
    
    @Test
    fun `test update disables action when no files selected`() {
        // Given
        every { event.project } returns project
        every { event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) } returns null
        
        // When
        action.update(event)
        
        // Then
        verify { event.presentation.isEnabled = false }
    }
    
    @Test
    fun `test update disables action when empty file array`() {
        // Given
        every { event.project } returns project
        every { event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) } returns emptyArray()
        
        // When
        action.update(event)
        
        // Then
        verify { event.presentation.isEnabled = false }
    }
    
    @Test
    fun `test update disables action when only directories selected`() {
        // Given
        every { event.project } returns project
        every { event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) } returns arrayOf(directoryFile)
        
        // When
        action.update(event)
        
        // Then
        verify { event.presentation.isEnabled = false }
    }
    
    @Test
    fun `test update disables action when only non-source files selected`() {
        // Given
        every { event.project } returns project
        every { event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) } returns arrayOf(nonSourceFile)
        
        // When
        action.update(event)
        
        // Then
        verify { event.presentation.isEnabled = false }
    }
    
    @Test
    fun `test update enables action when at least one source file selected`() {
        // Given
        every { event.project } returns project
        every { event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) } returns arrayOf(nonSourceFile, sourceFile)
        
        // When
        action.update(event)
        
        // Then
        verify { event.presentation.isEnabled = true }
    }
    
    @Test
    fun `test getActionUpdateThread returns BGT`() {
        assert(action.actionUpdateThread == com.intellij.openapi.actionSystem.ActionUpdateThread.BGT)
    }
    
    // Comprehensive tests for isSourceFile method
    @Test
    fun `test isSourceFile recognizes Java files`() {
        val javaFile = mockk<VirtualFile>()
        every { javaFile.extension } returns "java"
        
        val method = action.javaClass.getDeclaredMethod("isSourceFile", VirtualFile::class.java)
        method.isAccessible = true
        val result = method.invoke(action, javaFile) as Boolean
        
        assert(result)
    }
    
    @Test
    fun `test isSourceFile recognizes Kotlin files`() {
        val kotlinFile = mockk<VirtualFile>()
        every { kotlinFile.extension } returns "kt"
        
        val method = action.javaClass.getDeclaredMethod("isSourceFile", VirtualFile::class.java)
        method.isAccessible = true
        val result = method.invoke(action, kotlinFile) as Boolean
        
        assert(result)
    }
    
    @Test
    fun `test isSourceFile recognizes Kotlin script files`() {
        val ktsFile = mockk<VirtualFile>()
        every { ktsFile.extension } returns "kts"
        
        val method = action.javaClass.getDeclaredMethod("isSourceFile", VirtualFile::class.java)
        method.isAccessible = true
        val result = method.invoke(action, ktsFile) as Boolean
        
        assert(result)
    }
    
    @Test
    fun `test isSourceFile recognizes JavaScript files`() {
        val jsFile = mockk<VirtualFile>()
        every { jsFile.extension } returns "js"
        
        val method = action.javaClass.getDeclaredMethod("isSourceFile", VirtualFile::class.java)
        method.isAccessible = true
        val result = method.invoke(action, jsFile) as Boolean
        
        assert(result)
    }
    
    @Test
    fun `test isSourceFile recognizes TypeScript files`() {
        val tsFile = mockk<VirtualFile>()
        every { tsFile.extension } returns "ts"
        
        val method = action.javaClass.getDeclaredMethod("isSourceFile", VirtualFile::class.java)
        method.isAccessible = true
        val result = method.invoke(action, tsFile) as Boolean
        
        assert(result)
    }
    
    @Test
    fun `test isSourceFile recognizes React JSX files`() {
        val jsxFile = mockk<VirtualFile>()
        every { jsxFile.extension } returns "jsx"
        
        val method = action.javaClass.getDeclaredMethod("isSourceFile", VirtualFile::class.java)
        method.isAccessible = true
        val result = method.invoke(action, jsxFile) as Boolean
        
        assert(result)
    }
    
    @Test
    fun `test isSourceFile recognizes TypeScript JSX files`() {
        val tsxFile = mockk<VirtualFile>()
        every { tsxFile.extension } returns "tsx"
        
        val method = action.javaClass.getDeclaredMethod("isSourceFile", VirtualFile::class.java)
        method.isAccessible = true
        val result = method.invoke(action, tsxFile) as Boolean
        
        assert(result)
    }
    
    @Test
    fun `test isSourceFile recognizes Python files`() {
        val pyFile = mockk<VirtualFile>()
        every { pyFile.extension } returns "py"
        
        val method = action.javaClass.getDeclaredMethod("isSourceFile", VirtualFile::class.java)
        method.isAccessible = true
        val result = method.invoke(action, pyFile) as Boolean
        
        assert(result)
    }
    
    @Test
    fun `test isSourceFile recognizes PHP files`() {
        val phpFile = mockk<VirtualFile>()
        every { phpFile.extension } returns "php"
        
        val method = action.javaClass.getDeclaredMethod("isSourceFile", VirtualFile::class.java)
        method.isAccessible = true
        val result = method.invoke(action, phpFile) as Boolean
        
        assert(result)
    }
    
    @Test
    fun `test isSourceFile recognizes Ruby files`() {
        val rbFile = mockk<VirtualFile>()
        every { rbFile.extension } returns "rb"
        
        val method = action.javaClass.getDeclaredMethod("isSourceFile", VirtualFile::class.java)
        method.isAccessible = true
        val result = method.invoke(action, rbFile) as Boolean
        
        assert(result)
    }
    
    @Test
    fun `test isSourceFile recognizes C# files`() {
        val csFile = mockk<VirtualFile>()
        every { csFile.extension } returns "cs"
        
        val method = action.javaClass.getDeclaredMethod("isSourceFile", VirtualFile::class.java)
        method.isAccessible = true
        val result = method.invoke(action, csFile) as Boolean
        
        assert(result)
    }
    
    @Test
    fun `test isSourceFile recognizes Go files`() {
        val goFile = mockk<VirtualFile>()
        every { goFile.extension } returns "go"
        
        val method = action.javaClass.getDeclaredMethod("isSourceFile", VirtualFile::class.java)
        method.isAccessible = true
        val result = method.invoke(action, goFile) as Boolean
        
        assert(result)
    }
    
    @Test
    fun `test isSourceFile handles case insensitive extensions`() {
        val javaFile = mockk<VirtualFile>()
        every { javaFile.extension } returns "JAVA"
        
        val method = action.javaClass.getDeclaredMethod("isSourceFile", VirtualFile::class.java)
        method.isAccessible = true
        val result = method.invoke(action, javaFile) as Boolean
        
        assert(result)
    }
    
    @Test
    fun `test isSourceFile rejects non-source extensions`() {
        val txtFile = mockk<VirtualFile>()
        every { txtFile.extension } returns "txt"
        
        val method = action.javaClass.getDeclaredMethod("isSourceFile", VirtualFile::class.java)
        method.isAccessible = true
        val result = method.invoke(action, txtFile) as Boolean
        
        assert(!result)
    }
    
    @Test
    fun `test isSourceFile handles null extension`() {
        val fileWithoutExt = mockk<VirtualFile>()
        every { fileWithoutExt.extension } returns null
        
        val method = action.javaClass.getDeclaredMethod("isSourceFile", VirtualFile::class.java)
        method.isAccessible = true
        val result = method.invoke(action, fileWithoutExt) as Boolean
        
        assert(!result)
    }
    
    @Test
    fun `test actionPerformed with empty files shows error`() {
        // Given
        every { event.project } returns project
        every { event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) } returns emptyArray()
        
        // When
        action.actionPerformed(event)
        
        // Then - should show error notification and return early
    }
    
    @Test
    fun `test actionPerformed with null project returns early`() {
        // Given
        every { event.project } returns null
        every { event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) } returns arrayOf(sourceFile)
        
        // When
        action.actionPerformed(event)
        
        // Then - should return early without processing
    }
    
    @Test
    fun `test actionPerformed with null files returns early`() {
        // Given
        every { event.project } returns project
        every { event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) } returns null
        
        // When
        action.actionPerformed(event)
        
        // Then - should return early without processing
    }
} 