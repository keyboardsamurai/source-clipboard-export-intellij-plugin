package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class NotificationUtilsTest {

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `showNotification falls back to println when manager missing`() {
        val output = ByteArrayOutputStream()
        val original = System.out
        System.setOut(PrintStream(output))

        mockkStatic(NotificationGroupManager::class)
        every { NotificationGroupManager.getInstance() } throws IllegalStateException("no app")

        NotificationUtils.showNotification(null, "title", "content", NotificationType.ERROR)

        System.setOut(original)
        val text = output.toString()
        assertEquals(true, text.contains("NOTIFICATION"))
    }

    @Test
    fun `createNotification falls back when manager unavailable`() {
        mockkStatic(NotificationGroupManager::class)
        every { NotificationGroupManager.getInstance() } throws IllegalStateException("no app")

        val notification = NotificationUtils.createNotification("title", "body", NotificationType.WARNING)

        assertEquals("title", notification.title)
        assertEquals("body", notification.content)
    }
}
