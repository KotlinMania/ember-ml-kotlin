package ai.solace.klang.bitwise

import kotlin.test.Test
import kotlin.test.assertEquals

class BitShiftEngineParityTest {
    @Test
    fun leftShift16ParityOnPatterns() {
        val native = BitShiftEngine(BitShiftMode.NATIVE, 16)
        val arith = BitShiftEngine(BitShiftMode.ARITHMETIC, 16)
        val patterns = longArrayOf(0L, 1L, 0x7FFFL, 0x8000L, 0xFFFFL, 0x5555L, 0xAAAAL)
        for (v in patterns) {
            for (s in 0 until 16) {
                val n = native.leftShift(v, s)
                val a = arith.leftShift(v, s)
                assertEquals(a.value and 0xFFFF, n.value and 0xFFFF, "value v=$v s=$s")
                // carry parity across implementations will be validated in ArrayBitShifts tests
            }
        }
    }
}
