package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions

import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.config.SourceClipboardExportSettings
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core.ExportHistory
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core.SourceExporter
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core.gitignore.HierarchicalGitignoreParser
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.NotificationUtils
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.StringUtils
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.datatransfer.StringSelection

class SmartExportUtilsTest {

    private val project = mockk<Project>(relaxed = true)
    private val sourceFile = mockk<com.intellij.openapi.vfs.VirtualFile>(relaxed = true)

    @BeforeEach
    fun setup() {
        every { project.getService(HierarchicalGitignoreParser::class.java) } returns
                mockk(relaxed = true)
        SourceClipboardExportSettings.setTestInstance(SourceClipboardExportSettings())
        mockkConstructor(SourceExporter::class)

        val exportResult =
                SourceExporter.ExportResult(
                        content = "contents",
                        processedFileCount = 1,
                        excludedByFilterCount = 0,
                        excludedBySizeCount = 0,
                        excludedByBinaryContentCount = 0,
                        excludedByIgnoredNameCount = 0,
                        excludedByGitignoreCount = 0,
                        excludedExtensions = emptySet(),
                        limitReached = false,
                        includedPaths = listOf("Sample.kt")
                )
        coEvery { anyConstructed<SourceExporter>().exportSources(any()) } returns exportResult

        mockkObject(NotificationUtils)
        justRun { NotificationUtils.showNotification(any(), any(), any(), any()) }

        mockkObject(StringUtils)
        every { StringUtils.estimateTokensWithSubwordHeuristic(any()) } returns 42
    }

    @AfterEach
    fun tearDown() {
        SourceClipboardExportSettings.setTestInstance(null)
        unmockkAll()
    }

    @Test
    fun `exportFiles uses lightweight path in unit test mode`() {
        mockkStatic(ApplicationManager::class)
        val application = mockk<Application>(relaxed = true)
        every { application.isUnitTestMode } returns true
        every { ApplicationManager.getApplication() } returns application

        SmartExportUtils.exportFiles(project, arrayOf(sourceFile))

        coVerify { anyConstructed<SourceExporter>().exportSources(any()) }
        verify {
            NotificationUtils.showNotification(
                    project,
                    match { it.contains("Export completed") },
                    any(),
                    any()
            )
        }
    }

    @Test
    fun `exportFiles copies content and records history when app is available`() {
        mockkStatic(ApplicationManager::class)
        mockkStatic(ProgressManager::class)
        mockkStatic(CopyPasteManager::class)
        mockkObject(ExportHistory.Companion)

        val application = mockk<Application>(relaxed = true)
        every { application.isUnitTestMode } returns false
        every { ApplicationManager.getApplication() } returns application
        every { application.invokeLater(any()) } answers
                {
                    val runnable = firstArg<Runnable>()
                    runnable.run()
                }

        val progressManager = mockk<ProgressManager>(relaxed = true)
        every { ProgressManager.getInstance() } returns progressManager
        every {
            progressManager.runProcessWithProgressSynchronously(any(), any(), any(), any<Project>())
        } answers
                {
                    val runnable = firstArg<Runnable>()
                    runnable.run()
                    true
                }

        val copyPasteManager = mockk<CopyPasteManager>(relaxed = true)
        every { CopyPasteManager.getInstance() } returns copyPasteManager
        justRun { copyPasteManager.setContents(any<StringSelection>()) }

        val history = mockk<ExportHistory>(relaxed = true)
        every { ExportHistory.getInstance(project) } returns history

        SmartExportUtils.exportFiles(project, arrayOf(sourceFile))

        verify { copyPasteManager.setContents(any()) }
        verify { history.addExport(any(), any(), any(), any()) }
    }

    @Test
    fun `exportFiles notifies failure when exporter throws in unit test mode`() {
        mockkStatic(ApplicationManager::class)
        val application = mockk<Application>(relaxed = true)
        every { application.isUnitTestMode } returns true
        every { ApplicationManager.getApplication() } returns application

        coEvery { anyConstructed<SourceExporter>().exportSources(any()) } throws IllegalStateException("boom")

        SmartExportUtils.exportFiles(project, arrayOf(sourceFile))

        verify {
            NotificationUtils.showNotification(
                project,
                "Export failed",
                match { it.contains("boom") },
                com.intellij.notification.NotificationType.ERROR
            )
        }
    }

    @Test
    fun `exportFiles surfaces error notification when runProcess fails`() {
        mockkStatic(ApplicationManager::class)
        mockkStatic(ProgressManager::class)
        mockkStatic(CopyPasteManager::class)

        val application = mockk<Application>(relaxed = true)
        every { application.isUnitTestMode } returns false
        every { ApplicationManager.getApplication() } returns application
        every { application.invokeLater(any()) } answers { firstArg<Runnable>().run() }

        val progressManager = mockk<ProgressManager>()
        every { ProgressManager.getInstance() } returns progressManager
        every { progressManager.progressIndicator } returns null
        every {
            progressManager.runProcessWithProgressSynchronously(any(), any(), any(), any<Project>())
        } answers {
            val runnable = firstArg<Runnable>()
            runnable.run()
            true
        }

        coEvery { anyConstructed<SourceExporter>().exportSources(any()) } throws IllegalStateException("explode")

        val copyPasteManager = mockk<CopyPasteManager>(relaxed = true)
        every { CopyPasteManager.getInstance() } returns copyPasteManager

        SmartExportUtils.exportFiles(project, arrayOf(sourceFile))

        verify {
            NotificationUtils.showNotification(
                project,
                "Export failed",
                match { it.contains("explode") },
                com.intellij.notification.NotificationType.ERROR
            )
        }
        verify(exactly = 0) { copyPasteManager.setContents(any()) }
    }
}
