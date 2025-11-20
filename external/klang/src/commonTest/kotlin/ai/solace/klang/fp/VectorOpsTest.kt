package ai.solace.klang.fp

import ai.solace.klang.mem.GlobalHeap
import ai.solace.klang.mem.KMalloc
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.math.abs

class VectorOpsTest {
    private fun setup() {
        GlobalHeap.init(1 shl 20)  // 1MB
        KMalloc.init(1 shl 18)      // 256KB
    }
    
    @Test
    fun dotProductSimple() {
        setup()
        
        val v1 = floatArrayOf(1.0f, 2.0f, 3.0f)
        val v2 = floatArrayOf(4.0f, 5.0f, 6.0f)
        
        // 1*4 + 2*5 + 3*6 = 4 + 10 + 18 = 32
        val result = VectorOps.dotAccumulate(3, v1, 0, v2, 0)
        
        assertEquals(32.0f, result, 1e-5f, "Simple dot product failed")
    }
    
    @Test
    fun dotProductWithOffsets() {
        setup()
        
        val v1 = floatArrayOf(0.0f, 0.0f, 1.0f, 2.0f, 3.0f, 0.0f)
        val v2 = floatArrayOf(0.0f, 4.0f, 5.0f, 6.0f, 0.0f, 0.0f)
        
        // Dot product of [1,2,3] with [4,5,6] starting at offsets
        val result = VectorOps.dotAccumulate(3, v1, 2, v2, 1)
        
        assertEquals(32.0f, result, 1e-5f, "Dot product with offsets failed")
    }
    
    @Test
    fun dotProductZeroLength() {
        setup()
        
        val v1 = floatArrayOf(1.0f, 2.0f)
        val v2 = floatArrayOf(3.0f, 4.0f)
        
        val result = VectorOps.dotAccumulate(0, v1, 0, v2, 0)
        
        assertEquals(0.0f, result, 1e-5f, "Zero length dot product failed")
    }
    
    @Test
    fun dotProductSingleElement() {
        setup()
        
        val v1 = floatArrayOf(5.0f)
        val v2 = floatArrayOf(7.0f)
        
        val result = VectorOps.dotAccumulate(1, v1, 0, v2, 0)
        
        assertEquals(35.0f, result, 1e-5f, "Single element dot product failed")
    }
    
    @Test
    fun dotProductLargeVector() {
        setup()
        
        // Create vectors of length 100, all ones
        val v1 = FloatArray(100) { 1.0f }
        val v2 = FloatArray(100) { 2.0f }
        
        // Sum should be 100 * 1 * 2 = 200
        val result = VectorOps.dotAccumulate(100, v1, 0, v2, 0)
        
        assertEquals(200.0f, result, 1e-3f, "Large vector dot product failed")
    }
    
    @Test
    fun dotProductAccuracy() {
        setup()
        
        // Test deterministic floating-point accumulation
        val v1 = floatArrayOf(1e-5f, 1e-5f, 1e-5f, 1e-5f)
        val v2 = floatArrayOf(1.0f, 1.0f, 1.0f, 1.0f)
        
        // Should accumulate to 4e-5
        val result = VectorOps.dotAccumulate(4, v1, 0, v2, 0)
        
        assertEquals(4e-5f, result, 1e-8f, "Dot product accuracy failed")
    }
    
    @Test
    fun axpyBasic() {
        setup()
        
        val a = 2.0f
        val x = floatArrayOf(1.0f, 2.0f, 3.0f)
        val y = floatArrayOf(4.0f, 5.0f, 6.0f)
        val out = FloatArray(3)
        
        // out = a*x + y = 2*[1,2,3] + [4,5,6] = [6,9,12]
        VectorOps.axpy(3, a, x, 0, y, 0, out, 0)
        
        assertEquals(6.0f, out[0], 1e-5f, "AXPY [0] failed")
        assertEquals(9.0f, out[1], 1e-5f, "AXPY [1] failed")
        assertEquals(12.0f, out[2], 1e-5f, "AXPY [2] failed")
    }
    
