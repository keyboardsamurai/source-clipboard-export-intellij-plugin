package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions

// import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.config.SourceClipboardExportSettings // Keep if needed later
import com.intellij.execution.ui.ConsoleView
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.config.SourceClipboardExportSettings
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core.StackTraceFolder
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.NotificationUtils
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.StringUtils
import java.awt.datatransfer.StringSelection

/**
 * Collapses noisy stack traces in Run/Debug consoles and places the folded output on the clipboard.
 * The heuristics come from [StackTraceFolder] which knows how to differentiate project classes
 * from external libraries via PSI.
 *
 * Available from the console context menu and marked [com.intellij.openapi.project.DumbAware] so it
 * can run while indices are rebuilding (folding falls back to simple heuristics in that case).
 */
class FoldAndCopyStackTraceAction : AnAction() {

    private val logger = Logger.getInstance(FoldAndCopyStackTraceAction::class.java)


    /**
     * Enables the action when a project, editor, console, and selected text are present. Mirrors
     * the default console context menu behavior to avoid confusing users with a disabled entry.
     */
    override fun update(e: AnActionEvent) {
        // Retrieve necessary data from the event context
        val project: Project? = e.project
        val editor: Editor? = e.getData(CommonDataKeys.EDITOR)
        val consoleView: ConsoleView? = e.getData(LangDataKeys.CONSOLE_VIEW)
        val selectedText: String? = editor?.selectionModel?.selectedText

        // Determine if the action should be enabled based on all conditions
        val isEnabled = project != null &&           // Need a project for context
                editor != null &&           // Need an editor component
                consoleView != null &&      // Must be invoked from a console view
                !selectedText.isNullOrBlank() // Must have non-empty text selected

        // Set the presentation visibility and enabled state
        e.presentation.isEnabledAndVisible = isEnabled

        // Optional: Keep logging for debugging if needed
        logger.trace("FoldAndCopyStackTraceAction update: project=$project, editor=$editor, hasSelection=${!selectedText.isNullOrBlank()}, consoleView=$consoleView -> isEnabled=$isEnabled")
    }
    /**
     * Runs the folding pipeline on a background thread, respecting cancellation from the progress
     * manager. Clipboard interaction happens back on the EDT via [ApplicationManager.invokeLater].
     */
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val selectedText = editor.selectionModel.selectedText

        if (selectedText.isNullOrBlank()) {
            logger.warn("Action performed but no text selected.")
            return
        }

        logger.info("Action initiated: FoldAndCopyStackTraceAction")
        // val settings = SourceClipboardExportSettings.getInstance().state // Load settings if needed

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Folding Stack Trace") {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = "Analyzing stack trace..."

                try {
                    // Load settings
                    val settings = SourceClipboardExportSettings.getInstance().state
                    // Instantiate the folder with robust, LLM-friendly defaults
                    val stackSettings = settings.stackTraceSettings
                    val folder = StackTraceFolder(
                        project,
                        minFramesToFold = stackSettings.minFramesToFold,
                        keepHeadFrames = stackSettings.keepHeadFrames,
                        keepTailFrames = stackSettings.keepTailFrames,
                        includePackageHints = stackSettings.includePackageHints,
                        treatEllipsisAsFoldable = stackSettings.treatEllipsisAsFoldable,
                        preserveIndentation = true
                    )

                    // Folding requires read action, which folder handles internally
                    var foldedStackTrace = folder.foldStackTrace(selectedText)
                    if (stackSettings.appendRaw) {
                        foldedStackTrace = buildString {
                            append(foldedStackTrace)
                            append('\n')
                            append("--- folded above; raw trace below ---")
                            append('\n')
                            append(selectedText)
                        }
                    }

                    // Calculate counts *after* folding
                    val charCount = foldedStackTrace.length
                    val tokenCount = StringUtils.estimateTokensWithSubwordHeuristic(foldedStackTrace)

                    // Update clipboard on the EDT
                    ApplicationManager.getApplication().invokeLater {
                        copyToClipboard(foldedStackTrace, charCount, tokenCount, project) // Pass counts
                    }
                } catch (t: Throwable) {
                    logger.error("Error folding stack trace", t)
                    NotificationUtils.showNotification(
                        project,
                        "Folding Error",
                        "Failed to fold stack trace: ${t.message}",
                        NotificationType.ERROR
                    )
                }
            }
        })
    }

    private fun copyToClipboard(text: String, charCount: Int, tokenCount: Int, project: Project?) {
        try {
            CopyPasteManager.getInstance().setContents(StringSelection(text))
            val message = "Folded stack trace copied ($charCount chars, ~$tokenCount tokens)."
            logger.info(message)
            NotificationUtils.showNotification(
                project,
                "Stack Trace Copied",
                message,
                NotificationType.INFORMATION
            )
        } catch (e: Exception) {
            logger.error("Failed to set clipboard contents", e)
            NotificationUtils.showNotification(
                project,
                "Clipboard Error",
                "Failed to copy folded stack trace: ${e.message}",
                NotificationType.ERROR
            )
        }
    }

    /**
     * Specifies that the update method should run on the EDT.
     * This is generally recommended for actions updating UI state.
     */
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT
    }
} 
