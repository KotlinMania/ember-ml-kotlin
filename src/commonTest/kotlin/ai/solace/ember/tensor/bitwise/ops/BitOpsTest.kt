package ai.solace.ember.tensor.bitwise.ops

import kotlin.test.Test
import kotlin.test.assertEquals

class BitOpsTest {
    
    @Test
    fun testCountOnes() {
        assertEquals(0, countOnes(0))
        assertEquals(1, countOnes(1))
        assertEquals(4, countOnes(0b1111))
        assertEquals(3, countOnes(0b1011))
        assertEquals(32, countOnes(-1)) // All bits set
    }
    
    @Test
    fun testCountZeros() {
        assertEquals(32, countZeros(0))
        assertEquals(31, countZeros(1))
        assertEquals(28, countZeros(0b1111))
        assertEquals(29, countZeros(0b1011))
        assertEquals(0, countZeros(-1)) // All bits set
    }
    
    @Test
    fun testGetBit() {
        assertEquals(0, getBit(0b1010, 0)) // LSB
        assertEquals(1, getBit(0b1010, 1))
        assertEquals(0, getBit(0b1010, 2))
        assertEquals(1, getBit(0b1010, 3))
        assertEquals(0, getBit(0b1010, 4))
    }
    
    @Test
    fun testSetBit() {
        assertEquals(0b1011, setBit(0b1010, 0, 1)) // Set LSB
        assertEquals(0b1010, setBit(0b1010, 0, 0)) // Clear LSB (already 0)
        assertEquals(0b1110, setBit(0b1010, 2, 1)) // Set bit 2
        assertEquals(0b0010, setBit(0b1010, 3, 0)) // Clear bit 3
    }
    
    @Test
    fun testToggleBit() {
        assertEquals(0b1011, toggleBit(0b1010, 0)) // Toggle LSB
        assertEquals(0b1110, toggleBit(0b1010, 2)) // Toggle bit 2
        assertEquals(0b0010, toggleBit(0b1010, 3)) // Toggle bit 3
        assertEquals(0b11010, toggleBit(0b1010, 4)) // Toggle bit 4 (was 0)
    }
}