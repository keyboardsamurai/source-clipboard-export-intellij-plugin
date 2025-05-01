package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core.gitignore

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.FileUtils
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.spyk
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import io.mockk.*
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VfsUtilCore

class HierarchicalGitignoreParserTest {

    private lateinit var mockProject: Project
    private lateinit var mockApplication: Application
    private lateinit var mockVirtualFileManager: VirtualFileManager
    private lateinit var mockLogger: Logger
    private lateinit var mockRepoRoot: VirtualFile
    private lateinit var mockGitignoreInRoot: VirtualFile

    @BeforeEach
    fun setUp() {
        mockkStatic(ApplicationManager::class)
        mockkStatic(VirtualFileManager::class)
        mockkStatic(Disposer::class)
        mockkStatic(Logger::class)

        mockApplication = mockk(relaxed = true)
        mockVirtualFileManager = mockk(relaxed = true)
        mockProject = mockk(relaxed = true)
        mockLogger = mockk(relaxed = true)

        every { ApplicationManager.getApplication() } returns mockApplication
        every { mockApplication.getService(VirtualFileManager::class.java) } returns mockVirtualFileManager
        every { VirtualFileManager.getInstance() } returns mockVirtualFileManager
        every { Disposer.register(any(), any()) } just runs
        every { Logger.getInstance(any<Class<*>>()) } returns mockLogger

        mockkObject(FileUtils)

        mockRepoRoot = mockk(relaxed = true) {
            every { path } returns "/repo/root"
            every { isDirectory } returns true
            every { findChild(".gitignore") } returns null
            every { parent } returns null
        }

        every { FileUtils.getRepositoryRoot(mockProject) } returns mockRepoRoot

        mockGitignoreInRoot = mockk(relaxed = true) {
            every { path } returns "/repo/root/.gitignore"
            every { isDirectory } returns false
            every { parent } returns mockRepoRoot
        }
        every { mockRepoRoot.findChild(".gitignore") } returns mockGitignoreInRoot

        mockkStatic(com.intellij.openapi.vfs.VfsUtilCore::class)
        every { VfsUtilCore.loadText(mockGitignoreInRoot) } returns "*.log"

        val mockSrcDir = mockk<VirtualFile>(relaxed = true) {
            every { path } returns "/repo/root/src"
            every { isDirectory } returns true
            every { findChild(".gitignore") } returns null
            every { parent } returns mockRepoRoot
        }
        val mockLogFile = mockk<VirtualFile>(relaxed = true) {
            every { path } returns "/repo/root/src/debug.log"
            every { isDirectory } returns false
            every { parent } returns mockSrcDir
        }
        val mockOtherFile = mockk<VirtualFile>(relaxed = true) {
            every { path } returns "/repo/root/src/code.kt"
            every { isDirectory } returns false
            every { parent } returns mockSrcDir
        }

        every { FileUtils.getRelativePath(mockLogFile, mockProject) } returns "src/debug.log"
        every { FileUtils.getRelativePath(mockOtherFile, mockProject) } returns "src/code.kt"
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `isIgnored returns false when repository root is null`() {
        every { FileUtils.getRepositoryRoot(mockProject) } returns null
        val parser = HierarchicalGitignoreParser(mockProject)

        val mockFile = mockk<VirtualFile>(relaxed = true)
        val result = parser.isIgnored(mockFile)

        assertFalse(result)
    }

    @Test
    fun `isIgnored returns false when relative path is empty`() {
        val mockFile = mockk<VirtualFile>(relaxed = true)
        every { FileUtils.getRepositoryRoot(mockProject) } returns mockRepoRoot
        every { FileUtils.getRelativePath(mockFile, mockProject) } returns ""
        val parser = HierarchicalGitignoreParser(mockProject)

        val result = parser.isIgnored(mockFile)

        assertFalse(result)
    }

    @Test
    fun `test something basic without gitignore`() {
        val mockSrcDir = mockk<VirtualFile>(relaxed = true) {
            every { path } returns "/repo/root/src"
            every { isDirectory } returns true
            every { findChild(".gitignore") } returns null
            every { parent } returns mockRepoRoot
        }
        val mockFile = mockk<VirtualFile>(relaxed = true) {
            every { path } returns "/repo/root/src/file.txt"
            every { isDirectory } returns false
            every { parent } returns mockSrcDir
        }
        every { mockRepoRoot.findChild(".gitignore") } returns null

        every { FileUtils.getRelativePath(mockFile, mockProject) } returns "src/file.txt"

        val hierarchicalParser = HierarchicalGitignoreParser(mockProject)

        val ignored = hierarchicalParser.isIgnored(mockFile)

        assertFalse(ignored, "File should not be ignored when no .gitignore exists")
    }

    @Test
    fun `isIgnored respects root gitignore`() {
        val mockRootGitignoreContent = "*.log"
        mockGitignoreInRoot = mockk(relaxed = true) {
            every { path } returns "/repo/root/.gitignore"
            every { isDirectory } returns false
            every { parent } returns mockRepoRoot
        }
        every { mockRepoRoot.findChild(".gitignore") } returns mockGitignoreInRoot

        mockkStatic(com.intellij.openapi.vfs.VfsUtilCore::class)
        every { VfsUtilCore.loadText(mockGitignoreInRoot) } returns mockRootGitignoreContent

        val mockSrcDir = mockk<VirtualFile>(relaxed = true) {
            every { path } returns "/repo/root/src"
            every { isDirectory } returns true
            every { findChild(".gitignore") } returns null
            every { parent } returns mockRepoRoot
        }
        val mockLogFile = mockk<VirtualFile>(relaxed = true) {
            every { path } returns "/repo/root/src/debug.log"
            every { isDirectory } returns false
            every { parent } returns mockSrcDir
        }
        val mockOtherFile = mockk<VirtualFile>(relaxed = true) {
            every { path } returns "/repo/root/src/code.kt"
            every { isDirectory } returns false
            every { parent } returns mockSrcDir
        }

        every { FileUtils.getRelativePath(mockLogFile, mockProject) } returns "src/debug.log"
        every { FileUtils.getRelativePath(mockOtherFile, mockProject) } returns "src/code.kt"

        val hierarchicalParser = HierarchicalGitignoreParser(mockProject)

        val isIgnored = hierarchicalParser.isIgnored(mockLogFile)

        assertTrue(isIgnored, "Log file should be ignored by root gitignore")
        assertFalse(hierarchicalParser.isIgnored(mockOtherFile), "Other file should not be ignored")
    }

    @Test
    fun `isIgnored respects hierarchical precedence with child overriding parent`() {
        val mockRootGitignoreContent = "*.log"
        val mockChildGitignoreContent = "!debug.log"

        mockGitignoreInRoot = mockk(relaxed = true) {
            every { path } returns "/repo/root/.gitignore"
            every { isDirectory } returns false
            every { parent } returns mockRepoRoot
        }
        every { mockRepoRoot.findChild(".gitignore") } returns mockGitignoreInRoot

        val mockSrcGitignore = mockk<VirtualFile>(relaxed = true) {
            every { path } returns "/repo/root/src/.gitignore"
            every { isDirectory } returns false
        }
        val mockSrcDir = mockk<VirtualFile>(relaxed = true) {
            every { path } returns "/repo/root/src"
            every { isDirectory } returns true
            every { findChild(".gitignore") } returns mockSrcGitignore
            every { parent } returns mockRepoRoot
        }
        every { mockSrcGitignore.parent } returns mockSrcDir

        val mockDebugLog = mockk<VirtualFile>(relaxed = true) {
            every { path } returns "/repo/root/src/debug.log"
            every { isDirectory } returns false
            every { parent } returns mockSrcDir
        }
        val mockOtherLog = mockk<VirtualFile>(relaxed = true) {
            every { path } returns "/repo/root/src/other.log"
            every { isDirectory } returns false
            every { parent } returns mockSrcDir
        }

        mockkStatic(com.intellij.openapi.vfs.VfsUtilCore::class)
        every { VfsUtilCore.loadText(mockGitignoreInRoot) } returns mockRootGitignoreContent
        every { VfsUtilCore.loadText(mockSrcGitignore) } returns mockChildGitignoreContent

        // Mock VfsUtil.getRelativePath for the child gitignore
        mockkStatic(com.intellij.openapi.vfs.VfsUtil::class)
        every { com.intellij.openapi.vfs.VfsUtil.getRelativePath(mockDebugLog, mockSrcDir, '/') } returns "debug.log"
        every { com.intellij.openapi.vfs.VfsUtil.getRelativePath(mockOtherLog, mockSrcDir, '/') } returns "other.log"
        every { com.intellij.openapi.vfs.VfsUtil.getRelativePath(mockDebugLog, mockRepoRoot, '/') } returns "src/debug.log"
        every { com.intellij.openapi.vfs.VfsUtil.getRelativePath(mockOtherLog, mockRepoRoot, '/') } returns "src/other.log"

        every { FileUtils.getRelativePath(mockDebugLog, mockProject) } returns "src/debug.log"
        every { FileUtils.getRelativePath(mockOtherLog, mockProject) } returns "src/other.log"

        val hierarchicalParser = HierarchicalGitignoreParser(mockProject)

        val isDebugIgnored = hierarchicalParser.isIgnored(mockDebugLog)
        val isOtherIgnored = hierarchicalParser.isIgnored(mockOtherLog)

        assertFalse(isDebugIgnored, "debug.log should NOT be ignored due to child rule")
        assertTrue(isOtherIgnored, "other.log should be ignored by root rule")
    }

    @Test
    fun `isIgnored respects multi-level hierarchical precedence`() {
        val mockRootGitignoreContent = "*.log"
        val mockSrcGitignoreContent = "other.log"
        val mockSubGitignoreContent = "!other.log"

        // Root gitignore
        mockGitignoreInRoot = mockk(relaxed = true) {
            every { path } returns "/repo/root/.gitignore"
            every { isDirectory } returns false
            every { parent } returns mockRepoRoot
        }
        every { mockRepoRoot.findChild(".gitignore") } returns mockGitignoreInRoot

        // Src directory and gitignore
        val mockSrcGitignore = mockk<VirtualFile>(relaxed = true) {
            every { path } returns "/repo/root/src/.gitignore"
            every { isDirectory } returns false
        }
        val mockSrcDir = mockk<VirtualFile>(relaxed = true) {
            every { path } returns "/repo/root/src"
            every { isDirectory } returns true
            every { findChild(".gitignore") } returns mockSrcGitignore
            every { parent } returns mockRepoRoot
        }
        every { mockSrcGitignore.parent } returns mockSrcDir

        // Sub directory and gitignore
        val mockSubGitignore = mockk<VirtualFile>(relaxed = true) {
            every { path } returns "/repo/root/src/sub/.gitignore"
            every { isDirectory } returns false
        }
        val mockSubDir = mockk<VirtualFile>(relaxed = true) {
            every { path } returns "/repo/root/src/sub"
            every { isDirectory } returns true
            every { findChild(".gitignore") } returns mockSubGitignore
            every { parent } returns mockSrcDir
        }
        every { mockSubGitignore.parent } returns mockSubDir

        // Log files
        val mockDebugLog = mockk<VirtualFile>(relaxed = true) {
            every { path } returns "/repo/root/src/sub/debug.log"
            every { isDirectory } returns false
            every { parent } returns mockSubDir
        }
        val mockOtherLog = mockk<VirtualFile>(relaxed = true) {
            every { path } returns "/repo/root/src/sub/other.log"
            every { isDirectory } returns false
            every { parent } returns mockSubDir
        }

        // Mock VfsUtilCore.loadText
        mockkStatic(com.intellij.openapi.vfs.VfsUtilCore::class)
        every { VfsUtilCore.loadText(mockGitignoreInRoot) } returns mockRootGitignoreContent
        every { VfsUtilCore.loadText(mockSrcGitignore) } returns mockSrcGitignoreContent
        every { VfsUtilCore.loadText(mockSubGitignore) } returns mockSubGitignoreContent

        // Mock VfsUtil.getRelativePath
        mockkStatic(com.intellij.openapi.vfs.VfsUtil::class)
        // Relative to sub dir
        every { com.intellij.openapi.vfs.VfsUtil.getRelativePath(mockDebugLog, mockSubDir, '/') } returns "debug.log"
        every { com.intellij.openapi.vfs.VfsUtil.getRelativePath(mockOtherLog, mockSubDir, '/') } returns "other.log"
        // Relative to src dir
        every { com.intellij.openapi.vfs.VfsUtil.getRelativePath(mockDebugLog, mockSrcDir, '/') } returns "sub/debug.log"
        every { com.intellij.openapi.vfs.VfsUtil.getRelativePath(mockOtherLog, mockSrcDir, '/') } returns "sub/other.log"
        // Relative to root
        every { com.intellij.openapi.vfs.VfsUtil.getRelativePath(mockDebugLog, mockRepoRoot, '/') } returns "src/sub/debug.log"
        every { com.intellij.openapi.vfs.VfsUtil.getRelativePath(mockOtherLog, mockRepoRoot, '/') } returns "src/sub/other.log"

        // Mock FileUtils.getRelativePath
        every { FileUtils.getRelativePath(mockDebugLog, mockProject) } returns "src/sub/debug.log"
        every { FileUtils.getRelativePath(mockOtherLog, mockProject) } returns "src/sub/other.log"

        // Enable debug logging for the test
        every { mockLogger.isDebugEnabled() } returns true
        every { mockLogger.debug(any<String>()) } answers { 
            println("[DEBUG_LOG] ${arg<String>(0)}")
        }

        val hierarchicalParser = HierarchicalGitignoreParser(mockProject)

        println("[DEBUG_LOG] Testing debug.log")
        val isDebugIgnored = hierarchicalParser.isIgnored(mockDebugLog)
        println("[DEBUG_LOG] debug.log ignored: $isDebugIgnored")

        println("[DEBUG_LOG] Testing other.log")
        val isOtherIgnored = hierarchicalParser.isIgnored(mockOtherLog)
        println("[DEBUG_LOG] other.log ignored: $isOtherIgnored")

        assertTrue(isDebugIgnored, "debug.log should be ignored by root rule")
        assertFalse(isOtherIgnored, "other.log should NOT be ignored due to sub rule overriding src rule")
    }

    @Test
    fun `isIgnored respects multi-level hierarchical precedence for other log only`() {
        val mockRootGitignoreContent = "*.log"
        val mockSrcGitignoreContent = "other.log"
        val mockSubGitignoreContent = "!other.log"

        // Root gitignore
        mockGitignoreInRoot = mockk(relaxed = true) {
            every { path } returns "/repo/root/.gitignore"
            every { isDirectory } returns false
            every { parent } returns mockRepoRoot
        }
        every { mockRepoRoot.findChild(".gitignore") } returns mockGitignoreInRoot

        // Src directory and gitignore
        val mockSrcGitignore = mockk<VirtualFile>(relaxed = true) {
            every { path } returns "/repo/root/src/.gitignore"
            every { isDirectory } returns false
        }
        val mockSrcDir = mockk<VirtualFile>(relaxed = true) {
            every { path } returns "/repo/root/src"
            every { isDirectory } returns true
            every { findChild(".gitignore") } returns mockSrcGitignore
            every { parent } returns mockRepoRoot
        }
        every { mockSrcGitignore.parent } returns mockSrcDir

        // Sub directory and gitignore
        val mockSubGitignore = mockk<VirtualFile>(relaxed = true) {
            every { path } returns "/repo/root/src/sub/.gitignore"
            every { isDirectory } returns false
        }
        val mockSubDir = mockk<VirtualFile>(relaxed = true) {
            every { path } returns "/repo/root/src/sub"
            every { isDirectory } returns true
            every { findChild(".gitignore") } returns mockSubGitignore
            every { parent } returns mockSrcDir
        }
        every { mockSubGitignore.parent } returns mockSubDir

        // Log file
        val mockOtherLog = mockk<VirtualFile>(relaxed = true) {
            every { path } returns "/repo/root/src/sub/other.log"
            every { isDirectory } returns false
            every { parent } returns mockSubDir
        }

        // Mock VfsUtilCore.loadText
        mockkStatic(com.intellij.openapi.vfs.VfsUtilCore::class)
        every { VfsUtilCore.loadText(mockGitignoreInRoot) } returns mockRootGitignoreContent
        every { VfsUtilCore.loadText(mockSrcGitignore) } returns mockSrcGitignoreContent
        every { VfsUtilCore.loadText(mockSubGitignore) } returns mockSubGitignoreContent

        // Mock VfsUtil.getRelativePath
        mockkStatic(com.intellij.openapi.vfs.VfsUtil::class)
        // Relative to sub dir
        every { com.intellij.openapi.vfs.VfsUtil.getRelativePath(mockOtherLog, mockSubDir, '/') } returns "other.log"
        // Relative to src dir
        every { com.intellij.openapi.vfs.VfsUtil.getRelativePath(mockOtherLog, mockSrcDir, '/') } returns "sub/other.log"
        // Relative to root
        every { com.intellij.openapi.vfs.VfsUtil.getRelativePath(mockOtherLog, mockRepoRoot, '/') } returns "src/sub/other.log"

        // Mock FileUtils.getRelativePath
        every { FileUtils.getRelativePath(mockOtherLog, mockProject) } returns "src/sub/other.log"

        // Enable debug logging for the test
        every { mockLogger.isDebugEnabled() } returns true
        every { mockLogger.debug(any<String>()) } answers { 
            println("[DEBUG_LOG] ${arg<String>(0)}")
        }

        val hierarchicalParser = HierarchicalGitignoreParser(mockProject)

        println("[DEBUG_LOG] Testing other.log only")
        val isOtherIgnored = hierarchicalParser.isIgnored(mockOtherLog)
        println("[DEBUG_LOG] other.log ignored: $isOtherIgnored")

        assertFalse(isOtherIgnored, "other.log should NOT be ignored due to sub rule overriding src rule")
    }

    @Test
    fun `clearCache empties the parser cache`() {
        val parser = HierarchicalGitignoreParser(mockProject)

        val mockGitignoreFile = mockk<VirtualFile>(relaxed = true) {
            every { path } returns "/repo/root/some/.gitignore"
            every { isDirectory } returns false
            every { parent } returns mockk(relaxed = true)
        }
        mockkStatic(com.intellij.openapi.vfs.VfsUtilCore::class)
        every { VfsUtilCore.loadText(mockGitignoreFile) } returns "*.tmp"

        val getOrCreateMethod = HierarchicalGitignoreParser::class.java.getDeclaredMethod(
            "getOrCreateParser", VirtualFile::class.java
        )
        getOrCreateMethod.isAccessible = true
        getOrCreateMethod.invoke(parser, mockGitignoreFile)

        val cacheField = HierarchicalGitignoreParser::class.java.getDeclaredField("parserCache")
        cacheField.isAccessible = true
        val parserCache = cacheField.get(parser) as ConcurrentHashMap<String, GitignoreParser?>
        assertFalse(parserCache.isEmpty(), "Cache should have an entry before clearing")

        parser.clearCache()

        assertTrue(parserCache.isEmpty(), "Cache should be empty after clearCache")
    }
}
