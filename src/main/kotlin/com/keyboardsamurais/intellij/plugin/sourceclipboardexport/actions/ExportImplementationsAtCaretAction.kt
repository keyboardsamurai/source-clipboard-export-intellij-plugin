package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.components.JBList
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.ActionRunners
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.DebugTracer
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.InheritanceFinder
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.NotificationUtils
import kotlinx.coroutines.runBlocking
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.ListSelectionModel

/**
 * Editor-scoped entry point that exports implementations/subclasses related to the symbol at the
 * caret. Integrates with IntelliJ's editor context (caret, PSI) and funnels the resulting file set
 * into [SmartExportUtils].
 *
 * The action optimizes for the "caret on identifier" case by running immediately and falls back to
 * a picker dialog for ambiguous selections. PSI work is wrapped in [ActionRunners.runSmartBackground]
 * to respect dumb mode and to surface progress to the user.
 */
class ExportImplementationsAtCaretAction : AnAction() {
    private val logger = Logger.getInstance(ExportImplementationsAtCaretAction::class.java)

    init {
        templatePresentation.text = "Implementations/Subclasses (at caret)"
        templatePresentation.description = "Export implementations/subclasses for the type at caret"
    }

    /**
     * Resolves the symbol under the caret (or via picker) and triggers an implementation search.
     *
     * @param e action event containing `CommonDataKeys.EDITOR` and `CommonDataKeys.PSI_FILE`
     */
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return

        val selectedFile = psiFile.virtualFile ?: return

        // Try to resolve a clean symbol selection at caret
        val cleanSymbol = resolveCleanSymbolAtCaret(psiFile, editor)
        logger.info("SCE[Caret]: file=${selectedFile.path} caret=${editor.caretModel.primaryCaret.offset} cleanSymbol=${cleanSymbol?.qualifiedName}")
        if (cleanSymbol != null) {
            runForSymbols(project, selectedFile, listOf(cleanSymbol), includeAnonymous = true, includeTest = true)
            return
        }

        // Otherwise, show a lightweight picker dialog
        val candidates = InheritanceFinder.collectClasses(psiFile)
        if (candidates.isEmpty()) {
            logger.info("SCE[Picker]: no classes/interfaces found in file; exporting only base file ${selectedFile.path}")
            NotificationUtils.showNotification(
                project,
                "Export Info",
                "No classes or interfaces found in this file. Exporting only the current file.",
                com.intellij.notification.NotificationType.INFORMATION
            )
            SmartExportUtils.exportFiles(project, arrayOf(selectedFile))
            return
        }

        val dlg = ImplementationsPickerDialog(project, psiFile, candidates)
        if (!dlg.showAndGet()) return

