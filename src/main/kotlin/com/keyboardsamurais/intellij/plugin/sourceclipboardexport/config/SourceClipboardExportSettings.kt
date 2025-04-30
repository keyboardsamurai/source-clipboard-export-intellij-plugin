package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.config

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.AppConstants
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.AppConstants.OutputFormat

@Service(Service.Level.APP)
@State(
    name = "SourceClipboardExportSettings",
    storages = [Storage("SourceClipboardExportSettings.xml")]
)
class SourceClipboardExportSettings : PersistentStateComponent<SourceClipboardExportSettings.State> {
    class State {
        var fileCount: Int = 50
        var filenameFilters: MutableList<String> = mutableListOf()
        var areFiltersEnabled: Boolean = true
        var maxFileSizeKb: Int = 100
        var ignoredNames: MutableList<String> = AppConstants.DEFAULT_IGNORED_NAMES.toMutableList()
        var includePathPrefix: Boolean = true
        var includeDirectoryStructure: Boolean = false
        var includeFilesInStructure: Boolean = false
        var includeRepositorySummary: Boolean = false
        var outputFormat: OutputFormat = OutputFormat.PLAIN_TEXT
    }

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        if (state.ignoredNames.isNullOrEmpty()) {
            state.ignoredNames = AppConstants.DEFAULT_IGNORED_NAMES.toMutableList()
        }
        myState = state
    }

    companion object {
        fun getInstance(): SourceClipboardExportSettings {
            return ApplicationManager.getApplication().getService(SourceClipboardExportSettings::class.java)
        }
    }
} 
