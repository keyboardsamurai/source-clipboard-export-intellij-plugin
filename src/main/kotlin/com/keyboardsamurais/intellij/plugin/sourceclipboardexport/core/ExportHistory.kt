package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
@State(
    name = "ExportHistory",
    storages = [Storage("exportHistory.xml")]
)
class ExportHistory : PersistentStateComponent<ExportHistory.State> {
    
    data class ExportEntry(
        var timestamp: Long = 0,
        var fileCount: Int = 0,
        var sizeBytes: Int = 0,
        var tokens: Int = 0,
        var filePaths: MutableList<String> = mutableListOf(),
        var summary: String = ""
    )
    
    class State {
        var exports: MutableList<ExportEntry> = mutableListOf()
        var maxEntries: Int = 10 // Keep last 10 exports
    }
    
    private var myState = State()
    
    override fun getState(): State = myState
    
    override fun loadState(state: State) {
        myState = state
    }
    
    fun addExport(fileCount: Int, sizeBytes: Int, tokens: Int, filePaths: List<String>) {
        val entry = ExportEntry(
            timestamp = System.currentTimeMillis(),
            fileCount = fileCount,
            sizeBytes = sizeBytes,
            tokens = tokens,
            filePaths = filePaths.toMutableList(),
            summary = generateSummary(fileCount, sizeBytes, tokens)
        )
        
        myState.exports.add(0, entry) // Add to front
        
        // Keep only the last N entries
        while (myState.exports.size > myState.maxEntries) {
            myState.exports.removeAt(myState.exports.size - 1)
        }
    }
    
    fun getRecentExports(): List<ExportEntry> {
        return myState.exports.toList()
    }
    
    fun clearHistory() {
        myState.exports.clear()
    }
    
    private fun generateSummary(fileCount: Int, sizeBytes: Int, tokens: Int): String {
        val sizeKB = sizeBytes / 1024.0
        val sizeDisplay = if (sizeKB < 1024) {
            String.format("%.1f KB", sizeKB)
        } else {
            String.format("%.1f MB", sizeKB / 1024.0)
        }
        return "$fileCount files, $sizeDisplay, ~${String.format("%,d", tokens)} tokens"
    }
    
    companion object {
        fun getInstance(project: Project): ExportHistory {
            return project.getService(ExportHistory::class.java)
        }
    }
}