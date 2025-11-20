package ai.solace.klang.mem

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KMallocTest {
    @Test
    fun callocZeroAndWrite() {
        KMalloc.init(1 shl 18)
        val n = 257
        val p = KMalloc.calloc(n, 1)
        var z = 0
        repeat(n) { z = z or GlobalHeap.lbu(p + it) }
        assertEquals(0, z, "calloc must zero initialize")
        // write pattern and read back
        repeat(n) { GlobalHeap.sb(p + it, (it and 0xFF).toByte()) }
        repeat(n) { assertEquals(it and 0xFF, GlobalHeap.lbu(p + it)) }
        KMalloc.free(p)
    }

    @Test
    fun reallocGrowPreservesPrefix() {
        KMalloc.reset()
        val p = KMalloc.malloc(64)
        repeat(64) { GlobalHeap.sb(p + it, (0xA0 + (it and 0x0F)).toByte()) }
        val q = KMalloc.realloc(p, 192)
        repeat(64) { assertEquals(0xA0 + (it and 0x0F), GlobalHeap.lbu(q + it)) }
        KMalloc.free(q)
    }

    @Test
    fun reallocShrinkKeepsAddress() {
        KMalloc.reset()
        val p = KMalloc.malloc(256)
        repeat(256) { GlobalHeap.sb(p + it, 7) }
        val q = KMalloc.realloc(p, 64)
        assertEquals(p, q, "realloc shrink may keep same address")
        // still readable prefix
        repeat(64) { assertEquals(7, GlobalHeap.lbu(q + it)) }
        KMalloc.free(q)
    }
}

