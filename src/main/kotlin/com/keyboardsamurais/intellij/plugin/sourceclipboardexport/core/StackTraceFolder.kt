package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.config.SourceClipboardExportSettings
import java.util.regex.Pattern

/**
 * Folds stack traces by collapsing consecutive frames deemed "external" or "library" code.
 * Leverages IntelliJ's project structure knowledge via PSI, but remains resilient in dumb mode.
 */
class StackTraceFolder(
    private val project: Project,
    private val minFramesToFold: Int = 3, // Minimum consecutive external frames to trigger folding
    private val keepHeadFrames: Int = 0,
    private val keepTailFrames: Int = 0,
    private val includePackageHints: Boolean = false,
    private val treatEllipsisAsFoldable: Boolean = true,
    private val preserveIndentation: Boolean = false
) {

    private val logger = Logger.getInstance(StackTraceFolder::class.java)

    // Regex that handles any source info (e.g., "Unknown Source").
    // Groups: 1=Class FQN, 2=Method, 3=Source Info, 4=Line Number
    private val stackTraceLinePattern: Pattern = Pattern.compile(
        """^\s*at\s+([\w$.<>/]+)\.([\w$<>+]+)\(([^)]+?)(?::(\d+))?\)\s*$"""
    )

    // Regex for lines like "... 5 more"
    private val ellipsisPattern: Pattern = Pattern.compile("""^\s*\.{3}\s*\d+\s+more\s*$""")
    // Regex for "Caused by: ..." lines
    private val causedByPattern: Pattern = Pattern.compile("""^\s*Caused by:\s*.+$""")
    // Regex for "Suppressed: ..." lines
    private val suppressedPattern: Pattern = Pattern.compile("""^\s*Suppressed:\s*.+$""")

    // Settings-backed fold prefix lists (with sane built-in defaults)
    private val settingsState: SourceClipboardExportSettings.StackTraceSettings by lazy {
        try {
            // getInstance may return null at runtime if service is unavailable in tests; catch and fall back
            SourceClipboardExportSettings.getInstance().state.stackTraceSettings
        } catch (t: Throwable) {
            logger.warn("Settings service unavailable; using default folding settings.")
            SourceClipboardExportSettings.StackTraceSettings()
        }
    }

    private val alwaysFoldPrefixes: Set<String> by lazy { settingsState.alwaysFoldPrefixes.toSet() }
    private val neverFoldPrefixes: Set<String> by lazy { settingsState.neverFoldPrefixes.toSet() }

    /**
     * Represents a parsed line from the stack trace.
     * @param line The original text of the line.
     * @param isFoldable True if this line can be part of a folded block.
     * @param indentation Leading whitespace (spaces/tabs) preserved for output alignment.
     * @param className Fully qualified class name if this is a stack frame line, otherwise null.
     */
    private data class StackFrameInfo(
        val line: String,
        val isFoldable: Boolean,
        val indentation: String,
        val className: String?
    )

    /**
     * Parses and folds the given stack trace string. Runs in read action due to PSI access.
     * Returns the original input on any unexpected error.
     *
     * Example:
     * ```
     * val folded = StackTraceFolder(project).foldStackTrace(editor.selection!!)
     * CopyPasteManager.getInstance().setContents(StringSelection(folded))
     * ```
     */
    fun foldStackTrace(stackTrace: String): String {
        return try {
            ApplicationManager.getApplication().runReadAction<String> {
                val lines = stackTrace.lines()
                if (lines.size < minFramesToFold) {
                    return@runReadAction stackTrace
                }

                // Local cache for PSI lookups during this single operation
                val classNameCache = mutableMapOf<String, Boolean>()
                val frameInfoList = lines.mapNotNull { line ->
                    if (line.isBlank()) null else parseLine(line, classNameCache)
                }

                if (frameInfoList.isEmpty()) {
                    return@runReadAction stackTrace
                }

                foldFrames(frameInfoList)
            }
        } catch (t: Throwable) {
            logger.warn("Unexpected error during stack trace folding. Returning original.", t)
            stackTrace
        }
    }

    /** Parses a single line of the stack trace. */
    private fun parseLine(line: String, cache: MutableMap<String, Boolean>): StackFrameInfo {
        val indentation = line.takeWhile { it == ' ' || it == '\t' }
        val trimmedLine = line.trim()

        if (causedByPattern.matcher(trimmedLine).matches()) {
            return StackFrameInfo(line, isFoldable = false, indentation = indentation, className = null)
        }
        if (suppressedPattern.matcher(trimmedLine).matches()) {
            return StackFrameInfo(line, isFoldable = false, indentation = indentation, className = null)
        }

        val stackMatcher = stackTraceLinePattern.matcher(line)
        if (stackMatcher.matches()) {
            val className = stackMatcher.group(1)
            val isFoldable = !isProjectCode(className, cache)
            return StackFrameInfo(line, isFoldable, indentation, className)
        }

        if (ellipsisPattern.matcher(trimmedLine).matches()) {
            // Respect settings: by default do NOT fold ellipsis lines ("... N more")
            return StackFrameInfo(line, isFoldable = treatEllipsisAsFoldable, indentation = indentation, className = null)
        }

        return StackFrameInfo(line, isFoldable = false, indentation = indentation, className = null) // Exception messages, logs, etc.
    }

    /** Performs the actual folding logic on the list of parsed frames. */
    private fun foldFrames(frameInfoList: List<StackFrameInfo>): String {
        val resultLines = mutableListOf<String>()
        var i = 0
        while (i < frameInfoList.size) {
            val currentFrame = frameInfoList[i]

            if (currentFrame.isFoldable) {
                i = foldBlock(frameInfoList, i, resultLines)
            } else {
                addLine(resultLines, currentFrame.line)
                i++
            }
        }
        return resultLines.joinToString("\n")
    }

    private fun foldBlock(frameInfoList: List<StackFrameInfo>, startIndex: Int, resultLines: MutableList<String>): Int {
        var blockEnd = startIndex
        while (blockEnd < frameInfoList.size && frameInfoList[blockEnd].isFoldable) {
            blockEnd++
        }

        val foldCount = blockEnd - startIndex
        if (foldCount < minFramesToFold) {
            for (k in startIndex until blockEnd) {
                addLine(resultLines, frameInfoList[k].line)
            }
            return blockEnd
        }

        val headKeep = keepHeadFrames.coerceIn(0, foldCount)
        val tailKeep = keepTailFrames.coerceIn(0, foldCount - headKeep)

        emitHeadTail(frameInfoList, startIndex, headKeep, resultLines)
        emitPlaceholder(frameInfoList, startIndex, blockEnd, headKeep, tailKeep, resultLines)
        emitHeadTail(frameInfoList, blockEnd - tailKeep, tailKeep, resultLines)

        return blockEnd
    }

    private fun emitHeadTail(
        frameInfoList: List<StackFrameInfo>,
        startIndex: Int,
        count: Int,
        resultLines: MutableList<String>
    ) {
        if (count <= 0) return
        for (k in startIndex until startIndex + count) {
            addLine(resultLines, frameInfoList[k].line)
        }
    }

    private fun emitPlaceholder(
        frameInfoList: List<StackFrameInfo>,
        startIndex: Int,
        blockEnd: Int,
        headKeep: Int,
        tailKeep: Int,
        resultLines: MutableList<String>
    ) {
        val foldedStart = startIndex + headKeep
        val foldedEnd = blockEnd - tailKeep
        val foldedCount = (foldedEnd - foldedStart).coerceAtLeast(0)
        if (foldedCount <= 0) return

        val indent = if (preserveIndentation) frameInfoList[startIndex].indentation else "    "
        val hints = if (includePackageHints) summarizePackages(frameInfoList.subList(foldedStart, foldedEnd)) else null
        val placeholder = if (!hints.isNullOrBlank()) {
            "$indent... $foldedCount folded frames ($hints) ..."
        } else {
            "$indent... $foldedCount folded frames ..."
        }
        addLine(resultLines, placeholder)
    }

    private fun addLine(resultLines: MutableList<String>, line: String) {
        resultLines.add(line)
    }

    /**
     * Checks if a class belongs to the user's project code.
     * Uses cache to avoid repeated PSI lookups. Avoid PSI when in dumb mode.
     */
    private fun isProjectCode(className: String, cache: MutableMap<String, Boolean>): Boolean {
        // Use the cache
        return cache.getOrPut(className) {
            val effectiveClassName = className.substringAfterLast('/')

            if (neverFoldPrefixes.any { effectiveClassName.startsWith(it) }) return@getOrPut true
            if (alwaysFoldPrefixes.any { effectiveClassName.startsWith(it) }) return@getOrPut false

            // In dumb mode, skip PSI. Conservative default: treat as external (foldable)
            val isDumb = try {
                DumbService.isDumb(project)
            } catch (e: Throwable) {
                logger.warn("DumbService.isDumb check failed; assuming smart mode for tests.")
                false
            }
            if (isDumb) return@getOrPut false

            return@getOrPut try {
                val psiFacade = JavaPsiFacade.getInstance(project)
                val scope = GlobalSearchScope.projectScope(project)
                psiFacade.findClass(className, scope) != null
            } catch (e: Exception) {
                logger.warn("Error during PSI check for class '$className'. Assuming library code.", e)
                false // Safe default: treat as external library code
            }
        }
    }

    private fun summarizePackages(frames: List<StackFrameInfo>, maxGroups: Int = 3): String {
        val counts = mutableMapOf<String, Int>()
        for (f in frames) {
            val cn = f.className ?: continue
            val effective = cn.substringAfterLast('/')
            val pkg = effective.substringBeforeLast('.', "")
            if (pkg.isEmpty()) continue
            val top = topLevelPackage(pkg)
            counts[top] = (counts[top] ?: 0) + 1
        }
        if (counts.isEmpty()) return ""
        return counts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(maxGroups)
            .joinToString(", ") { "${it.key}.*(${it.value})" }
    }

    private fun topLevelPackage(pkg: String): String {
        val parts = pkg.split('.')
        return when {
            parts.size >= 2 && (parts[0] == "org" || parts[0] == "com") -> parts.take(2).joinToString(".")
            else -> parts.firstOrNull() ?: pkg
        }
    }
} 
