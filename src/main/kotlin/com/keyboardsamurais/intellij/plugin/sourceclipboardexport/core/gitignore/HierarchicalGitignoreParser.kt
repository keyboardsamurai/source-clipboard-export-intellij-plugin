package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core.gitignore

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.FileUtils
import java.util.concurrent.ConcurrentHashMap

/**
 * Implements hierarchical .gitignore parsing by finding and applying rules from
 * all .gitignore files in the path hierarchy from a file up to the project root.
 * 
 * Git's ignore rules are hierarchical – rules in subdirectories can override or add to parent rules.
 * This class caches GitignoreParser instances for efficiency and applies rules with proper precedence.
 */
class HierarchicalGitignoreParser(private val project: Project) {
    private val logger = Logger.getInstance(HierarchicalGitignoreParser::class.java)
    
    // Cache of GitignoreParser instances by directory path
    private val parserCache = ConcurrentHashMap<String, GitignoreParser?>()
    
    // Repository root for determining the top of the hierarchy
    private val repositoryRoot: VirtualFile? = FileUtils.getRepositoryRoot(project)
    
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
        
        // Find all applicable .gitignore files in the path hierarchy
        val gitignoreParsers = findApplicableGitignoreParsers(file)
        if (gitignoreParsers.isEmpty()) {
            logger.debug("No applicable .gitignore files found for: ${file.path}")
            return false
        }
        
        // Apply rules with proper precedence (child directories override parent directories)
        // So we iterate from the root down to the file's directory
        for (parser in gitignoreParsers) {
            // Calculate the path relative to the .gitignore file's directory
            val gitignoreDir = parser.gitignoreFile.parent
            val relativeToGitignore = VfsUtil.getRelativePath(file, gitignoreDir, '/')
            
            if (relativeToGitignore != null) {
                val isIgnored = parser.matches(relativeToGitignore, file.isDirectory)
                if (isIgnored) {
                    logger.debug("File ${file.path} is ignored by ${parser.gitignoreFile.path}")
                    return true
                }
            }
        }
        
        return false
    }
    
    /**
     * Finds all applicable .gitignore files in the path hierarchy from the file up to the project root.
     * Returns a list of GitignoreParser instances ordered from root to leaf (for proper precedence).
     *
     * @param file The file to find applicable .gitignore files for
     * @return List of GitignoreParser instances ordered from root to leaf
     */
    private fun findApplicableGitignoreParsers(file: VirtualFile): List<GitignoreParser> {
        val parsers = mutableListOf<GitignoreParser>()
        
        // Start with the file's parent directory and traverse up to the repository root
        var currentDir = if (file.isDirectory) file else file.parent
        
        while (currentDir != null && (repositoryRoot == null || VfsUtil.isAncestor(repositoryRoot, currentDir, true))) {
            val gitignoreFile = currentDir.findChild(".gitignore")
            
            if (gitignoreFile != null && !gitignoreFile.isDirectory) {
                // Use cached parser or create a new one
                val parser = getOrCreateParser(gitignoreFile)
                if (parser != null) {
                    parsers.add(parser)
                }
            }
            
            // Move up to the parent directory
            currentDir = currentDir.parent
        }
        
        // Reverse the list to get the correct precedence order (root to leaf)
        return parsers.reversed()
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