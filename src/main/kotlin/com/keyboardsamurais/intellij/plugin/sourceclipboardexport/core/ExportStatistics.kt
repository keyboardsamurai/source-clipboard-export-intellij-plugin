package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core

import java.util.*
import java.util.concurrent.atomic.AtomicInteger

/** Thread-safe container for export statistics. */
class ExportStatistics {
    val fileCount = AtomicInteger(0) // Total files considered/processed (used for limit)
    val processedFileCount = AtomicInteger(0)
    val excludedByFilterCount = AtomicInteger(0)
    val excludedBySizeCount = AtomicInteger(0)
    val excludedByBinaryContentCount = AtomicInteger(0)
    val excludedByIgnoredNameCount = AtomicInteger(0)
    val excludedByGitignoreCount = AtomicInteger(0)
    val excludedExtensions: MutableSet<String> = Collections.synchronizedSet(mutableSetOf<String>())
}
