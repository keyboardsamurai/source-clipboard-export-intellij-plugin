package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.ui

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.config.SourceClipboardExportSettings
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core.SourceExporter
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.NotificationUtils
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ExportNotificationPresenterTest {

    private lateinit var project: Project
    private lateinit var presenter: ExportNotificationPresenter
    private lateinit var settings: SourceClipboardExportSettings.State

    @BeforeEach
    fun setUp() {
        project = mockk(relaxed = true)
        presenter = ExportNotificationPresenter(project)
        settings = mockk(relaxed = true)

        val settingsService = mockk<SourceClipboardExportSettings>()
        every { settingsService.state } returns settings
        SourceClipboardExportSettings.setTestInstance(settingsService)

        mockkObject(NotificationUtils)
    }

    @AfterEach
    fun tearDown() {
        SourceClipboardExportSettings.setTestInstance(null)
        unmockkObject(NotificationUtils)
    }

    @Test
    fun `showSuccessNotification with no exclusions`() {
        // Given
        val result = mockk<SourceExporter.ExportResult>()
        every { result.processedFileCount } returns 5
        every { result.excludedByFilterCount } returns 0
        every { result.excludedBySizeCount } returns 0
        every { result.excludedByBinaryContentCount } returns 0
        every { result.excludedByIgnoredNameCount } returns 0
        every { result.excludedByGitignoreCount } returns 0
        every { result.excludedExtensions } returns emptySet()
        every { settings.areFiltersEnabled } returns false
        every { settings.filenameFilters } returns mutableListOf()
        every { settings.maxFileSizeKb } returns 1024

        // When
        presenter.showSuccessNotification(result, "1.0KB", "100")

        // Then
        verify {
            NotificationUtils.showNotification(
                    project,
                    "Content Copied",
                    match { message ->
                        message.contains("Copied 5 files (1.0KB, ~100 tokens)") &&
                                message.contains("Processed files: 5") &&
                                !message.contains("Excluded files:") &&
                                message.contains("Filters disabled.")
                    },
                    NotificationType.INFORMATION
            )
        }
    }

    @Test
    fun `showSuccessNotification with filter exclusions`() {
        // Given
        val result = mockk<SourceExporter.ExportResult>()
        every { result.processedFileCount } returns 3
        every { result.excludedByFilterCount } returns 2
        every { result.excludedBySizeCount } returns 1
        every { result.excludedByBinaryContentCount } returns 1
        every { result.excludedByIgnoredNameCount } returns 0
        every { result.excludedByGitignoreCount } returns 0
        every { result.excludedExtensions } returns setOf("class", "jar", "exe")
        every { settings.areFiltersEnabled } returns true
        every { settings.filenameFilters } returns mutableListOf("*.java", "*.kt")
        every { settings.maxFileSizeKb } returns 1024

        // When
        presenter.showSuccessNotification(result, "1.0KB", "100")

        // Then
        verify {
            NotificationUtils.showNotification(
                    project,
                    "Content Copied",
                    match { message ->
                        message.contains("Excluded files: 4") &&
                                message.contains("By filter: 2") &&
                                message.contains("Types: class, jar, exe") &&
                                message.contains("By size (> 1024 KB): 1") &&
                                message.contains("Binary content: 1") &&
                                message.contains("Active filters: *.java, *.kt")
                    },
                    NotificationType.INFORMATION
            )
        }
    }

    @Test
    fun `showSuccessNotification with filters enabled but empty list`() {
        // Given
        val result = mockk<SourceExporter.ExportResult>()
        every { result.processedFileCount } returns 5
        every { result.excludedByFilterCount } returns 0
        every { result.excludedBySizeCount } returns 0
        every { result.excludedByBinaryContentCount } returns 0
        every { result.excludedByIgnoredNameCount } returns 0
        every { result.excludedByGitignoreCount } returns 0
        every { result.excludedExtensions } returns emptySet()
        every { settings.areFiltersEnabled } returns true
        every { settings.filenameFilters } returns mutableListOf()
        every { settings.maxFileSizeKb } returns 1024

        // When
        presenter.showSuccessNotification(result, "1.0KB", "100")

        // Then
        verify {
            NotificationUtils.showNotification(
                    project,
                    "Content Copied",
                    match { message -> message.contains("Filters enabled, but list is empty") },
                    NotificationType.INFORMATION
            )
        }
    }

    @Test
    fun `showSuccessNotification with many excluded extensions shows truncated list`() {
        // Given
        val result = mockk<SourceExporter.ExportResult>()
        every { result.processedFileCount } returns 3
        every { result.excludedByFilterCount } returns 8
        every { result.excludedBySizeCount } returns 0
        every { result.excludedByBinaryContentCount } returns 0
        every { result.excludedByIgnoredNameCount } returns 0
        every { result.excludedByGitignoreCount } returns 0
        every { result.excludedExtensions } returns
                setOf("class", "jar", "exe", "dll", "so", "dylib", "bin", "obj")
        every { settings.areFiltersEnabled } returns true
        every { settings.filenameFilters } returns mutableListOf("*.java")
        every { settings.maxFileSizeKb } returns 1024

        // When
        presenter.showSuccessNotification(result, "1.0KB", "100")

        // Then
        verify {
            NotificationUtils.showNotification(
                    project,
                    "Content Copied",
                    match { message -> message.contains("Types: class, jar, exe, dll, so, ...") },
                    NotificationType.INFORMATION
            )
        }
    }

    @Test
    fun `showLimitReachedNotification`() {
        // Given
        val notification = mockk<com.intellij.notification.Notification>(relaxed = true)
        every { NotificationUtils.createNotification(any(), any(), any()) } returns notification

        // When
        presenter.showLimitReachedNotification(1000)

        // Then
        verify {
            NotificationUtils.createNotification(
                    "File Limit Reached",
                    "Processing stopped after reaching the limit of 1000 files.",
                    NotificationType.WARNING
            )
            notification.addAction(any())
            notification.notify(project)
        }
    }

    @Test
    fun `showLimitReachedNotification action opens settings`() {
        val notification = mockk<Notification>(relaxed = true)
        every { NotificationUtils.createNotification(any(), any(), any()) } returns notification
        val actionSlot = slot<NotificationAction>()
        every { notification.addAction(capture(actionSlot)) } returns notification
        val showSettings = mockk<ShowSettingsUtil>(relaxed = true)
        mockkStatic(ShowSettingsUtil::class)
        every { ShowSettingsUtil.getInstance() } returns showSettings

        try {
            presenter.showLimitReachedNotification(50)

            actionSlot.captured.actionPerformed(mockk(relaxed = true), notification)

            verify {
                showSettings.showSettingsDialog(
                        project,
                        "com.keyboardsamurais.intellij.plugin.sourceclipboardexport.config.SourceClipboardExportConfigurable"
                )
                notification.expire()
            }
        } finally {
            unmockkStatic(ShowSettingsUtil::class)
        }
    }

    @Test
    fun `showNoPreviousExportNotification`() {
        // When
        presenter.showNoPreviousExportNotification()

        // Then
        verify {
            NotificationUtils.showNotification(
                    project,
                    "No Previous Export",
                    "No previous export found to compare against",
                    NotificationType.INFORMATION
            )
        }
    }

    @Test
    fun `showDiffCopiedNotification`() {
        // When
        presenter.showDiffCopiedNotification()

        // Then
        verify {
            NotificationUtils.showNotification(
                    project,
                    "Diff Copied",
                    "Export diff copied to clipboard",
                    NotificationType.INFORMATION
            )
        }
    }

    @Test
    fun `showNoChangesNotification`() {
        // When
        presenter.showNoChangesNotification()

        // Then
        verify {
            NotificationUtils.showNotification(
                    project,
                    "No Changes",
                    "No new files to export",
                    NotificationType.INFORMATION
            )
        }
    }

    @Test
    fun `showSimpleSuccessNotification relays stats`() {
        presenter.showSimpleSuccessNotification(7, "10.0KB", "123")

        verify {
            NotificationUtils.showNotification(
                    project,
                    "Content Copied",
                    "Copied 7 files (10.0KB, ~123 tokens)",
                    NotificationType.INFORMATION
            )
        }
    }

    @Test
    fun `showEmptyContentWarning enumerates likely causes`() {
        every { settings.maxFileSizeKb } returns 256
        every { settings.ignoredNames } returns mutableListOf("build", "out", "dist", "tmp")

        presenter.showEmptyContentWarning(settings)

        verify {
            NotificationUtils.showNotification(
                    project,
                    "Warning",
                    match { it.contains("No content to copy") && it.contains("256KB") && it.contains("build, out, dist") },
                    NotificationType.WARNING
            )
        }
    }

    @Test
    fun `showCancelledNotification emits warning`() {
        presenter.showCancelledNotification()

        verify {
            NotificationUtils.showNotification(
                    project,
                    "Export Cancelled",
                    "The operation was cancelled",
                    NotificationType.WARNING
            )
        }
    }

    @Test
    fun `showErrorNotification emits error balloon`() {
        presenter.showErrorNotification("boom")

        verify {
            NotificationUtils.showNotification(
                    project,
                    "Export Error",
                    "Failed to export source: boom",
                    NotificationType.ERROR
            )
        }
    }

    @Test
    fun `showClipboardErrorNotification surfaces copy failure`() {
        presenter.showClipboardErrorNotification("clipboard blocked")

        verify {
            NotificationUtils.showNotification(
                    project,
                    "Error",
                    "Failed to copy to clipboard: clipboard blocked",
                    NotificationType.ERROR
            )
        }
    }
}
