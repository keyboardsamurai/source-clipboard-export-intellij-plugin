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

    // Regex to parse standard Java stack trace lines. Handles inner classes ($) and lambdas ($$).
    // Groups: 1=Class FQN, 2=Method, 3=File:Line or "Native Method"
    private val stackTraceLinePattern: Pattern = Pattern.compile(
        """^\s*at\s+([\w$.<>/]+)\.([\w$<>+]+)\(((?:[^):]+\.(?:java|kt|scala|groovy)|Native Method))(?::(\d+))?\)\s*$"""
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
        "org.springframework.", "jakarta.", // Added Spring/Jakarta
        "reactor.", "io.reactivex.",
        "io.netty.",
        "org.apache.", "com.google.", "org.slf4j.", "ch.qos.logback.",
        "com.intellij.rt.", "org.gradle.", "org.jetbrains.",
        "worker.org.gradle.process.",
        // Consider adding others based on common patterns if PSI check isn't enough
        "org.hibernate.", // Explicitly mark hibernate as foldable
        "com.zaxxer.hikari.", // Explicitly mark hikari as foldable
        "org.postgresql." // Explicitly mark postgresql driver as foldable
    )

    // Prefixes that should *never* be folded (e.g., user's core domain packages)
    private val neverFoldPrefixes = setOf<String>() // e.g., "com.mycompany.myapp."

    /**
     * Represents a parsed line from the stack trace.
     * @param line The original text of the line.
     * @param className Fully qualified name of the class, if applicable.
     * @param isFoldable True if this line *can* be part of a folded block
     */
    private data class StackFrameInfo(
        val line: String,
        val className: String?,
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

            val frameInfoList = mutableListOf<StackFrameInfo>()
            var isFirstLine = true
            for (line in lines) {
                val trimmedLine = line.trim()
                if (trimmedLine.isEmpty()) continue

                val frame = parseLine(line, isFirstLine)
                frameInfoList.add(frame)
                isFirstLine = false
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
    private fun parseLine(line: String, isFirstLine: Boolean): StackFrameInfo {
        // --- CHANGE 1: Check for Caused by FIRST and mark NOT foldable ---
        if (causedByPattern.matcher(line).find()) {
            // Keep "Caused by:" lines, treat them as essential context separators
            return StackFrameInfo(line, null, false) // NOT foldable
        }

        val stackMatcher = stackTraceLinePattern.matcher(line)
        if (stackMatcher.matches()) {
            val className = stackMatcher.group(1)
            val isProject = isProjectCode(className)
            // Foldable if it's NOT project code
            return StackFrameInfo(line, className, !isProject)
        }

        // Check for "... N more" - these *can* be folded away
        if (ellipsisPattern.matcher(line).matches()) {
            return StackFrameInfo(line, null, true) // Foldable (will be replaced)
        }

        // If it's the first line (exception message) or doesn't match known patterns,
        // keep it and mark it as non-foldable.
        return StackFrameInfo(line, null, false)
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
                var foldCount = 0
                var j = i
                while (j < frameInfoList.size && frameInfoList[j].isFoldable) {
                    // Only count actual 'at ...' lines or '... N more' towards the fold count.
                    // We no longer need to worry about 'Caused by:' here as they are marked isFoldable=false.
                    if (frameInfoList[j].className != null || ellipsisPattern.matcher(frameInfoList[j].line).matches()) {
                        foldCount++
                    }
                    j++
                }

                if (foldCount >= minFramesToFold) {
                    // --- CHANGE 2: Adjust placeholder text ---
                    // Indent placeholder to align with 'at' lines
                    resultLines.add("    ... ${foldCount} folded frames ...")
                    i = j // Advance index past the folded block
                } else {
                    // Not enough consecutive frames to fold, add them individually
                    for (k in i until j) {
                        resultLines.add(frameInfoList[k].line)
                    }
                    i = j
                }
            } else {
                // Not a foldable frame (project code, exception message, Caused by:), just add it
                resultLines.add(currentFrame.line)
                i++
            }
        }
        return resultLines.joinToString("\n")
    }

    /**
     * Checks if a class belongs to the user's project code or is likely library/external code.
     * Requires Read Action.
     */
    private fun isProjectCode(className: String): Boolean {
        // --- Add handling for module prefix before checking prefixes ---
        val effectiveClassName = className.substringAfterLast('/') // Get part after last '/' or full name if no '/'

        if (neverFoldPrefixes.any { effectiveClassName.startsWith(it) }) { // Check effectiveClassName
            logger.trace("Class $className (effective: $effectiveClassName) matches neverFoldPrefixes, considering project code.")
            return true // Not foldable
        }
        if (alwaysFoldPrefixes.any { effectiveClassName.startsWith(it) }) { // Check effectiveClassName
            logger.trace("Class $className (effective: $effectiveClassName) matches alwaysFoldPrefixes, considering library code.")
            return false // IS foldable
        }
        // --- End addition ---

        // Use PSI to determine if the class is part of the project source scope
        // Pass the original className (with potential module prefix) to findClass
        return try {
            val psiFacade = JavaPsiFacade.getInstance(project)
            val scope = GlobalSearchScope.projectScope(project) // Only user's source code
            val foundInProject = psiFacade.findClass(className, scope) != null
            logger.trace("PSI check for $className in project scope: $foundInProject")
            foundInProject // Returns true if found (project code), false otherwise (library code)
        } catch (e: Exception) {
            // Log PSI errors but don't crash; default to treating as non-project code on error.
            logger.warn("Error checking project scope for class $className. Assuming library code.", e)
            false // IS foldable on error
        }
    }
} 