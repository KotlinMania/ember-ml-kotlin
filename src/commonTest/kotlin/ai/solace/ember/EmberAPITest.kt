package ai.solace.ember

import ai.solace.ember.dtype.DType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EmberAPITest {
    
    @Test
    fun testDTypeAccessors() {
        assertEquals(DType.Float16, Ember.float16)
        assertEquals(DType.Float32, Ember.float32)
        assertEquals(DType.Float64, Ember.float64)
        assertEquals(DType.Int32, Ember.int32)
        assertEquals(DType.Bool, Ember.bool)
    }
    
    @Test
    fun testScalarCreation() {
        val x = Ember.array(5.0f)
        assertTrue(x.isScalar)
        assertEquals(0, x.ndim)
        assertEquals(5.0f, x.toScalar().toFloat(), 0.0001f)
    }
    
    @Test
    fun testVectorCreation() {
        val x = Ember.array(listOf(1.0f, 2.0f, 3.0f))
        assertEquals(1, x.ndim)
        assertEquals(3, x.size)
        assertEquals(intArrayOf(3).contentToString(), x.shape.contentToString())
    }
    
    @Test
    fun testMatrixCreation() {
        val x = Ember.array(listOf(
            listOf(1.0f, 2.0f),
            listOf(3.0f, 4.0f)
        ))
        assertEquals(2, x.ndim)
        assertEquals(4, x.size)
        assertEquals(intArrayOf(2, 2).contentToString(), x.shape.contentToString())
    }
    
    @Test
    fun testZeros() {
        val x = Ember.zeros(intArrayOf(2, 3))
        assertEquals(2, x.ndim)
        assertEquals(6, x.size)
        val data = x.toFloatArray()
        assertTrue(data.all { it == 0f })
    }
    
    @Test
    fun testOnes() {
        val x = Ember.ones(intArrayOf(3))
        assertEquals(1, x.ndim)
        assertEquals(3, x.size)
        val data = x.toFloatArray()
        assertTrue(data.all { it == 1f })
    }
    
    @Test
    fun testFull() {
        val x = Ember.full(intArrayOf(2, 2), 7.0f)
        assertEquals(2, x.ndim)
        assertEquals(4, x.size)
        val data = x.toFloatArray()
        assertTrue(data.all { it == 7f })
    }
    
    @Test
    fun testEye() {
        val x = Ember.eye(3)
        assertEquals(2, x.ndim)
        assertEquals(9, x.size)
        val data = x.toFloatArray()
        // Check diagonal
        assertEquals(1f, data[0], 0.0001f)
        assertEquals(1f, data[4], 0.0001f)
        assertEquals(1f, data[8], 0.0001f)
        // Check off-diagonal
        assertEquals(0f, data[1], 0.0001f)
        assertEquals(0f, data[3], 0.0001f)
    }
    
    @Test
    fun testArange() {
        val x = Ember.arange(0, 5)
        assertEquals(1, x.ndim)
        assertEquals(5, x.size)
        val data = x.toFloatArray()
        assertEquals(floatArrayOf(0f, 1f, 2f, 3f, 4f).contentToString(), data.contentToString())
    }
    
    @Test
    fun testLinspace() {
        val x = Ember.linspace(0.0, 1.0, 5)
        assertEquals(1, x.ndim)
        assertEquals(5, x.size)
        val data = x.toFloatArray()
        assertEquals(0.0f, data[0], 0.0001f)
        assertEquals(0.25f, data[1], 0.0001f)
        assertEquals(0.5f, data[2], 0.0001f)
        assertEquals(0.75f, data[3], 0.0001f)
        assertEquals(1.0f, data[4], 0.0001f)
    }
    
    @Test
    fun testElementWiseAddition() {
        val a = Ember.array(listOf(1.0f, 2.0f, 3.0f))
        val b = Ember.array(listOf(4.0f, 5.0f, 6.0f))
        val c = a + b
        
        val data = c.toFloatArray()
        assertEquals(5.0f, data[0], 0.0001f)
        assertEquals(7.0f, data[1], 0.0001f)
        assertEquals(9.0f, data[2], 0.0001f)
    }
    
    @Test
    fun testElementWiseMultiplication() {
        val a = Ember.array(listOf(2.0f, 3.0f, 4.0f))
        val b = Ember.array(listOf(5.0f, 6.0f, 7.0f))
        val c = a * b
        
        val data = c.toFloatArray()
        assertEquals(10.0f, data[0], 0.0001f)
        assertEquals(18.0f, data[1], 0.0001f)
        assertEquals(28.0f, data[2], 0.0001f)
    }
    
    @Test
    fun testScalarAddition() {
        val x = Ember.array(listOf(1.0f, 2.0f, 3.0f))
        val y = x + 10f
        
        val data = y.toFloatArray()
        assertEquals(11.0f, data[0], 0.0001f)
        assertEquals(12.0f, data[1], 0.0001f)
        assertEquals(13.0f, data[2], 0.0001f)
    }
    
    @Test
    fun testMathOperations() {
        val x = Ember.array(listOf(0.0f, 1.0f, 2.0f))
        
        // Test sin
        val sinX = Ember.sin(x)
        assertEquals(0.0f, sinX.toFloatArray()[0], 0.0001f)
        assertEquals(0.8414f, sinX.toFloatArray()[1], 0.001f)
        
        // Test exp
        val expX = Ember.exp(x)
        assertEquals(1.0f, expX.toFloatArray()[0], 0.0001f)
        assertEquals(2.7182f, expX.toFloatArray()[1], 0.001f)
        
        // Test sqrt
        val sqrtX = Ember.sqrt(x)
        assertEquals(0.0f, sqrtX.toFloatArray()[0], 0.0001f)
        assertEquals(1.0f, sqrtX.toFloatArray()[1], 0.0001f)
        assertEquals(1.4142f, sqrtX.toFloatArray()[2], 0.001f)
    }
    
    @Test
    fun testSum() {
        val x = Ember.array(listOf(1.0f, 2.0f, 3.0f, 4.0f))
        val s = Ember.sum(x)
        
        assertTrue(s.isScalar)
        assertEquals(10.0f, s.toScalar().toFloat(), 0.0001f)
    }
    
    @Test
    fun testMean() {
        val x = Ember.array(listOf(1.0f, 2.0f, 3.0f, 4.0f))
        val m = Ember.mean(x)
        
        assertTrue(m.isScalar)
        assertEquals(2.5f, m.toScalar().toFloat(), 0.0001f)
    }
    
    @Test
    fun testMaxMin() {
        val x = Ember.array(listOf(3.0f, 1.0f, 4.0f, 2.0f))
        
        val maxVal = Ember.max(x)
        assertEquals(4.0f, maxVal.toScalar().toFloat(), 0.0001f)
        
        val minVal = Ember.min(x)
        assertEquals(1.0f, minVal.toScalar().toFloat(), 0.0001f)
    }
    
    @Test
    fun testReshape() {
        val x = Ember.arange(0, 6)
        val y = Ember.reshape(x, intArrayOf(2, 3))
        
        assertEquals(2, y.ndim)
        assertEquals(intArrayOf(2, 3).contentToString(), y.shape.contentToString())
        assertEquals(6, y.size)
    }
    
    @Test
    fun testTranspose2D() {
        val x = Ember.array(listOf(
            listOf(1.0f, 2.0f, 3.0f),
            listOf(4.0f, 5.0f, 6.0f)
        ))
        val y = Ember.transpose(x)
        
        assertEquals(intArrayOf(3, 2).contentToString(), y.shape.contentToString())
        val data = y.toFloatArray()
        assertEquals(1.0f, data[0], 0.0001f)
        assertEquals(4.0f, data[1], 0.0001f)
        assertEquals(2.0f, data[2], 0.0001f)
        assertEquals(5.0f, data[3], 0.0001f)
    }
    
    @Test
    fun testMatmul() {
        val a = Ember.array(listOf(
            listOf(1.0f, 2.0f),
            listOf(3.0f, 4.0f)
        ))
        val b = Ember.array(listOf(
            listOf(5.0f, 6.0f),
            listOf(7.0f, 8.0f)
        ))
        val c = Ember.matmul(a, b)
        
        assertEquals(intArrayOf(2, 2).contentToString(), c.shape.contentToString())
        val data = c.toFloatArray()
        // [1*5 + 2*7, 1*6 + 2*8] = [19, 22]
        // [3*5 + 4*7, 3*6 + 4*8] = [43, 50]
        assertEquals(19.0f, data[0], 0.0001f)
        assertEquals(22.0f, data[1], 0.0001f)
        assertEquals(43.0f, data[2], 0.0001f)
        assertEquals(50.0f, data[3], 0.0001f)
    }
    
    @Test
    fun testChainedOperations() {
        // Test: (x + 1) * 2
        val x = Ember.array(listOf(1.0f, 2.0f, 3.0f))
        val y = (x + 1f) * 2f
        
        val data = y.toFloatArray()
        assertEquals(4.0f, data[0], 0.0001f)
        assertEquals(6.0f, data[1], 0.0001f)
        assertEquals(8.0f, data[2], 0.0001f)
    }
    
    @Test
    fun testUtilityFunctions() {
        val x = Ember.array(listOf(
            listOf(1.0f, 2.0f, 3.0f),
            listOf(4.0f, 5.0f, 6.0f)
        ))
        
        assertEquals(intArrayOf(2, 3).contentToString(), Ember.shape(x).contentToString())
        assertEquals(2, Ember.ndim(x))
        assertEquals(6, Ember.size(x))
        assertEquals(DType.Float32, Ember.dtype(x))
    }
    
    @Test
    fun testTypeAlias() {
        val x: Tensor = Ember.array(listOf(1.0f, 2.0f))
        assertEquals(1, x.ndim)
    }
}
