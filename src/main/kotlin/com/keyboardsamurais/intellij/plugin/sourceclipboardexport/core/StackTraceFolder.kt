package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import java.util.regex.Pattern

/**
 * Folds stack traces by collapsing consecutive frames deemed "external" or "library" code.
 * Leverages IntelliJ's project structure knowledge via PSI.
 */
class StackTraceFolder(
    private val project: Project,
    private val minFramesToFold: Int = 3 // Minimum consecutive external frames to trigger folding
) {

    private val logger = Logger.getInstance(StackTraceFolder::class.java)

    // Regex that handles any source info (e.g., "Unknown Source").
    // Groups: 1=Class FQN, 2=Method, 3=Source Info (e.g., "MyClass.java:123" or "Unknown Source"), 4=Line Number
    private val stackTraceLinePattern: Pattern = Pattern.compile(
        """^\s*at\s+([\w$.<>/]+)\.([\w$<>+]+)\(([^)]+?)(?::(\d+))?\)\s*$"""
    )

    // Regex for lines like "... 5 more"
    private val ellipsisPattern: Pattern = Pattern.compile("""^\s*\.{3}\s*\d+\s+more\s*$""")
    // Regex for "Caused by: ..." lines
    private val causedByPattern: Pattern = Pattern.compile("""^\s*Caused by:""")

    // Prefixes always considered external/library code (even if sources are available)
    private val alwaysFoldPrefixes = setOf(
        "java.", "javax.", "kotlin.", "kotlinx.", "scala.",
        "jdk.", "sun.", "com.sun.",
        "org.junit.", "junit.", "org.testng.",
        "org.mockito.", "net.bytebuddy.",
        "jakarta.",
        "reactor.", "io.reactivex.",
        "io.netty.",
        "org.apache.", "com.google.", "org.slf4j.", "ch.qos.logback.",
        "com.intellij.rt.", "org.gradle.", "org.jetbrains.",
        "worker.org.gradle.process.",
        "org.hibernate.",
        "com.zaxxer.hikari.",
        "org.postgresql."
    )

    // Prefixes that should *never* be folded (e.g., user's core domain packages)
    private val neverFoldPrefixes = setOf(
        "org.springframework.test.context.",
        "com.mycompany.myapp."
    )

    /**
     * Represents a parsed line from the stack trace.
     * @param line The original text of the line.
     * @param isFoldable True if this line *can* be part of a folded block
     */
    private data class StackFrameInfo(
        val line: String,
        val isFoldable: Boolean
    )

    /**
     * Parses and folds the given stack trace string.
     * Requires Read Action because it accesses PSI.
     *
     * @param stackTrace The raw stack trace text.
     * @return The folded stack trace text.
     */
    fun foldStackTrace(stackTrace: String): String {
        return ApplicationManager.getApplication().runReadAction<String> {
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
    }

    /**
     * Parses a single line of the stack trace.
     */
    private fun parseLine(line: String, cache: MutableMap<String, Boolean>): StackFrameInfo {
        val trimmedLine = line.trim()

        if (causedByPattern.matcher(trimmedLine).find()) {
            return StackFrameInfo(line, isFoldable = false)
        }

        val stackMatcher = stackTraceLinePattern.matcher(line)
        if (stackMatcher.matches()) {
            val className = stackMatcher.group(1)
            val isFoldable = !isProjectCode(className, cache)
            return StackFrameInfo(line, isFoldable)
        }

        if (ellipsisPattern.matcher(trimmedLine).matches()) {
            return StackFrameInfo(line, isFoldable = true)
        }

        return StackFrameInfo(line, isFoldable = false) // Exception messages, etc.
    }

    /**
     * Performs the actual folding logic on the list of parsed frames.
     */
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
                    // It's long enough, fold it
                    // Use exactly 4 spaces for the folded frames placeholder (matches expected test format)
                    resultLines.add("    ... $foldCount folded frames ...")
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
     * Uses the provided cache to avoid expensive repeated lookups.
     */
    private fun isProjectCode(className: String, cache: MutableMap<String, Boolean>): Boolean {
        // Use the cache
        return cache.getOrPut(className) {
            // Logic to compute the value if it's not in the cache
            val effectiveClassName = className.substringAfterLast('/')

            if (neverFoldPrefixes.any { effectiveClassName.startsWith(it) }) return@getOrPut true
            if (alwaysFoldPrefixes.any { effectiveClassName.startsWith(it) }) return@getOrPut false

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
} 
