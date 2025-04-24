package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core.gitignore

import com.intellij.openapi.diagnostic.Logger
// Remove Project import if GitignoreParser class doesn't need it directly
// import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
// Remove VirtualFileManager and Paths imports if not used directly by the class
// import com.intellij.openapi.vfs.VirtualFileManager
import java.nio.file.FileSystems
import java.nio.file.PathMatcher
import java.nio.file.Paths


/**
 * Parses a single .gitignore file's content.
 * Handles comments, blank lines, negation (!), directory patterns (/),
 * root anchoring (/), and basic wildcards (*, ?).
 * Uses NIO PathMatcher for glob matching relative to the location of the .gitignore file.
 */
class GitignoreParser(val gitignoreFile: VirtualFile) { // Takes the VirtualFile

    private val rules: List<IgnoreRule>
    // Store the directory of the gitignore file for relative path calculations if needed by rules
    private val gitignoreDir: VirtualFile = gitignoreFile.parent
        ?: throw IllegalArgumentException(".gitignore file must have a parent directory: ${gitignoreFile.path}")

    companion object {
        // Logger can be static or instance-level
        internal val LOG = Logger.getInstance(GitignoreParser::class.java)
        private val fileSystem = FileSystems.getDefault()
    }

    init {
        val lines = try {
            // Read content directly from the passed VirtualFile
            VfsUtilCore.loadText(gitignoreFile).lines()
        } catch (e: Exception) {
            LOG.warn("Could not read .gitignore file content: ${gitignoreFile.path}", e)
            emptyList()
        }
        rules = lines.mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || (trimmed.startsWith("#") && !trimmed.startsWith("\\#"))) {
                null
            } else {
                try {
                    // Pass gitignoreDir path if needed by Rule, but PathMatcher handles relativity
                    IgnoreRule(trimmed)
                } catch (e: Exception) {
                    LOG.warn("Failed to create IgnoreRule for pattern '$trimmed' in ${gitignoreFile.path}", e)
                    null
                }
            }
        }.toList()
        LOG.debug("Parsed ${rules.size} rules from ${gitignoreFile.path}")
    }

    /**
     * Checks if a given path matches the rules in this specific .gitignore file.
     * Assumes the relativePath is relative to the directory containing this .gitignore file.
     *
     * @param relativePath Path relative to the .gitignore file's directory (using '/').
     * @param isDirectory Whether the path represents a directory.
     * @return True if the path should be ignored according to these rules, false otherwise.
     */
    fun matches(relativePath: String, isDirectory: Boolean): Boolean {
        // Normalize path: remove leading slash if present, as patterns are matched relative
        val normalizedPath = relativePath.removePrefix("/")
        if (normalizedPath.isEmpty()) {
            return false // Cannot ignore the directory containing the .gitignore itself via its own rules
        }

        var isIgnored = false // Default: not ignored
        var lastMatchNegated = false // Track if the last match was a negation

        for (rule in rules) {
            val matchResult = rule.matches(normalizedPath, isDirectory)
            if (matchResult != MatchResult.NO_MATCH) {
                // Last matching rule in the file wins
                isIgnored = (matchResult == MatchResult.MATCH_IGNORE)
                lastMatchNegated = (matchResult == MatchResult.MATCH_NEGATE)
                LOG.trace("Path '$normalizedPath' (isDir=$isDirectory) matched rule '${rule.originalPattern}' in ${gitignoreFile.name}. Intermediate ignore status: $isIgnored")
            }
        }

        LOG.trace("Final decision for '$normalizedPath' based on ${gitignoreFile.name}: Ignored = $isIgnored")
        return isIgnored
    }

    // Enum to represent the outcome of matching a path against a rule.
    // Defined inside GitignoreParser or outside, depending on preference.
    // Needs to be accessible by IgnoreRule.
    internal enum class MatchResult {
        NO_MATCH,     // Rule does not apply to this path
        MATCH_IGNORE, // Rule matches and indicates the path should be ignored
        MATCH_NEGATE  // Rule matches and indicates the path should NOT be ignored (overrides previous ignores)
    }


    // Internal representation of a single .gitignore pattern rule.
    // Removed gitignoreDirPath from constructor as PathMatcher handles relativity implicitly
    internal class IgnoreRule(val originalPattern: String) {
        val isNegated: Boolean
        val pattern: String // The pattern string after processing negation, slashes, etc.
        val matchOnlyDir: Boolean // Pattern ends with /
        val isRooted: Boolean // Pattern starts with /
        private val pathMatcher: PathMatcher? // Use NIO PathMatcher for glob handling

        init {
            var currentPattern = originalPattern

            // 1. Handle negation
            if (currentPattern.startsWith("!")) {
                isNegated = true
                currentPattern = currentPattern.substring(1)
            } else {
                isNegated = false
            }
            // Handle escaped chars
            if (currentPattern.startsWith("\\#")) currentPattern = currentPattern.substring(1)
            if (currentPattern.startsWith("\\!")) currentPattern = currentPattern.substring(1)

            // 2. Handle directory matching and trailing spaces (basic trim)
            currentPattern = currentPattern.trimEnd { it <= ' ' && it != '\\' } // Basic trim end
             if (currentPattern.endsWith("\\ ")) { // Handle escaped space
                 currentPattern = currentPattern.dropLast(2) + " "
             }

            if (currentPattern.endsWith("/")) {
                matchOnlyDir = true
                currentPattern = currentPattern.dropLast(1)
            } else {
                matchOnlyDir = false
            }

            // 3. Handle rooted paths
            if (currentPattern.startsWith("/")) {
                isRooted = true
                currentPattern = currentPattern.substring(1)
            } else {
                isRooted = false
            }

            this.pattern = currentPattern

            // 4. Prepare the glob pattern for PathMatcher
            var glob = pattern
            if (glob.isEmpty()) {
                 pathMatcher = null
            } else {
                // If a pattern doesn't contain any slashes and isn't rooted,
                // it matches anywhere in the directory tree below the .gitignore file.
                // PathMatcher needs `**/` for this.
                if (!isRooted && !pattern.contains('/')) {
                    glob = "**/" + glob
                }
                // If rooted or contains slash, match relative to .gitignore dir (PathMatcher default)

                this.pathMatcher = try {
                    LOG.trace("Creating PathMatcher for glob: 'glob:$glob' from original '$originalPattern'")
                    fileSystem.getPathMatcher("glob:$glob")
                } catch (e: Exception) {
                     LOG.warn("Failed to create PathMatcher for glob 'glob:$glob' from pattern '$originalPattern'. Rule will be ignored.", e)
                     null
                }
            }
            LOG.trace("Rule created: original='$originalPattern', pattern='$pattern', glob='${pathMatcher?.toString() ?: "INVALID"}', negated=$isNegated, rooted=$isRooted, onlyDir=$matchOnlyDir")
        }

        /**
         * Checks if this rule matches the given relative path.
         *
         * @param relativePath Path relative to the .gitignore file's directory, using '/' separators.
         *                     MUST NOT have a leading slash.
         * @param isDirectory Whether the path represents a directory.
         * @return MatchResult indicating the outcome.
         */
        fun matches(relativePath: String, isDirectory: Boolean): MatchResult {
            if (pathMatcher == null || pattern.isEmpty()) {
                return MatchResult.NO_MATCH
            }
            // Rule applies only to directories, but path is a file.
            // Important: This check was moved *after* pathMatcher check in the previous version.
            // Let's keep it here for now based on the provided code, but verify if the order matters.
            // if (matchOnlyDir && !isDirectory) {
            //     return MatchResult.NO_MATCH
            // }

            if (relativePath.isEmpty()) {
                 return MatchResult.NO_MATCH
            }

            val pathToCheck = try {
                fileSystem.getPath(relativePath)
            } catch (e: java.nio.file.InvalidPathException) {
                LOG.warn("Could not create Path object for relative path: '$relativePath'. Skipping match for rule '$originalPattern'.")
                return MatchResult.NO_MATCH
            }

            val matched = try {
                 pathMatcher.matches(pathToCheck)
            } catch (e: Exception) {
                 LOG.warn("Error executing PathMatcher for glob '${pathMatcher}' on path '$relativePath'. Skipping match for rule '$originalPattern'.", e)
                 return MatchResult.NO_MATCH
            }

            // --- Start of Corrected Logic --- (Applying the logic discussed)
            if (!matched) {
                // If the PathMatcher doesn't match, the rule definitely doesn't apply
                return MatchResult.NO_MATCH
            }

            // If the PathMatcher *did* match, we now need to apply the directory-only constraint.
            // This is subtle: if the pattern is `foo/` (matchOnlyDir=true) it should ignore
            // `foo/bar.txt`. PathMatcher handles this correctly if the glob is `foo/**` or similar.
            // The main case `matchOnlyDir` affects is when the pattern *exactly* matches the path name.
            if (matchOnlyDir && !isDirectory && relativePath == this.pattern) {
                // Rule requires a directory (e.g., "build/").
                // Path matches the pattern string exactly (e.g., "build").
                // But the path is NOT a directory.
                // Therefore, this rule doesn't ignore this specific file entry.
                LOG.trace("Rule '$originalPattern' requires directory, path '$relativePath' is a file matching pattern name. NO_MATCH for this file.")
                return MatchResult.NO_MATCH
            }

            // If we reach here, the path matched the pattern and satisfied directory constraints (if any).
            LOG.trace("Rule '$originalPattern' (glob='${pathMatcher}') MATCHED path '$relativePath' (isDirectory=$isDirectory). Negated=$isNegated")
            return if (isNegated) MatchResult.MATCH_NEGATE else MatchResult.MATCH_IGNORE
            // --- End of Corrected Logic ---
        }
    }
} 