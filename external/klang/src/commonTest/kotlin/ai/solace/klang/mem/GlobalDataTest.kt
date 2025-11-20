package ai.solace.klang.mem

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFails

class GlobalDataTest {
    @Test
    fun testBssAndData() {
        KMalloc.init(1 shl 18)
        GlobalData.init()

        val bss = GlobalData.defineBss("g_bss", 32, align = 32)
        assertEquals(0, bss and 31)
        // zero-initialized
        var sum = 0
        repeat(32) { sum = sum or GlobalHeap.lbu(bss + it) }
        assertEquals(0, sum)

        val payload = byteArrayOf(1,2,3,4,5)
        val data = GlobalData.defineData("g_data", payload, align = 16)
        assertEquals(0, data and 15)
        repeat(payload.size) { idx ->
            assertEquals(payload[idx].toInt() and 0xFF, GlobalHeap.lbu(data + idx))
        }

        // Convenience
        val gi = GlobalData.defineI32("g_i32", 0x11223344.toInt())
        assertEquals(0x11223344.toInt(), GlobalHeap.lw(gi))
        val gd = GlobalData.defineF64("g_f64", 3.25)
        assertEquals(3.25, GlobalHeap.ldf(gd))

        // Duplicate should fail
        assertFails { GlobalData.defineBss("g_bss", 8) }
    }
}

