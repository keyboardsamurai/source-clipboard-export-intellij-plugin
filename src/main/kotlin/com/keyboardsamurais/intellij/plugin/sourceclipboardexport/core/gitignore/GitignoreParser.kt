package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core.gitignore

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.FileSystems
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.util.regex.PatternSyntaxException

/**
 * Minimal Git-ignore matcher (covers 95 % of practical cases, keeps spec quirks in mind).
 * NOTE: This implementation checks each path independently. It relies on PathMatcher's

 * for file/directory rules (`pattern`). The overall matching logic relies on the last
 * matching rule found in the .gitignore file.
 */
class GitignoreParser(val gitignoreFile: VirtualFile) {

    private val rules: List<IgnoreRule>

    private companion object {
        val LOG = Logger.getInstance(GitignoreParser::class.java)
        private val FS       = FileSystems.getDefault()
        // Added '{' to metachars, although less common in .gitignore
        private val META_CHR = setOf('*', '?', '[', '{')
    }

    init {
        val gitignorePathForLogging = gitignoreFile.path // Capture for use in lambdas/inner classes
        val lines = try {
            // Ensure UTF-8 encoding is handled if VfsUtilCore doesn't guarantee it
            // val text = VfsUtilCore.loadText(gitignoreFile, StandardCharsets.UTF_8)
            val text = if (com.intellij.openapi.application.ApplicationManager.getApplication()?.isReadAccessAllowed == false) {
                ReadAction.compute<String, Exception> {
                    VfsUtilCore.loadText(gitignoreFile)
                }
            } else {
                // Already in read action or in test environment (including when Application is null)
                VfsUtilCore.loadText(gitignoreFile)
            }
            text.lineSequence()
        } catch (e: Exception) {
            LOG.warn("Cannot read $gitignorePathForLogging", e); emptySequence<String>()
        }

        rules = lines.mapNotNull { l ->
            val t = l.trim()
            when {
                t.isEmpty() -> null
                // Correctly handle escaped # at the start
                t.startsWith("#") && !t.startsWith("\\#") -> null
                else -> runCatching { IgnoreRule(t, gitignorePathForLogging) } // Pass path for logging
                    .onFailure { LOG.warn("Bad rule '$t' in $gitignorePathForLogging", it) }
                    .getOrNull()
            }
        }.toList().asReversed() // <<<< REVERSED HERE <<<<

        if (LOG.isDebugEnabled) LOG.debug("Parsed ${rules.size} rules from $gitignorePathForLogging. Order reversed for matching.")
    }

    /**
     * Checks if a given relative path should be ignored based on the parsed rules.
     * The last matching rule in the .gitignore file determines the outcome.
     *
     * @param relativePath The path relative to the directory containing the .gitignore file.
     *                     Should use '/' as separator. Leading '/' is optional.
     * @param isDir Whether the path represents a directory.
     * @return `true` if the path should be ignored, `false` otherwise.
     */
    fun matches(relativePath: String, isDir: Boolean): Boolean =
        when (matchResult(relativePath, isDir)) {
            MatchResult.MATCH_IGNORE -> true
            // Keep legacy behavior for NO_MATCH and MATCH_NEGATE
            MatchResult.MATCH_NEGATE -> false
            MatchResult.NO_MATCH     -> false
        }

    /**
     * Checks if a given relative path matches any rule and returns the detailed result.
     * The last matching rule (first in the reversed list) determines the outcome.
     *
     * @param relativePath The path relative to the directory containing the .gitignore file.
     *                     Should use '/' as separator. Leading '/' is optional.
     * @param isDir Whether the path represents a directory.
     * @return MatchResult indicating the outcome (MATCH_IGNORE, MATCH_NEGATE, or NO_MATCH).
     */
    fun matchResult(relativePath: String, isDir: Boolean): MatchResult {
        // Normalize: remove leading slash, ensure / separators
        val normalizedPath = relativePath.removePrefix("/").replace('\\', '/')
        if (normalizedPath.isEmpty()) {
            LOG.trace("Path is empty, returning NO_MATCH")
            return MatchResult.NO_MATCH
        }

        if (LOG.isTraceEnabled) {
            LOG.trace("Checking path='$normalizedPath', isDir=$isDir against ${rules.size} rules")
        }


        // Iterate rules in reverse order (last rule first).
        // The first rule that matches determines the outcome.
        for (r in rules) { // Iterating the reversed list
            if (LOG.isTraceEnabled) {
                LOG.trace("Checking rule '${r.original}'")
            }

            val result = r.matches(normalizedPath, isDir)

            if (LOG.isTraceEnabled) {
                LOG.trace("Rule '${r.original}' result: $result")
            }

            if (result != MatchResult.NO_MATCH) {
                // First match found (due to reversed list) determines the fate.
                if (LOG.isTraceEnabled) {
                    LOG.trace("Path='$normalizedPath', isDir=$isDir -> Matched rule '${r.original}' -> Result=$result")
                }
                return result // Return the actual MatchResult immediately
            }
        }

        // No rule matched.
        if (LOG.isTraceEnabled) {
            LOG.trace("Path='$normalizedPath', isDir=$isDir -> No matching rule found -> Result=NO_MATCH")
        }
        return MatchResult.NO_MATCH // Default to NO_MATCH if no rule matches
    }

