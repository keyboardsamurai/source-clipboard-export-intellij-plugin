package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions

import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core.ExportHistory
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core.SourceExporter
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.ui.ExportNotificationPresenter
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.FileUtils
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import javax.swing.SwingUtilities
import kotlin.test.assertTrue

class ExportDiffDialogTest {

    private val project = mockk<Project>(relaxed = true)
    private val presenter = mockk<ExportNotificationPresenter>(relaxed = true)

    @BeforeEach
    fun setup() {
        mockkStatic(ApplicationManager::class)
        val app = mockk<Application>(relaxed = true)
        every { ApplicationManager.getApplication() } returns app
        every { app.isUnitTestMode } returns true
        every { app.isHeadlessEnvironment } returns true
        every { app.invokeLater(any()) } answers { (it.invocation.args[0] as Runnable).run() }
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `copyDiffToClipboard emits summary and notification`() {
        mockkObject(FileUtils)
        val repoRoot = mockk<VirtualFile>()
        every { repoRoot.path } returns "/repo"
        every { FileUtils.getRepositoryRoot(project) } returns repoRoot

        val copyPasteManager = mockk<CopyPasteManager>(relaxed = true)
        mockkStatic(CopyPasteManager::class)
        every { CopyPasteManager.getInstance() } returns copyPasteManager
        val selectionSlot = slot<StringSelection>()
        justRun { copyPasteManager.setContents(capture(selectionSlot)) }

        val lastExport = ExportHistory.ExportEntry(
                timestamp = 0,
                fileCount = 1,
                sizeBytes = 10,
                tokens = 5,
                filePaths = mutableListOf("/repo/src/Old.kt"),
                summary = "Prev"
        )
        val currentResult = SourceExporter.ExportResult(
                content = "class Foo {}",
                processedFileCount = 1,
                excludedByFilterCount = 0,
                excludedBySizeCount = 0,
                excludedByBinaryContentCount = 0,
                excludedByIgnoredNameCount = 0,
                excludedByGitignoreCount = 0,
                excludedExtensions = emptySet(),
                limitReached = false,
                includedPaths = listOf("src/New.kt")
        )

        val dialog = createDialog(lastExport, currentResult)
        val method = ExportDiffDialog::class.java.getDeclaredMethod("copyDiffToClipboard")
        method.isAccessible = true

        method.invoke(dialog)

        val copied = selectionSlot.captured.getTransferData(DataFlavor.stringFlavor) as String
        assertTrue(copied.contains("Added Files"))
        assertTrue(copied.contains("src/New.kt"))
        io.mockk.verify { presenter.showDiffCopiedNotification() }
    }

    @Test
    fun `exportChangesOnly shows friendly message when no additions`() {
        mockkObject(FileUtils)
        every { FileUtils.getRepositoryRoot(project) } returns null

        val lastExport = ExportHistory.ExportEntry(
                timestamp = 0,
                fileCount = 1,
                sizeBytes = 10,
                tokens = 5,
                filePaths = mutableListOf("src/Shared.kt"),
                summary = "Prev"
        )
        val currentResult = SourceExporter.ExportResult(
                content = "unchanged",
                processedFileCount = 1,
                excludedByFilterCount = 0,
                excludedBySizeCount = 0,
                excludedByBinaryContentCount = 0,
                excludedByIgnoredNameCount = 0,
                excludedByGitignoreCount = 0,
                excludedExtensions = emptySet(),
                limitReached = false,
                includedPaths = listOf("src/Shared.kt")
        )

        val dialog = createDialog(lastExport, currentResult)
        val method = ExportDiffDialog::class.java.getDeclaredMethod("exportChangesOnly")
        method.isAccessible = true

        method.invoke(dialog)

        verify { presenter.showNoChangesNotification() }
    }

    private fun createDialog(
        lastExport: ExportHistory.ExportEntry,
        currentResult: SourceExporter.ExportResult
    ): ExportDiffDialog {
        lateinit var dialog: ExportDiffDialog
        SwingUtilities.invokeAndWait {
            dialog = ExportDiffDialog(project, lastExport, currentResult, emptyList(), presenter)
        }
        return dialog
    }
}
