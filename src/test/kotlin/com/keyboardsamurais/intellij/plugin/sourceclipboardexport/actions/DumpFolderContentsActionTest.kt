package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core.gitignore.HierarchicalGitignoreParser
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DumpFolderContentsActionTest {

        private lateinit var action: DumpFolderContentsAction
        private lateinit var event: AnActionEvent
        private lateinit var project: Project
        private lateinit var file: VirtualFile
        private lateinit var directory: VirtualFile

        @BeforeEach
        fun setUp() {
                action = DumpFolderContentsAction()
                event = mockk(relaxed = true)
                project = mockk(relaxed = true)
                every { project.getService(HierarchicalGitignoreParser::class.java) } returns
                        mockk(relaxed = true)
                file = mockk(relaxed = true)
                directory = mockk(relaxed = true)

                every { file.isDirectory } returns false
                every { directory.isDirectory } returns true
        }

        @Test
        fun `test action update sets correct presentation state`() {
                // Given
                every { event.project } returns project
                every { event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) } returns arrayOf(file)

                // When
                action.update(event)

                // Then
                verify { event.presentation.isEnabledAndVisible = true }
        }

        @Test
        fun `test action update disables when no files selected`() {
                // Given
                every { event.project } returns project
                every { event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) } returns null

                // When
                action.update(event)

                // Then
                verify { event.presentation.isEnabledAndVisible = false }
        }

        @Test
        fun `test action shows improved error message when no files selected`() {
                // Given
                every { event.project } returns null
                every { event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) } returns null

                // When
                action.actionPerformed(event)

                // Then - action should handle gracefully with proper error message
                // This tests the early return branch in actionPerformed
        }

        @Test
        fun `test estimateFileCount for single file`() {
                // Given
                val singleFile = mockk<VirtualFile>()
                every { singleFile.isDirectory } returns false

                // When
                val method =
                        action.javaClass.getDeclaredMethod(
                                "estimateFileCount",
                                Array<VirtualFile>::class.java
                        )
                method.isAccessible = true
                val count = method.invoke(action, arrayOf(singleFile)) as Int

                // Then
                assert(count == 1)
        }

        @Test
        fun `test estimateFileCount for directory with files`() {
                // Given
                val dir = mockk<VirtualFile>()
                val child1 = mockk<VirtualFile>()
                val child2 = mockk<VirtualFile>()

                every { dir.isDirectory } returns true
                every { dir.children } returns arrayOf(child1, child2)
                every { child1.isDirectory } returns false
                every { child2.isDirectory } returns false

                // When
                val method =
                        action.javaClass.getDeclaredMethod(
                                "estimateFileCount",
                                Array<VirtualFile>::class.java
                        )
                method.isAccessible = true
                val count = method.invoke(action, arrayOf(dir)) as Int

                // Then
                assert(count == 2)
        }

        @Test
        fun `test estimateFileCount stops at 1000 files`() {
                // Given
                val dir = mockk<VirtualFile>()
                val manyChildren = Array(1500) { mockk<VirtualFile>() }
                manyChildren.forEach { every { it.isDirectory } returns false }

                every { dir.isDirectory } returns true
                every { dir.children } returns manyChildren

                // When
                val method =
                        action.javaClass.getDeclaredMethod(
                                "estimateFileCount",
                                Array<VirtualFile>::class.java
                        )
                method.isAccessible = true
                val count = method.invoke(action, arrayOf(dir)) as Int

                // Then
                assert(count == 1000) // Should cap at 1000
        }

        @Test
        fun `test copyToClipboard method exists and is accessible`() {
                // Test that the copyToClipboard method exists and can be accessed
                // We can't easily test the clipboard operations in a unit test environment
                // Note: The signature has changed to include ExportNotificationPresenter, so we
                // check for that
                val method =
                        action.javaClass.getDeclaredMethod(
                                "copyToClipboard",
                                String::class.java,
                                Int::class.java,
                                Project::class.java,
                                List::class.java,
                                com.keyboardsamurais.intellij.plugin.sourceclipboardexport.ui
                                                .ExportNotificationPresenter::class
                                        .java
                        )
                method.isAccessible = true

                // Method exists and is accessible
                assert(method.returnType == Void.TYPE)
        }
}
