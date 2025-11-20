package ai.solace.klang.mem

import kotlin.test.Test
import kotlin.test.assertEquals

class CScalarsTest {
    @Test
    fun autoIntAndDoubleInPlace() {
        KMalloc.init(1 shl 18)
        KStack.init(1 shl 16)
        KStack.withFrame {
            val xi = CAutos.int(41)
            val xd = CAutos.double(2.5)
            xi.addAssign(1)
            xd.value = xd.value * 4.0
            assertEquals(42, xi.value)
            assertEquals(10.0, xd.value)
            // Verify the underlying bytes are updated in place
            assertEquals(42, GlobalHeap.lw(xi.addr))
            assertEquals(10.0, GlobalHeap.ldf(xd.addr))
        }
    }

    @Test
    fun globalIntAndDouble() {
        KMalloc.init(1 shl 18)
        GlobalData.init()
        val gi = CGlobals.int("g_counter", 7)
        val gd = CGlobals.double("g_pi", 3.5)
        gi.addAssign(5)
        gd.value = gd.value + 0.25
        assertEquals(12, GlobalHeap.lw(gi.addr))
        assertEquals(3.75, GlobalHeap.ldf(gd.addr))
    }
}

