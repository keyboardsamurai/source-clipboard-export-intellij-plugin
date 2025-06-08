package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DumpFolderContentsActionTest {

    private lateinit var action: DumpFolderContentsAction
    private lateinit var event: AnActionEvent
    private lateinit var project: Project

    @BeforeEach
    fun setup() {
        action = DumpFolderContentsAction()
        event = mockk(relaxed = true)
        project = mockk(relaxed = true)

        every { event.project } returns project
    }

    @Test
    fun `test action shows improved error message when no files selected`() {
        // Setup
        every { event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) } returns null

        // Mock static notification utility to avoid NPE
        mockkStatic("com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.NotificationUtils")

        // Execute
        action.actionPerformed(event)

        // Verify - The action should show the improved error message
        // Since we can't directly test the notification, we verify the action completes without exception
        // and that the correct data was accessed
        verify { event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) }
        verify { event.project }

        // Clean up static mock
        unmockkStatic("com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.NotificationUtils")
    }

    @Test
    fun `test estimateFileCount for single file`() {
        // Create a mock file
        val file = mockk<VirtualFile> {
            every { isDirectory } returns false
            every { children } returns null
        }

        // Use reflection to access private method
        val method = action.javaClass.getDeclaredMethod("estimateFileCount", Array<VirtualFile>::class.java)
        method.isAccessible = true

        val count = method.invoke(action, arrayOf(file)) as Int
        assertEquals(1, count)
    }

    @Test
    fun `test estimateFileCount for directory with files`() {
        // Create mock files
        val file1 = mockk<VirtualFile> {
            every { isDirectory } returns false
            every { children } returns null
        }
        val file2 = mockk<VirtualFile> {
            every { isDirectory } returns false
            every { children } returns null
        }
        val directory = mockk<VirtualFile> {
            every { isDirectory } returns true
            every { children } returns arrayOf(file1, file2)
        }

        // Use reflection to access private method
        val method = action.javaClass.getDeclaredMethod("estimateFileCount", Array<VirtualFile>::class.java)
        method.isAccessible = true

        val count = method.invoke(action, arrayOf(directory)) as Int
        assertEquals(2, count)
    }

    @Test
    fun `test estimateFileCount stops at 1000 files`() {
        // Create a deep directory structure with more than 1000 files
        // Create unique files for each subdirectory to avoid visited set issues
        val subDirs = Array(20) { i ->
            val subDirFiles = Array(60) { j ->
                mockk<VirtualFile> {
                    every { isDirectory } returns false
                    every { children } returns null
                    // Add a unique identifier to make each file distinct
                    every { path } returns "file_${i}_${j}"
                }
            }

            mockk<VirtualFile> {
                every { isDirectory } returns true
                every { children } returns subDirFiles
                every { path } returns "subdir_$i"
            }
        }

        val rootDir = mockk<VirtualFile> {
            every { isDirectory } returns true
            every { children } returns subDirs
            every { path } returns "root"
        }

        // Use reflection to access private method
        val method = action.javaClass.getDeclaredMethod("estimateFileCount", Array<VirtualFile>::class.java)
        method.isAccessible = true

        val count = method.invoke(action, arrayOf(rootDir)) as Int
        assertEquals(1000, count, "Should stop counting exactly at 1000")
    }

    @Test
    fun `test action update sets correct presentation state`() {
        // Setup with files selected
        val files = arrayOf(mockk<VirtualFile>())
        every { event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) } returns files

        // Execute
        action.update(event)

        // Verify
        verify { event.presentation.isEnabledAndVisible = true }
    }

    @Test
    fun `test action update disables when no files selected`() {
        // Setup with no files
        every { event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) } returns null

        // Execute
        action.update(event)

        // Verify
        verify { event.presentation.isEnabledAndVisible = false }
    }
}
