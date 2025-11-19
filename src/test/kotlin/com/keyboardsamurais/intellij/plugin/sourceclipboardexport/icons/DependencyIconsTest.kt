package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.icons

import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage

class DependencyIconsTest {

    @Test
    fun `test icon dimensions are 16x16`() {
        // Test all icons have correct dimensions
        val icons = listOf(
            DependencyIcons.DIRECT_IMPORTS,
            DependencyIcons.TRANSITIVE_IMPORTS,
            DependencyIcons.DEPENDENTS,
            DependencyIcons.BIDIRECTIONAL
        )
        
        icons.forEach { icon ->
            assert(icon.iconWidth == 16) { "Icon width should be 16" }
            assert(icon.iconHeight == 16) { "Icon height should be 16" }
        }
    }
    
    @Test
    fun `test icons render without error`() {
        // Test that all icons can be rendered without throwing exceptions
        val icons = mapOf(
            "Direct Imports" to DependencyIcons.DIRECT_IMPORTS,
            "Transitive Imports" to DependencyIcons.TRANSITIVE_IMPORTS,
            "Dependents" to DependencyIcons.DEPENDENTS,
            "Bidirectional" to DependencyIcons.BIDIRECTIONAL
        )
        
        icons.forEach { (name, icon) ->
            try {
                val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
                val g = image.createGraphics()
                icon.paintIcon(null, g, 0, 0)
                g.dispose()
                // If we get here, the icon rendered successfully
                assert(true) { "$name icon rendered successfully" }
            } catch (e: Exception) {
                assert(false) { "$name icon failed to render: ${e.message}" }
            }
        }
    }
    
    @Test
    fun `test icons render correctly regardless of theme`() {
        // Check current theme brightness
        try {
            // This is a workaround since we can't directly set dark theme in tests
            // We'll just ensure rendering works regardless of theme
            val icons = listOf(
                DependencyIcons.DIRECT_IMPORTS,
                DependencyIcons.TRANSITIVE_IMPORTS,
                DependencyIcons.DEPENDENTS,
                DependencyIcons.BIDIRECTIONAL
            )
            
            icons.forEach { icon ->
                val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
                val g = image.createGraphics()
                icon.paintIcon(null, g, 0, 0)
                g.dispose()
                // Verify some pixels were painted (icon is not empty)
                var pixelsPainted = false
                for (x in 0 until 16) {
                    for (y in 0 until 16) {
                        if (image.getRGB(x, y) != 0) {
                            pixelsPainted = true
                            break
                        }
                    }
                    if (pixelsPainted) break
                }
                assert(pixelsPainted) { "Icon should paint some pixels" }
            }
        } finally {
            // Restore original theme state if we changed it
        }
    }
    
    @Test
    fun `test icons are visually distinct`() {
        // Test that each icon produces a different visual output
        val icons = mapOf(
            "Direct Imports" to DependencyIcons.DIRECT_IMPORTS,
            "Transitive Imports" to DependencyIcons.TRANSITIVE_IMPORTS,
            "Dependents" to DependencyIcons.DEPENDENTS,
            "Bidirectional" to DependencyIcons.BIDIRECTIONAL
        )
        
        val renderedImages = mutableMapOf<String, BufferedImage>()
        
        // Render each icon
        icons.forEach { (name, icon) ->
            val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
            val g = image.createGraphics()
            icon.paintIcon(null, g, 0, 0)
            g.dispose()
            renderedImages[name] = image
        }
        
        // Compare each pair of icons to ensure they're different
        val iconNames = renderedImages.keys.toList()
        for (i in 0 until iconNames.size) {
            for (j in i + 1 until iconNames.size) {
                val name1 = iconNames[i]
                val name2 = iconNames[j]
                val image1 = renderedImages[name1]!!
                val image2 = renderedImages[name2]!!
                
                var foundDifference = false
                outer@ for (x in 0 until 16) {
                    for (y in 0 until 16) {
                        if (image1.getRGB(x, y) != image2.getRGB(x, y)) {
                            foundDifference = true
                            break@outer
                        }
                    }
                }
                
                assert(foundDifference) { "$name1 and $name2 icons should be visually different" }
            }
        }
    }
}
