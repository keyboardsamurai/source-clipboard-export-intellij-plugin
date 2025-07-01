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
        var fileCount: Int = 200
        var filenameFilters: MutableList<String> = mutableListOf()
        var areFiltersEnabled: Boolean = false  // Disable by default when filter list is empty
        var maxFileSizeKb: Int = 500
        var ignoredNames: MutableList<String> = AppConstants.DEFAULT_IGNORED_NAMES.toMutableList()
        var includePathPrefix: Boolean = true
        var includeDirectoryStructure: Boolean = false
        var includeFilesInStructure: Boolean = false
        var includeRepositorySummary: Boolean = false
        var includeLineNumbers: Boolean = true  // Enable by default for better AI context
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
        private var testInstance: SourceClipboardExportSettings? = null

        fun getInstance(): SourceClipboardExportSettings {
            try {
                val application = ApplicationManager.getApplication()
                return if (application != null) {
                    application.getService(SourceClipboardExportSettings::class.java)
                } else {
                    // Return a mock instance for testing
                    testInstance ?: SourceClipboardExportSettings().also { testInstance = it }
                }
            } catch (e: Exception) {
                // Return a mock instance for testing if any exception occurs
                return testInstance ?: SourceClipboardExportSettings().also { testInstance = it }
            }
        }

        // For testing purposes only
        fun setTestInstance(instance: SourceClipboardExportSettings?) {
            testInstance = instance
        }
    }
} 
