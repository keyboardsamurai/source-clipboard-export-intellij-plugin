package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.config.SourceClipboardExportSettings
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core.ExportHistory
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core.SourceExporter
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core.gitignore.HierarchicalGitignoreParser
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.ui.ExportNotificationPresenter
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.StringUtils
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkConstructor
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
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
        fun `actionPerformed shows empty content warning`() {
                val settings = SourceClipboardExportSettings()
                SourceClipboardExportSettings.setTestInstance(settings)
                try {
                        every { event.project } returns project
                        every { event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) } returns arrayOf(file)
                        every { file.isDirectory } returns false

                        mockkStatic(ProgressManager::class)
                        val progressManager = mockk<ProgressManager>()
                        every { ProgressManager.getInstance() } returns progressManager
                        every { progressManager.run(any<Task.Backgroundable>()) } answers {
                                val task = invocation.args[0] as Task.Backgroundable
                                val indicator = mockk<ProgressIndicator>(relaxed = true)
                                task.run(indicator)
                        }

                        mockkConstructor(SourceExporter::class)
                        val emptyResult =
                                SourceExporter.ExportResult(
                                        content = "",
                                        processedFileCount = 0,
                                        excludedByFilterCount = 0,
                                        excludedBySizeCount = 0,
                                        excludedByBinaryContentCount = 0,
                                        excludedByIgnoredNameCount = 0,
                                        excludedByGitignoreCount = 0,
                                        excludedExtensions = emptySet(),
                                        limitReached = false,
                                        includedPaths = emptyList()
                                )
                        coEvery { anyConstructed<SourceExporter>().exportSources(any()) } returns emptyResult

                        mockkConstructor(ExportNotificationPresenter::class)
                        every { anyConstructed<ExportNotificationPresenter>().showEmptyContentWarning(settings.state) } just Runs

                        mockkStatic(CopyPasteManager::class)
                        val copyPasteManager = mockk<CopyPasteManager>(relaxed = true)
                        every { CopyPasteManager.getInstance() } returns copyPasteManager

                        action.actionPerformed(event)

                        verify { anyConstructed<ExportNotificationPresenter>().showEmptyContentWarning(settings.state) }
                        verify(exactly = 0) { copyPasteManager.setContents(any()) }
                } finally {
                        SourceClipboardExportSettings.setTestInstance(null)
                        unmockkStatic(ProgressManager::class)
                        unmockkConstructor(SourceExporter::class)
                        unmockkConstructor(ExportNotificationPresenter::class)
                        unmockkStatic(CopyPasteManager::class)
                }
        }

        @Test
        fun `actionPerformed copies content and reports limit`() {
                val settings = SourceClipboardExportSettings()
                SourceClipboardExportSettings.setTestInstance(settings)
                try {
                        every { event.project } returns project
                        every { event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) } returns arrayOf(file)
                        every { file.isDirectory } returns false

                        mockkStatic(ProgressManager::class)
                        val progressManager = mockk<ProgressManager>()
                        every { ProgressManager.getInstance() } returns progressManager
                        every { progressManager.run(any<Task.Backgroundable>()) } answers {
                                val task = invocation.args[0] as Task.Backgroundable
                                val indicator = mockk<ProgressIndicator>(relaxed = true)
                                task.run(indicator)
                        }

                        mockkConstructor(SourceExporter::class)
                        val exportResult =
                                SourceExporter.ExportResult(
                                        content = "body",
                                        processedFileCount = 2,
                                        excludedByFilterCount = 0,
                                        excludedBySizeCount = 0,
                                        excludedByBinaryContentCount = 0,
                                        excludedByIgnoredNameCount = 0,
                                        excludedByGitignoreCount = 0,
                                        excludedExtensions = emptySet(),
                                        limitReached = true,
                                        includedPaths = listOf("src/Main.kt")
                                )
                        coEvery { anyConstructed<SourceExporter>().exportSources(any()) } returns exportResult

                        mockkConstructor(ExportNotificationPresenter::class)
                        every { anyConstructed<ExportNotificationPresenter>().showSuccessNotification(any(), any(), any()) } just Runs
                        every { anyConstructed<ExportNotificationPresenter>().showLimitReachedNotification(any()) } just Runs

                        mockkStatic(CopyPasteManager::class)
                        val copyPasteManager = mockk<CopyPasteManager>(relaxed = true)
                        every { CopyPasteManager.getInstance() } returns copyPasteManager
                        justRun { copyPasteManager.setContents(any()) }

                        mockkObject(StringUtils)
                        every { StringUtils.estimateTokensWithSubwordHeuristic(any()) } returns 10

                        mockkObject(ExportHistory.Companion)
                        val history = mockk<ExportHistory>(relaxed = true)
                        every { ExportHistory.getInstance(project) } returns history

                        action.actionPerformed(event)

                        verify { copyPasteManager.setContents(any()) }
                        verify { anyConstructed<ExportNotificationPresenter>().showSuccessNotification(exportResult, "0.0KB", "10") }
                        verify { anyConstructed<ExportNotificationPresenter>().showLimitReachedNotification(settings.state.fileCount) }
                        verify { history.addExport(exportResult.processedFileCount, any(), 10, exportResult.includedPaths) }
                } finally {
                        SourceClipboardExportSettings.setTestInstance(null)
                        unmockkStatic(ProgressManager::class)
                        unmockkConstructor(SourceExporter::class)
                        unmockkConstructor(ExportNotificationPresenter::class)
                        unmockkStatic(CopyPasteManager::class)
                        unmockkObject(StringUtils)
                        unmockkObject(ExportHistory.Companion)
                }
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

        @Test
        fun `copyToClipboard legacy path notifies presenter and history`() {
                mockkStatic(CopyPasteManager::class)
                mockkObject(StringUtils)
                mockkObject(ExportHistory.Companion)
                try {
                        val copyPasteManager = mockk<CopyPasteManager>(relaxed = true)
                        every { CopyPasteManager.getInstance() } returns copyPasteManager
                        justRun { copyPasteManager.setContents(any()) }
                        every { StringUtils.estimateTokensWithSubwordHeuristic(any()) } returns 42
                        val history = mockk<ExportHistory>(relaxed = true)
                        every { ExportHistory.getInstance(project) } returns history
                        val presenter = mockk<ExportNotificationPresenter>(relaxed = true)

                        val method =
                                action.javaClass.getDeclaredMethod(
                                        "copyToClipboard",
                                        String::class.java,
                                        Int::class.javaPrimitiveType,
                                        Project::class.java,
                                        List::class.java,
                                        ExportNotificationPresenter::class.java
                                )
                        method.isAccessible = true

                        method.invoke(
                                action,
                                "hello world",
                                2,
                                project,
                                listOf("src/A.kt", "src/B.kt"),
                                presenter
                        )

                        verify { presenter.showSimpleSuccessNotification(2, "0.0KB", "42") }
                        verify { history.addExport(2, "hello world".toByteArray().size, 42, listOf("src/A.kt", "src/B.kt")) }
                } finally {
                        unmockkStatic(CopyPasteManager::class)
                        unmockkObject(StringUtils)
                        unmockkObject(ExportHistory.Companion)
                }
        }

        @Test
        fun `copyToClipboard legacy path surfaces clipboard failures`() {
                mockkStatic(CopyPasteManager::class)
                mockkObject(StringUtils)
                mockkStatic(Logger::class)
                try {
                        val copyPasteManager = mockk<CopyPasteManager>(relaxed = true)
                        every { CopyPasteManager.getInstance() } returns copyPasteManager
                        every { copyPasteManager.setContents(any()) } throws IllegalStateException("clipboard blocked")
                        every { StringUtils.estimateTokensWithSubwordHeuristic(any()) } returns 1
                        val presenter = mockk<ExportNotificationPresenter>(relaxed = true)
                        val logger = mockk<Logger>(relaxed = true)
                        every { Logger.getInstance(any<Class<*>>()) } returns logger
                        val testAction = DumpFolderContentsAction()

                        val method =
                                testAction.javaClass.getDeclaredMethod(
                                        "copyToClipboard",
                                        String::class.java,
                                        Int::class.javaPrimitiveType,
                                        Project::class.java,
                                        List::class.java,
                                        ExportNotificationPresenter::class.java
                                )
                        method.isAccessible = true

                        method.invoke(testAction, "body", 1, project, listOf("src/Main.kt"), presenter)

                        verify { presenter.showClipboardErrorNotification("clipboard blocked") }
                } finally {
                        unmockkStatic(CopyPasteManager::class)
                        unmockkObject(StringUtils)
                        unmockkStatic(Logger::class)
                }
        }

        @Test
        fun `copyToClipboard export result notifies presenter and history`() {
                mockkStatic(CopyPasteManager::class)
                mockkObject(StringUtils)
                mockkObject(ExportHistory.Companion)
                try {
                        val copyPasteManager = mockk<CopyPasteManager>(relaxed = true)
                        every { CopyPasteManager.getInstance() } returns copyPasteManager
                        justRun { copyPasteManager.setContents(any()) }
                        every { StringUtils.estimateTokensWithSubwordHeuristic(any()) } returns 100
                        val history = mockk<ExportHistory>(relaxed = true)
                        every { ExportHistory.getInstance(project) } returns history
                        val presenter = mockk<ExportNotificationPresenter>(relaxed = true)
                        val result =
                                SourceExporter.ExportResult(
                                        content = "ignored",
                                        processedFileCount = 3,
                                        excludedByFilterCount = 1,
                                        excludedBySizeCount = 0,
                                        excludedByBinaryContentCount = 0,
                                        excludedByIgnoredNameCount = 0,
                                        excludedByGitignoreCount = 0,
                                        excludedExtensions = setOf("class"),
                                        limitReached = false,
                                        includedPaths = listOf("src/Main.kt")
                                )

                        val method =
                                action.javaClass.getDeclaredMethod(
                                        "copyToClipboard",
                                        String::class.java,
                                        SourceExporter.ExportResult::class.java,
                                        Project::class.java,
                                        ExportNotificationPresenter::class.java
                                )
                        method.isAccessible = true

                        method.invoke(action, "file body", result, project, presenter)

                        verify { presenter.showSuccessNotification(result, "0.0KB", "100") }
                        verify {
                                history.addExport(
                                        result.processedFileCount,
                                        "file body".toByteArray().size,
                                        100,
                                        result.includedPaths
                                )
                        }
                } finally {
                        unmockkStatic(CopyPasteManager::class)
                        unmockkObject(StringUtils)
                        unmockkObject(ExportHistory.Companion)
                }
        }

        @Test
        fun `copyToClipboard export result reports clipboard errors`() {
                mockkStatic(CopyPasteManager::class)
                mockkObject(StringUtils)
                mockkStatic(Logger::class)
                try {
                        val copyPasteManager = mockk<CopyPasteManager>(relaxed = true)
                        every { CopyPasteManager.getInstance() } returns copyPasteManager
                        every { copyPasteManager.setContents(any()) } throws RuntimeException("failure")
                        every { StringUtils.estimateTokensWithSubwordHeuristic(any()) } returns 5
                        val presenter = mockk<ExportNotificationPresenter>(relaxed = true)
                        val logger = mockk<Logger>(relaxed = true)
                        every { Logger.getInstance(any<Class<*>>()) } returns logger
                        val testAction = DumpFolderContentsAction()
                        val result =
                                SourceExporter.ExportResult(
                                        content = "ignored",
                                        processedFileCount = 1,
                                        excludedByFilterCount = 0,
                                        excludedBySizeCount = 0,
                                        excludedByBinaryContentCount = 0,
                                        excludedByIgnoredNameCount = 0,
                                        excludedByGitignoreCount = 0,
                                        excludedExtensions = emptySet(),
                                        limitReached = false,
                                        includedPaths = emptyList()
                                )

                        val method =
                                testAction.javaClass.getDeclaredMethod(
                                        "copyToClipboard",
                                        String::class.java,
                                        SourceExporter.ExportResult::class.java,
                                        Project::class.java,
                                        ExportNotificationPresenter::class.java
                                )
                        method.isAccessible = true

                        method.invoke(testAction, "text", result, project, presenter)

                        verify { presenter.showClipboardErrorNotification("failure") }
                } finally {
                        unmockkStatic(CopyPasteManager::class)
                        unmockkObject(StringUtils)
                        unmockkStatic(Logger::class)
                }
        }
}
