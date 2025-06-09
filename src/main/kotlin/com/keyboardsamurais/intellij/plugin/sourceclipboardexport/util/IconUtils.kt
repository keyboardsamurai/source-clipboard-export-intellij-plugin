package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

object IconUtils {
    /**
     * Safely loads an icon, returning null if the icon cannot be loaded (e.g., in test environments)
     */
    fun loadIcon(path: String, clazz: Class<*>): Icon? {
        return try {
            IconLoader.getIcon(path, clazz)
        } catch (e: Exception) {
            // Icon loading can fail in test environments or when resources are not available
            null
        }
    }
}