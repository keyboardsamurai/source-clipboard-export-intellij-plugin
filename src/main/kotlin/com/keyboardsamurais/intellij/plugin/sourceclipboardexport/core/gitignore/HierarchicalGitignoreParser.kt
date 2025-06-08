package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core.gitignore

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileEvent
import com.intellij.openapi.vfs.VirtualFileListener
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.VirtualFileMoveEvent
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.FileUtils
import java.util.concurrent.ConcurrentHashMap

/**
 * Implements hierarchical .gitignore parsing by finding and applying rules from
 * all .gitignore files in the path hierarchy from a file up to the project root.
 * 
 * Git's ignore rules are hierarchical – rules in subdirectories can override or add to parent rules.
 * This class caches GitignoreParser instances for efficiency and applies rules with proper precedence.
 * 
 * It also listens for VFS events to invalidate the cache when .gitignore files are modified.
 */
class HierarchicalGitignoreParser(private val project: Project) : Disposable {
    private val logger = Logger.getInstance(HierarchicalGitignoreParser::class.java)

    // Cache of GitignoreParser instances by directory path
    private val parserCache = ConcurrentHashMap<String, GitignoreParser?>()

    // Repository root for determining the top of the hierarchy
    private val repositoryRoot: VirtualFile? = FileUtils.getRepositoryRoot(project)

    // VFS listener to invalidate cache when .gitignore files change
    private val vfsListener = object : VirtualFileListener {
        override fun contentsChanged(event: VirtualFileEvent) {
            if (event.file.name == ".gitignore") {
                logger.debug("Gitignore file changed: ${event.file.path}. Invalidating cache entry.")
                parserCache.remove(event.file.path)
            }
        }

        override fun fileDeleted(event: VirtualFileEvent) {
            if (event.file.name == ".gitignore") {
                logger.debug("Gitignore file deleted: ${event.file.path}. Invalidating cache entry.")
                parserCache.remove(event.file.path)
            }
        }

        override fun fileMoved(event: VirtualFileMoveEvent) {
            if (event.file.name == ".gitignore") {
                val oldPath = event.oldParent.path + "/" + event.file.name
                logger.debug("Gitignore file moved: ${event.file.path} (from $oldPath). Invalidating cache entries.")
                parserCache.remove(event.file.path)
                parserCache.remove(oldPath)
            }
        }
    }

    init {
        try {
            // Register the VFS listener
            VirtualFileManager.getInstance().addVirtualFileListener(vfsListener)
            // Register this instance for disposal when the project is closed
            Disposer.register(project, this)
        } catch (e: Exception) {
            // In test environments, the VirtualFileManager service might not be available
            logger.warn("Could not register VFS listener: ${e.message}")
            // No need to register for disposal if we couldn't register the listener
        }
    }

    /**
     * Disposes this instance by removing the VFS listener.
     */
    override fun dispose() {
        try {
            VirtualFileManager.getInstance().removeVirtualFileListener(vfsListener)
            logger.debug("HierarchicalGitignoreParser disposed, VFS listener removed")
        } catch (e: Exception) {
            // In test environments, the VirtualFileManager service might not be available
            logger.warn("Could not remove VFS listener: ${e.message}")
        }
    }

    /**
     * Checks if a file should be ignored based on all applicable .gitignore rules
     * in the path hierarchy from the file up to the project root.
     *
     * @param file The file to check
     * @return true if the file should be ignored, false otherwise
     */
    fun isIgnored(file: VirtualFile): Boolean {
        if (repositoryRoot == null) {
            logger.warn("Repository root is null. Cannot perform hierarchical gitignore check.")
            return false
        }

        // Get the relative path of the file to the repository root
        val relativePath = FileUtils.getRelativePath(file, project)
        if (relativePath.isEmpty()) {
            return false // Cannot ignore the repository root
        }

        logger.debug("Checking if file ${file.path} (relative: $relativePath) should be ignored")

        // Find all applicable .gitignore files in the path hierarchy
        val gitignoreParsers = findApplicableGitignoreParsers(file)
        if (gitignoreParsers.isEmpty()) {
            logger.debug("No applicable .gitignore files found for: ${file.path}")
            return false
        }

        logger.debug("Found ${gitignoreParsers.size} applicable gitignore parsers:")
        gitignoreParsers.forEachIndexed { index, parser ->
            logger.debug("  $index: ${parser.gitignoreFile.path}")
        }

        // Initialize decision as null (undecided)
        var decision: Boolean? = null // null = undecided, true = ignore, false = negate

        // Process parsers in reverse order (leaf to root) to ensure child rules override parent rules
        for (parser in gitignoreParsers.reversed()) {
            // Calculate the path relative to the .gitignore file's directory
            val gitignoreDir = parser.gitignoreFile.parent

            // Special handling for root gitignore
            val relativeToGitignore = if (gitignoreDir == repositoryRoot) {
                // For root gitignore, use the path relative to the repository root
                relativePath
            } else {
                // For other gitignores, use the path relative to the gitignore directory
                VfsUtil.getRelativePath(file, gitignoreDir, '/')
            }

            logger.debug("Checking against gitignore: ${parser.gitignoreFile.path}")
            logger.debug("  gitignoreDir: ${gitignoreDir.path}")
            logger.debug("  relativeToGitignore: $relativeToGitignore")

            if (relativeToGitignore != null && relativeToGitignore.isNotEmpty()) {
                // Use the method that returns MatchResult
                val result = parser.matchResult(relativeToGitignore, file.isDirectory)
                logger.debug("  matchResult: $result")

                // Update the decision only if a rule explicitly matched
                when (result) {
                    GitignoreParser.MatchResult.MATCH_IGNORE -> {
                        decision = true
                        logger.debug("  File ${file.path} decision set to IGNORE by ${parser.gitignoreFile.path}")
                        // Once a child gitignore makes a decision, stop processing
                        break
                    }
                    GitignoreParser.MatchResult.MATCH_NEGATE -> {
                        decision = false
                        logger.debug("  File ${file.path} decision set to NEGATE (not ignored) by ${parser.gitignoreFile.path}")
                        // Once a child gitignore makes a decision, stop processing
                        break
                    }
                    GitignoreParser.MatchResult.NO_MATCH -> {
                        // Do nothing, keep the decision from the parent parser
                        logger.debug("  File ${file.path} NO_MATCH by ${parser.gitignoreFile.path}, keeping previous decision: $decision")
                    }
                }
            } else {
                logger.debug("  relativeToGitignore is null, skipping this gitignore")
            }
        }

        // Final decision: if null (no rules matched or all were NO_MATCH), default to false (not ignored)
        val finalDecision = decision ?: false
        logger.debug("Final decision for ${file.path}: $finalDecision (initial decision was $decision)")
        return finalDecision
    }

