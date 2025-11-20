package ai.solace.klang.mem

import kotlin.test.Test
import kotlin.test.assertEquals

class KAlignedTest {
    @Test
    fun alignedAllocAndFree() {
        KMalloc.init(1 shl 18)
        val p = KAligned.alignedAlloc(64, 256)
        assertEquals(0, p and 63, "aligned to 64")
        // Write across payload
        repeat(256) { GlobalHeap.sb(p + it, 0x5A) }
        KAligned.alignedFree(p)
    }

    @Test
    fun posixMemalignSemantics() {
        KMalloc.reset()
        val bad1 = KAligned.posixMemalign(3, 16)
        assertEquals(22, bad1.err)
        val bad2 = KAligned.posixMemalign(32, 16) // not multiple of PTR_SIZE(8)? 32 is ok; use 12 instead
        // 32 is multiple of 8, so ok. Use 12 to trigger EINVAL
        val bad3 = KAligned.posixMemalign(12, 16)
        assertEquals(22, bad3.err)

        val ok = KAligned.posixMemalign(128, 64)
        assertEquals(0, ok.err)
        assertEquals(0, ok.ptr and 127)
        KAligned.alignedFree(ok.ptr)
    }
}
