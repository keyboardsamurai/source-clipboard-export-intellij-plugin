package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.CaretModel
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.InheritanceFinder
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.NotificationUtils
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ExportImplementationsAtCaretActionTest {

    private lateinit var project: Project
    private lateinit var event: AnActionEvent
    private lateinit var psiFile: PsiFile
    private lateinit var editor: Editor
    private lateinit var caretModel: CaretModel
    private lateinit var primaryFile: VirtualFile

    @BeforeEach
    fun setup() {
        project = mockk(relaxed = true)
        event = mockk(relaxed = true)
        psiFile = mockk(relaxed = true)
        editor = mockk(relaxed = true)
        caretModel = mockk(relaxed = true)
        primaryFile = mockk(relaxed = true)

        every { event.project } returns project
        every { event.getData(CommonDataKeys.PSI_FILE) } returns psiFile
        every { event.getData(CommonDataKeys.EDITOR) } returns editor
        every { psiFile.virtualFile } returns primaryFile
        every { editor.caretModel } returns caretModel
        every { caretModel.primaryCaret } returns mockk(relaxed = true)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `actionPerformed exports base file when no classes are found`() {
        val action = spyk(ExportImplementationsAtCaretAction(), recordPrivateCalls = true)

        every { action["resolveCleanSymbolAtCaret"](psiFile, editor) } returns null
        mockkObject(InheritanceFinder)
        every { InheritanceFinder.collectClasses(psiFile) } returns emptyList()
        mockkObject(NotificationUtils)
        mockkObject(SmartExportUtils)
        justRun { NotificationUtils.showNotification(any(), any(), any(), any()) }
        justRun { SmartExportUtils.exportFiles(any(), any()) }

        action.actionPerformed(event)

        verify {
            SmartExportUtils.exportFiles(project, match { files ->
                files.size == 1 && files[0] == primaryFile
            })
        }
    }
}
