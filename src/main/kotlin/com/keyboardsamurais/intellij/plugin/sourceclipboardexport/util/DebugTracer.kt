package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util

/**
 * Simple thread-local debug trace collector to surface internal steps to users.
 * Use DebugTracer.start() before work, DebugTracer.log() during, and DebugTracer.dump() to retrieve.
 */
object DebugTracer {
    private val local = ThreadLocal<StringBuilder?>()

    fun start(header: String? = null) {
        val sb = StringBuilder()
        if (!header.isNullOrBlank()) {
            sb.append("SCE[Start]: ").append(header).append('\n')
        }
        local.set(sb)
    }

    fun log(msg: String) {
        local.get()?.append(msg)?.append('\n')
    }

    fun dump(): String {
        return local.get()?.toString() ?: ""
    }

    fun end() {
        local.remove()
    }
}

