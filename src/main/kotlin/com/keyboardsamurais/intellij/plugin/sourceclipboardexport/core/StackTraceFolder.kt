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
    private val settingsState: SourceClipboardExportSettings.State by lazy {
        try {
            // getInstance may return null at runtime if service is unavailable in tests; catch and fall back
            SourceClipboardExportSettings.getInstance().state
        } catch (t: Throwable) {
            logger.warn("Settings service unavailable; using default folding settings.")
            SourceClipboardExportSettings.State()
        }
    }

    private val alwaysFoldPrefixes: Set<String> by lazy { settingsState.stackTraceAlwaysFoldPrefixes.toSet() }
    private val neverFoldPrefixes: Set<String> by lazy { settingsState.stackTraceNeverFoldPrefixes.toSet() }

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
                // Start of a foldable block, find its end
                var j = i
                while (j < frameInfoList.size && frameInfoList[j].isFoldable) {
                    j++
                }

                val foldCount = j - i
                if (foldCount >= minFramesToFold) {
                    // Keep head/tail context frames within the folded block
                    val headKeep = keepHeadFrames.coerceAtLeast(0).coerceAtMost(foldCount)
                    val tailKeep = keepTailFrames.coerceAtLeast(0).coerceAtMost(foldCount - headKeep)

                    // Emit head frames
                    if (headKeep > 0) {
                        for (k in i until (i + headKeep)) {
                            resultLines.add(frameInfoList[k].line)
                        }
                    }

                    // Summarize packages for the actually folded middle section
                    val foldedStart = i + headKeep
                    val foldedEnd = j - tailKeep
                    val foldedCount = (foldedEnd - foldedStart).coerceAtLeast(0)

                    if (foldedCount > 0) {
                        val indent = if (preserveIndentation) frameInfoList[i].indentation else "    "
                        val hints = if (includePackageHints) summarizePackages(frameInfoList.subList(foldedStart, foldedEnd)) else null
                        val placeholder = if (!hints.isNullOrBlank()) {
                            "$indent... $foldedCount folded frames ($hints) ..."
                        } else {
                            "$indent... $foldedCount folded frames ..."
                        }
                        resultLines.add(placeholder)
                    }

                    // Emit tail frames
                    if (tailKeep > 0) {
                        for (k in (j - tailKeep) until j) {
                            resultLines.add(frameInfoList[k].line)
                        }
                    }
                    i = j // Skip past the folded block
                } else {
                    // Not long enough, add frames individually
                    for (k in i until j) {
                        resultLines.add(frameInfoList[k].line)
                    }
                    i = j
                }
            } else {
                // Not a foldable frame, just add it
                resultLines.add(currentFrame.line)
                i++
            }
        }
        return resultLines.joinToString("\n")
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