        val base = dlg.getSelectedBase() ?: run {
            logger.info("SCE[Picker]: no base selected; exporting only base file ${selectedFile.path}")
            SmartExportUtils.exportFiles(project, arrayOf(selectedFile))
            return
        }
        logger.info("SCE[Picker]: selected base=${base.qualifiedName ?: base.name} includeAnonymous=${dlg.includeAnonymous()} includeTests=${dlg.includeTests()}")
        runForSymbols(project, selectedFile, listOf(base), includeAnonymous = dlg.includeAnonymous(), includeTest = dlg.includeTests())
    }

    /**
     * Runs the expensive implementation search with a progress indicator, merges the resulting
     * files with the base file, and hands the list to [SmartExportUtils].
     *
     * @param project IntelliJ project needed for PSI access
     * @param selectedFile the file hosting the caret; always exported even if no implementations are found
     * @param bases symbols to search for (interfaces/classes)
     * @param includeAnonymous whether anonymous/object implementations are allowed
     * @param includeTest whether test sources can appear in the result
     */
    private fun runForSymbols(
        project: Project,
        selectedFile: VirtualFile,
        bases: List<PsiClass>,
        includeAnonymous: Boolean,
        includeTest: Boolean
    ) {
        ActionRunners.runSmartBackground(project, "Finding Implementations...") { indicator: ProgressIndicator ->
            try {
                indicator.isIndeterminate = false
                indicator.text = "Searching class hierarchy..."
                DebugTracer.start("CaretAction bases=${bases.map { it.qualifiedName ?: it.name }}")
                val implementations = runBlocking {
                    InheritanceFinder.findImplementationsFor(bases, project, includeAnonymous = includeAnonymous, includeTest = includeTest)
                }

                logger.info("SCE[Search]: bases=${bases.map { it.qualifiedName ?: it.name }} includeAnonymous=$includeAnonymous includeTest=$includeTest implCount=${implementations.size} impls=${implementations.map { it.path }}")
                DebugTracer.log("SCE[Search]: bases=${bases.map { it.qualifiedName ?: it.name }} includeAnonymous=$includeAnonymous includeTest=$includeTest implCount=${implementations.size} impls=${implementations.map { it.path }}")

                val allFiles = mutableSetOf<VirtualFile>()
                allFiles.add(selectedFile)
                allFiles.addAll(implementations)

                if (implementations.isEmpty()) {
                    logger.info("SCE[Search]: no implementations found; exporting only base file ${selectedFile.path}")
                    NotificationUtils.showNotification(
                        project,
                        "Export Info",
                        "No implementations found; exported only the current file.",
                        com.intellij.notification.NotificationType.INFORMATION
                    )
                }

                indicator.text = "Exporting ${allFiles.size} files..."
                logger.info("SCE[Export]: exporting ${allFiles.size} files: ${allFiles.map { it.path }}")
                DebugTracer.log("SCE[Export]: exporting ${allFiles.size} files: ${allFiles.map { it.path }}")
                SmartExportUtils.exportFiles(project, allFiles.toTypedArray())

                // Surface debug log to user (dialog) for easy copy/paste
                val logText = DebugTracer.dump()
                javax.swing.SwingUtilities.invokeLater {
                    DebugOutputDialog(project, logText).show()
                }
            } catch (ex: Exception) {
                logger.warn("SCE[Error]: implementations search failed: ${ex.message}", ex)
                val logText = DebugTracer.dump() + "\nSCE[Error]: ${ex.message}\n"
                javax.swing.SwingUtilities.invokeLater {
                    DebugOutputDialog(project, logText).show()
                }
                NotificationUtils.showNotification(
                    project,
                    "Export Error",
                    "Failed to find implementations: ${ex.message}",
                    com.intellij.notification.NotificationType.ERROR
                )
            }
            finally {
                DebugTracer.end()
            }
        }
    }

    /**
     * Attempts to resolve an unambiguous type at the caret by checking Kotlin/Java PSI and, as a
     * fallback, short-name search through [com.intellij.psi.search.PsiShortNamesCache].
     *
     * @return the resolved [PsiClass] or `null` when user input is ambiguous
     */
    private fun resolveCleanSymbolAtCaret(psiFile: PsiFile, editor: Editor): PsiClass? {
        return ReadAction.compute<PsiClass?, Exception> {
            val caret = editor.caretModel.primaryCaret
            val hasSelection = caret.hasSelection()
            val offset = caret.offset
            val elementAtOffset = psiFile.findElementAt(offset)

            // 1) Try Kotlin class/interface at caret name identifier
            val ktCandidate = findKotlinClassAtCaret(elementAtOffset)
            if (ktCandidate != null) {
                val nameId = getKotlinNameIdentifier(ktCandidate)
                if (nameId != null) {
                    val tr = nameId.textRange
                    val isClean = if (hasSelection) {
                        caret.selectionStart == tr.startOffset && caret.selectionEnd == tr.endOffset
                    } else {
                        tr.containsOffset(offset)
                    }
                    if (isClean) {
                        val light = toLightPsiClasses(ktCandidate).firstOrNull()
                        if (light is PsiClass) return@compute light
                    }
                }
            }

            // 2) Try Java PsiClass at caret name identifier
            val javaClass = PsiTreeUtil.getParentOfType(elementAtOffset, PsiClass::class.java, false)
            if (javaClass != null) {
                val nameId = javaClass.nameIdentifier
                if (nameId != null) {
                    val tr = nameId.textRange
                    val isClean = if (hasSelection) {
                        caret.selectionStart == tr.startOffset && caret.selectionEnd == tr.endOffset
                    } else {
                        tr.containsOffset(offset)
                    }
                    if (isClean) return@compute javaClass
                }
            }

            // 3) If selection or caret text looks like a TypeName, resolve by short name in project
            val project = psiFile.project
            val selectedText = if (hasSelection) editor.document.charsSequence.subSequence(caret.selectionStart, caret.selectionEnd).toString() else null
            val tokenText = elementAtOffset?.text

            val candidateName = when {
                !selectedText.isNullOrBlank() && selectedText.matches(Regex("[A-Z][A-Za-z0-9_]*")) -> selectedText
                tokenText != null && tokenText.matches(Regex("[A-Z][A-Za-z0-9_]*")) -> tokenText
                else -> null
            }

            if (candidateName != null) {
                try {
                    val cache = com.intellij.psi.search.PsiShortNamesCache.getInstance(project)
                    val classes = cache.getClassesByName(candidateName, com.intellij.psi.search.GlobalSearchScope.allScope(project))
                    if (classes.size == 1) {
                        return@compute classes.first()
                    }
                    // If multiple matches, treat as ambiguous and fall back to dialog; logging helps debug
                    if (classes.isNotEmpty()) {
                        logger.info("SCE[Caret]: short-name '$candidateName' resolved to ${classes.size} classes: ${classes.map { it.qualifiedName }}; falling back to picker")
                    } else {
                        logger.info("SCE[Caret]: short-name '$candidateName' resolved to 0 classes")
                    }
                } catch (t: Throwable) {
                    logger.warn("SCE[Caret]: short-name resolution failed for '$candidateName': ${t.message}", t)
                }
            }
            null
        }
    }

    // Kotlin helpers via reflection to avoid hard dependency when plugin is absent
    private fun findKotlinClassAtCaret(element: PsiElement?): Any? {
        if (element == null) return null
        return try {
            val ktClassOrObject = Class.forName("org.jetbrains.kotlin.psi.KtClassOrObject")
            var cur: PsiElement? = element
            while (cur != null) {
                if (ktClassOrObject.isInstance(cur)) return cur
                cur = cur.parent
            }
            null
        } catch (_: Throwable) {
            null
        }
    }

    private fun getKotlinNameIdentifier(ktClassOrObject: Any): PsiElement? {
        return try {
            val method = ktClassOrObject.javaClass.methods.firstOrNull { it.name == "getNameIdentifier" && it.parameterCount == 0 }
            method?.invoke(ktClassOrObject) as? PsiElement
        } catch (_: Throwable) {
            null
        }
    }

    private fun toLightPsiClasses(ktClassOrObject: Any): List<PsiElement> {
        return try {
            val utils = Class.forName("org.jetbrains.kotlin.asJava.LightClassUtilsKt")
            val method = utils.methods.firstOrNull { it.name == "toLightClasses" && it.parameterTypes.size == 1 }
            val res = method?.invoke(null, ktClassOrObject)
            when (res) {
                is List<*> -> res.filterIsInstance<PsiElement>()
                is Array<*> -> res.filterIsInstance<PsiElement>()
                else -> emptyList()
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    /**
     * Enables the action only when an editor/PSI file is available and the current file extension
     * is JVM-friendly.
     */
    override fun update(e: AnActionEvent) {
        val project = e.project
        val editor = e.getData(CommonDataKeys.EDITOR)
        val psiFile = e.getData(CommonDataKeys.PSI_FILE)
        e.presentation.isEnabledAndVisible = project != null && editor != null && psiFile != null && isJvmFile(psiFile.virtualFile)
    }

    private fun isJvmFile(file: VirtualFile?): Boolean {
        val ext = file?.extension?.lowercase() ?: return false
        return ext in setOf("java", "kt", "kts")
    }

    /** Resolving editor/PSI context can be slow, so `update` is run on BGT. */
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

/**
 * Lightweight dialog that surfaces `DebugTracer` output to users after complex exports. Allows
 * users to copy diagnostic data when reporting issues.
 */
class DebugOutputDialog(project: Project, private val text: String) : DialogWrapper(project) {
    init {
        title = "Implementations Debug Output"
        setSize(720, 420)
        init()
    }
    override fun createCenterPanel(): JComponent {
        val ta = JTextArea(text)
        ta.isEditable = false
        ta.font = javax.swing.UIManager.getFont("MonoSpaced.font")
        return JScrollPane(ta)
    }
    override fun createActions(): Array<Action> {
        val copy = object : AbstractAction("Copy") {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                val sel = java.awt.datatransfer.StringSelection(text)
                com.intellij.openapi.ide.CopyPasteManager.getInstance().setContents(sel)
            }
        }
        return arrayOf(copy, cancelAction, okAction)
    }
}

private class ImplementationsPickerDialog(
    project: Project,
    private val psiFile: PsiFile,
    private val candidates: List<PsiClass>
) : DialogWrapper(project) {

    private val list = JBList(candidates.map { it.qualifiedName ?: it.name ?: "<anonymous>" })
    private val includeTestsCheckbox = JCheckBox("Include test sources", true)
    private val includeAnonymousCheckbox = JCheckBox("Include anonymous/objects", true)

    init {
        title = "Select Base Type"
        setSize(500, 300)
        init()
        list.selectionMode = ListSelectionModel.SINGLE_SELECTION
        if (candidates.isNotEmpty()) {
            list.selectedIndex = 0
        }
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.add(JLabel("Choose class/interface to find implementations for:"))
        val scroll = JScrollPane(list)
        scroll.preferredSize = java.awt.Dimension(480, 180)
        panel.add(scroll)
        panel.add(Box.createVerticalStrut(8))
        panel.add(includeTestsCheckbox)
        panel.add(includeAnonymousCheckbox)
        return panel
    }

    fun getSelectedBase(): PsiClass? {
        val idx = list.selectedIndex
        return if (idx in candidates.indices) candidates[idx] else null
    }

    fun includeTests(): Boolean = includeTestsCheckbox.isSelected
    fun includeAnonymous(): Boolean = includeAnonymousCheckbox.isSelected
}
