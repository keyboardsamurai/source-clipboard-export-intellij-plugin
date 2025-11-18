package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap

class DependencyFinderTest {

    private val cacheField = DependencyFinder::class.java.getDeclaredField("dependentsCache").apply {
        isAccessible = true
    }

    @BeforeEach
    fun setup() {
        (cacheField.get(DependencyFinder) as ConcurrentHashMap<*, *>).clear()
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
        (cacheField.get(DependencyFinder) as ConcurrentHashMap<*, *>).clear()
    }

    @Test
    fun `findDependents returns cached result`() = runBlocking {
        val project = mockk<Project>(relaxed = true)
        val file = mockk<VirtualFile>(relaxed = true)
        val cachedFile = mockk<VirtualFile>(relaxed = true)
        every { file.path } returns "/src/Foo.kt"
        every { cachedFile.path } returns "/src/Bar.kt"

        val cache = cacheField.get(DependencyFinder) as ConcurrentHashMap<String, Set<VirtualFile>>
        val key = listOf("/src/Foo.kt").sorted().joinToString(";")
        cache[key] = setOf(cachedFile)

        val result = DependencyFinder.findDependents(arrayOf(file), project)

        assertEquals(setOf(cachedFile), result)
    }

    @Test
    fun `clearCaches empties dependents cache`() {
        val cache = cacheField.get(DependencyFinder) as ConcurrentHashMap<String, Set<VirtualFile>>
        cache["key"] = emptySet()
        DependencyFinder.clearCaches()
        assertEquals(0, cache.size)
    }

    @Test
    fun `getCacheStats reflects cache size`() {
        val cache = cacheField.get(DependencyFinder) as ConcurrentHashMap<String, Set<VirtualFile>>
        cache["stat"] = emptySet()
        val stats = DependencyFinder.getCacheStats()
        assertEquals("Dependents cache: 1 entries", stats)
    }

    @Test
    fun `validateConfiguration completes for large selections`() {
        DependencyFinder.validateConfiguration(mockk(relaxed = true), selectedFilesCount = 20)
    }
}
