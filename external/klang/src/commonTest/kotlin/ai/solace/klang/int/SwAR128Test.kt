package ai.solace.klang.int

import ai.solace.klang.int.hpc.HeapUInt128
import ai.solace.klang.mem.GlobalHeap
import ai.solace.klang.mem.KMalloc
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for SwAR128 heap-based operations.
 * 
 * Note: Legacy array-based UInt128 operations have been removed.
 * Use HeapUInt128 for zero-copy 128-bit arithmetic.
 */
class SwAR128Test {
    private fun setup() {
        GlobalHeap.init(1 shl 20)  // 1MB
        KMalloc.init(1 shl 18)      // 256KB
    }
    
    @Test
    fun heapAddOperation() {
        setup()
        
        // Allocate three 128-bit values in heap
        val aAddr = KMalloc.malloc(16)
        val bAddr = KMalloc.malloc(16)
        val resultAddr = KMalloc.malloc(16)
        
        // Write 100 and 200 to heap
        GlobalHeap.sw(aAddr, 100)
        GlobalHeap.sw(bAddr, 200)
        
        // Perform heap addition
        val carry = SwAR128.addHeap(aAddr, bAddr, resultAddr)
        
        assertEquals(0, carry, "No carry expected")
        
        // Read result (should be 300)
        val resultLow = GlobalHeap.lw(resultAddr)
        assertEquals(300, resultLow, "Heap add result should be 300")
        
        // Clean up
        KMalloc.free(aAddr)
        KMalloc.free(bAddr)
        KMalloc.free(resultAddr)
    }
    
    @Test
    fun heapSubOperation() {
        setup()
        
        val aAddr = KMalloc.malloc(16)
        val bAddr = KMalloc.malloc(16)
        val resultAddr = KMalloc.malloc(16)
        
        // Write 300 and 100
        GlobalHeap.sw(aAddr, 300)
        GlobalHeap.sw(bAddr, 100)
        
        val borrow = SwAR128.subHeap(aAddr, bAddr, resultAddr)
        
        assertEquals(0, borrow, "No borrow expected")
        
        val resultLow = GlobalHeap.lw(resultAddr)
        assertEquals(200, resultLow, "Heap sub result should be 200")
        
        KMalloc.free(aAddr)
        KMalloc.free(bAddr)
        KMalloc.free(resultAddr)
    }
    
    @Test
    fun heapCompareOperation() {
        setup()
        
        val aAddr = KMalloc.malloc(16)
        val bAddr = KMalloc.malloc(16)
        
        // Write different values
        GlobalHeap.sw(aAddr, 100)
        GlobalHeap.sw(bAddr, 200)
        
        val cmp = SwAR128.compareHeap(aAddr, bAddr)
        
        assertTrue(cmp < 0, "100 should be less than 200")
        
        // Test equality
        GlobalHeap.sw(bAddr, 100)
        val cmpEqual = SwAR128.compareHeap(aAddr, bAddr)
        assertEquals(0, cmpEqual, "Equal values should compare as 0")
        
        KMalloc.free(aAddr)
        KMalloc.free(bAddr)
    }
    
    @Test
    fun heapShiftOperations() {
        setup()
        
        val srcAddr = KMalloc.malloc(16)
        val destAddr = KMalloc.malloc(16)
        
        // Write value 1 to source
        SwAR128.zeroHeap(srcAddr)
        GlobalHeap.sb(srcAddr, 1)
        
        // Shift left by 8 bits
        val spillLeft = SwAR128.shiftLeftHeap(srcAddr, destAddr, 8)
        
        assertEquals(0uL, spillLeft, "No spill expected")
        
        val resultLeft = GlobalHeap.lw(destAddr)
        assertEquals(256, resultLeft, "1 << 8 should be 256")
        
        // Shift right by 4 bits
        val spillRight = SwAR128.shiftRightHeap(destAddr, srcAddr, 4)
        
        val resultRight = GlobalHeap.lw(srcAddr)
        assertEquals(16, resultRight, "256 >> 4 should be 16")
        
        KMalloc.free(srcAddr)
        KMalloc.free(destAddr)
    }
    
    @Test
    fun heapZeroOperation() {
        setup()
        
        val addr = KMalloc.malloc(16)
        
        // Write garbage
        GlobalHeap.sw(addr, 0xDEADBEEF.toInt())
        
        // Zero it
        SwAR128.zeroHeap(addr)
        
        // Verify all bytes are zero
        for (i in 0 until 16) {
            assertEquals(0, GlobalHeap.lbu(addr + i), "Byte $i should be zero")
        }
        
        KMalloc.free(addr)
    }
    
    @Test
    fun heapWriteULongOperation() {
        setup()
        
        val addr = KMalloc.malloc(16)
        
        // Write ULong value
        SwAR128.writeULongToHeap(addr, 0x123456789ABCDEFuL)
        
        // Read back and verify
        val low = GlobalHeap.lw(addr)
        val high = GlobalHeap.lw(addr + 4)
        
        // Low 32 bits
        assertEquals(0x89ABCDEF.toInt(), low, "Low word should match")
        // Next 32 bits
        assertEquals(0x01234567, high, "High word should match")
        
        KMalloc.free(addr)
    }
    
    @Test
    fun heapToBigEndianHex() {
        setup()
        
        val addr = KMalloc.malloc(16)
        
        // Write specific pattern
        SwAR128.writeULongToHeap(addr, 0xFEDCBA9876543210uL)
        
        // Convert to hex
        val hex = SwAR128.toBigEndianHexHeap(addr)
        
        assertEquals("fedcba9876543210", hex, "Hex conversion failed")
        
        KMalloc.free(addr)
    }
    
    @Test
    fun arrayOperationsAddInto() {
        setup()
        
        val a = intArrayOf(100, 0, 0, 0, 0, 0, 0, 0)
        val b = intArrayOf(200, 0, 0, 0, 0, 0, 0, 0)
        val dest = IntArray(8)
        
        val carry = SwAR128.addInto(a, b, dest)
        
        assertEquals(0, carry, "No carry expected")
        assertEquals(300, dest[0], "addInto result should be 300")
    }
    
    @Test
    fun arrayOperationsSubInto() {
        setup()
        
        val a = intArrayOf(300, 0, 0, 0, 0, 0, 0, 0)
        val b = intArrayOf(100, 0, 0, 0, 0, 0, 0, 0)
        val dest = IntArray(8)
        
        val borrow = SwAR128.subInto(a, b, dest)
        
        assertEquals(0, borrow, "No borrow expected")
        assertEquals(200, dest[0], "subInto result should be 200")
    }
    
    @Test
    fun multiplyBySmall() {
        setup()
        
        val limbs = intArrayOf(10, 0, 0, 0, 0, 0, 0, 0)
        val dest = IntArray(8)
        
        val carry = SwAR128.multiplyBySmall(limbs, 3, dest)
        
        assertEquals(0, carry, "No carry expected")
        assertEquals(30, dest[0], "10 * 3 should be 30")
    }
    
    @Test
    fun addSmallValue() {
        setup()
        
        val limbs = intArrayOf(100, 0, 0, 0, 0, 0, 0, 0)
        val dest = IntArray(8)
        
        val carry = SwAR128.addSmall(limbs, 50, dest)
        
        assertEquals(0, carry, "No carry expected")
        assertEquals(150, dest[0], "100 + 50 should be 150")
    }
}
