package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope

/**
 * Extended test finder that includes all types of tests and test resources
 */
object ExtendedTestFinder {
    
    private val TEST_PATTERNS = listOf(
        // Unit tests
        "*Test", "*Tests", "Test*", "*TestCase", "*TestSuite",
        // Specs
        "*Spec", "*Specs",
        // Integration tests
        "*IT", "*IntegrationTest", "*IntegrationTests", "*IntTest",
        // End-to-end tests
        "*E2ETest", "*E2E", "*EndToEndTest",
        // Performance tests
        "*PerfTest", "*PerformanceTest", "*LoadTest", "*StressTest",
        // Other test types
        "*Should", "*Feature", "*Scenario"
    )
    
    private val TEST_RESOURCE_PATTERNS = listOf(
        "test-data", "testdata", "test_data",
        "fixtures", "fixture", "mocks", "mock",
        "test-resources", "test_resources",
        "data", "samples", "examples"
    )
    
    /**
     * Find all test files related to the selected files, including all test types
     * @param project The current project
     * @param sourceFiles The source files to find tests for
     * @return Set of all related test files
     */
    fun findAllRelatedTests(project: Project, sourceFiles: Array<VirtualFile>): Set<VirtualFile> {
        val testFiles = mutableSetOf<VirtualFile>()
        val projectScope = GlobalSearchScope.projectScope(project)
        
        // For each source file, find related tests
        for (sourceFile in sourceFiles) {
            if (sourceFile.isDirectory) continue
            
            val baseName = sourceFile.nameWithoutExtension
            val extension = sourceFile.extension ?: continue
            
            // Find tests with direct naming relationship
            TEST_PATTERNS.forEach { pattern ->
                val testName = pattern.replace("*", baseName)
                val files = ReadAction.compute<Collection<VirtualFile>, Exception> {
                    FilenameIndex.getVirtualFilesByName("$testName.$extension", projectScope)
                }
                testFiles.addAll(files)
            }
            
            // Also look for tests in conventional test directories
            findTestsInTestDirectories(project, baseName, extension, testFiles)
        }
        
        // Find test resources
        testFiles.addAll(findTestResources(project))
        
        // Find test utilities and helpers
        testFiles.addAll(findTestUtilities(project))
        
        return testFiles
    }
    
    /**
     * Find all test files in the project (not just related to specific source files)
     */
    fun findAllTestFiles(project: Project): Set<VirtualFile> {
        val testFiles = mutableSetOf<VirtualFile>()
        
        runReadAction {
            val projectFileIndex = ProjectFileIndex.getInstance(project)
            
            projectFileIndex.iterateContent { file ->
                if (!file.isDirectory && isTestFile(file)) {
                    testFiles.add(file)
                }
                true
            }
        }
        
        return testFiles
    }
    
    private fun findTestsInTestDirectories(
        project: Project,
        baseName: String,
        extension: String,
        testFiles: MutableSet<VirtualFile>
    ) {
        runReadAction {
            val projectFileIndex = ProjectFileIndex.getInstance(project)
            
            projectFileIndex.iterateContent { file ->
                if (!file.isDirectory && 
                    file.extension == extension &&
                    isInTestDirectory(file) &&
                    file.nameWithoutExtension.contains(baseName, ignoreCase = true)) {
                    testFiles.add(file)
                }
                true
            }
        }
    }
    
    private fun findTestResources(project: Project): Set<VirtualFile> {
        val resources = mutableSetOf<VirtualFile>()
        
        runReadAction {
            val projectFileIndex = ProjectFileIndex.getInstance(project)
            
            projectFileIndex.iterateContent { file ->
                if (isTestResource(file)) {
                    resources.add(file)
                }
                true
            }
        }
        
        return resources
    }
    
    private fun findTestUtilities(project: Project): Set<VirtualFile> {
        val utilities = mutableSetOf<VirtualFile>()
        val projectScope = GlobalSearchScope.projectScope(project)
        
        // Common test utility patterns
        val utilityPatterns = listOf(
            "TestUtil*", "*TestUtil", "*TestUtils", "TestHelper*", "*TestHelper",
            "MockUtil*", "*MockUtil", "TestBase", "BaseTest", "AbstractTest*"
        )
        
        utilityPatterns.forEach { pattern ->
            // Search for common extensions using modern API
            listOf("java", "kt", "js", "ts", "py").forEach { ext ->
                val files = ReadAction.compute<Collection<VirtualFile>, Exception> {
                    FilenameIndex.getVirtualFilesByName("$pattern.$ext", projectScope)
                }
                utilities.addAll(files)
            }
        }
        
        return utilities
    }
    
    private fun isTestFile(file: VirtualFile): Boolean {
        val name = file.nameWithoutExtension
        val path = file.path
        
        // Check if in test directory
        if (isInTestDirectory(file)) return true
        
        // Check file name patterns
        return TEST_PATTERNS.any { pattern ->
            when {
                pattern.startsWith("*") && pattern.endsWith("*") -> {
                    name.contains(pattern.trim('*'), ignoreCase = true)
                }
                pattern.startsWith("*") -> {
                    name.endsWith(pattern.substring(1), ignoreCase = true)
                }
                pattern.endsWith("*") -> {
                    name.startsWith(pattern.dropLast(1), ignoreCase = true)
                }
                else -> {
                    name.equals(pattern, ignoreCase = true)
                }
            }
        }
    }
    
    private fun isInTestDirectory(file: VirtualFile): Boolean {
        val path = file.path
        return path.contains("/test/") || 
               path.contains("/tests/") ||
               path.contains("/spec/") ||
               path.contains("/specs/") ||
               path.contains("/__tests__/") ||
               path.contains("/test_") ||
               path.contains("_test/")
    }
    
    private fun isTestResource(file: VirtualFile): Boolean {
        val path = file.path
        
        // Must be in a test directory
        if (!isInTestDirectory(file)) return false
        
        // Check if in a test resource directory
        return TEST_RESOURCE_PATTERNS.any { pattern ->
            path.contains("/$pattern/", ignoreCase = true)
        } || (file.isDirectory && TEST_RESOURCE_PATTERNS.any { 
            file.name.equals(it, ignoreCase = true) 
        })
    }
}