    @Test
    fun axpyWithOffsets() {
        setup()
        
        val a = 3.0f
        val x = floatArrayOf(0.0f, 1.0f, 2.0f, 0.0f)
        val y = floatArrayOf(0.0f, 0.0f, 5.0f, 6.0f, 0.0f)
        val out = FloatArray(5)
        
        // out = 3*[1,2] + [5,6] = [8,12]
        VectorOps.axpy(2, a, x, 1, y, 2, out, 2)
        
        assertEquals(0.0f, out[0], "AXPY offset [0] should be 0")
        assertEquals(0.0f, out[1], "AXPY offset [1] should be 0")
        assertEquals(8.0f, out[2], 1e-5f, "AXPY offset [2] failed")
        assertEquals(12.0f, out[3], 1e-5f, "AXPY offset [3] failed")
        assertEquals(0.0f, out[4], "AXPY offset [4] should be 0")
    }
    
    @Test
    fun axpyZeroScalar() {
        setup()
        
        val a = 0.0f
        val x = floatArrayOf(1.0f, 2.0f, 3.0f)
        val y = floatArrayOf(4.0f, 5.0f, 6.0f)
        val out = FloatArray(3)
        
        // out = 0*x + y = y
        VectorOps.axpy(3, a, x, 0, y, 0, out, 0)
        
        assertEquals(4.0f, out[0], 1e-5f)
        assertEquals(5.0f, out[1], 1e-5f)
        assertEquals(6.0f, out[2], 1e-5f)
    }
    
    @Test
    fun axpyNegativeScalar() {
        setup()
        
        val a = -1.0f
        val x = floatArrayOf(1.0f, 2.0f, 3.0f)
        val y = floatArrayOf(4.0f, 5.0f, 6.0f)
        val out = FloatArray(3)
        
        // out = -1*[1,2,3] + [4,5,6] = [3,3,3]
        VectorOps.axpy(3, a, x, 0, y, 0, out, 0)
        
        assertEquals(3.0f, out[0], 1e-5f)
        assertEquals(3.0f, out[1], 1e-5f)
        assertEquals(3.0f, out[2], 1e-5f)
    }
    
    @Test
    fun axpyLargeVector() {
        setup()
        
        val n = 100
        val a = 0.5f
        val x = FloatArray(n) { 2.0f }
        val y = FloatArray(n) { 3.0f }
        val out = FloatArray(n)
        
        // out = 0.5*2 + 3 = 1 + 3 = 4
        VectorOps.axpy(n, a, x, 0, y, 0, out, 0)
        
        for (i in 0 until n) {
            assertEquals(4.0f, out[i], 1e-5f, "AXPY large vector failed at index $i")
        }
    }
    
    @Test
    fun deterministicResults() {
        setup()
        
        // Test that operations are deterministic across multiple runs
        val v1 = floatArrayOf(1.1f, 2.2f, 3.3f, 4.4f)
        val v2 = floatArrayOf(5.5f, 6.6f, 7.7f, 8.8f)
        
        val result1 = VectorOps.dotAccumulate(4, v1, 0, v2, 0)
        val result2 = VectorOps.dotAccumulate(4, v1, 0, v2, 0)
        
        assertEquals(result1, result2, "Results should be deterministic")
    }
    
    @Test
    fun crossPlatformConsistency() {
        setup()
        
        // Test case designed to expose platform differences in floating-point
        val v1 = floatArrayOf(0.1f, 0.2f, 0.3f)
        val v2 = floatArrayOf(0.4f, 0.5f, 0.6f)
        
        // Using soft-float math should give consistent results
        val result = VectorOps.dotAccumulate(3, v1, 0, v2, 0)
        
        // Expected: 0.1*0.4 + 0.2*0.5 + 0.3*0.6 = 0.04 + 0.1 + 0.18 = 0.32
        val expected = 0.32f
        val error = abs(result - expected)
        
        // Should be very close (within floating-point tolerance)
        assertTrue(error < 1e-5f, "Cross-platform consistency failed, error: $error")
    }
}
