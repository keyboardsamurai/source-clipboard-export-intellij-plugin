package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ActionUpdateSupportTest {

    private val event = mockk<AnActionEvent>(relaxed = true)
    private val project = mockk<Project>(relaxed = true)
    private val file = mockk<VirtualFile>(relaxed = true)

    @Test
    fun `hasProjectAndFiles returns true when project and files exist`() {
        every { event.project } returns project
        every { event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) } returns arrayOf(file)

        assertTrue(ActionUpdateSupport.hasProjectAndFiles(event))
    }

    @Test
    fun `hasProjectAndFiles applies predicate`() {
        every { event.project } returns project
        every { event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) } returns arrayOf(file)

        assertFalse(ActionUpdateSupport.hasProjectAndFiles(event) { false })
    }

    @Test
    fun `hasProjectAndFiles returns false when selection missing`() {
        every { event.project } returns project
        every { event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) } returns null

        assertFalse(ActionUpdateSupport.hasProjectAndFiles(event))
    }

    @Test
    fun `hasProject checks only project`() {
        every { event.project } returns project
        assertTrue(ActionUpdateSupport.hasProject(event))
        every { event.project } returns null
        assertFalse(ActionUpdateSupport.hasProject(event))
    }
}
