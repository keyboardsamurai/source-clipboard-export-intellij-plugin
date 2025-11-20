package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util

import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.EncodingType

/**
 * Small collection of string helpers shared between exporter UI and core logic. Primarily houses
 * token estimators and validation helpers so we do not sprinkle regexes throughout the codebase.
 */
object StringUtils {

    // Cache the encoding registry and encoding to avoid recreating them for each call
    private val registry by lazy { Encodings.newDefaultEncodingRegistry() }
    private val encoding by lazy { registry.getEncoding(EncodingType.CL100K_BASE) }

    /**
     * Estimates token count using the jtokkit library with the CL100K_BASE encoding (used by GPT-3.5-Turbo and GPT-4).
     */
    fun estimateTokensWithSubwordHeuristic(text: String): Int {
        if (text.isEmpty()) return 0

        // Encode the text and count the tokens using the cached encoding
        return encoding.countTokens(text)
    }

    /** Returns `true` when the filter looks like `.ext` or `ext` (alphanumeric). */
    fun isValidFilterFormat(filter: String): Boolean {
        return filter.matches(Regex("^\\.?\\w+$"))
    }

    /**
     * Escapes special characters in XML content.
     */
    fun escapeXml(input: String): String {
        return input.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&apos;")
    }
} 
