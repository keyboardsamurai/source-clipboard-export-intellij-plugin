package com.keyboardsamurais.intellij.plugin.sourceclipboardexport

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

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
        var ignoredNames: MutableList<String> = mutableListOf(".git", "node_modules", "build", "target", "__pycache__")
        var includePathPrefix: Boolean = true
    }

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        if (state.ignoredNames.isNullOrEmpty()) {
            state.ignoredNames = mutableListOf(".git", "node_modules", "build", "target", "__pycache__")
        }
        myState = state
    }

    companion object {
        fun getInstance(): SourceClipboardExportSettings {
            return ApplicationManager.getApplication().getService(SourceClipboardExportSettings::class.java)
        }
    }
}
