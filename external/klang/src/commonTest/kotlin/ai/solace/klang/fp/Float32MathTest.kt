package ai.solace.klang.fp

import ai.solace.klang.mem.GlobalHeap
import ai.solace.klang.mem.KMalloc
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.math.abs

class Float32MathTest {
    private fun setup() {
        GlobalHeap.init(1 shl 20)  // 1MB
        KMalloc.init(1 shl 18)      // 256KB
    }
    
    @Test
    fun multiplicationBasic() {
        setup()
        
        val result = Float32Math.mul(2.0f, 3.0f)
        assertEquals(6.0f, result, 1e-5f, "Basic multiplication failed")
    }
    
    @Test
    fun multiplicationByZero() {
        setup()
        
        val result = Float32Math.mul(5.0f, 0.0f)
        assertEquals(0.0f, result, "Multiplication by zero failed")
    }
    
    @Test
    fun multiplicationByOne() {
        setup()
        
        val result = Float32Math.mul(42.5f, 1.0f)
        assertEquals(42.5f, result, 1e-5f, "Multiplication by one failed")
    }
    
    @Test
    fun additionBasic() {
        setup()
        
        val result = Float32Math.add(10.0f, 20.0f)
        assertEquals(30.0f, result, 1e-5f, "Basic addition failed")
    }
    
    @Test
    fun additionWithZero() {
        setup()
        
        val result = Float32Math.add(42.5f, 0.0f)
        assertEquals(42.5f, result, 1e-5f, "Addition with zero failed")
    }
    
    @Test
    fun additionNegativeNumbers() {
        setup()
        
        val result = Float32Math.add(-10.0f, 5.0f)
        assertEquals(-5.0f, result, 1e-5f, "Addition with negative numbers failed")
    }
    
    @Test
    fun subtractionBasic() {
        setup()
        
        val result = Float32Math.sub(30.0f, 10.0f)
        assertEquals(20.0f, result, 1e-5f, "Basic subtraction failed")
    }
    
    @Test
    fun subtractionResultingInZero() {
        setup()
        
        val result = Float32Math.sub(42.5f, 42.5f)
        assertEquals(0.0f, result, 1e-5f, "Subtraction resulting in zero failed")
    }
    
    @Test
    fun divisionBasic() {
        setup()
        
        val result = Float32Math.div(20.0f, 4.0f)
        assertEquals(5.0f, result, 1e-5f, "Basic division failed")
    }
    
    @Test
    fun divisionByOne() {
        setup()
        
        val result = Float32Math.div(42.5f, 1.0f)
        assertEquals(42.5f, result, 1e-5f, "Division by one failed")
    }
    
    @Test
    fun divisionResultingInOne() {
        setup()
        
        val result = Float32Math.div(7.0f, 7.0f)
        assertEquals(1.0f, result, 1e-5f, "Division resulting in one failed")
    }
    
    @Test
    fun fusedMultiplyAdd() {
        setup()
        
        // fma(a, b, c) = (a * b) + c
        val result = Float32Math.fma(2.0f, 3.0f, 4.0f)
        assertEquals(10.0f, result, 1e-5f, "FMA failed")
    }
    
    @Test
    fun intToFloatConversion() {
        setup()
        
        val result = Float32Math.intToFloat(42)
        assertEquals(42.0f, result, 1e-5f, "Int to float conversion failed")
    }
    
    @Test
    fun intToFloatNegative() {
        setup()
        
        val result = Float32Math.intToFloat(-123)
        assertEquals(-123.0f, result, 1e-5f, "Negative int to float failed")
    }
    
    @Test
    fun intToFloatZero() {
        setup()
        
        val result = Float32Math.intToFloat(0)
        assertEquals(0.0f, result, "Zero int to float failed")
    }
    
    @Test
    fun uintToFloatConversion() {
        setup()
        
        val result = Float32Math.uintToFloat(42u)
        assertEquals(42.0f, result, 1e-5f, "UInt to float conversion failed")
    }
    
