package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.threading

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions.SmartExportUtils
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.config.SourceClipboardExportSettings
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core.SourceExporter
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Tests to ensure proper threading and read action handling
 */
class ThreadingTest : BasePlatformTestCase() {
    
    @Test
    fun testExportUtilsDoesNotThrowReadActionException() {
        // Create test files
        val testFile = myFixture.addFileToProject("Test.java", """
            public class Test {
                public void method() {}
            }
        """.trimIndent())
        
        val virtualFile = testFile.virtualFile
        assertNotNull(virtualFile)
        
        // This should not throw "Read access is allowed from inside read-action only"
        SmartExportUtils.exportFiles(project, arrayOf(virtualFile))
        
        // Give some time for async operations to complete
        Thread.sleep(1000)
    }
    
    @Test
    fun testSourceExporterHandlesReadActionsCorrectly() {
        // Create test files
        val testFile1 = myFixture.addFileToProject("Test1.java", """
            public class Test1 {
                public void method1() {}
            }
        """.trimIndent())
        
        val testFile2 = myFixture.addFileToProject("Test2.java", """
            public class Test2 {
                public void method2() {}
            }
        """.trimIndent())
        
        val virtualFiles = arrayOf(testFile1.virtualFile, testFile2.virtualFile)
        
        // Run the exporter directly
        val settings = SourceClipboardExportSettings.getInstance().state
        val indicator = EmptyProgressIndicator()
        val exporter = SourceExporter(project, settings, indicator)
        
        // This should not throw read action exceptions even when run from a coroutine
        val result = runBlocking {
            exporter.exportSources(virtualFiles)
        }
        
        assertTrue(result.processedFileCount > 0)
        assertFalse(result.content.isEmpty())
    }
    
    @Test
    fun testVirtualFileAccessInCoroutines() {
        val testFile = myFixture.addFileToProject("TestAccess.java", """
            public class TestAccess {
                private String field;
            }
        """.trimIndent())
        
        val virtualFile = testFile.virtualFile
        
        // Test that we can access file properties safely in coroutines
        runBlocking {
            // This would throw without proper read action handling
            val name = ReadAction.compute<String, Exception> { virtualFile.name }
            val path = ReadAction.compute<String, Exception> { virtualFile.path }
            val isDirectory = ReadAction.compute<Boolean, Exception> { virtualFile.isDirectory }
            
            assertEquals("TestAccess.java", name)
            assertFalse(isDirectory)
            assertTrue(path.contains("TestAccess.java"))
        }
    }
    
    @Test
    fun testConcurrentFileAccess() {
        // Create multiple test files
        val files = (1..10).map { i ->
            myFixture.addFileToProject("Test$i.java", """
                public class Test$i {
                    public void method$i() {}
                }
            """.trimIndent()).virtualFile
        }.toTypedArray()
        
        // Run export with multiple files to test concurrent access
        val settings = SourceClipboardExportSettings.getInstance().state
        val indicator = EmptyProgressIndicator()
        val exporter = SourceExporter(project, settings, indicator)
        
        val result = runBlocking {
            exporter.exportSources(files)
        }
        
        assertEquals(10, result.processedFileCount)
        files.forEach { file ->
            val fileName = ReadAction.compute<String, Exception> { file.name }
            assertTrue(result.content.contains(fileName))
        }
    }
}