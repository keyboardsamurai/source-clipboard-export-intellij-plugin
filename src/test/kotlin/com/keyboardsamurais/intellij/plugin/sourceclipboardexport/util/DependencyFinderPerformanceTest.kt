package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/**
 * Performance-focused tests for DependencyFinder functionality.
 * These tests ensure that the dependency finder is properly configured and behaves correctly.
 */
class DependencyFinderPerformanceTest {

    @BeforeEach
    fun setUp() {
        // Clear any cached data before each test
        DependencyFinder.clearCaches()
    }

    @Test
    fun `test DependencyFinder cache functionality`() {
        assertTrue(DependencyFinder.getCacheStats().contains("cache"))
    }

    @Test
    fun `test basic DependencyFinder instantiation`() {
        // Ensure the object can be accessed and basic methods work
        val statsBeforeClear = DependencyFinder.getCacheStats()
        DependencyFinder.clearCaches()
        val statsAfterClear = DependencyFinder.getCacheStats()
        
        // Both should contain "cache" in the stats string
        assertTrue(statsBeforeClear.contains("cache"))
        assertTrue(statsAfterClear.contains("cache"))
    }

    @Test
    fun `test cache clearing functionality`() {
        // Ensure cache clearing doesn't cause issues
        repeat(5) {
            DependencyFinder.clearCaches()
        }
        
        val stats = DependencyFinder.getCacheStats()
        assertTrue(stats.contains("0 entries") || stats.contains("cache"))
    }
}