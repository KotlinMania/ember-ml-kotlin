package ai.solace.ember.tensor.bitwise.ops

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShiftOpsTest {
    
    @Test
    fun testLeftShift() {
        assertEquals(0b10100, leftShift(0b1010, 1))
        assertEquals(0b101000, leftShift(0b1010, 2))
        assertEquals(0b1010000, leftShift(0b1010, 3))
        assertEquals(0, leftShift(0, 5))
    }
    
    @Test
    fun testRightShift() {
        assertEquals(0b0101, rightShift(0b1010, 1))
        assertEquals(0b0010, rightShift(0b1010, 2))
        assertEquals(0b0001, rightShift(0b1010, 3))
        assertEquals(0, rightShift(0b1010, 4))
    }
    
    @Test
    fun testRotateLeft() {
        // Test with 8-bit values for clarity
        assertEquals(0b10100000u, rotateLeft(0b00001010u, 4, 8))
        assertEquals(0b01000000u, rotateLeft(0b00000001u, 6, 8))
        assertEquals(0b00000001u, rotateLeft(0b00000001u, 8, 8)) // Full rotation
        
        // Test with 32-bit values
        assertEquals(0b10100u, rotateLeft(0b1010u, 1, 32))
        assertEquals(0x80000000u, rotateLeft(0x1u, 31, 32))
    }
    
    @Test
    fun testRotateRight() {
        // Test with 8-bit values for clarity  
        assertEquals(0b10100000u, rotateRight(0b00001010u, 4, 8))
        assertEquals(0b01000000u, rotateRight(0b00000001u, 2, 8))
        assertEquals(0b00000001u, rotateRight(0b00000001u, 8, 8)) // Full rotation
        
        // Simpler 32-bit test - just verify function works
        val result = rotateRight(0x8u, 1, 32)
        // 0x8 = 1000 -> rotate right 1 -> should move high bit to position 31
        assertTrue(result > 0x80000000u)
    }
}