    /* ── helpers ────────────────────────────────────────────────────────── */

    // Changed from internal to public to be returned by public function
    public enum class MatchResult { NO_MATCH, MATCH_IGNORE, MATCH_NEGATE }

    // Make IgnoreRule an inner class to access gitignoreFilePath easily if needed, or pass it
    internal class IgnoreRule(val original: String, private val gitignoreFilePath: String) {
        val negated: Boolean
        val dirOnly: Boolean
        val rooted:  Boolean
        private val pattern: String // The pattern string after removing !, /, \
        private val simpleStringMatch: Boolean
        private val matcher   : PathMatcher? // Single matcher for glob patterns / dir rules
        private val fileRegex: Regex?       // Fallback for simple filename patterns like *.txt

        init {
            var p = original
            negated = p.startsWith("!").also { if (it) p = p.substring(1) }
            // Handle escaped chars AFTER checking negation
            if (p.startsWith("\\#") || p.startsWith("\\!")) p = p.substring(1)

            // Trailing spaces are ignored unless escaped
            if (p.endsWith(" ") && !p.endsWith("\\ ")) {
                p = p.trimEnd()
            }
            p = p.replace("\\ ", " ")

            dirOnly = p.endsWith("/").also { if (it) p = p.dropLast(1) }
            rooted  = p.startsWith("/").also { if (it) p = p.substring(1) }
            pattern = p // This is the cleaned pattern string

            // A pattern is simple if it contains no glob metachars AND is not dirOnly
            simpleStringMatch = pattern.none { it in META_CHR } && !dirOnly

            val hasSlash = pattern.contains('/')

            // --- Construct the single PathMatcher for glob/dirOnly rules ---
            matcher = if (simpleStringMatch) {
                null // Simple string matches handled directly later
            } else {
                val globPattern = buildString {
                    // 1. Handle rooting/prefixing based on gitignore rules
                    if (!rooted && !pattern.startsWith("**") && !hasSlash) {
                        // Pattern 'foo' or '*.txt' -> glob '**/foo' or '**/*.txt'
                        append("**/")
                    }
                    // Append the core pattern (e.g., 'foo', '*.txt', 'bar/baz', '**/build')
                    append(pattern)

                    // 2. Handle directory matching (contents)
                    // Rule 'dir/' (pattern 'dir', dirOnly=true) -> glob '**/dir/**'
                    // Rule 'foo/bar/' (pattern 'foo/bar', dirOnly=true) -> glob 'foo/bar/**'
                    // Rule '**/build/' (pattern '**/build', dirOnly=true) -> glob '**/build/**'
                    if (dirOnly && !pattern.endsWith("/**")) {
                        append("/**")
                    }
                }
                try {
                    // Ensure syntax=glob is explicit
                    val fullGlob = "glob:$globPattern"
                    LOG.trace("Rule '$original' -> Glob: '$fullGlob'")
                    FS.getPathMatcher(fullGlob)
                } catch (e: PatternSyntaxException) {
                    // Log matcher creation errors as WARN - indicates bad rule/pattern
                    LOG.warn("Invalid glob pattern '$globPattern' generated from rule '$original' in $gitignoreFilePath", e)
                    null
                } catch (e: Exception) {
                    LOG.warn("Error creating PathMatcher for glob '$globPattern' (rule '$original') in $gitignoreFilePath", e)
                    null
                }
            }

            // --- Filename-only regex fallback ---
            // Used for patterns without '/' (like *.log) when the PathMatcher might fail
            // because it wasn't given the '**/' prefix implicitly.
            // Also used as fallback for dirOnly rules (like build/ or **/build/) to match the dir name itself,
            // as PathMatcher(**/pattern/**) seems unreliable for matching just 'pattern'.
            val regexPatternSource = if (dirOnly) {
                // For dirOnly rules, regex should match the directory name itself
                pattern.substringAfterLast('/') // e.g., "build" from "**/build" or "dir" from "dir"
            } else pattern
            fileRegex =
                // Create regex if not simple, and source is not empty, and (either no slash OR dirOnly)
                if (!simpleStringMatch && regexPatternSource.isNotEmpty() && (!hasSlash || dirOnly))
                    globToRegex(regexPatternSource)
                else null

            if (LOG.isTraceEnabled) {
                LOG.trace("Rule '$original' -> pattern='$pattern', simple=$simpleStringMatch, neg=$negated, dirOnly=$dirOnly, rooted=$rooted, matcher=${matcher != null}, fileRegex=${fileRegex != null}")
            }
        }

