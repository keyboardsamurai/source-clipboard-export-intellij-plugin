package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util

import com.intellij.notification.Notification
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.project.Project
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.AppConstants
import javax.swing.Timer

object NotificationUtils {
    fun showNotification(
        project: Project?,
        title: String,
        content: String,
        type: NotificationType,
        groupId: String = AppConstants.NOTIFICATION_GROUP_ID // Use constant
    ) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(groupId)
            .createNotification(title, content, type)
            .notify(project)
    }

    // Optional: Keep the expireAfter extension if used elsewhere, or integrate its logic if needed.
    fun Notification.expireAfter(millis: Int) {
        Timer(millis) { expire() }.apply {
            isRepeats = false
            start()
        }
    }
} 