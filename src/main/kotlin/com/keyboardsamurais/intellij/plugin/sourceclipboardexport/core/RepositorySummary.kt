package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.AppConstants
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.StringUtils
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * Class for generating repository summary information.
 */
class RepositorySummary(
    private val project: Project,
    private val selectedFiles: Array<VirtualFile>,
    private val fileContents: List<String>,
    private val processedFileCount: Int,
    private val excludedByFilterCount: Int,
    private val excludedBySizeCount: Int,
    private val excludedByBinaryContentCount: Int,
    private val excludedByIgnoredNameCount: Int,
    private val excludedByGitignoreCount: Int
) {
    /**
     * Data class to hold file statistics.
     */
    data class FileStats(
        val path: String,
        val charCount: Int,
        val tokenCount: Int,
        val percentageOfTotal: Double
    )

    /**
     * Generates a repository summary in the specified format.
     */
    fun generateSummary(outputFormat: AppConstants.OutputFormat): String {
        val fileStats = collectFileStats()
        val totalChars = fileStats.sumOf { it.charCount }
        val totalTokens = fileStats.sumOf { it.tokenCount }
        val top10Files = fileStats.sortedByDescending { it.charCount }.take(10)

        return when (outputFormat) {
            AppConstants.OutputFormat.PLAIN_TEXT -> formatPlainText(totalChars, totalTokens, top10Files)
            AppConstants.OutputFormat.MARKDOWN -> formatMarkdown(totalChars, totalTokens, top10Files)
            AppConstants.OutputFormat.XML -> formatXml(totalChars, totalTokens, top10Files)
        }
    }

    /**
     * Collects statistics for each file.
     */
    private fun collectFileStats(): List<FileStats> {
        val result = mutableListOf<FileStats>()

        for (content in fileContents) {
            if (content.startsWith(AppConstants.FILENAME_PREFIX)) {
                // Extract the path from the first line
                val firstLine = content.substringBefore('\n')
                val path = firstLine.substring(AppConstants.FILENAME_PREFIX.length).trim()

                // Extract the file content (everything after the first line)
                val fileContent = content.substringAfter('\n')

                // Calculate statistics
                val charCount = fileContent.length
                val tokenCount = estimateTokenCount(fileContent)

                result.add(FileStats(path, charCount, tokenCount, 0.0)) // Percentage will be calculated later
            }
        }

        // Calculate percentages
        val totalChars = result.sumOf { it.charCount }.toDouble()
        if (totalChars > 0) {
            result.forEach { stats ->
                val percentage = (stats.charCount / totalChars) * 100.0
                result[result.indexOf(stats)] = stats.copy(percentageOfTotal = percentage)
            }
        }

        return result
    }

    /**
     * Estimates the number of tokens in a string.
     * This is a simple estimation based on whitespace and punctuation.
     */
    private fun estimateTokenCount(text: String): Int {
        // Split by whitespace and punctuation
        val tokens = text.split(Regex("[\\s\\p{Punct}]+"))
        return tokens.count { it.isNotEmpty() }
    }

    /**
     * Formats the repository summary as plain text.
     */
    private fun formatPlainText(totalChars: Int, totalTokens: Int, top10Files: List<FileStats>): String {
        val sb = StringBuilder()

        sb.appendLine("**Repository Info**")
        sb.appendLine()
        sb.appendLine("Repository")
        sb.appendLine("    ${getRepositoryUrl()}")
        sb.appendLine("Generated At")
        sb.appendLine("    ${getCurrentDateTime()}")
        sb.appendLine("Format")
        sb.appendLine("    plain text")
        sb.appendLine()
        sb.appendLine("Pack Summary")
        sb.appendLine()
        sb.appendLine("Total Files")
        sb.appendLine("    $processedFileCount files")
        sb.appendLine("Total Size")
        sb.appendLine("    $totalChars chars")
        sb.appendLine("Total Tokens")
        sb.appendLine("    $totalTokens tokens")
        sb.appendLine()
        sb.appendLine("Top 10 Files")
        sb.appendLine()

        top10Files.forEach { stats ->
            sb.appendLine("    ${stats.path}")
            sb.appendLine("    ${stats.charCount} chars | ${stats.tokenCount} tokens | ${String.format("%.1f", stats.percentageOfTotal)}%")
        }

        sb.appendLine()
        return sb.toString()
    }

    /**
     * Formats the repository summary as Markdown.
     */
    private fun formatMarkdown(totalChars: Int, totalTokens: Int, top10Files: List<FileStats>): String {
        val sb = StringBuilder()

        sb.appendLine("# Repository Info")
        sb.appendLine()
        sb.appendLine("## Repository")
        sb.appendLine("${getRepositoryUrl()}")
        sb.appendLine("## Generated At")
        sb.appendLine("${getCurrentDateTime()}")
        sb.appendLine("## Format")
        sb.appendLine("markdown")
        sb.appendLine()
        sb.appendLine("# Pack Summary")
        sb.appendLine()
        sb.appendLine("## Total Files")
        sb.appendLine("$processedFileCount files")
        sb.appendLine("## Total Size")
        sb.appendLine("$totalChars chars")
        sb.appendLine("## Total Tokens")
        sb.appendLine("$totalTokens tokens")
        sb.appendLine()
        sb.appendLine("## Top 10 Files")
        sb.appendLine()

        top10Files.forEach { stats ->
            sb.appendLine("### ${stats.path}")
            sb.appendLine("${stats.charCount} chars | ${stats.tokenCount} tokens | ${String.format("%.1f", stats.percentageOfTotal)}%")
            sb.appendLine()
        }

        return sb.toString()
    }

    /**
     * Formats the repository summary as XML.
     */
    private fun formatXml(totalChars: Int, totalTokens: Int, top10Files: List<FileStats>): String {
        val sb = StringBuilder()

        sb.appendLine("<repository-summary>")
        sb.appendLine("  <repository-info>")
        sb.appendLine("    <repository-url>${StringUtils.escapeXml(getRepositoryUrl())}</repository-url>")
        sb.appendLine("    <generated-at>${StringUtils.escapeXml(getCurrentDateTime())}</generated-at>")
        sb.appendLine("    <format>xml</format>")
        sb.appendLine("  </repository-info>")
        sb.appendLine("  <pack-summary>")
        sb.appendLine("    <total-files>$processedFileCount</total-files>")
        sb.appendLine("    <total-chars>$totalChars</total-chars>")
        sb.appendLine("    <total-tokens>$totalTokens</total-tokens>")
        sb.appendLine("    <top-files>")

        top10Files.forEach { stats ->
            sb.appendLine("      <file>")
            sb.appendLine("        <path>${StringUtils.escapeXml(stats.path)}</path>")
            sb.appendLine("        <chars>${stats.charCount}</chars>")
            sb.appendLine("        <tokens>${stats.tokenCount}</tokens>")
            sb.appendLine("        <percentage>${String.format("%.1f", stats.percentageOfTotal)}</percentage>")
            sb.appendLine("      </file>")
        }

        sb.appendLine("    </top-files>")
        sb.appendLine("  </pack-summary>")
        sb.appendLine("</repository-summary>")

        return sb.toString()
    }

    /**
     * Gets the repository URL.
     */
    private fun getRepositoryUrl(): String {
        // In a real implementation, this would get the actual repository URL
        // For now, we'll use a placeholder
        return "https://github.com/keyboardsamurai/source-clipboard-export-intellij-plugin"
    }

    /**
     * Gets the current date and time formatted as a string.
     */
    private fun getCurrentDateTime(): String {
        val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm:ss")
        return LocalDateTime.now().format(formatter)
    }

}
