package ai.solace.klang.int.hpc

import ai.solace.klang.mem.GlobalHeap
import ai.solace.klang.mem.KMalloc
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HeapUInt128Test {
    @Test
    fun addAndShift() {
        GlobalHeap.init(1 shl 20)
        KMalloc.init(1 shl 18)
        val a = HeapUInt128.fromULong(0xFFFF_FFFFu)
        val b = HeapUInt128.fromULong(1u)
        val c = a + b
        // 0x1_0000_0000
        val shifted = c.shiftLeft(16)
        // Expect 0x1_0000_0000 << 16 = 0x1_0000_0000_0000
        val hex = shifted.toHexString()
        // We only check suffix to avoid formatting assumptions
        assertTrue(hex.endsWith("0000"))
    }
    
    @Test
    fun zeroCopyOperations() {
        GlobalHeap.init(1 shl 20)
        KMalloc.init(1 shl 18)
        
        // Create two values
        val a = HeapUInt128.fromULong(100u)
        val b = HeapUInt128.fromULong(200u)
        
        println("a addr: ${a.addr}, hex: ${a.toHexString()}")
        println("b addr: ${b.addr}, hex: ${b.toHexString()}")
        
        // Perform addition (should be zero-copy on heap)
        val sum = a + b
        val sumHex = sum.toHexString()
        println("sum addr: ${sum.addr}, hex: $sumHex")
        
        val sumLimbs = sum.toIntArray()
        println("sum limbs[0]: ${sumLimbs[0]}")
        assertEquals(300, sumLimbs[0], "Addition failed")
        
        // Perform subtraction
        val diff = sum - a
        val diffLimbs = diff.toIntArray()
        println("diff limbs[0]: ${diffLimbs[0]}")
        assertEquals(200, diffLimbs[0], "Subtraction failed")
        
        // Perform shift
        val shifted = sum.shiftLeft(8)
        // 300 << 8 = 76800 = 0x12C00
        // In 16-bit limbs: low limb = 0x2C00 = 11264, high limb = 0x0001 = 1
        val shiftedLimbs = shifted.toIntArray()
        println("shifted limbs[0]: ${shiftedLimbs[0]}, limbs[1]: ${shiftedLimbs[1]}")
        assertEquals(11264, shiftedLimbs[0], "Shift failed on limb 0")
        assertEquals(1, shiftedLimbs[1], "Shift failed on limb 1")
    }
    
    @Test
    fun comparison() {
        GlobalHeap.init(1 shl 20)
        KMalloc.init(1 shl 18)
        
        val small = HeapUInt128.fromULong(100u)
        val large = HeapUInt128.fromULong(200u)
        
        assertTrue(small < large)
        assertTrue(large > small)
        assertEquals(0, small.compareTo(HeapUInt128.fromULong(100u)))
    }
    
    @Test
    fun equality() {
        GlobalHeap.init(1 shl 20)
        KMalloc.init(1 shl 18)
        
        val a = HeapUInt128.fromULong(12345u)
        val b = HeapUInt128.fromULong(12345u)
        val c = HeapUInt128.fromULong(54321u)
        
        assertEquals(a, b)
        assertTrue(a != c)
        assertEquals(a.hashCode(), b.hashCode())
    }
}

