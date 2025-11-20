package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core.gitignore

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.FileUtils
import java.util.concurrent.ConcurrentHashMap

/**
 * Implements hierarchical .gitignore parsing by finding and applying rules from all .gitignore
 * files in the path hierarchy from a file up to the project root.
 *
 * Git's ignore rules are hierarchical – rules in subdirectories can override or add to parent
 * rules. This class caches GitignoreParser instances for efficiency and applies rules with proper
 * precedence.
 *
 * It also listens for VFS events to invalidate the cache when .gitignore files are modified.
 */
@Service(Service.Level.PROJECT)
class HierarchicalGitignoreParser(private val project: Project) : Disposable {
    private val logger = Logger.getInstance(HierarchicalGitignoreParser::class.java)

    // Cache of GitignoreParser instances by directory path
    private val parserCache = ConcurrentHashMap<String, GitignoreParser?>()

    // Repository root for determining the top of the hierarchy
    private val repositoryRoot: VirtualFile? = FileUtils.getRepositoryRoot(project)

    // Modern VFS listener using BulkFileListener for message bus
    private val vfsListener =
            object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    events.forEach { event ->
                        val file = event.file
                        if (file?.name == ".gitignore") {
                            when (event) {
                                is VFileContentChangeEvent -> {
                                    logger.debug("Gitignore file changed: ${file.path}. Invalidating cache entry.")
                                    parserCache.remove(file.path)
                                }
                                is VFileDeleteEvent -> {
                                    logger.debug("Gitignore file deleted: ${file.path}. Invalidating cache entry.")
                                    parserCache.remove(file.path)
                                }
                                is VFileMoveEvent -> {
                                    val oldPath = event.oldPath
                                    logger.debug("Gitignore file moved: ${file.path} (from $oldPath). Invalidating cache entries.")
                                    parserCache.remove(file.path)
                                    parserCache.remove(oldPath)
                                }
                            }
                        }
                    }
                }
            }

    init {
        try {
            // Modern way: Use message bus with disposable connection
            ApplicationManager.getApplication()
                    .messageBus
                    .connect(this)
                    .subscribe(VirtualFileManager.VFS_CHANGES, vfsListener)
            logger.debug("HierarchicalGitignoreParser initialized with VFS listener via message bus")
        } catch (e: Exception) {
            // In test environments, the message bus might not be available
            logger.warn("Could not register VFS listener via message bus: ${e.message}")
        }
    }

    /**
     * Disposes this instance. The VFS listener is automatically removed via the disposable
     * connection.
     */
    override fun dispose() {
        // No need to manually remove listener - disposable connection handles it automatically
        logger.debug("HierarchicalGitignoreParser disposed, VFS listener automatically removed via disposable connection")
    }

    /**
     * Checks if a file should be ignored based on all applicable .gitignore rules in the path
     * hierarchy from the file up to the project root.
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

        // Process parsers from root to leaf to ensure child rules override parent rules
        for (parser in gitignoreParsers) {
            // Calculate the path relative to the .gitignore file's directory
            val gitignoreDir = parser.gitignoreFile.parent

            // Special handling for root gitignore
            val relativeToGitignore =
                    if (gitignoreDir == repositoryRoot) {
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

                // Update the decision only if a rule explicitly matched.
                // This allows child rules to override parent decisions.
                when (result) {
                    GitignoreParser.MatchResult.MATCH_IGNORE -> {
                        decision = true // Set decision to IGNORE
                        logger.debug("  File ${file.path} decision updated to IGNORE by ${parser.gitignoreFile.path}")
                    }
                    GitignoreParser.MatchResult.MATCH_NEGATE -> {
                        decision = false // Set decision to NEGATE (not ignored)
                        logger.debug("  File ${file.path} decision updated to NEGATE by ${parser.gitignoreFile.path}")
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

        // Final decision: if null (no rules matched or all were NO_MATCH), default to false (not
        // ignored)
        val finalDecision = decision ?: false
        logger.debug("Final decision for ${file.path}: $finalDecision (initial decision was $decision)")
        return finalDecision
    }

    /**
     * Finds all applicable .gitignore files in the path hierarchy from the file up to the project
     * root. Returns a list of GitignoreParser instances ordered from root to leaf (for proper
     * precedence). This ensures that child rules can override parent rules, following Git's
     * precedence rules.
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

        // Add the current directory and all parent directories up to (and including) the repository
        // root
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

    /** Clears the parser cache. */
    /** Clears all cached [GitignoreParser] instances. Called before each export to avoid staleness. */
    fun clearCache() {
        parserCache.clear()
        logger.debug("Cleared GitignoreParser cache")
    }
}