        /**
         * Checks if this rule matches a given path.
         * @param normalizedPath Path relative to .gitignore location, using '/' separators.
         * @param isDir Whether the path is a directory.
         * @return MatchResult indicating match type or NO_MATCH.
         */
        fun matches(normalizedPath: String, isDir: Boolean): MatchResult {
            val pathObj: Path? = try {
                FS.getPath(normalizedPath)
            } catch (e: InvalidPathException) {
                // On Windows (or other FS), names with '*' or '?' are invalid.
                // Do NOT return; fall back to string/regex matching.
                LOG.debug("Rule '$original': Invalid path syntax: $normalizedPath", e)
                null
            } catch (e: Exception) {
                LOG.debug("Rule '$original': Error creating Path object for: $normalizedPath", e)
                null
            }

            val fileNameOnly = try {
                pathObj?.fileName?.toString()
            } catch (_: Exception) {
                null
            } ?: run {
                val idx = normalizedPath.lastIndexOf('/')
                if (idx >= 0) normalizedPath.substring(idx + 1) else normalizedPath
            }

            var hit = false
            var matchedBy = MatchSource.NONE // Track how the match occurred

            /* ── 1. Simple String Match (no wildcards, not dirOnly) ──────── */
            if (simpleStringMatch) {
                hit = if (rooted || pattern.contains('/')) {
                    normalizedPath == pattern
                } else {
                    fileNameOnly == pattern
                }
                if (hit) matchedBy = MatchSource.SIMPLE
                if (LOG.isTraceEnabled && hit) LOG.trace("Rule '$original': Simple match success for '$normalizedPath'")
            }
            /* ── 2. Glob Matcher / dirOnly=true ───────────────────────────── */
            // Try Glob Matcher first if it exists
            else if (matcher != null) {
                try {
                    // Directory contents quick check (string-only, no Path needed)
                    if (dirOnly && normalizedPath.startsWith("$pattern/")) {
                        hit = true
                        matchedBy = MatchSource.GLOB
                        if (LOG.isTraceEnabled) LOG.trace("Rule '$original': Directory pattern matches path inside directory '$normalizedPath'")
                    }
                    // Special handling for patterns like "doc/**/*.pdf"
                    else if (pattern.contains("/**/*.") && normalizedPath.startsWith(pattern.substringBefore("/**/*."))) {
                        val extension = pattern.substringAfterLast(".")
                        if (normalizedPath.endsWith(".$extension")) {
                            hit = true
                            matchedBy = MatchSource.GLOB
                            if (LOG.isTraceEnabled) LOG.trace("Rule '$original': Complex pattern matches path with extension '$normalizedPath'")
                        }
                    }
                    // Try PathMatcher if we have a valid Path
                    else if (pathObj != null && matcher.matches(pathObj)) {
                        hit = true
                        matchedBy = MatchSource.GLOB
                        if (LOG.isTraceEnabled) LOG.trace("Rule '$original': PathMatcher glob '${matcher}' SUCCESS for '$normalizedPath'")
                    }
                    // Last resort: emulate the glob against the full path with a regex when we don't have a Path
                    else if (pathObj == null) {
                        val hasSlash = pattern.contains('/')
                        val fullGlob = buildString {
                            if (!rooted && !pattern.startsWith("**") && !hasSlash) append("**/")
                            append(pattern)
                            if (dirOnly && !pattern.endsWith("/**")) append("/**")
                        }
                        val fullRegex = try {
                            globToRegex(fullGlob)
                        } catch (e: Exception) {
                            LOG.debug("Rule '$original': Error converting glob '$fullGlob' to regex", e)
                            null
                        }
                        if (fullRegex != null && fullRegex.matches(normalizedPath)) {
                            hit = true
                            matchedBy = MatchSource.GLOB
                            if (LOG.isTraceEnabled) LOG.trace("Rule '$original': Regex fallback glob match SUCCESS for '$normalizedPath'")
                        }
                    } else {
                         if (LOG.isTraceEnabled) LOG.trace("Rule '$original': PathMatcher glob '${matcher}' FAILED for '$normalizedPath'")
                    }
                } catch (e: Exception) {
                    // Log runtime matching errors as DEBUG
                    LOG.debug("Rule '$original': Error during glob matching for path '$normalizedPath'", e)
                    // hit remains false
                }

                /* ── 3. Filename-only regex fallback (if Glob didn't hit) ── */
                // Apply if: Glob didn't hit, regex exists
                if (!hit && fileRegex != null) {
                    try {
                        if (fileRegex.matches(fileNameOnly)) {
                            hit = true
                            matchedBy = MatchSource.REGEX
                            if (LOG.isTraceEnabled) LOG.trace("Rule '$original': fileRegex '$fileRegex' SUCCESS for filename '$fileNameOnly' (path '$normalizedPath')")
                        } else {
                            if (LOG.isTraceEnabled) LOG.trace("Rule '$original': fileRegex '$fileRegex' FAILED for filename '$fileNameOnly' (path '$normalizedPath')")
                        }
                    } catch (e: Exception) {
                        // Log runtime matching errors as DEBUG
                        LOG.debug("Rule '$original': Error during fileRegex.matches for name '$fileNameOnly'", e)
                    }
                }
            }
            // Case where only fileRegex might apply (e.g., simpleStringMatch=false but matcher failed to create?)
            else if (fileRegex != null) {
                try {
                    if (fileRegex.matches(fileNameOnly)) {
                        hit = true
                        matchedBy = MatchSource.REGEX
                        if (LOG.isTraceEnabled) LOG.trace("Rule '$original': fileRegex (no matcher) SUCCESS for filename '$fileNameOnly'")
                    }
                } catch (e: Exception) {
                    LOG.debug("Rule '$original': Error during fileRegex.matches (no matcher) for name '$fileNameOnly'", e)
                }
            }


            /* --- No match found --- */
            if (!hit) return MatchResult.NO_MATCH

            /* --- Apply dirOnly constraints if applicable --- */
            if (dirOnly) {
                // Get the base directory name the rule targets (e.g., "build" from "**/build/")
                val dirNamePattern = pattern.substringAfterLast('/')
                // Check if the path's filename component matches the target directory name
                val filenameMatchesDirName = fileNameOnly == dirNamePattern

                when (matchedBy) {
                    MatchSource.GLOB -> {
                        // If we matched a FILE whose name equals the directory rule, reject it.
                        if (filenameMatchesDirName && !isDir) {
                            if (LOG.isTraceEnabled) LOG.trace("Rule '$original' (dirOnly): Glob matched file '$normalizedPath' with same name as pattern. -> REJECTED (NO_MATCH)")
                            return MatchResult.NO_MATCH
                        }
                    }
                    MatchSource.REGEX -> {
                        // Regex on filename is only valid for dirOnly if target is a directory
                        if (!isDir) {
                            if (LOG.isTraceEnabled) LOG.trace("Rule '$original' (dirOnly): fileRegex matched file '$normalizedPath', but rule requires directory. -> REJECTED (NO_MATCH)")
                            return MatchResult.NO_MATCH
                        }
                    }
                    MatchSource.SIMPLE -> {
                        LOG.warn("Rule '$original' (dirOnly): Unexpected match by SIMPLE source.")
                        return MatchResult.NO_MATCH
                    }
                    MatchSource.NONE -> {
                        LOG.warn("Rule '$original' (dirOnly): Hit=true but matchedBy=NONE.")
                        return MatchResult.NO_MATCH
                    }
                }
            }

            /* --- Determine final result based on negation --- */
            return if (negated) MatchResult.MATCH_NEGATE else MatchResult.MATCH_IGNORE
        }

