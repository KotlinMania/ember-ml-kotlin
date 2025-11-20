package ai.solace.klang.bitwise

import ai.solace.klang.mem.GlobalHeap
import ai.solace.klang.mem.KMalloc
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ArrayBitShiftsHeapTest {
    
    @BeforeTest
    fun setup() {
        // Reinitialize heap for each test to ensure clean state
        KMalloc.init(1 shl 18)
    }
    
    @Test
    fun testByteComposition() {
        // Test that byte composition/decomposition works correctly
        val base = KMalloc.malloc(4)
        val eng16 = BitShiftEngine(BitShiftMode.ARITHMETIC, 16)
        val arith8 = ArithmeticBitwiseOps(8)
        val arith16 = ArithmeticBitwiseOps(16)
        
        // Write 0x1234 as little-endian bytes
        GlobalHeap.sb(base, 0x34.toByte())
        val high1 = eng16.byteShiftRight(0x1234L, 1)
        GlobalHeap.sb(base + 1, arith8.and(high1.value, 0xFF).toInt().toByte())
        
        // Read back
        val low = GlobalHeap.lbu(base)
        val high = GlobalHeap.lbu(base + 1)
        val composed = eng16.byteShiftLeft(high.toLong(), 1)
        val result = arith16.and(arith16.or(low.toLong(), composed.value), 0xFFFF)
        
        assertEquals(0x1234L, result, "Byte composition failed")
    }
    
    @Test
    fun shl16HeapMatchesIntArray() {
        val len = 32
        val arith16 = ArithmeticBitwiseOps(16)
        val arr = IntArray(len) { arith16.and((it * 17).toLong(), 0xFFFF).toInt() }
        val base = KMalloc.malloc(len * 2 + 2)
        val eng16 = BitShiftEngine(BitShiftMode.ARITHMETIC, 16)
        val arith8 = ArithmeticBitwiseOps(8)
        
        // write arr to heap using BitShiftEngine
        for (i in 0 until len) {
            val v = arr[i]
            GlobalHeap.sb(base + i * 2, arith8.and(v.toLong(), 0xFF).toInt().toByte())
            val highByte = eng16.byteShiftRight(v.toLong(), 1)
            GlobalHeap.sb(base + i * 2 + 1, arith8.and(highByte.value, 0xFF).toInt().toByte())
        }
        
        // Verify writes
        for (i in 0 until 3) {
            val low = GlobalHeap.lbu(base + i * 2)
            val high = GlobalHeap.lbu(base + i * 2 + 1)
            val composed = eng16.byteShiftLeft(high.toLong(), 1)
            val readBack = arith16.and(arith16.or(low.toLong(), composed.value), 0xFFFF)
            assertEquals(arr[i].toLong(), readBack, "Write verification failed at $i")
        }

        val s = 9
        val r1 = ArrayBitShifts.shl16LEInPlace(arr, 0, len, s, 0)
        val r2 = ArrayBitShifts.shl16LEInPlace(base, 0, len, s, 0)
        assertEquals(
            arith16.and(r1.carryOut.toLong(), 0xFFFF), 
            arith16.and(r2.carryOut.toLong(), 0xFFFF), 
            "Carry mismatch"
        )
        
        // read heap back using BitShiftEngine
        val back = IntArray(len)
        for (i in 0 until len) {
            val low = GlobalHeap.lbu(base + i * 2)
            val high = GlobalHeap.lbu(base + i * 2 + 1)
            val composed = eng16.byteShiftLeft(high.toLong(), 1)
            back[i] = arith16.and(arith16.or(low.toLong(), composed.value), 0xFFFF).toInt()
        }
        for (i in 0 until len) {
            assertEquals(
                arith16.and(arr[i].toLong(), 0xFFFF), 
                arith16.and(back[i].toLong(), 0xFFFF), 
                "Value mismatch at index $i"
            )
        }
    }
}

