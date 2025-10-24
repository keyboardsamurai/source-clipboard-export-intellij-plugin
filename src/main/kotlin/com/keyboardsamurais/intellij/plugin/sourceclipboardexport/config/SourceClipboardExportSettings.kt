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

        // Stack trace folding settings
        var stackTraceMinFramesToFold: Int = 3
        // Head/tail context frames to keep visible around a folded block
        var stackTraceKeepHeadFrames: Int = 1
        var stackTraceKeepTailFrames: Int = 1
        // Whether to include package hints in the placeholder line
        var stackTraceIncludePackageHints: Boolean = true
        // Whether to treat lines like "... N more" as foldable (default false to preserve canonical elision)
        var stackTraceTreatEllipsisAsFoldable: Boolean = false
        // Whether to append the raw, unfurled stack trace for lossless consumption by LLMs
        var stackTraceAppendRaw: Boolean = true
        // Customizable fold prefix lists
        var stackTraceAlwaysFoldPrefixes: MutableList<String> = mutableListOf(
            "java.", "javax.", "kotlin.", "kotlinx.", "scala.",
            "jdk.", "sun.", "com.sun.",
            "org.junit.", "junit.", "org.testng.",
            "org.mockito.", "net.bytebuddy.",
            "jakarta.",
            "reactor.", "io.reactivex.",
            "io.netty.",
            "org.apache.", "com.google.", "org.slf4j.", "ch.qos.logback.",
            "com.intellij.rt.", "org.gradle.", "org.jetbrains.",
            "worker.org.gradle.process.",
            "org.hibernate.",
            "com.zaxxer.hikari.",
            "org.postgresql."
        )
        var stackTraceNeverFoldPrefixes: MutableList<String> = mutableListOf(
            "org.springframework.test.context.",
            "com.mycompany.myapp."
        )
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
