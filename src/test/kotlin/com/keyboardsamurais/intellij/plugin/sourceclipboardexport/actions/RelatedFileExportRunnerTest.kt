package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.testutils.ActionRunnersTestSetup
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RelatedFileExportRunnerTest {

    private val project = mockk<Project>(relaxed = true)
    private val selected = arrayOf(mockFile("Main.kt"), mockFile("Helper.kt"))
    private val collectedFile = mockFile("Test.kt")

    @BeforeEach
    fun setup() {
        ActionRunnersTestSetup.setupMocks(project)
    }

    @Test
    fun `run aggregates additional files and invokes callback`() {
        var callbackInvoked = false

        RelatedFileExportRunner.run(
            project = project,
            selectedFiles = selected,
            progressTitle = "Collecting",
            collector = { _, _ -> setOf(collectedFile) }
        ) { originals, additional ->
            callbackInvoked = true
            assertEquals(selected.toSet(), originals.toSet())
            assertEquals(setOf(collectedFile), additional)
        }

        assertTrue(callbackInvoked)
    }

    private fun mockFile(name: String): VirtualFile {
        val file = mockk<VirtualFile>(relaxed = true)
        every { file.name } returns name
        every { file.isDirectory } returns false
        return file
    }
}
