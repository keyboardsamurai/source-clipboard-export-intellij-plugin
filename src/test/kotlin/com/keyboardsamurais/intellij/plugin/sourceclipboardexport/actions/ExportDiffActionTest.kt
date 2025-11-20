package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions

import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.config.SourceClipboardExportSettings
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core.ExportHistory
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core.SourceExporter
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core.gitignore.HierarchicalGitignoreParser
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.FileUtils
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.NotificationUtils
import io.mockk.coEvery
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import javax.swing.SwingUtilities

class ExportDiffActionTest {

    @BeforeEach
    fun init() {
        SourceClipboardExportSettings.setTestInstance(SourceClipboardExportSettings())
    }

    @AfterEach
    fun cleanup() {
        SourceClipboardExportSettings.setTestInstance(null)
        unmockkAll()
    }

    @Test
    fun `normalizeHistoricalPaths strips repository root from absolute paths`() {
        val project = mockk<Project>(relaxed = true)
        every { project.getService(HierarchicalGitignoreParser::class.java) } returns
                mockk(relaxed = true)
        val root = mockk<VirtualFile>()

        mockkObject(FileUtils)
        try {
            every { root.path } returns "/repo"
            every { FileUtils.getRepositoryRoot(project) } returns root

            val input = listOf("/repo/src/Main.kt", "/repo/src/util/Util.kt", "/other/Outside.kt")

            val normalized = normalizeHistoricalPaths(project, input)

            assertEquals(listOf("src/Main.kt", "src/util/Util.kt", "/other/Outside.kt"), normalized)
        } finally {
            unmockkObject(FileUtils)
        }
    }

    @Test
    fun `normalizePaths converts backslashes to forward slashes`() {
        val input = listOf("src\\Main.kt", "src\\util/Util.kt")

        val normalized = normalizePaths(input)

        assertEquals(listOf("src/Main.kt", "src/util/Util.kt"), normalized)
    }
    @Test
    fun `actionPerformed notifies when no history available`() {
        val project = mockk<Project>(relaxed = true)
        every { project.getService(HierarchicalGitignoreParser::class.java) } returns
                mockk(relaxed = true)
        val event = mockk<AnActionEvent>(relaxed = true)
        val selectedFile = mockk<VirtualFile>(relaxed = true)

        every { event.project } returns project
        every { event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) } returns arrayOf(selectedFile)

        mockkObject(ExportHistory.Companion)
        val history = mockk<ExportHistory>()
        every { ExportHistory.getInstance(project) } returns history
        every { history.getRecentExports() } returns emptyList()

        mockkObject(NotificationUtils)
        justRun { NotificationUtils.showNotification(any(), any(), any(), any()) }

        ExportDiffAction().actionPerformed(event)

        verify {
            NotificationUtils.showNotification(
                    project,
                    any(),
                    match { it.contains("No previous export") },
                    NotificationType.INFORMATION
            )
        }
    }

    @Test
    fun `actionPerformed launches diff dialog when history exists`() {
        val project = mockk<Project>(relaxed = true)
        every { project.getService(HierarchicalGitignoreParser::class.java) } returns
                mockk(relaxed = true)
        val event = mockk<AnActionEvent>(relaxed = true)
        val selectedFile = mockk<VirtualFile>(relaxed = true)

        every { event.project } returns project
        every { event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) } returns arrayOf(selectedFile)

        mockkObject(ExportHistory.Companion)
        val history = mockk<ExportHistory>()
        val previousExport =
                ExportHistory.ExportEntry(
                        fileCount = 1,
                        sizeBytes = 10,
                        tokens = 5,
                        filePaths = mutableListOf("src/Main.kt"),
                        summary = "1 files"
                )
        every { ExportHistory.getInstance(project) } returns history
        every { history.getRecentExports() } returns listOf(previousExport)

        mockkStatic(ProgressManager::class)
        val progressManager = mockk<ProgressManager>()
        every { ProgressManager.getInstance() } returns progressManager
        every { progressManager.run(any<Task.Backgroundable>()) } answers
                {
                    val task = firstArg<Task.Backgroundable>()
                    task.run(mockk(relaxed = true))
                }

        mockkConstructor(SourceExporter::class)
        val exportResult =
                SourceExporter.ExportResult(
                        content = "body",
                        processedFileCount = 1,
                        excludedByFilterCount = 0,
                        excludedBySizeCount = 0,
                        excludedByBinaryContentCount = 0,
                        excludedByIgnoredNameCount = 0,
                        excludedByGitignoreCount = 0,
                        excludedExtensions = emptySet(),
                        limitReached = false,
                        includedPaths = listOf("src/Main.kt")
                )
        coEvery { anyConstructed<SourceExporter>().exportSources(any()) } returns exportResult

        val action = spyk(ExportDiffAction(), recordPrivateCalls = true)
        justRun {
            action["showDiffDialog"](
                    project,
                    previousExport,
                    exportResult,
                    any<List<VirtualFile>>(),
                    any<
                            com.keyboardsamurais.intellij.plugin.sourceclipboardexport.ui.ExportNotificationPresenter>()
            )
        }

        mockkStatic(SwingUtilities::class)
        every { SwingUtilities.invokeLater(any()) } answers { firstArg<Runnable>().run() }

        action.actionPerformed(event)

        verify {
            action["showDiffDialog"](
                    project,
                    previousExport,
                    exportResult,
                    any<List<VirtualFile>>(),
                    any<
                            com.keyboardsamurais.intellij.plugin.sourceclipboardexport.ui.ExportNotificationPresenter>()
            )
        }
    }
}
