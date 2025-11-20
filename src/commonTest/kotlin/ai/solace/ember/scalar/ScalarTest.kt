package ai.solace.ember.scalar

import ai.solace.ember.dtype.DType
import ai.solace.klang.fp.CDouble
import ai.solace.klang.fp.CFloat16
import ai.solace.klang.fp.CFloat32
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScalarTest {
    
    @Test
    fun testFloat16Creation() {
        val s = Scalar.Float16(CFloat16.fromFloat(5.0f))
        assertEquals(DType.Float16, s.dtype)
        assertEquals(5.0f, s.toFloat(), 0.01f)
    }
    
    @Test
    fun testFloat32Creation() {
        val s = Scalar.Float32(CFloat32.fromFloat(5.0f))
        assertEquals(DType.Float32, s.dtype)
        assertEquals(5.0f, s.toFloat(), 0.0001f)
    }
    
    @Test
    fun testFloat64Creation() {
        val s = Scalar.Float64(CDouble.fromDouble(5.0))
        assertEquals(DType.Float64, s.dtype)
        assertEquals(5.0, s.toDouble(), 0.0001)
    }

    @Test
    fun smokeFloat64Ops() {
        val a = Scalar.Float64(CDouble.fromDouble(2.0))
        val b = Scalar.Float64(CDouble.fromDouble(3.0))

        assertEquals(5.0, (a + b).toDouble(), 0.0)
        assertEquals(-1.0, (a - b).toDouble(), 0.0)
        assertEquals(6.0, (a * b).toDouble(), 0.0)
        assertEquals(2.0 / 3.0, (a / b).toDouble(), 1e-12)
    }
    
    @Test
    fun testFloat32Arithmetic() {
        val a = Scalar.Float32(CFloat32.fromFloat(5.0f))
        val b = Scalar.Float32(CFloat32.fromFloat(3.0f))

        val sum = a + b
        assertEquals(8.0f, sum.toFloat(), 0.0001f)

        val diff = a - b
        assertEquals(2.0f, diff.toFloat(), 0.0001f)

        val product = a * b
        assertEquals(15.0f, product.toFloat(), 0.0001f)

        val quotient = a / b
        assertEquals(1.6666f, quotient.toFloat(), 0.01f)
    }

    @Test
    fun testFloatComparisons() {
        val a = Scalar.Float32(CFloat32.fromFloat(2.0f))
        val b = Scalar.Float32(CFloat32.fromFloat(3.0f))
        assertTrue(a < b)
        assertTrue(b > a)
        val c = Scalar.Float64(CDouble.fromDouble(4.0))
        val d = Scalar.Float64(CDouble.fromDouble(4.0))
        assertTrue(c >= d)
        assertTrue(c <= d)
    }

    @Test
    fun smokeFloat16Ops() {
        val a = Scalar.Float16(CFloat16.fromFloat(1.5f))
        val b = Scalar.Float16(CFloat16.fromFloat(0.5f))

        assertEquals(2.0f, (a + b).toFloat(), 0.01f)
        assertEquals(1.0f, (a - b).toFloat(), 0.01f)
        assertEquals(0.75f, (a * b).toFloat(), 0.01f)
        assertEquals(3.0f, (a / b).toFloat(), 0.05f)
    }
    
    @Test
    fun testInt32Arithmetic() {
        val a = Scalar.Int32(10)
        val b = Scalar.Int32(3)
        
        val sum = a + b
        assertEquals(13, sum.toInt())
        
        val diff = a - b
        assertEquals(7, diff.toInt())
        
        val product = a * b
        assertEquals(30, product.toInt())
        
        val quotient = a / b
        assertEquals(3, quotient.toInt())
    }

    @Test
    fun testIntComparisons() {
        val a = Scalar.Int32(1)
        val b = Scalar.Int32(2)
        assertTrue(a < b)
        val c = Scalar.Int64(5L)
        val d = Scalar.Int64(5L)
        assertTrue(c >= d)
        assertTrue(c == d)
    }
    
    @Test
    fun testBoolLogic() {
        val t = Scalar.Bool(true)
        val f = Scalar.Bool(false)
        
        assertEquals(false, (!t).value)
        assertEquals(true, (!f).value)
        
        assertEquals(false, (t and f).value)
        assertEquals(true, (t or f).value)
        assertEquals(true, (t xor f).value)
    }
    
    @Test
    fun testFromValue() {
        val f32 = Scalar.fromValue(5.5, DType.Float32)
        assertTrue(f32 is Scalar.Float32)
        assertEquals(5.5f, f32.toFloat(), 0.0001f)
        
        val i32 = Scalar.fromValue(42, DType.Int32)
        assertTrue(i32 is Scalar.Int32)
        assertEquals(42, i32.toInt())
    }
    
    @Test
    fun testCrossPlatformDeterminism() {
        // This test verifies bit-exact results using CFloat32
        val a = Scalar.Float32(CFloat32.fromFloat(1.5f))
        val b = Scalar.Float32(CFloat32.fromFloat(2.5f))
        
        val result = (a * b) + Scalar.Float32(CFloat32.fromFloat(3.5f))
        
        // Verify the computation
        assertEquals(7.25f, result.toFloat(), 0.0001f)
        
        // Bit-exact check (same bits on all platforms)
        val expectedBits = 0x40e80000 // 7.25f
        assertEquals(expectedBits, result.value.toBits())
    }
}
