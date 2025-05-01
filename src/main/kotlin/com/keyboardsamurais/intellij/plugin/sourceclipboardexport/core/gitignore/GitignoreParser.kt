package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core.gitignore

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.*
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
            val text = VfsUtilCore.loadText(gitignoreFile)
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
    fun matches(relativePath: String, isDir: Boolean): Boolean {
        // Normalize: remove leading slash, ensure / separators
        val normalizedPath = relativePath.removePrefix("/").replace('\\', '/')
        if (normalizedPath.isEmpty()) {
            LOG.trace("Path is empty, returning false")
            return false
        }

        // Iterate rules in reverse order (last rule first). <<<< LOGIC CHANGED HERE <<<<
        // The first rule that matches determines the outcome.
        for (r in rules) { // Iterating the reversed list
            val result = r.matches(normalizedPath, isDir)
            if (result != MatchResult.NO_MATCH) {
                // First match found (due to reversed list) determines the fate.
                val decision = result == MatchResult.MATCH_IGNORE
                if (LOG.isTraceEnabled) {
                    LOG.trace("Path='$normalizedPath', isDir=$isDir -> Matched rule '${r.original}' -> Result=$result -> Decision=$decision")
                }
                return decision // Return immediately on first match
            }
        }

        // No rule matched.
        if (LOG.isTraceEnabled) {
            LOG.trace("Path='$normalizedPath', isDir=$isDir -> No matching rule found -> Decision=false")
        }
        return false // Default to not ignored if no rule matches
    }

    /* ── helpers ────────────────────────────────────────────────────────── */

    internal enum class MatchResult { NO_MATCH, MATCH_IGNORE, MATCH_NEGATE }

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
                    if (dirOnly) {
                        // Append /** to match contents, unless the pattern already ends with **
                        if (!pattern.endsWith("/**")) {
                             append("/**")
                        }
                    }
                    // If not dirOnly but pattern doesn't end with a wildcard, append /** to match files inside
                    else if (!pattern.endsWith("*") && !pattern.endsWith("**")) {
                        append("/**")
                    }
                }
                try {
                    // Ensure syntax=glob is explicit
                    val fullGlob = "glob:$globPattern"
                    LOG.trace("Rule '$original' -> Glob: '$fullGlob'")
                    FS.getPathMatcher(fullGlob)
                } catch (e: PatternSyntaxException) {
                    LOG.warn("Invalid glob pattern '$globPattern' generated from rule '$original' in $gitignoreFilePath", e)
                    null // Handle invalid patterns gracefully
                } catch (e: Exception) { // Catch other potential exceptions
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
            val pathObj : Path = try {
                 FS.getPath(normalizedPath)
            } catch (e: InvalidPathException) {
                LOG.debug("Rule '$original': Invalid path syntax: $normalizedPath", e)
                return MatchResult.NO_MATCH
            } catch (e: Exception) {
                 LOG.warn("Rule '$original': Error creating Path object for: $normalizedPath", e)
                 return MatchResult.NO_MATCH
            }

            var hit = false
            var matchedBy = MatchSource.NONE // Track how the match occurred

            /* ── 1. Simple String Match (no wildcards, not dirOnly) ──────── */
            if (simpleStringMatch) {
                if (rooted || pattern.contains('/')) {
                    hit = (normalizedPath == pattern)
                } else {
                    // Use pathObj.fileName which handles separators correctly.
                    // Check if pathObj has a filename (it might be just "foo")
                    hit = (pathObj.fileName?.toString() == pattern)
                }
                if (hit) matchedBy = MatchSource.SIMPLE
                if (LOG.isTraceEnabled && hit) LOG.trace("Rule '$original': Simple match success for '$normalizedPath'")
            }
            /* ── 2. Glob Matcher / dirOnly=true ───────────────────────────── */
            // Try Glob Matcher first if it exists
            else if (matcher != null) {
                try {
                    // For directory patterns, also check if the path is inside the directory
                    if (dirOnly && normalizedPath.startsWith(pattern + "/")) {
                        hit = true
                        matchedBy = MatchSource.GLOB
                        if (LOG.isTraceEnabled) LOG.trace("Rule '$original': Directory pattern matches path inside directory '$normalizedPath'")
                    }
                    // For complex patterns with directory and extension, special handling
                    else if (pattern.contains("/**/*.") && normalizedPath.startsWith(pattern.substringBefore("/**/*."))) {
                        val extension = pattern.substringAfterLast(".")
                        if (normalizedPath.endsWith(".$extension")) {
                            hit = true
                            matchedBy = MatchSource.GLOB
                            if (LOG.isTraceEnabled) LOG.trace("Rule '$original': Complex pattern matches path with extension '$normalizedPath'")
                        }
                    }
                    // Standard glob matching
                    else if (matcher.matches(pathObj)) {
                        hit = true
                        matchedBy = MatchSource.GLOB
                        if (LOG.isTraceEnabled) LOG.trace("Rule '$original': PathMatcher glob '${matcher}' SUCCESS for '$normalizedPath'")
                    } else {
                         if (LOG.isTraceEnabled) LOG.trace("Rule '$original': PathMatcher glob '${matcher}' FAILED for '$normalizedPath'")
                    }
                } catch (e: Exception) {
                    LOG.warn("Rule '$original': Error during PathMatcher.matches for path '$normalizedPath'", e)
                    // hit remains false
                }

                /* ── 3. Filename-only regex fallback (if Glob didn't hit) ── */
                // Apply if: Glob didn't hit, regex exists
                if (!hit && fileRegex != null) {
                    val name = pathObj.fileName?.toString()
                    if (!name.isNullOrEmpty()) {
                         try {
                            val regexHit = fileRegex.matches(name)
                            if (regexHit) {
                                hit = true
                                matchedBy = MatchSource.REGEX
                                if (LOG.isTraceEnabled) LOG.trace("Rule '$original': fileRegex '$fileRegex' SUCCESS for filename '$name' (path '$normalizedPath')")
                            } else {
                                if (LOG.isTraceEnabled) LOG.trace("Rule '$original': fileRegex '$fileRegex' FAILED for filename '$name' (path '$normalizedPath')")
                            }
                         } catch (e: Exception) {
                             LOG.warn("Rule '$original': Error during fileRegex.matches for name '$name'", e)
                         }
                    } else {
                         if (LOG.isTraceEnabled) LOG.trace("Rule '$original': fileRegex fallback skipped for path '$normalizedPath' (no filename)")
                    }
                }
            }
            // Case where only fileRegex might apply (e.g., simpleStringMatch=false but matcher failed to create?)
            else if (fileRegex != null) {
                 val name = pathObj.fileName?.toString()
                 if (!name.isNullOrEmpty()) {
                     try {
                         if (fileRegex.matches(name)) {
                             hit = true
                             matchedBy = MatchSource.REGEX
                             if (LOG.isTraceEnabled) LOG.trace("Rule '$original': fileRegex (no matcher) SUCCESS for filename '$name'")
                         }
                     } catch (e: Exception) {
                          LOG.warn("Rule '$original': Error during fileRegex.matches (no matcher) for name '$name'", e)
                     }
                 }
            }


            /* --- No match found --- */
            if (!hit) return MatchResult.NO_MATCH

            /* --- Apply dirOnly constraints if applicable --- */
            if (dirOnly) {
                // Get the base directory name the rule targets (e.g., "build" from "**/build/")
                val dirNamePattern = pattern.substringAfterLast('/')
                // Check if the path's filename component matches the target directory name
                val filenameMatchesDirName = pathObj.fileName?.toString() == dirNamePattern

                when (matchedBy) {
                    MatchSource.GLOB -> {
                        // Glob `**/pattern/**` matched.
                        // If it matched a FILE whose name is exactly the target directory name, reject it.
                        // Example: Rule `dir/`, Path `dir` (isDir=false). Glob matches, but this check rejects.
                        if (filenameMatchesDirName && !isDir) {
                             if (LOG.isTraceEnabled) LOG.trace("Rule '$original' (dirOnly): Glob matched file '$normalizedPath' with same name as pattern base. -> REJECTED (NO_MATCH)")
                             return MatchResult.NO_MATCH
                        }
                        // Example: Rule `dir/`, Path `dir` (isDir=true). Glob matches, check passes. -> OK
                        // Example: Rule `dir/`, Path `dir/file.txt`. Glob matches, filename != dirName, check passes. -> OK
                        if (LOG.isTraceEnabled) LOG.trace("Rule '$original' (dirOnly): Glob match is valid.")
                    }
                    MatchSource.REGEX -> {
                        // Regex matched the filename (e.g., rule `build/`, path `build`).
                        // This is only valid for dirOnly if the path IS a directory.
                        if (!isDir) {
                            if (LOG.isTraceEnabled) LOG.trace("Rule '$original' (dirOnly): fileRegex matched file '$normalizedPath', but rule requires directory. -> REJECTED (NO_MATCH)")
                            return MatchResult.NO_MATCH
                        }
                        // Example: Rule `build/`, Path `build` (isDir=true). Regex matches, isDir=true, check passes -> OK
                        if (LOG.isTraceEnabled) LOG.trace("Rule '$original' (dirOnly): fileRegex match on directory name is valid.")
                    }
                    MatchSource.SIMPLE -> {
                        // Simple match shouldn't happen if dirOnly=true due to init logic
                        LOG.warn("Rule '$original' (dirOnly): Unexpected match by SIMPLE source.")
                        return MatchResult.NO_MATCH // Treat as error / no match
                    }
                    MatchSource.NONE -> {
                        // Should not happen if hit=true
                        LOG.warn("Rule '$original' (dirOnly): Hit=true but matchedBy=NONE.")
                        return MatchResult.NO_MATCH // Treat as error / no match
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
