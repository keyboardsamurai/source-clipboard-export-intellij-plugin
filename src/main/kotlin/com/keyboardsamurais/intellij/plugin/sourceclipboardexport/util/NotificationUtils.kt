package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util

import com.intellij.notification.Notification
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import javax.swing.Timer

object NotificationUtils {
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

    // Optional: Keep the expireAfter extension if used elsewhere, or integrate its logic if needed.
    fun Notification.expireAfter(millis: Int) {
        Timer(millis) { expire() }.apply {
            isRepeats = false
            start()
        }
    }
} 
