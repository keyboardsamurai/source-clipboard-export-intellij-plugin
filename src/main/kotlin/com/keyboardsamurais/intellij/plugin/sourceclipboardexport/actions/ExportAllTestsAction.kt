package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.vfs.VirtualFile
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.ActionRunners
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.ExtendedTestFinder
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.NotificationUtils

/**
 * Collects every test artifact related to the selection: unit/integration/E2E code, fixtures, and
 * helpers. Ideal when sharing a behavior and the corresponding specs.
 */
class ExportAllTestsAction : AnAction() {
    
    init {
        templatePresentation.text = "All Related Tests"
        templatePresentation.description = "Export all test types including integration and E2E tests"
    }
    
    /**
     * Uses [ExtendedTestFinder] to discover every relevant test artifact, enforces optional caps,
     * and exports the combined set. Also posts an informational balloon describing the mix of test
     * types included.
     */
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val selectedFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return
        
        if (selectedFiles.isEmpty()) {
            NotificationUtils.showNotification(project, "Export Error", "No files selected", com.intellij.notification.NotificationType.ERROR)
            return
        }
        
        ActionRunners.runSmartBackground(project, "Finding All Related Tests...") { indicator: ProgressIndicator ->
                try {
                    indicator.isIndeterminate = false
                    indicator.text = "Searching for all test types..."
                    
                    // Find all related tests
                    val testFiles = ExtendedTestFinder.findAllRelatedTests(project, selectedFiles)
                    
                    if (testFiles.isEmpty()) {
                        NotificationUtils.showNotification(
                            project, 
                            "Export Info",
                            "No tests found for the selected files",
                            com.intellij.notification.NotificationType.INFORMATION
                        )
                        return@runSmartBackground
                    }
                    
                    // Optional cap via system property
                    val maxTests = System.getProperty("sce.tests.max")?.toIntOrNull() ?: Int.MAX_VALUE
                    val limitedTests = if (testFiles.size > maxTests) testFiles.toList().sortedBy { it.path }.take(maxTests).toSet() else testFiles
                    if (testFiles.size > maxTests) {
                        NotificationUtils.showNotification(
                            project,
                            "Export Info",
                            "Processing first $maxTests of ${testFiles.size} tests",
                            com.intellij.notification.NotificationType.INFORMATION
                        )
                    }

                    // Categorize tests for better notification
                    val categorizedTests = categorizeTests(limitedTests)
                    
                    // Combine selected files with their tests
                    val allFiles = selectedFiles.toMutableSet()
                    allFiles.addAll(limitedTests)
                    
                    val description = buildTestDescription(categorizedTests)
                    
                    indicator.text = "Exporting ${allFiles.size} files..."
                    
                    // Export using SmartExportUtils
                    SmartExportUtils.exportFiles(
                        project,
                        allFiles.toTypedArray()
                    )
                    
                    NotificationUtils.showNotification(
                        project,
                        "All Tests Exported",
                        description,
                        com.intellij.notification.NotificationType.INFORMATION
                    )
                } catch (e: Exception) {
                    NotificationUtils.showNotification(
                        project, 
                        "Export Error",
                        "Failed to find tests: ${e.message}",
                        com.intellij.notification.NotificationType.ERROR
                    )
                }
        }
    }
    
    /** Requires at least one non-directory file because tests are resolved relative to files. */
    override fun update(e: AnActionEvent) {
        val project = e.project
        val selectedFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        
        e.presentation.isEnabled = project != null && 
                                  !selectedFiles.isNullOrEmpty() &&
                                  selectedFiles.any { !it.isDirectory }
    }
    
    /** Uses the BGT because update logic queries selected files. */
    override fun getActionUpdateThread() = com.intellij.openapi.actionSystem.ActionUpdateThread.BGT
    
    private data class TestCategories(
        val unitTests: MutableList<VirtualFile> = mutableListOf(),
        val integrationTests: MutableList<VirtualFile> = mutableListOf(),
        val e2eTests: MutableList<VirtualFile> = mutableListOf(),
        val performanceTests: MutableList<VirtualFile> = mutableListOf(),
        val testResources: MutableList<VirtualFile> = mutableListOf(),
        val testUtilities: MutableList<VirtualFile> = mutableListOf()
    ) {
        val total: Int
            get() = unitTests.size + integrationTests.size + e2eTests.size + 
                   performanceTests.size + testResources.size + testUtilities.size
    }
    
    /**
     * Buckets discovered tests to build a friendly notification and to help users understand
     * precisely what was included.
     */
    private fun categorizeTests(testFiles: Set<VirtualFile>): TestCategories {
        val categories = TestCategories()
        
        for (file in testFiles) {
            val name = file.nameWithoutExtension.lowercase()
            val path = file.path.lowercase()
            
            when {
                // Test resources (non-code files in test directories)
                file.extension !in setOf("java", "kt", "js", "ts", "py", "rb", "php", "cs", "go") &&
                (path.contains("/test/") || path.contains("/tests/")) -> {
                    categories.testResources.add(file)
                }
                // Test utilities
                name.contains("testutil") || name.contains("testhelper") || 
                name.contains("mockutil") || name.contains("basetest") -> {
                    categories.testUtilities.add(file)
                }
                // E2E tests
                name.contains("e2e") || name.contains("endtoend") -> {
                    categories.e2eTests.add(file)
                }
                // Performance tests
                name.contains("perf") || name.contains("performance") || 
                name.contains("load") || name.contains("stress") -> {
                    categories.performanceTests.add(file)
                }
                // Integration tests
                name.endsWith("it") || name.contains("integration") || 
                name.contains("inttest") -> {
                    categories.integrationTests.add(file)
                }
                // Unit tests (default)
                else -> {
                    categories.unitTests.add(file)
                }
            }
        }
        
        return categories
    }
    
    /** Builds a human-readable summary like `3 unit, 1 integration, 2 resources`. */
    private fun buildTestDescription(categories: TestCategories): String {
        return buildString {
            append("All Tests Export (")
            val parts = mutableListOf<String>()
            
            if (categories.unitTests.isNotEmpty()) {
                parts.add("${categories.unitTests.size} unit")
            }
            if (categories.integrationTests.isNotEmpty()) {
                parts.add("${categories.integrationTests.size} integration")
            }
            if (categories.e2eTests.isNotEmpty()) {
                parts.add("${categories.e2eTests.size} E2E")
            }
            if (categories.performanceTests.isNotEmpty()) {
                parts.add("${categories.performanceTests.size} performance")
            }
            if (categories.testResources.isNotEmpty()) {
                parts.add("${categories.testResources.size} resources")
            }
            if (categories.testUtilities.isNotEmpty()) {
                parts.add("${categories.testUtilities.size} utilities")
            }
            
            append(parts.joinToString(", "))
            append(")")
        }
    }
}