        private enum class MatchSource { NONE, SIMPLE, GLOB, REGEX }

        // globToRegex remains the same as previous version
        private fun globToRegex(glob: String): Regex {
             val sb = StringBuilder("^")
             var i = 0
             while (i < glob.length) {
                 val c = glob[i]
                 when (c) {
                     '*' -> {
                         if (i + 1 < glob.length && glob[i + 1] == '*') {
                             sb.append(".*")
                             i++
                         } else {
                             sb.append("[^/]*")
                         }
                     }
                     '?'  -> sb.append("[^/]")
                     '\\' -> {
                         // More robust escape handling in regex conversion
                         if (i + 1 < glob.length) {
                             val nextChar = glob[i + 1]
                             // Escape regex metacharacters if they are escaped in the glob
                             if (".()[]{}?*+|^$\\".contains(nextChar)) {
                                 sb.append('\\')
                             }
                             // Always append the escaped character literally
                             sb.append(nextChar)
                             i++
                         } else {
                             // Trailing backslash - treat as literal backslash in regex?
                             sb.append("\\\\")
                         }
                     }
                     // Escape regex metacharacters
                     '.', '(', ')', '+', '|', '^', '$', '{', '}', '[', ']', '@', '%' ->
                         sb.append('\\').append(c)
                     else -> sb.append(c)
                 }
                 i++
             }
             sb.append('$')
             return try {
                  sb.toString().toRegex()
             } catch (e: PatternSyntaxException) {
                 LOG.warn("Failed to compile regex: $sb from glob: $glob", e)
                 Regex("a^") // Return a regex that matches nothing reliably
             }
         }
    }
}
