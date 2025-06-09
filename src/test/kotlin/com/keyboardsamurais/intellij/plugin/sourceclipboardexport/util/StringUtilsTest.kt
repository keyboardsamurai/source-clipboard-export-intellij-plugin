package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util

import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.EncodingType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class StringUtilsTest {

    @Test
    fun `estimateTokensWithSubwordHeuristic returns 0 for empty string`() {
        val result = StringUtils.estimateTokensWithSubwordHeuristic("")
        assertEquals(0, result)
    }

    @Test
    fun `estimateTokensWithSubwordHeuristic returns correct token count for simple text`() {
        val text = "Hello, world!"
        val result = StringUtils.estimateTokensWithSubwordHeuristic(text)
        // "Hello, world!" should be around 3 tokens with CL100K_BASE encoding
        assertTrue(result in 2..4, "Expected token count to be between 2 and 4, but was $result")
    }

    @Test
    fun `estimateTokensWithSubwordHeuristic handles longer text correctly`() {
        val text = "This is a longer piece of text that should be tokenized into multiple tokens. " +
                "The CL100K_BASE encoding used by GPT-3.5-Turbo and GPT-4 should handle this efficiently."
        val result = StringUtils.estimateTokensWithSubwordHeuristic(text)
        // This text should be around 30-40 tokens
        assertTrue(result > 20, "Expected token count to be greater than 20, but was $result")
        assertTrue(result < 50, "Expected token count to be less than 50, but was $result")
    }

    @Test
    fun `estimateTokensWithSubwordHeuristic handles code snippets correctly`() {
        val code = """
            fun main() {
                println("Hello, world!")
                val x = 10
                if (x > 5) {
                    println("x is greater than 5")
                }
            }
        """.trimIndent()
        val result = StringUtils.estimateTokensWithSubwordHeuristic(code)
        // Code snippets typically have more tokens due to special characters and formatting
        assertTrue(result > 15, "Expected token count to be greater than 15, but was $result")
    }

    @ParameterizedTest
    @MethodSource("provideTextAndExpectedTokenRanges")
    fun `estimateTokensWithSubwordHeuristic returns expected token counts for various inputs`(
        text: String, 
        minTokens: Int, 
        maxTokens: Int
    ) {
        val result = StringUtils.estimateTokensWithSubwordHeuristic(text)
        assertTrue(
            result in minTokens..maxTokens, 
            "Expected token count to be between $minTokens and $maxTokens, but was $result for text: '${text.take(20)}${if (text.length > 20) "..." else ""}'"
        )
    }

    companion object {
        @JvmStatic
        fun provideTextAndExpectedTokenRanges(): Stream<Arguments> {
            return Stream.of(
                Arguments.of("a", 1, 1),  // Single character
                Arguments.of("Hello", 1, 1),  // Simple word
                Arguments.of("Hello world", 2, 2),  // Two simple words
                Arguments.of("123456789", 1, 3),  // Numbers
                Arguments.of("!@#$%^&*()", 2, 10),  // Special characters
                Arguments.of("const val x = 42", 4, 8),  // Simple code
                Arguments.of("", 0, 0),  // Empty string
                Arguments.of("Lorem ipsum dolor sit amet", 5, 10),  // Latin text
                Arguments.of("こんにちは世界", 2, 10)  // Non-Latin text (Japanese "Hello World")
            )
        }
    }

    @Test
    fun `isValidFilterFormat returns true for valid filter formats`() {
        val validFilters = listOf(".java", "java", ".kt", "kt", ".xml", "xml", ".gradle", "gradle")

        for (filter in validFilters) {
            assertTrue(StringUtils.isValidFilterFormat(filter), "Filter '$filter' should be valid")
        }
    }

    @Test
    fun `isValidFilterFormat returns false for invalid filter formats`() {
        val invalidFilters = listOf("", " ", "java.", ".java.", ".java with space", "java/kt", "*.java", ".j*va")

        for (filter in invalidFilters) {
            assertFalse(StringUtils.isValidFilterFormat(filter), "Filter '$filter' should be invalid")
        }
    }

    /**
     * Regression test for the jtokkit library integration.
     * This test verifies that the code can successfully access and use the jtokkit library,
     * which was previously causing a runtime error: "Failed to fold stack trace: com/knuddels/jtokkit/Encodings"
     */
    @Test
    fun `verify jtokkit library is accessible and working correctly`() {
        // Directly access the jtokkit library to ensure it's available at runtime
        val registry = Encodings.newDefaultEncodingRegistry()
        assertNotNull(registry, "Encoding registry should not be null")

        val encoding = registry.getEncoding(EncodingType.CL100K_BASE)
        assertNotNull(encoding, "CL100K_BASE encoding should be available")

        // Test a sample stack trace that would be processed by the StackTraceFolder
        val stackTrace = """
            java.lang.Exception: Test exception
                at com.example.TestClass.testMethod(TestClass.java:42)
                at java.base/java.util.ArrayList.forEach(ArrayList.java:1511)
                at org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor.lambda${'$'}invokeTestMethod${'$'}6(TestMethodTestDescriptor.java:217)
        """.trimIndent()

        // Use the StringUtils method that internally uses jtokkit
        val tokenCount = StringUtils.estimateTokensWithSubwordHeuristic(stackTrace)

        // Verify the result is reasonable (greater than 0)
        assertTrue(tokenCount > 0, "Token count should be greater than 0")

        // Also verify that the direct use of the library gives a similar result
        val directTokenCount = encoding.countTokens(stackTrace)
        assertEquals(directTokenCount, tokenCount, "Direct token count should match the utility method result")
    }
}
