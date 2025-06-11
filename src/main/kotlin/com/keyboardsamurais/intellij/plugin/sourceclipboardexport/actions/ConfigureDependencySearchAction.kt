package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ui.Messages
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.DependencyFinderConfig

class ConfigureDependencySearchAction : AnAction("Configure Dependency Search Performance...") {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getData(CommonDataKeys.PROJECT) ?: return
        
        val options = arrayOf(
            "Small Project (thorough search)",
            "Large Project (faster search)", 
            "Performance Critical (fastest)",
            "Default Settings",
            "View Current Settings"
        )
        
        val choice = Messages.showChooseDialog(
            project,
            "Configure dependency search performance based on your project size:",
            "Dependency Search Performance",
            Messages.getQuestionIcon(),
            options,
            options[3] // Default to "Default Settings"
        )
        
        when (choice) {
            0 -> {
                DependencyFinderConfig.configureForSmallProject()
                showConfigApplied("Small Project", """
                    • Max files to scan: ${DependencyFinderConfig.maxFilesToScan}
                    • Max file size: ${DependencyFinderConfig.maxFileSizeBytes / 1024}KB
                    • Text search fallback enabled
                    • Thorough search for better coverage
                """.trimIndent())
            }
            1 -> {
                DependencyFinderConfig.configureForLargeProject()
                showConfigApplied("Large Project", """
                    • Max files to scan: ${DependencyFinderConfig.maxFilesToScan}
                    • Max file size: ${DependencyFinderConfig.maxFileSizeBytes / 1024}KB
                    • Text search fallback enabled
                    • Faster search with more directory exclusions
                """.trimIndent())
            }
            2 -> {
                DependencyFinderConfig.configureForPerformance()
                showConfigApplied("Performance Critical", """
                    • Max files to scan: ${DependencyFinderConfig.maxFilesToScan}
                    • Max file size: ${DependencyFinderConfig.maxFileSizeBytes / 1024}KB
                    • Text search fallback disabled
                    • Fastest search, may miss some references
                """.trimIndent())
            }
            3 -> {
                DependencyFinderConfig.resetToDefaults()
                showConfigApplied("Default", """
                    • Max files to scan: ${DependencyFinderConfig.maxFilesToScan}
                    • Max file size: ${DependencyFinderConfig.maxFileSizeBytes / 1024}KB
                    • Text search fallback enabled
                    • Balanced performance and coverage
                """.trimIndent())
            }
            4 -> {
                showCurrentSettings()
            }
        }
    }
    
    private fun showConfigApplied(configName: String, details: String) {
        Messages.showInfoMessage(
            "$configName configuration applied successfully!\n\n$details",
            "Configuration Applied"
        )
    }
    
    private fun showCurrentSettings() {
        val settings = """
            Current Dependency Search Settings:
            
            Performance Limits:
            • Max files to scan: ${DependencyFinderConfig.maxFilesToScan}
            • Max file size: ${DependencyFinderConfig.maxFileSizeBytes / 1024}KB
            
            Search Strategy:
            • Text search fallback: ${if (DependencyFinderConfig.enableTextSearchFallback) "Enabled" else "Disabled"}
            • PSI fallback threshold: ${DependencyFinderConfig.psiSearchFallbackThreshold} references
            
            Search Scope:
            • Search directories: ${DependencyFinderConfig.searchDirs.joinToString(", ")}
            • Skip directories: ${DependencyFinderConfig.skipDirs.take(5).joinToString(", ")}${if (DependencyFinderConfig.skipDirs.size > 5) "..." else ""}
            
            Logging:
            • Performance logging: ${if (DependencyFinderConfig.enablePerformanceLogging) "Enabled" else "Disabled"}
            • Detailed search info: ${if (DependencyFinderConfig.logDetailedSearchInfo) "Enabled" else "Disabled"}
        """.trimIndent()
        
        Messages.showInfoMessage(settings, "Current Settings")
    }

    override fun update(e: AnActionEvent) {
        val project = e.getData(CommonDataKeys.PROJECT)
        e.presentation.isEnabledAndVisible = project != null
    }
} 