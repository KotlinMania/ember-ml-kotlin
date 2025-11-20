package ai.solace.klang.bitwise

import ai.solace.klang.mem.GlobalHeap
import ai.solace.klang.mem.KMalloc
import kotlin.random.Random
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ArrayBitShiftsRightShiftHeapTest {
    
    @BeforeTest
    fun setup() {
        // Reinitialize heap for each test to ensure clean state
        KMalloc.init(1 shl 18)
    }
    
    @Test
    fun rsh16HeapMatchesIntArrayRandomized() {
        val rnd = Random(0xC0FFEE)
        val eng16 = BitShiftEngine(BitShiftMode.ARITHMETIC, 16)
        val arith16 = ArithmeticBitwiseOps(16)
        
        repeat(10) {
            val len = 16 + rnd.nextInt(16)
            val a = IntArray(len) { rnd.nextInt() and 0xFFFF }
            val base = KMalloc.malloc(len * 2)
            
            // write arr to heap using BitShiftEngine
            for (i in 0 until len) {
                val v = a[i]
                GlobalHeap.sb(base + i * 2, (v and 0xFF).toByte())
                val highByte = eng16.byteShiftRight(v.toLong(), 1)
                GlobalHeap.sb(base + i * 2 + 1, (highByte.value.toInt() and 0xFF).toByte())
            }
            
            val s = 1 + rnd.nextInt(15)
            val r1 = ArrayBitShifts.rsh16LEInPlace(a, 0, len, s)
            val r2 = ArrayBitShifts.rsh16LEInPlace(base, 0, len, s)
            assertEquals(r1.carryOut and 0xFFFF, r2.carryOut and 0xFFFF, "Carry mismatch at iteration $it")
            
            // Compare arrays using BitShiftEngine to read
            for (i in 0 until len) {
                val low = GlobalHeap.lbu(base + i * 2)
                val high = GlobalHeap.lbu(base + i * 2 + 1)
                val composed = eng16.byteShiftLeft(high.toLong(), 1)
                val v = arith16.and(arith16.or(low.toLong(), composed.value), 0xFFFF).toInt()
                assertEquals(a[i] and 0xFFFF, v, "Value mismatch at iteration $it, index $i")
            }
        }
    }
}

