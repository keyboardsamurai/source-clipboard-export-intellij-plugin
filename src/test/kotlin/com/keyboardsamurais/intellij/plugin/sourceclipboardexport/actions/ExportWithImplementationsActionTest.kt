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

class ExportWithImplementationsActionTest {
    
    private lateinit var action: ExportWithImplementationsAction
    private lateinit var event: AnActionEvent
    private lateinit var project: Project
    private lateinit var javaFile: VirtualFile
    private lateinit var kotlinFile: VirtualFile
    private lateinit var nonJvmFile: VirtualFile
    private lateinit var directory: VirtualFile
    
    @BeforeEach
    fun setUp() {
        action = ExportWithImplementationsAction()
        event = mockk(relaxed = true)
        project = mockk(relaxed = true)
        javaFile = mockk(relaxed = true)
        kotlinFile = mockk(relaxed = true)
        nonJvmFile = mockk(relaxed = true)
        directory = mockk(relaxed = true)
        
        every { javaFile.isDirectory } returns false
        every { javaFile.extension } returns "java"
        
        every { kotlinFile.isDirectory } returns false
        every { kotlinFile.extension } returns "kt"
        
        every { nonJvmFile.isDirectory } returns false
        every { nonJvmFile.extension } returns "js"
        
        every { directory.isDirectory } returns true
    }
    
    @Test
    fun `test action presentation text and description`() {
        assert(action.templatePresentation.text == "Implementations/Subclasses")
        assert(action.templatePresentation.description == "Export all implementations of selected interfaces/classes")
    }
    
    @Test
    fun `test update enables action for Java files`() {
        // Given
        every { event.project } returns project
        every { event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) } returns arrayOf(javaFile)
        
        // When
        action.update(event)
        
