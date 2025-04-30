package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppConstantsTest {

    @Test
    fun `verify notification group ID constant`() {
        assertEquals("SourceClipboardExport", AppConstants.NOTIFICATION_GROUP_ID)
    }

    @Test
    fun `verify filename prefix constant`() {
        assertEquals("// filename: ", AppConstants.FILENAME_PREFIX)
    }

    @Test
    fun `verify default ignored names contains expected entries`() {
        val expectedEntries = listOf(".git", "node_modules", "build", "target", "__pycache__")
        assertTrue(AppConstants.DEFAULT_IGNORED_NAMES.containsAll(expectedEntries))
        assertEquals(expectedEntries.size, AppConstants.DEFAULT_IGNORED_NAMES.size)
    }

    @Test
    fun `verify common binary extensions contains expected entries`() {
        // Check a sample of expected binary extensions
        val sampleExtensions = listOf("png", "jpg", "exe", "zip", "pdf", "class")
        assertTrue(sampleExtensions.all { it in AppConstants.COMMON_BINARY_EXTENSIONS })

        // Verify the size is as expected
        assertEquals(55, AppConstants.COMMON_BINARY_EXTENSIONS.size)

        // Verify all entries are lowercase
        assertTrue(AppConstants.COMMON_BINARY_EXTENSIONS.all { it == it.lowercase() })
    }
}
