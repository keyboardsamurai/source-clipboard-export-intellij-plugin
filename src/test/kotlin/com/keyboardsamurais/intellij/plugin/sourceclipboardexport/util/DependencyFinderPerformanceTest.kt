package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util

import com.intellij.openapi.project.Project
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DependencyFinderPerformanceTest {
    
    @BeforeEach
    fun setUp() {
        // Reset config to defaults
        DependencyFinderConfig.resetToDefaults()
        DependencyFinderConfig.enablePerformanceLogging = true
        
        // Clear caches
        DependencyFinder.clearCaches()
    }
    
    @AfterEach
    fun tearDown() {
        DependencyFinderConfig.resetToDefaults()
        DependencyFinder.clearCaches()
    }
    
    @Test
    fun `test caching configuration`() {
        // Test that caching can be enabled/disabled
        DependencyFinderConfig.enableCaching = true
        assertTrue(DependencyFinderConfig.enableCaching)
        
        DependencyFinderConfig.enableCaching = false
        assertFalse(DependencyFinderConfig.enableCaching)
        
        // Clear caches
        DependencyFinder.clearCaches()
        val stats = DependencyFinder.getCacheStats()
        assertTrue(stats.contains("0 entries"))
    }
    
    @Test
    fun `test performance configuration affects settings`() {
        // Configure for performance
        DependencyFinderConfig.configureForPerformance()
        
        assertEquals(300, DependencyFinderConfig.maxFilesToScan)
        assertEquals(100_000, DependencyFinderConfig.maxFileSizeBytes)
        assertFalse(DependencyFinderConfig.enableTextSearchFallback)
        assertEquals(2, DependencyFinderConfig.maxTraversalDepth)
        assertEquals(20, DependencyFinderConfig.elementBatchSize)
        assertTrue(DependencyFinderConfig.enableCaching)
        assertEquals(2, DependencyFinderConfig.maxConcurrentPsiSearches)
        assertEquals(50, DependencyFinderConfig.maxResultsPerSearch)
        assertTrue(DependencyFinderConfig.enableEarlyTermination)
    }
    
    @Test
    fun `test small project configuration`() {
        // Configure for small project
        DependencyFinderConfig.configureForSmallProject()
        
        assertEquals(2000, DependencyFinderConfig.maxFilesToScan)
        assertEquals(1_000_000, DependencyFinderConfig.maxFileSizeBytes)
        assertEquals(1, DependencyFinderConfig.psiSearchFallbackThreshold)
        assertTrue(DependencyFinderConfig.enableTextSearchFallback)
        assertEquals(6, DependencyFinderConfig.maxConcurrentPsiSearches)
        assertEquals(500, DependencyFinderConfig.maxResultsPerSearch)
        assertFalse(DependencyFinderConfig.enableEarlyTermination)
        assertEquals(100, DependencyFinderConfig.maxElementsPerFile)
    }
    
    @Test
    fun `test large project configuration`() {
        // Configure for large project
        DependencyFinderConfig.configureForLargeProject()
        
        assertEquals(500, DependencyFinderConfig.maxFilesToScan)
        assertEquals(200_000, DependencyFinderConfig.maxFileSizeBytes)
        assertEquals(3, DependencyFinderConfig.psiSearchFallbackThreshold)
        assertTrue(DependencyFinderConfig.enableTextSearchFallback)
        
        // Should have additional skip directories
        assertTrue(DependencyFinderConfig.skipDirs.contains("vendor"))
        assertTrue(DependencyFinderConfig.skipDirs.contains("target"))
        
        // Performance settings
        assertEquals(2, DependencyFinderConfig.maxConcurrentPsiSearches)
        assertEquals(100, DependencyFinderConfig.maxResultsPerSearch)
        assertTrue(DependencyFinderConfig.enableEarlyTermination)
        assertEquals(20, DependencyFinderConfig.elementBatchSize)
        assertEquals(25, DependencyFinderConfig.maxElementsPerFile)
    }
    
    @Test
    fun `test element batching configuration`() {
        // Test different batch sizes
        DependencyFinderConfig.elementBatchSize = 5
        assertEquals(5, DependencyFinderConfig.elementBatchSize)
        
        DependencyFinderConfig.elementBatchSize = 20
        assertEquals(20, DependencyFinderConfig.elementBatchSize)
    }
    
    @Test
    fun `test traversal depth configuration`() {
        // Test different traversal depths
        DependencyFinderConfig.maxTraversalDepth = 1
        assertEquals(1, DependencyFinderConfig.maxTraversalDepth)
        
        DependencyFinderConfig.maxTraversalDepth = 5
        assertEquals(5, DependencyFinderConfig.maxTraversalDepth)
    }
    
    @Test
    fun `test interactive configuration`() {
        // Configure for interactive/responsive use
        DependencyFinderConfig.configureForInteractive()
        
        assertEquals(200, DependencyFinderConfig.maxFilesToScan)
        assertEquals(50_000, DependencyFinderConfig.maxFileSizeBytes)
        assertEquals(1, DependencyFinderConfig.maxConcurrentPsiSearches)
        assertEquals(30, DependencyFinderConfig.maxResultsPerSearch)
        assertTrue(DependencyFinderConfig.enableEarlyTermination)
        assertEquals(5, DependencyFinderConfig.elementBatchSize)
        assertEquals(2, DependencyFinderConfig.maxTraversalDepth)
        assertEquals(10, DependencyFinderConfig.maxElementsPerFile)
    }
    
    @Test
    fun `test concurrency control settings`() {
        // Test different concurrency settings
        DependencyFinderConfig.maxConcurrentPsiSearches = 1
        assertEquals(1, DependencyFinderConfig.maxConcurrentPsiSearches)
        
        DependencyFinderConfig.maxConcurrentPsiSearches = 8
        assertEquals(8, DependencyFinderConfig.maxConcurrentPsiSearches)
        
        // Test progressive batching
        DependencyFinderConfig.enableProgressiveBatching = true
        assertTrue(DependencyFinderConfig.enableProgressiveBatching)
        
        DependencyFinderConfig.enableProgressiveBatching = false
        assertFalse(DependencyFinderConfig.enableProgressiveBatching)
    }
    
    @Test
    fun `test early termination settings`() {
        // Test early termination configuration
        DependencyFinderConfig.enableEarlyTermination = true
        assertTrue(DependencyFinderConfig.enableEarlyTermination)
        
        DependencyFinderConfig.maxResultsPerSearch = 100
        assertEquals(100, DependencyFinderConfig.maxResultsPerSearch)
        
        DependencyFinderConfig.enableEarlyTermination = false
        assertFalse(DependencyFinderConfig.enableEarlyTermination)
        
        DependencyFinderConfig.maxResultsPerSearch = 1000
        assertEquals(1000, DependencyFinderConfig.maxResultsPerSearch)
    }
    
    @Test
    fun `test configuration validation warnings`() {
        val mockProject = mockk<Project>(relaxed = true)
        
        // Test high concurrency warning
        DependencyFinderConfig.maxConcurrentPsiSearches = 6
        DependencyFinder.validateConfiguration(mockProject, 15)
        // Should log warning about high concurrency
        
        // Test early termination disabled warning
        DependencyFinderConfig.enableEarlyTermination = false
        DependencyFinder.validateConfiguration(mockProject, 10)
        // Should log warning about early termination disabled
        
        // Test high elements per file warning
        DependencyFinderConfig.maxElementsPerFile = 150
        DependencyFinder.validateConfiguration(mockProject, 5)
        // Should log warning about high maxElementsPerFile
    }
}