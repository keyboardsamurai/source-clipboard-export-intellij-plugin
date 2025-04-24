package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util

object StringUtils {
    /**
     * Splits a string by camelCase boundaries.
     * E.g., "myVariableName" -> ["my", "Variable", "Name"]
     */
    fun splitCamelCase(input: String): List<String> {
        if (input.isEmpty()) return emptyList()
        val camelCaseSplitRegex = Regex("(?<=\\p{Ll})(?=\\p{Lu})|(?<=\\p{L})(?=\\p{Lu}\\p{Ll})|(?<=\\p{N})(?=\\p{L})|(?<=\\p{L})(?=\\p{N})")
        return camelCaseSplitRegex.split(input).filter { it.isNotEmpty() }
    }

    /**
     * Estimates token count using a regex heuristic combined with simulated subword splitting.
     */
    fun estimateTokensWithSubwordHeuristic(text: String): Int {
        if (text.isEmpty()) return 0
        val baseSplitRegex = Regex("([\\p{L}\\p{N}_]+)|([^\\p{L}\\p{N}_\\s]+)|(\\s+)")
        var tokenCount = 0
        val matches = baseSplitRegex.findAll(text)

        for (match in matches) {
            when {
                match.groups[1] != null -> { // Potential Identifier/Keyword/Number
                    val identifierPart = match.value
                    val snakeParts = identifierPart.split('_').filter { it.isNotEmpty() }
                    var subTokenCount = 0
                    for (part in snakeParts) {
                        subTokenCount += splitCamelCase(part).size
                    }
                    tokenCount += if (subTokenCount > 0) subTokenCount else 1
                }
                match.groups[2] != null -> { // Symbol/Punctuation Sequence
                    tokenCount += match.value.length
                }
                // Group 3: Whitespace - Ignored
            }
        }
        return tokenCount
    }

    fun isValidFilterFormat(filter: String): Boolean {
        return filter.matches(Regex("^\\.?\\w+$"))
    }
} 