    /**
     * Finds all applicable .gitignore files in the path hierarchy from the file up to the project root.
     * Returns a list of GitignoreParser instances ordered from root to leaf (for proper precedence).
     * This ensures that child rules can override parent rules, following Git's precedence rules.
     *
     * @param file The file to find applicable .gitignore files for
     * @return List of GitignoreParser instances ordered from root to leaf
     */
    private fun findApplicableGitignoreParsers(file: VirtualFile): List<GitignoreParser> {
        val rootToLeafParsers = mutableListOf<GitignoreParser>()

        logger.debug("findApplicableGitignoreParsers for file: ${file.path}")
        logger.debug("Repository root: ${repositoryRoot?.path}")

        if (repositoryRoot == null) {
            logger.debug("Repository root is null, returning empty list")
            return emptyList()
        }

        // Start with the file's parent directory and traverse up to the repository root
        var currentDir = if (file.isDirectory) file else file.parent
        logger.debug("Starting directory: ${currentDir?.path}")

        // Collect all directories from the file up to the repository root (inclusive)
        val dirsToCheck = mutableListOf<VirtualFile>()

        // First collect all directories from leaf to root
        val leafToRootDirs = mutableListOf<VirtualFile>()

        // Add the current directory and all parent directories up to (and including) the repository root
        while (currentDir != null) {
            leafToRootDirs.add(currentDir)
            logger.debug("Added directory to check (leaf-to-root): ${currentDir.path}")
            if (currentDir == repositoryRoot) break
            currentDir = currentDir.parent
        }

        // Reverse to get root -> leaf order (correct Git precedence)
        dirsToCheck.addAll(leafToRootDirs.reversed())
        logger.debug("Created directory list in root-to-leaf order for correct Git precedence")

        // Process directories from root to leaf
        for (dir in dirsToCheck) {
            logger.debug("Checking directory for gitignore: ${dir.path}")
            val gitignoreFile = dir.findChild(".gitignore")
            logger.debug("Found gitignore file: ${gitignoreFile != null}")

            if (gitignoreFile != null && !gitignoreFile.isDirectory) {
                logger.debug("Found gitignore file at: ${gitignoreFile.path}")
                // Use cached parser or create a new one
                val parser = getOrCreateParser(gitignoreFile)
                if (parser != null) {
                    logger.debug("Added parser for: ${gitignoreFile.path}")
                    rootToLeafParsers.add(parser)
                } else {
                    logger.debug("Failed to create parser for: ${gitignoreFile.path}")
                }
            }
        }

        logger.debug("Found ${rootToLeafParsers.size} parsers in root-to-leaf order")

        // Return in root-to-leaf order (child rules override parent rules)
        return rootToLeafParsers
    }

    /**
     * Gets a cached GitignoreParser instance for the given .gitignore file or creates a new one.
     *
     * @param gitignoreFile The .gitignore file
     * @return GitignoreParser instance or null if parsing failed
     */
    private fun getOrCreateParser(gitignoreFile: VirtualFile): GitignoreParser? {
        val path = gitignoreFile.path

        return parserCache.computeIfAbsent(path) {
            try {
                GitignoreParser(gitignoreFile)
            } catch (e: Exception) {
                logger.warn("Failed to create GitignoreParser for ${gitignoreFile.path}", e)
                null
            }
        }
    }

    /**
     * Clears the parser cache.
     */
    fun clearCache() {
        parserCache.clear()
        logger.debug("Cleared GitignoreParser cache")
    }
}
