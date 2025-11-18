package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.concurrent.thread

class DebugTracerTest {

    @Test
    fun `debug tracer stores logs per thread`() {
        DebugTracer.start("main")
        DebugTracer.log("line1")
        val mainDump = DebugTracer.dump()

        var backgroundDump = ""
        val t = thread {
            DebugTracer.start("worker")
            DebugTracer.log("worker-line")
            backgroundDump = DebugTracer.dump()
            DebugTracer.end()
        }
        t.join()

        DebugTracer.end()

        assertEquals(true, mainDump.contains("SCE[Start]: main"))
        assertEquals(true, mainDump.contains("line1"))
        assertEquals(true, backgroundDump.contains("worker-line"))
    }
}
