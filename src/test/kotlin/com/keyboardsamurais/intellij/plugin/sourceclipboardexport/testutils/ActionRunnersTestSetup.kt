package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.testutils

import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic

/**
 * Utility object for setting up common mocks required for tests that use ActionRunners.
 * This ensures consistent mocking across all test classes that use ActionRunners.runSmartBackground
 */
object ActionRunnersTestSetup {
    
    /**
     * Sets up all necessary mocks for ActionRunners to work in test environment.
     * Call this in @BeforeEach of your test class.
     * 
     * @param project The mocked project instance to use
     * @return The mocked Application instance (in case you need it for additional setup)
     */
    fun setupMocks(project: Project): Application {
        // Mock ApplicationManager
        mockkStatic(ApplicationManager::class)
        val mockApplication = mockk<Application>(relaxed = true)
        every { ApplicationManager.getApplication() } returns mockApplication
        every { mockApplication.isUnitTestMode } returns true
        
        // Mock ProgressManager
        mockkStatic(ProgressManager::class)
        val mockProgressManager = mockk<ProgressManager>(relaxed = true)
        every { ProgressManager.getInstance() } returns mockProgressManager
        every { mockProgressManager.run(any<Task.Backgroundable>()) } answers {
            val task = firstArg<Task.Backgroundable>()
            task.run(mockk<ProgressIndicator>(relaxed = true))
        }
        
        // Mock DumbService
        mockkStatic(DumbService::class)
        val mockDumbService = mockk<DumbService>(relaxed = true)
        every { DumbService.getInstance(project) } returns mockDumbService
        every { mockDumbService.runWhenSmart(any()) } answers {
            val runnable = firstArg<Runnable>()
            runnable.run()
        }
        
        return mockApplication
    }
}