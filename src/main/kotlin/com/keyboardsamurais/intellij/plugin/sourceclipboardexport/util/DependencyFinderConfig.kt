package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util

/**
 * Configuration settings for DependencyFinder performance optimization
 */
object DependencyFinderConfig {
    
    // Text search performance limits
    var maxFilesToScan: Int = 1000
    var maxFileSizeBytes: Long = 500_000 // 500KB
    
    // Search scope configuration
    var searchDirs: List<String> = listOf("src", "components", "pages", "lib", "utils", "hooks", "views", "app")
    var skipDirs: List<String> = listOf("node_modules", "build", "dist", "out", ".next", ".git", "coverage", "test", "tests", "__tests__", ".vscode", ".idea")
    
    // Search strategy thresholds
    var psiSearchFallbackThreshold: Int = 2 // Use text search if PSI finds fewer than this many references
    var enableTextSearchFallback: Boolean = true
    
    // Performance monitoring
    var enablePerformanceLogging: Boolean = false // Reduced by default for better performance
    var logDetailedSearchInfo: Boolean = false    // Set to true for debugging
    
    // Caching configuration
    var enableCaching: Boolean = true
    
    // Processing configuration
    var elementBatchSize: Int = 10 // Process elements in batches
    var maxTraversalDepth: Int = 3 // Limit PSI tree traversal depth
    var maxElementsPerFile: Int = 50 // Limit elements to search per file
    
    /**
     * Reset to default values
     */
    fun resetToDefaults() {
        maxFilesToScan = 1000
        maxFileSizeBytes = 500_000
        searchDirs = listOf("src", "components", "pages", "lib", "utils", "hooks", "views", "app")
        skipDirs = listOf("node_modules", "build", "dist", "out", ".next", ".git", "coverage", "test", "tests", "__tests__", ".vscode", ".idea")
        psiSearchFallbackThreshold = 2
        enableTextSearchFallback = true
        enablePerformanceLogging = false
        logDetailedSearchInfo = false
        enableCaching = true
        elementBatchSize = 10
        maxTraversalDepth = 3
        maxElementsPerFile = 50
    }
    
    /**
     * Configure for small projects (more thorough search)
     */
    fun configureForSmallProject() {
        maxFilesToScan = 2000
        maxFileSizeBytes = 1_000_000 // 1MB
        psiSearchFallbackThreshold = 1
        enableTextSearchFallback = true
    }
    
    /**
     * Configure for large projects (faster search)
     */
    fun configureForLargeProject() {
        maxFilesToScan = 500
        maxFileSizeBytes = 200_000 // 200KB
        psiSearchFallbackThreshold = 3
        enableTextSearchFallback = true
        // Add more aggressive skip patterns for large projects
        skipDirs = skipDirs + listOf("vendor", "target", "tmp", "temp", "cache", "logs")
    }
    
    /**
     * Configure for performance-critical scenarios
     */
    fun configureForPerformance() {
        maxFilesToScan = 300
        maxFileSizeBytes = 100_000 // 100KB
        psiSearchFallbackThreshold = 5 // Only use text search if PSI really fails
        enableTextSearchFallback = false // Disable text search entirely
        logDetailedSearchInfo = false
        enableCaching = true
        elementBatchSize = 20 // Larger batches for performance
        maxTraversalDepth = 2 // Shallow traversal for speed
        maxElementsPerFile = 20 // Fewer elements for speed
    }
} 