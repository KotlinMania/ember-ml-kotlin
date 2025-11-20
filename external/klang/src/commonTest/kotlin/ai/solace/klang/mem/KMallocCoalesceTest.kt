package ai.solace.klang.mem

import kotlin.test.Test
import kotlin.test.assertEquals

class KMallocCoalesceTest {
    @Test
    fun coalesceAdjacentOnFree() {
        KMalloc.init(1 shl 18)
        val a = KMalloc.malloc(128)
        val b = KMalloc.malloc(128)
        // Fill to detect reuse
        repeat(128) { GlobalHeap.sb(a + it, 0x11) }
        repeat(128) { GlobalHeap.sb(b + it, 0x22) }
        // Free middle then previous, expecting coalesced free chunk at 'a'
        KMalloc.free(b)
        KMalloc.free(a)
        // Now allocate something larger than 128 but smaller than combined a+b (+overhead)
        val c = KMalloc.malloc(200)
        // Write across the new block to ensure it is valid and contiguous
        repeat(200) { GlobalHeap.sb(c + it, 0x33) }
        KMalloc.free(c)
    }
}
