package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util

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
    }
    
    @Test
    fun `test small project configuration`() {
        // Configure for small project
        DependencyFinderConfig.configureForSmallProject()
        
        assertEquals(2000, DependencyFinderConfig.maxFilesToScan)
        assertEquals(1_000_000, DependencyFinderConfig.maxFileSizeBytes)
        assertEquals(1, DependencyFinderConfig.psiSearchFallbackThreshold)
        assertTrue(DependencyFinderConfig.enableTextSearchFallback)
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
}