package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util

import com.intellij.notification.Notification
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.NotificationUtils.showNotification
import javax.swing.Timer

/**
 * Wraps IDEA's notification APIs so the rest of the plugin can emit balloons without repeating
 * boilerplate or worrying about test environments (where [NotificationGroupManager] may be
 * unavailable).
 */
object NotificationUtils {
    /**
     * Shows a notification and falls back to stdout logging in headless tests.
     *
     * @param project project whose notification bus should display the balloon (nullable for app-level notifications)
     * @param title notification title
     * @param content main message; HTML supported by IDEA
     * @param type info/warn/error classification
     * @param groupId optional notification group
     */
    fun showNotification(
        project: Project?,
        title: String,
        content: String,
        type: NotificationType,
        groupId: String = AppConstants.NOTIFICATION_GROUP_ID // Use constant
    ) {
        try {
            NotificationGroupManager.getInstance()
                .getNotificationGroup(groupId)
                .createNotification(title, content, type)
                .notify(project)
        } catch (e: Exception) {
            // In test environments, the Application service might not be available
            // Just log the notification content instead
            println("NOTIFICATION: [$type] $title - $content")
        }
    }

    /**
     * Creates a notification object without displaying it so callers can add actions first (e.g.,
     * open settings). Mirrors [showNotification]'s fallback path for tests.
     */
    fun createNotification(
        title: String,
        content: String,
        type: NotificationType,
        groupId: String = AppConstants.NOTIFICATION_GROUP_ID
    ): Notification {
        return try {
            NotificationGroupManager.getInstance()
                .getNotificationGroup(groupId)
                .createNotification(title, content, type)
        } catch (e: Exception) {
            // Fallback for test environments
            Notification(groupId, title, content, type)
        }
    }

    /** Auto-expires the notification after [millis] milliseconds. Handy for low-signal info balloons. */
    fun Notification.expireAfter(millis: Int) {
        Timer(millis) { expire() }.apply {
            isRepeats = false
            start()
        }
    }
} 