    @Test
    fun uintToFloatLarge() {
        setup()
        
        val result = Float32Math.uintToFloat(1000000u)
        assertEquals(1000000.0f, result, 10.0f, "Large uint to float failed")
    }
    
    @Test
    fun roundingToNearestEven() {
        setup()
        
        val result = Float32Math.nearbyint(2.5f)
        assertEquals(2.0f, result, 1e-5f, "Round to nearest even (2.5) failed")
        
        val result2 = Float32Math.nearbyint(3.5f)
        assertEquals(4.0f, result2, 1e-5f, "Round to nearest even (3.5) failed")
    }
    
    @Test
    fun lrintConversion() {
        setup()
        
        val result = Float32Math.lrint(42.7f)
        assertEquals(43L, result, "lrint failed")
        
        val result2 = Float32Math.lrint(42.2f)
        assertEquals(42L, result2, "lrint (round down) failed")
    }
    
    @Test
    fun negativeNumberOperations() {
        setup()
        
        val mul = Float32Math.mul(-2.0f, 3.0f)
        assertEquals(-6.0f, mul, 1e-5f, "Negative multiplication failed")
        
        val div = Float32Math.div(-10.0f, 2.0f)
        assertEquals(-5.0f, div, 1e-5f, "Negative division failed")
    }
    
    @Test
    fun smallNumberOperations() {
        setup()
        
        val tiny = 1e-6f
        val result = Float32Math.add(tiny, tiny)
        assertEquals(2e-6f, result, 1e-9f, "Small number addition failed")
    }
    
    @Test
    fun largeNumberOperations() {
        setup()
        
        val large = 1e6f
        val result = Float32Math.mul(large, 2.0f)
        assertEquals(2e6f, result, 100.0f, "Large number multiplication failed")
    }
    
    @Test
    fun chainedOperations() {
        setup()
        
        // (2 * 3) + (4 * 5) = 6 + 20 = 26
        val mul1 = Float32Math.mul(2.0f, 3.0f)
        val mul2 = Float32Math.mul(4.0f, 5.0f)
        val result = Float32Math.add(mul1, mul2)
        
        assertEquals(26.0f, result, 1e-5f, "Chained operations failed")
    }
    
    @Test
    fun determinism() {
        setup()
        
        // Same operation should give same result
        val result1 = Float32Math.mul(1.1f, 2.2f)
        val result2 = Float32Math.mul(1.1f, 2.2f)
        
        assertEquals(result1, result2, "Determinism check failed")
    }
    
    @Test
    fun divisionByTwo() {
        setup()
        
        val result = Float32Math.div(100.0f, 2.0f)
        assertEquals(50.0f, result, 1e-5f, "Division by two failed")
    }
    
    @Test
    fun multiplicationCommutative() {
        setup()
        
        val result1 = Float32Math.mul(3.0f, 4.0f)
        val result2 = Float32Math.mul(4.0f, 3.0f)
        
        assertEquals(result1, result2, 1e-5f, "Multiplication commutativity failed")
    }
    
    @Test
    fun additionCommutative() {
        setup()
        
        val result1 = Float32Math.add(7.0f, 11.0f)
        val result2 = Float32Math.add(11.0f, 7.0f)
        
        assertEquals(result1, result2, 1e-5f, "Addition commutativity failed")
    }
    
    @Test
    fun intToFloatBitsRoundTrip() {
        setup()
        
        val original = 12345
        val bits = Float32Math.intToFloatBits(original)
        val backToFloat = Float.fromBits(bits)
        
        assertEquals(original.toFloat(), backToFloat, 1.0f, "Int to float bits round-trip failed")
    }
    
    @Test
    fun uintToFloatBitsRoundTrip() {
        setup()
        
        val original = 54321u
        val bits = Float32Math.uintToFloatBits(original)
        val backToFloat = Float.fromBits(bits)
        
        assertEquals(original.toFloat(), backToFloat, 1.0f, "UInt to float bits round-trip failed")
    }
}
