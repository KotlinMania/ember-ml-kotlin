package ai.solace.ember.tensor.bitwise.ops

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContentEquals

class BasicOpsTest {
    
    @Test
    fun testBitwiseAnd() {
        assertEquals(0b1000, bitwiseAnd(0b1100, 0b1010))
        assertEquals(0b0000, bitwiseAnd(0b1010, 0b0101))
        assertEquals(0b1111, bitwiseAnd(0b1111, 0b1111))
    }
    
    @Test
    fun testBitwiseOr() {
        assertEquals(0b1110, bitwiseOr(0b1100, 0b1010))
        assertEquals(0b1111, bitwiseOr(0b1010, 0b0101))
        assertEquals(0b1111, bitwiseOr(0b1111, 0b1111))
    }
    
    @Test
    fun testBitwiseXor() {
        assertEquals(0b0110, bitwiseXor(0b1100, 0b1010))
        assertEquals(0b1111, bitwiseXor(0b1010, 0b0101))
        assertEquals(0b0000, bitwiseXor(0b1111, 0b1111))
    }
    
    @Test
    fun testBitwiseNot() {
        assertEquals(-1, bitwiseNot(0))
        assertEquals(0, bitwiseNot(-1))
        assertEquals(-0b1011, bitwiseNot(0b1010))
    }
    
    @Test
    fun testArrayOperations() {
        val a = intArrayOf(0b1100, 0b1010)
        val b = intArrayOf(0b1010, 0b0101)
        
        val andResult = bitwiseAnd(a, b)
        assertContentEquals(intArrayOf(0b1000, 0b0000), andResult)
        
        val orResult = bitwiseOr(a, b)
        assertContentEquals(intArrayOf(0b1110, 0b1111), orResult)
        
        val xorResult = bitwiseXor(a, b)
        assertContentEquals(intArrayOf(0b0110, 0b1111), xorResult)
        
        val notResult = bitwiseNot(a)
        assertContentEquals(intArrayOf(-0b1101, -0b1011), notResult)
    }
}