package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project

object ActionRunners {
    fun runSmartBackground(
        project: Project,
        title: String,
        cancellable: Boolean = true,
        task: (ProgressIndicator) -> Unit
    ) {
        val runBg = {
            ProgressManager.getInstance().run(object : Task.Backgroundable(project, title, cancellable) {
                override fun run(indicator: ProgressIndicator) {
                    task(indicator)
                }
            })
        }

        try {
            val app = ApplicationManager.getApplication()
            // In unit tests or when application/project services are not fully initialized,
            // avoid ProgressManager/DumbService and execute task directly with a simple indicator.
            if (app == null || app.isUnitTestMode) {
                task(EmptyProgressIndicator())
                return
            }
            DumbService.getInstance(project).runWhenSmart { runBg() }
        } catch (t: Throwable) {
            // If services aren't available, execute synchronously with a simple indicator.
            task(EmptyProgressIndicator())
        }
    }
}
