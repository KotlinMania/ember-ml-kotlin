package ai.solace.klang.mem

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KStackTest {
    @Test
    fun testPushPopAllocAlign() {
        KMalloc.init(1 shl 18)
        KStack.init(1 shl 16)

        val beforeUsed = KStack.usedBytes()
        val mark = KStack.pushFrame()

        val p8 = KStack.alloca(8, 8)
        assertEquals(0, p8 and 0x7, "8-byte alignment")
        GlobalHeap.sd(p8, 0x1122334455667788L)
        assertEquals(0x1122334455667788L, GlobalHeap.ld(p8))

        val p4 = KStack.alloca(4, 4)
        assertEquals(0, p4 and 0x3, "4-byte alignment")
        GlobalHeap.sw(p4, 0xCAFEBABE.toInt())
        assertEquals(0xCAFEBABE.toInt(), GlobalHeap.lw(p4))

        KStack.popFrame(mark)
        assertEquals(beforeUsed, KStack.usedBytes(), "stack usage restored after pop")
    }

    @Test
    fun testNestedFrames() {
        KMalloc.reset(); KStack.reset()
        val m1 = KStack.pushFrame()
        val a1 = KStack.alloca(16, 16)
        val m2 = KStack.pushFrame()
        val a2 = KStack.alloca(32, 16)
        assertTrue(a2 != a1)
        KStack.popFrame(m2)
        val a3 = KStack.alloca(16, 16)
        // After popping m2, allocating again should reuse space near a2
        assertEquals(0, a3 and 0xF)
        KStack.popFrame(m1)
    }
}

