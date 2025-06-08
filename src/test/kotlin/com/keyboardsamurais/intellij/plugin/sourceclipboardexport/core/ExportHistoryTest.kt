package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core

import com.intellij.openapi.project.Project
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ExportHistoryTest {

    private lateinit var project: Project
    private lateinit var exportHistory: ExportHistory

    @BeforeEach
    fun setup() {
        project = mockk(relaxed = true)
        exportHistory = ExportHistory()
    }

    @Test
    fun `test add export creates entry with correct data`() {
        val filePaths = listOf("/project/src/Main.java", "/project/src/Utils.java")
        
        exportHistory.addExport(2, 1024, 250, filePaths)
        
        val exports = exportHistory.getRecentExports()
        assertEquals(1, exports.size)
        
        val export = exports.first()
        assertEquals(2, export.fileCount)
        assertEquals(1024, export.sizeBytes)
        assertEquals(250, export.tokens)
        assertEquals(filePaths, export.filePaths)
        assertTrue(export.timestamp > 0)
    }

    @Test
    fun `test export history maintains max 10 entries`() {
        // Add 15 exports
        repeat(15) { i ->
            exportHistory.addExport(
                fileCount = i + 1,
                sizeBytes = (i + 1) * 1024,
                tokens = (i + 1) * 100,
                filePaths = listOf("/project/file$i.java")
            )
        }
        
        val exports = exportHistory.getRecentExports()
        assertEquals(10, exports.size)
        
        // Should contain the 10 most recent exports (6-15)
        assertEquals(15, exports.first().fileCount) // Most recent
        assertEquals(6, exports.last().fileCount)   // Oldest kept
    }

    @Test
    fun `test export history returns entries in chronological order`() {
        // Add exports with small delays to ensure different timestamps
        exportHistory.addExport(1, 100, 25, listOf("/file1.java"))
        Thread.sleep(1)
        exportHistory.addExport(2, 200, 50, listOf("/file2.java"))
        Thread.sleep(1)
        exportHistory.addExport(3, 300, 75, listOf("/file3.java"))
        
        val exports = exportHistory.getRecentExports()
        assertEquals(3, exports.size)
        
        // Should be in reverse chronological order (newest first)
        assertEquals(3, exports[0].fileCount)
        assertEquals(2, exports[1].fileCount)
        assertEquals(1, exports[2].fileCount)
        
        // Verify timestamps are in descending order
        assertTrue(exports[0].timestamp > exports[1].timestamp)
        assertTrue(exports[1].timestamp > exports[2].timestamp)
    }

    @Test
    fun `test empty history returns empty list`() {
        val exports = exportHistory.getRecentExports()
        assertTrue(exports.isEmpty())
    }

    @Test
    fun `test export entry summary formatting`() {
        val filePaths = listOf("/project/src/Main.java")
        exportHistory.addExport(1, 1536, 384, filePaths) // 1.5 KB
        
        val export = exportHistory.getRecentExports().first()
        val summary = export.summary
        
        assertTrue(summary.contains("1 files"))
        assertTrue(summary.contains("1.5 KB"))
        assertTrue(summary.contains("384 tokens"))
    }

    @Test
    fun `test export entry summary formatting for MB`() {
        val filePaths = listOf("/project/src/Large.java")
        exportHistory.addExport(1, 2 * 1024 * 1024, 512000, filePaths) // 2 MB
        
        val export = exportHistory.getRecentExports().first()
        val summary = export.summary
        
        assertTrue(summary.contains("1 files"))
        assertTrue(summary.contains("2.0 MB"))
        assertTrue(summary.contains("512,000 tokens"))
    }

    @Test
    fun `test state persistence structure`() {
        exportHistory.addExport(2, 1024, 250, listOf("/file1.java", "/file2.java"))
        
        val state = exportHistory.state
        assertNotNull(state)
        assertEquals(1, state.exports.size)
        
        val exportData = state.exports.first()
        assertEquals(2, exportData.fileCount)
        assertEquals(1024, exportData.sizeBytes)
        assertEquals(250, exportData.tokens)
        assertEquals(listOf("/file1.java", "/file2.java"), exportData.filePaths)
    }

    @Test
    fun `test state loading`() {
        // Create a state with data
        val state = ExportHistory.State()
        val exportData = ExportHistory.ExportEntry(
            timestamp = System.currentTimeMillis(),
            fileCount = 3,
            sizeBytes = 2048,
            tokens = 500,
            filePaths = mutableListOf("/a.java", "/b.java", "/c.java"),
            summary = "3 files, 2.0 KB, ~500 tokens"
        )
        state.exports.add(exportData)
        
        // Load state into history
        exportHistory.loadState(state)
        
        val exports = exportHistory.getRecentExports()
        assertEquals(1, exports.size)
        
        val export = exports.first()
        assertEquals(3, export.fileCount)
        assertEquals(2048, export.sizeBytes)
        assertEquals(500, export.tokens)
        assertEquals(listOf("/a.java", "/b.java", "/c.java"), export.filePaths)
    }
}