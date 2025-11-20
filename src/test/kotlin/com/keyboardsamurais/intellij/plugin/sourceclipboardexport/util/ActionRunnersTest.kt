package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util

import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertTrue

class ActionRunnersTest {

    private val project = mockk<Project>(relaxed = true)

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `runSmartBackground executes synchronously in unit test mode`() {
        mockkStatic(ApplicationManager::class)
        val application = mockk<Application>(relaxed = true)
        every { application.isUnitTestMode } returns true
        every { ApplicationManager.getApplication() } returns application

        val executed = AtomicBoolean(false)

        ActionRunners.runSmartBackground(project, "Test") { executed.set(true) }

        assertTrue(executed.get())
    }

    @Test
    fun `runSmartBackground defers to dumb service when IDE services available`() {
        mockkStatic(ApplicationManager::class)
        mockkStatic(ProgressManager::class)
        mockkStatic(DumbService::class)

        val application = mockk<Application>(relaxed = true)
        every { application.isUnitTestMode } returns false
        every { ApplicationManager.getApplication() } returns application

        val progressManager = mockk<ProgressManager>(relaxed = true)
        every { ProgressManager.getInstance() } returns progressManager
        every { progressManager.run(any<Task.Backgroundable>()) } answers {
            val task = firstArg<Task.Backgroundable>()
            task.run(EmptyProgressIndicator())
        }

        val dumbService = mockk<DumbService>(relaxed = true)
        every { DumbService.getInstance(project) } returns dumbService
        val executed = AtomicBoolean(false)
        every { dumbService.runWhenSmart(any()) } answers {
            val runnable = firstArg<Runnable>()
            runnable.run()
        }

        ActionRunners.runSmartBackground(project, "Test") { _: ProgressIndicator ->
            executed.set(true)
        }

        assertTrue(executed.get())
    }
}
