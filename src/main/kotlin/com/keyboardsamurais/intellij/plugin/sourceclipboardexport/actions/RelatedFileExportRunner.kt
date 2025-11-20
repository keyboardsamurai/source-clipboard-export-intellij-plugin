package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.ActionRunners

/**
 * Shared orchestration for actions that collect additional related files before delegating to the exporter.
 * Keeps progress handling and iteration logic consistent across different action implementations.
 */
object RelatedFileExportRunner {
    /**
     * Runs a collector block for each selected file while reporting progress, then invokes the
     * provided callback with both the original and aggregated related files. This keeps all related
     * actions (tests, resources, dependencies, etc.) consistent.
     *
     * Example:
     * ```
     * RelatedFileExportRunner.run(project, files, "Finding tests") { proj, file ->
     *     RelatedFileFinder.findTestFiles(proj, file)
     * } { originals, extras ->
     *     SmartExportUtils.exportFiles(project, (originals + extras).toTypedArray())
     * }
     * ```
     *
     * @param project project context; used for progress manager and PSI access
     * @param selectedFiles files chosen by the user
     * @param progressTitle message shown in the progress dialog
     * @param indicatorMessage lambda that produces per-file progress text
     * @param collector callback that returns additional files for a given input file
     * @param onComplete invoked exactly once after collection finishes, even if no extra files were found
     */
    fun run(
        project: Project,
        selectedFiles: Array<VirtualFile>,
        progressTitle: String,
        indicatorMessage: (file: VirtualFile, index: Int, total: Int) -> String = { file, index, total ->
            "Analyzing ${file.name} (${index + 1}/$total)"
        },
        collector: (project: Project, file: VirtualFile) -> Collection<VirtualFile>,
        onComplete: (selectedFiles: Array<VirtualFile>, additionalFiles: Set<VirtualFile>) -> Unit
    ) {
        ActionRunners.runSmartBackground(project, progressTitle) { indicator ->
            val additionalFiles = mutableSetOf<VirtualFile>()
            selectedFiles.forEachIndexed { idx, file ->
                indicator.fraction = (idx.toDouble() / selectedFiles.size).coerceIn(0.0, 1.0)
                indicator.text = indicatorMessage(file, idx, selectedFiles.size)
                val found = collector(project, file)
                additionalFiles.addAll(found)
            }

            onComplete(selectedFiles, additionalFiles)
        }
    }
}
