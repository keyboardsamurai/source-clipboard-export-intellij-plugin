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
/**
 * Application-level service that persists exporter settings and exposes them to actions, UI
 * components, and tests. IntelliJ serializes [State] to disk automatically, so this service just
 * needs to provide getters/setters.
 */
class SourceClipboardExportSettings : PersistentStateComponent<SourceClipboardExportSettings.State> {
    /** Mutable bean persisted by IntelliJ's state store. */
    class State {
        var fileCount: Int = 200
        var filenameFilters: MutableList<String> = mutableListOf()
        var areFiltersEnabled: Boolean = false  // Disable by default when filter list is empty
        var hasMigratedFilterFlag: Boolean = false
        var maxFileSizeKb: Int = 500
        var ignoredNames: MutableList<String> = AppConstants.DEFAULT_IGNORED_NAMES.toMutableList()
        var includePathPrefix: Boolean = true
        var includeDirectoryStructure: Boolean = false
        var includeFilesInStructure: Boolean = false
        var includeRepositorySummary: Boolean = false
        var includeLineNumbers: Boolean = true  // Enable by default for better AI context
        var outputFormat: OutputFormat = OutputFormat.PLAIN_TEXT
        var stackTraceSettings: StackTraceSettings = StackTraceSettings()
    }

    /** Nested bean for stack-trace folding preferences used by [StackTraceFolder]. */
    data class StackTraceSettings(
        var minFramesToFold: Int = 3,
        var keepHeadFrames: Int = 1,
        var keepTailFrames: Int = 1,
        var includePackageHints: Boolean = true,
        var treatEllipsisAsFoldable: Boolean = false,
        var appendRaw: Boolean = true,
        var alwaysFoldPrefixes: MutableList<String> = mutableListOf(
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
        ),
        var neverFoldPrefixes: MutableList<String> = mutableListOf(
            "org.springframework.test.context.",
            "com.mycompany.myapp."
        )
    )

    private var myState = State()

    override fun getState(): State = myState

    /**
     * Applies persisted state and performs lightweight migrations (e.g., enabling filters when a
     * user already had patterns configured).
     */
    override fun loadState(state: State) {
        // Backward-compatible migration: if a user already has filename filters configured
        // from a previous version (when filters were always applied), enable filters so
        // their existing behavior is preserved. This migration runs only once.
        if (!state.hasMigratedFilterFlag) {
            if (state.filenameFilters.isNotEmpty() && !state.areFiltersEnabled) {
                state.areFiltersEnabled = true
            }
            state.hasMigratedFilterFlag = true
        }

        if (state.ignoredNames.isNullOrEmpty()) {
            state.ignoredNames = AppConstants.DEFAULT_IGNORED_NAMES.toMutableList()
        }
        myState = state
    }

    companion object {
        private var testInstance: SourceClipboardExportSettings? = null

        /**
         * Returns the service instance when running inside IDEA or a lightweight in-memory copy
         * when tests call code outside of the platform container.
         */
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

        /** For tests that need a deterministic settings object. */
        fun setTestInstance(instance: SourceClipboardExportSettings?) {
            testInstance = instance
        }
    }
} 
