package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vfs.VirtualFile
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core.filter.ExclusionReason
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core.filter.ExportFilter
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core.filter.FileProperties
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.FileUtils
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class FileTraverserTest {

    private val project = mockk<Project>(relaxed = true)
    private lateinit var stats: ExportStatistics

    @BeforeEach
    fun setup() {
        stats = ExportStatistics()
        mockkStatic(ReadAction::class)
        mockkObject(FileUtils)
        every { ReadAction.compute(any<ThrowableComputable<*, *>>()) } answers { call ->
            @Suppress("UNCHECKED_CAST")
            val computable = call.invocation.args[0] as ThrowableComputable<Any?, Exception>
            computable.compute()
        }
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `traverse emits relative paths for files that pass filters`() = runBlocking {
        val file = mockFile("Main.kt", isDirectory = false, path = "/repo/Main.kt")
        every { FileUtils.getRelativePath(file, project) } returns "src/Main.kt"

        val traverser = FileTraverser(project, stats, emptyList(), emptyList(), fileCountLimit = 5)
        val seen = mutableListOf<String>()

        traverser.traverse(arrayOf(file)) { _, _, relative ->
            stats.fileCount.incrementAndGet()
            seen += relative
        }

        assertEquals(listOf("src/Main.kt"), seen)
    }

    @Test
    fun `traverse honors traversal filters for directories`() = runBlocking {
        val nestedFile = mockFile("Hidden.kt", isDirectory = false, path = "/repo/build/Hidden.kt")
        val dir = mockFile("build", isDirectory = true, path = "/repo/build", children = arrayOf(nestedFile))

        val traversalFilter = object : ExportFilter {
            override fun shouldExclude(file: VirtualFile, properties: FileProperties, relativePath: String?): ExclusionReason? {
                return if (properties.isDirectory && properties.name == "build") ExclusionReason.IGNORED_NAME else null
            }
        }

        var invoked = false
        val traverser = FileTraverser(project, stats, listOf(traversalFilter), emptyList(), fileCountLimit = 10)

        traverser.traverse(arrayOf(dir)) { _, _, _ ->
            invoked = true
        }

        assertFalse(invoked, "No files should be emitted when the directory is excluded")
        assertEquals(1, stats.excludedByIgnoredNameCount.get())
    }

    @Test
    fun `traverse records inclusion filter exclusions and extensions`() = runBlocking {
        val file = mockFile("Sample.kt", isDirectory = false, path = "/repo/Sample.kt")
        every { FileUtils.getRelativePath(file, project) } returns "src/Sample.kt"

        val inclusionFilter = object : ExportFilter {
            override fun shouldExclude(file: VirtualFile, properties: FileProperties, relativePath: String?): ExclusionReason? {
                return ExclusionReason.FILENAME_FILTER
            }
        }

        var invoked = false
        val traverser = FileTraverser(project, stats, emptyList(), listOf(inclusionFilter), fileCountLimit = 10)

        traverser.traverse(arrayOf(file)) { _, _, _ ->
            invoked = true
        }

        assertFalse(invoked, "Inclusion filter should prevent file emission")
        assertEquals(1, stats.excludedByFilterCount.get())
        assertTrue("kt" in stats.excludedExtensions, "Extension should be recorded when filename filter excludes a file")
    }

    private fun mockFile(
        name: String,
        isDirectory: Boolean,
        path: String,
        children: Array<VirtualFile> = emptyArray()
    ): VirtualFile {
        val vf = mockk<VirtualFile>(relaxed = true)
        every { vf.name } returns name
        every { vf.path } returns path
        every { vf.isDirectory } returns isDirectory
        every { vf.isValid } returns true
        every { vf.exists() } returns true
        every { vf.length } returns 10
        val extension = name.substringAfterLast('.', missingDelimiterValue = name)
            .takeIf { name.contains('.') && !isDirectory }
        every { vf.extension } returns extension
        every { vf.children } returns children
        children.forEach { child -> every { child.parent } returns vf }
        return vf
    }
}
