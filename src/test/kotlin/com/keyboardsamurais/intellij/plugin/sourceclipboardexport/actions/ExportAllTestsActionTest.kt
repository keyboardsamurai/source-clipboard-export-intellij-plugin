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

class ExportAllTestsActionTest {
    
    private lateinit var action: ExportAllTestsAction
    private lateinit var event: AnActionEvent
    private lateinit var project: Project
    private lateinit var sourceFile: VirtualFile
    private lateinit var directoryFile: VirtualFile
    
    @BeforeEach
    fun setUp() {
        action = ExportAllTestsAction()
        event = mockk(relaxed = true)
        project = mockk(relaxed = true)
        sourceFile = mockk(relaxed = true)
        directoryFile = mockk(relaxed = true)
        
        every { sourceFile.isDirectory } returns false
        every { directoryFile.isDirectory } returns true
    }
    
    @Test
    fun `test action presentation text and description`() {
        assert(action.templatePresentation.text == "All Related Tests")
        assert(action.templatePresentation.description == "Export all test types including integration and E2E tests")
    }
    
    @Test
    fun `test update enables action when project and files exist`() {
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
    fun `test update enables action when at least one non-directory file selected`() {
        // Given
        every { event.project } returns project
        every { event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) } returns arrayOf(directoryFile, sourceFile)
        
        // When
        action.update(event)
        
        // Then
        verify { event.presentation.isEnabled = true }
    }
    
    @Test
    fun `test getActionUpdateThread returns BGT`() {
        assert(action.actionUpdateThread == com.intellij.openapi.actionSystem.ActionUpdateThread.BGT)
    }
    
    // New comprehensive tests for categorizeTests logic
    @Test
    fun `test categorizeTests identifies unit tests by default`() {
        val testFile = mockk<VirtualFile>()
        every { testFile.nameWithoutExtension } returns "SomeTest"
        every { testFile.path } returns "/src/test/SomeTest.java"
        every { testFile.extension } returns "java"
        
        val method = action.javaClass.getDeclaredMethod("categorizeTests", Set::class.java)
        method.isAccessible = true
        val result = method.invoke(action, setOf(testFile))
        
        val unitTestsField = result.javaClass.getDeclaredField("unitTests")
        unitTestsField.isAccessible = true
        val unitTests = unitTestsField.get(result) as MutableList<*>
        
        assert(unitTests.size == 1)
        assert(unitTests.contains(testFile))
    }
    
    @Test
    fun `test categorizeTests identifies integration tests`() {
        val integrationTest = mockk<VirtualFile>()
        every { integrationTest.nameWithoutExtension } returns "DatabaseIntegrationTest"
        every { integrationTest.path } returns "/src/test/DatabaseIntegrationTest.java"
        every { integrationTest.extension } returns "java"
        
        val method = action.javaClass.getDeclaredMethod("categorizeTests", Set::class.java)
        method.isAccessible = true
        val result = method.invoke(action, setOf(integrationTest))
        
        val integrationTestsField = result.javaClass.getDeclaredField("integrationTests")
        integrationTestsField.isAccessible = true
        val integrationTests = integrationTestsField.get(result) as MutableList<*>
        
        assert(integrationTests.size == 1)
        assert(integrationTests.contains(integrationTest))
    }
    
    @Test
    fun `test categorizeTests identifies integration tests by IT suffix`() {
        val integrationTest = mockk<VirtualFile>()
        every { integrationTest.nameWithoutExtension } returns "DatabaseIT"  // This will be "databaseit" in lowercase and end with "it"
        every { integrationTest.path } returns "/src/test/DatabaseIT.java"
        every { integrationTest.extension } returns "java"
        
        val method = action.javaClass.getDeclaredMethod("categorizeTests", Set::class.java)
        method.isAccessible = true
        val result = method.invoke(action, setOf(integrationTest))
        
        val integrationTestsField = result.javaClass.getDeclaredField("integrationTests")
        integrationTestsField.isAccessible = true
        val integrationTests = integrationTestsField.get(result) as MutableList<*>
        
        // Should categorize as integration test (name.endsWith("it") check)
        assert(integrationTests.size == 1)
    }
    
