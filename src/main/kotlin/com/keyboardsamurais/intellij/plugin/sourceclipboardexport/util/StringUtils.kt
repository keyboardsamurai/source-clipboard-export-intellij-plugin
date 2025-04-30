package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util

import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.EncodingType

object StringUtils {

    /**
     * Estimates token count using the jtokkit library with the CL100K_BASE encoding (used by GPT-3.5-Turbo and GPT-4).
     */
    fun estimateTokensWithSubwordHeuristic(text: String): Int {
        if (text.isEmpty()) return 0

        // Get the encoding registry
        val registry = Encodings.newDefaultEncodingRegistry()

        // Get the encoding for CL100K_BASE (used by GPT-3.5-Turbo and GPT-4)
        val encoding = registry.getEncoding(EncodingType.CL100K_BASE)

        // Encode the text and count the tokens
        return encoding.countTokens(text)
    }

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
