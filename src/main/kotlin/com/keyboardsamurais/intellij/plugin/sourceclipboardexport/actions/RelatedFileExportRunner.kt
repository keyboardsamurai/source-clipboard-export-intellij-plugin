package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.actions

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.ActionRunners

/**
 * Shared orchestration for actions that collect additional related files before delegating to the exporter.
 * Keeps progress handling and iteration logic consistent across different action implementations.
 */
object RelatedFileExportRunner {
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