    @Test
    fun `test categorizeTests identifies E2E tests`() {
        val e2eTest = mockk<VirtualFile>()
        every { e2eTest.nameWithoutExtension } returns "UserJourneyE2ETest"
        every { e2eTest.path } returns "/src/test/UserJourneyE2ETest.java"
        every { e2eTest.extension } returns "java"
        
        val method = action.javaClass.getDeclaredMethod("categorizeTests", Set::class.java)
        method.isAccessible = true
        val result = method.invoke(action, setOf(e2eTest))
        
        val e2eTestsField = result.javaClass.getDeclaredField("e2eTests")
        e2eTestsField.isAccessible = true
        val e2eTests = e2eTestsField.get(result) as MutableList<*>
        
        assert(e2eTests.size == 1)
        assert(e2eTests.contains(e2eTest))
    }
    
    @Test
    fun `test categorizeTests identifies performance tests`() {
        val perfTest = mockk<VirtualFile>()
        every { perfTest.nameWithoutExtension } returns "LoadTest"
        every { perfTest.path } returns "/src/test/LoadTest.java"
        every { perfTest.extension } returns "java"
        
        val method = action.javaClass.getDeclaredMethod("categorizeTests", Set::class.java)
        method.isAccessible = true
        val result = method.invoke(action, setOf(perfTest))
        
        val performanceTestsField = result.javaClass.getDeclaredField("performanceTests")
        performanceTestsField.isAccessible = true
        val performanceTests = performanceTestsField.get(result) as MutableList<*>
        
        assert(performanceTests.size == 1)
        assert(performanceTests.contains(perfTest))
    }
    
    @Test
    fun `test categorizeTests identifies test utilities`() {
        val testUtil = mockk<VirtualFile>()
        every { testUtil.nameWithoutExtension } returns "TestUtil"
        every { testUtil.path } returns "/src/test/TestUtil.java"
        every { testUtil.extension } returns "java"
        
        val method = action.javaClass.getDeclaredMethod("categorizeTests", Set::class.java)
        method.isAccessible = true
        val result = method.invoke(action, setOf(testUtil))
        
        val testUtilitiesField = result.javaClass.getDeclaredField("testUtilities")
        testUtilitiesField.isAccessible = true
        val testUtilities = testUtilitiesField.get(result) as MutableList<*>
        
        assert(testUtilities.size == 1)
        assert(testUtilities.contains(testUtil))
    }
    
    @Test
    fun `test categorizeTests identifies test resources`() {
        val testResource = mockk<VirtualFile>()
        every { testResource.nameWithoutExtension } returns "test-data"
        every { testResource.path } returns "/src/test/resources/test-data.json"
        every { testResource.extension } returns "json"
        
        val method = action.javaClass.getDeclaredMethod("categorizeTests", Set::class.java)
        method.isAccessible = true
        val result = method.invoke(action, setOf(testResource))
        
        val testResourcesField = result.javaClass.getDeclaredField("testResources")
        testResourcesField.isAccessible = true
        val testResources = testResourcesField.get(result) as MutableList<*>
        
        assert(testResources.size == 1)
        assert(testResources.contains(testResource))
    }
    
    @Test
    fun `test categorizeTests handles mixed test types`() {
        val unitTest = mockk<VirtualFile>()
        every { unitTest.nameWithoutExtension } returns "ServiceTest"
        every { unitTest.path } returns "/src/test/ServiceTest.kt"
        every { unitTest.extension } returns "kt"
        
        val integrationTest = mockk<VirtualFile>()
        every { integrationTest.nameWithoutExtension } returns "DatabaseIntegrationTest"
        every { integrationTest.path } returns "/src/test/DatabaseIntegrationTest.kt"
        every { integrationTest.extension } returns "kt"
        
        val e2eTest = mockk<VirtualFile>()
        every { e2eTest.nameWithoutExtension } returns "UserFlowE2E"
        every { e2eTest.path } returns "/src/test/UserFlowE2E.kt"
        every { e2eTest.extension } returns "kt"
        
        val method = action.javaClass.getDeclaredMethod("categorizeTests", Set::class.java)
        method.isAccessible = true
        val result = method.invoke(action, setOf(unitTest, integrationTest, e2eTest))
        
        // Access total via reflection of the getter method
        val totalMethod = result.javaClass.getDeclaredMethod("getTotal")
        totalMethod.isAccessible = true
        val total = totalMethod.invoke(result) as Int
        
        assert(total == 3)
    }
    
    @Test
    fun `test buildTestDescription method exists`() {
        // Test that the buildTestDescription method exists and can be accessed
        val categoriesClass = Class.forName("com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions.ExportAllTestsAction\$TestCategories")
        val method = action.javaClass.getDeclaredMethod("buildTestDescription", categoriesClass)
        method.isAccessible = true
        
        // Method exists and returns String
        assert(method.returnType == String::class.java)
    }
} 