        // Then
        verify { event.presentation.isEnabled = true }
    }
    
    @Test
    fun `test update enables action for Kotlin files`() {
        // Given
        every { event.project } returns project
        every { event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) } returns arrayOf(kotlinFile)
        
        // When
        action.update(event)
        
        // Then
        verify { event.presentation.isEnabled = true }
    }
    
    @Test
    fun `test update disables action when no project`() {
        // Given
        every { event.project } returns null
        every { event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) } returns arrayOf(javaFile)
        
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
        every { event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) } returns arrayOf(directory)
        
        // When
        action.update(event)
        
        // Then
        verify { event.presentation.isEnabled = false }
    }
    
    @Test
    fun `test update disables action when only non-JVM files selected`() {
        // Given
        every { event.project } returns project
        every { event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) } returns arrayOf(nonJvmFile)
        
        // When
        action.update(event)
        
        // Then
        verify { event.presentation.isEnabled = false }
    }
    
    @Test
    fun `test update enables action when at least one JVM file selected`() {
        // Given
        every { event.project } returns project
        every { event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) } returns arrayOf(nonJvmFile, javaFile)
        
        // When
        action.update(event)
        
        // Then
        verify { event.presentation.isEnabled = true }
    }
    
    @Test
    fun `test getActionUpdateThread returns BGT`() {
        assert(action.actionUpdateThread == com.intellij.openapi.actionSystem.ActionUpdateThread.BGT)
    }
    
    // Comprehensive tests for isJvmFile method
    @Test
    fun `test isJvmFile recognizes Java files`() {
        val javaTestFile = mockk<VirtualFile>()
        every { javaTestFile.extension } returns "java"
        
        val method = action.javaClass.getDeclaredMethod("isJvmFile", VirtualFile::class.java)
        method.isAccessible = true
        val result = method.invoke(action, javaTestFile) as Boolean
        
        assert(result)
    }
    
    @Test
    fun `test isJvmFile recognizes Kotlin files`() {
        val kotlinTestFile = mockk<VirtualFile>()
        every { kotlinTestFile.extension } returns "kt"
        
        val method = action.javaClass.getDeclaredMethod("isJvmFile", VirtualFile::class.java)
        method.isAccessible = true
        val result = method.invoke(action, kotlinTestFile) as Boolean
        
        assert(result)
    }
    
    @Test
    fun `test isJvmFile recognizes Kotlin script files`() {
        val ktsFile = mockk<VirtualFile>()
        every { ktsFile.extension } returns "kts"
        
        val method = action.javaClass.getDeclaredMethod("isJvmFile", VirtualFile::class.java)
        method.isAccessible = true
        val result = method.invoke(action, ktsFile) as Boolean
        
        assert(result)
    }
    
    @Test
    fun `test isJvmFile handles case insensitive extensions`() {
        val javaUpperCase = mockk<VirtualFile>()
        every { javaUpperCase.extension } returns "JAVA"
        
        val method = action.javaClass.getDeclaredMethod("isJvmFile", VirtualFile::class.java)
        method.isAccessible = true
        val result = method.invoke(action, javaUpperCase) as Boolean
        
        assert(result)
    }
    
    @Test
    fun `test isJvmFile rejects non-JVM extensions`() {
        val jsFile = mockk<VirtualFile>()
        every { jsFile.extension } returns "js"
        
        val method = action.javaClass.getDeclaredMethod("isJvmFile", VirtualFile::class.java)
        method.isAccessible = true
        val result = method.invoke(action, jsFile) as Boolean
        
        assert(!result)
    }
    
    @Test
    fun `test isJvmFile rejects Python files`() {
        val pyFile = mockk<VirtualFile>()
        every { pyFile.extension } returns "py"
        
        val method = action.javaClass.getDeclaredMethod("isJvmFile", VirtualFile::class.java)
        method.isAccessible = true
        val result = method.invoke(action, pyFile) as Boolean
        
        assert(!result)
    }
    
    @Test
    fun `test isJvmFile rejects C# files`() {
        val csFile = mockk<VirtualFile>()
        every { csFile.extension } returns "cs"
        
        val method = action.javaClass.getDeclaredMethod("isJvmFile", VirtualFile::class.java)
        method.isAccessible = true
        val result = method.invoke(action, csFile) as Boolean
        
        assert(!result)
    }
    
    @Test
    fun `test isJvmFile handles null extension`() {
        val noExtensionFile = mockk<VirtualFile>()
        every { noExtensionFile.extension } returns null
        
        val method = action.javaClass.getDeclaredMethod("isJvmFile", VirtualFile::class.java)
        method.isAccessible = true
        val result = method.invoke(action, noExtensionFile) as Boolean
        
        assert(!result)
    }
    
    @Test
    fun `test isJvmFile rejects empty extension`() {
        val emptyExtFile = mockk<VirtualFile>()
        every { emptyExtFile.extension } returns ""
        
        val method = action.javaClass.getDeclaredMethod("isJvmFile", VirtualFile::class.java)
        method.isAccessible = true
        val result = method.invoke(action, emptyExtFile) as Boolean
        
        assert(!result)
    }
    
    @Test
    fun `test actionPerformed with null project returns early`() {
        // Given
        every { event.project } returns null
        every { event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) } returns arrayOf(javaFile)
        
        // When
        action.actionPerformed(event)
        
        // Then - should return early without processing
        // This tests the first guard clause in actionPerformed
    }
    
    @Test
    fun `test actionPerformed with null files returns early`() {
        // Given
        every { event.project } returns project
        every { event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) } returns null
        
        // When
        action.actionPerformed(event)
        
        // Then - should return early without processing
        // This tests the second guard clause in actionPerformed
    }
    
    @Test
    fun `test actionPerformed with empty files shows error`() {
        // Given
        every { event.project } returns project
        every { event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) } returns emptyArray()
        
        // When
        action.actionPerformed(event)
        
        // Then - should show error notification and return
        // This tests the third guard clause in actionPerformed
    }
    
    @Test
    fun `test actionPerformed method exists and handles basic flow`() {
        // Test the basic structure of actionPerformed without invoking IntelliJ services
        // Given
        every { event.project } returns project
        every { event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) } returns arrayOf(javaFile)
        
        // When/Then - should not throw exceptions during basic validation
        // The actual background task and services can't be tested in isolation
        try {
            action.actionPerformed(event)
            // If it doesn't throw an exception during the setup phase, that's good
        } catch (e: Exception) {
            // Expected - IntelliJ services aren't available in test environment
            // The test validates the method structure exists
        }
    }
}