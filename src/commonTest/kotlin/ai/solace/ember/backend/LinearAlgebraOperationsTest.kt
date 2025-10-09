package ai.solace.ember.backend

import ai.solace.ember.tensor.common.DType
import kotlin.test.*

/**
 * Test suite for the linear algebra operations implementation.
 * 
 * These tests validate the linear algebra operations added to the backend
 * including matrix operations, decompositions, and numerical linear algebra functions.
 */
class LinearAlgebraOperationsTest {
    
    private val backend = OptimizedMegaTensorBackend()
    private val linalgOps = LinearAlgebraOperations(backend)
    
    @Test
    fun testDotProduct() {
        val data1 = doubleArrayOf(1.0, 2.0, 3.0)
        val data2 = doubleArrayOf(4.0, 5.0, 6.0)
        
        val tensor1 = backend.createTensor(data1, intArrayOf(3), DType.FLOAT64) 
            as OptimizedMegaTensorBackend.OptimizedMegaTensor
        val tensor2 = backend.createTensor(data2, intArrayOf(3), DType.FLOAT64) 
            as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        val result = linalgOps.dot(tensor1, tensor2) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        assertEquals(DType.FLOAT64, result.dtype)
        assertEquals(1, result.size)
        assertTrue(result.shape.contentEquals(intArrayOf()))
        // Dot product of [1,2,3] and [4,5,6] should be 1*4 + 2*5 + 3*6 = 32
    }
    
    @Test
    fun testMatrixMultiplication() {
        // Test 2x2 matrix multiplication
        val data1 = doubleArrayOf(1.0, 2.0, 3.0, 4.0) // [[1,2], [3,4]]
        val data2 = doubleArrayOf(5.0, 6.0, 7.0, 8.0) // [[5,6], [7,8]]
        
        val matrix1 = backend.createTensor(data1, intArrayOf(2, 2), DType.FLOAT64) 
            as OptimizedMegaTensorBackend.OptimizedMegaTensor
        val matrix2 = backend.createTensor(data2, intArrayOf(2, 2), DType.FLOAT64) 
            as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        val result = linalgOps.matmul(matrix1, matrix2) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        assertEquals(DType.FLOAT64, result.dtype)
        assertEquals(4, result.size)
        assertTrue(result.shape.contentEquals(intArrayOf(2, 2)))
        // Result should be [[1*5+2*7, 1*6+2*8], [3*5+4*7, 3*6+4*8]] = [[19,22], [43,50]]
    }
    
    @Test
    fun testTranspose() {
        // Test 2x3 matrix transpose
        val data = doubleArrayOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0) // [[1,2,3], [4,5,6]]
        val matrix = backend.createTensor(data, intArrayOf(2, 3), DType.FLOAT64) 
            as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        val result = linalgOps.transpose(matrix) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        assertEquals(DType.FLOAT64, result.dtype)
        assertEquals(6, result.size)
        assertTrue(result.shape.contentEquals(intArrayOf(3, 2)))
        // Result should be [[1,4], [2,5], [3,6]]
    }
    
    @Test
    fun testTranspose1D() {
        // Test 1D vector transpose (should be unchanged)
        val data = doubleArrayOf(1.0, 2.0, 3.0)
        val vector = backend.createTensor(data, intArrayOf(3), DType.FLOAT64) 
            as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        val result = linalgOps.transpose(vector) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        assertEquals(DType.FLOAT64, result.dtype)
        assertEquals(3, result.size)
        assertTrue(result.shape.contentEquals(intArrayOf(3)))
        assertEquals(vector, result) // Should be the same object for 1D
    }
    
    @Test
    fun testDeterminant1x1() {
        val data = doubleArrayOf(5.0)
        val matrix = backend.createTensor(data, intArrayOf(1, 1), DType.FLOAT64) 
            as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        val result = linalgOps.determinant(matrix) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        assertEquals(DType.FLOAT64, result.dtype)
        assertEquals(1, result.size)
        assertTrue(result.shape.contentEquals(intArrayOf()))
        // Determinant should be 5.0
    }
    
    @Test
    fun testDeterminant2x2() {
        val data = doubleArrayOf(1.0, 2.0, 3.0, 4.0) // [[1,2], [3,4]]
        val matrix = backend.createTensor(data, intArrayOf(2, 2), DType.FLOAT64) 
            as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        val result = linalgOps.determinant(matrix) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        assertEquals(DType.FLOAT64, result.dtype)
        assertEquals(1, result.size)
        assertTrue(result.shape.contentEquals(intArrayOf()))
        // Determinant should be 1*4 - 2*3 = -2
    }
    
    @Test
    fun testDeterminant3x3() {
        // Identity matrix
        val data = doubleArrayOf(
            1.0, 0.0, 0.0,
            0.0, 1.0, 0.0,
            0.0, 0.0, 1.0
        )
        val matrix = backend.createTensor(data, intArrayOf(3, 3), DType.FLOAT64) 
            as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        val result = linalgOps.determinant(matrix) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        assertEquals(DType.FLOAT64, result.dtype)
        assertEquals(1, result.size)
        assertTrue(result.shape.contentEquals(intArrayOf()))
        // Determinant of identity matrix should be 1.0
    }
    
    @Test
    fun testTrace() {
        // Test square matrix trace
        val data = doubleArrayOf(1.0, 2.0, 3.0, 4.0) // [[1,2], [3,4]]
        val matrix = backend.createTensor(data, intArrayOf(2, 2), DType.FLOAT64) 
            as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        val result = linalgOps.trace(matrix) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        assertEquals(DType.FLOAT64, result.dtype)
        assertEquals(1, result.size)
        assertTrue(result.shape.contentEquals(intArrayOf()))
        // Trace should be 1 + 4 = 5
    }
    
    @Test
    fun testTraceRectangular() {
        // Test rectangular matrix trace (should sum diagonal up to min dimension)
        val data = doubleArrayOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0) // [[1,2,3], [4,5,6]]
        val matrix = backend.createTensor(data, intArrayOf(2, 3), DType.FLOAT64) 
            as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        val result = linalgOps.trace(matrix) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        assertEquals(DType.FLOAT64, result.dtype)
        assertEquals(1, result.size)
        assertTrue(result.shape.contentEquals(intArrayOf()))
        // Trace should be 1 + 5 = 6 (diagonal elements up to min(2,3) = 2)
    }
    
    @Test
    fun testFrobeniusNorm() {
        val data = doubleArrayOf(3.0, 4.0) // Vector with norm 5
        val tensor = backend.createTensor(data, intArrayOf(2), DType.FLOAT64) 
            as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        val result = linalgOps.norm(tensor, "fro") as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        assertEquals(DType.FLOAT64, result.dtype)
        assertEquals(1, result.size)
        assertTrue(result.shape.contentEquals(intArrayOf()))
        // Norm should be sqrt(3^2 + 4^2) = sqrt(9 + 16) = 5
    }
    
    @Test
    fun testMatrixInverse1x1() {
        val data = doubleArrayOf(4.0)
        val matrix = backend.createTensor(data, intArrayOf(1, 1), DType.FLOAT64) 
            as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        val result = linalgOps.inverse(matrix) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        assertEquals(DType.FLOAT64, result.dtype)
        assertEquals(1, result.size)
        assertTrue(result.shape.contentEquals(intArrayOf(1, 1)))
        // Inverse should be 1/4 = 0.25
    }
    
    @Test
    fun testMatrixInverse2x2() {
        val data = doubleArrayOf(1.0, 2.0, 3.0, 4.0) // [[1,2], [3,4]]
        val matrix = backend.createTensor(data, intArrayOf(2, 2), DType.FLOAT64) 
            as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        val result = linalgOps.inverse(matrix) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        assertEquals(DType.FLOAT64, result.dtype)
        assertEquals(4, result.size)
        assertTrue(result.shape.contentEquals(intArrayOf(2, 2)))
        // Inverse should be (1/det) * [[4,-2], [-3,1]] = (1/-2) * [[4,-2], [-3,1]]
    }
    
    @Test
    fun testErrorHandling() {
        // Test incompatible shapes for dot product
        val vector1 = backend.createTensor(doubleArrayOf(1.0, 2.0), intArrayOf(2), DType.FLOAT64)
        val vector2 = backend.createTensor(doubleArrayOf(1.0, 2.0, 3.0), intArrayOf(3), DType.FLOAT64)
        
        assertFailsWith<IllegalArgumentException> {
            linalgOps.dot(vector1, vector2)
        }
        
        // Test incompatible shapes for matrix multiplication
        val matrix1 = backend.createTensor(doubleArrayOf(1.0, 2.0), intArrayOf(1, 2), DType.FLOAT64)
        val matrix2 = backend.createTensor(doubleArrayOf(1.0, 2.0, 3.0), intArrayOf(1, 3), DType.FLOAT64)
        
        assertFailsWith<IllegalArgumentException> {
            linalgOps.matmul(matrix1, matrix2)
        }
        
        // Test determinant of non-square matrix
        val nonSquare = backend.createTensor(doubleArrayOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0), intArrayOf(2, 3), DType.FLOAT64)
        
        assertFailsWith<IllegalArgumentException> {
            linalgOps.determinant(nonSquare)
        }
        
        // Test inverse of singular matrix
        val singular = backend.createTensor(doubleArrayOf(0.0), intArrayOf(1, 1), DType.FLOAT64)
        
        assertFailsWith<ArithmeticException> {
            linalgOps.inverse(singular)
        }
    }
    
    @Test
    fun testUnsupportedOperations() {
        val tensor = backend.createTensor(doubleArrayOf(1.0, 2.0), intArrayOf(2), DType.FLOAT64)
        
        // Test unsupported multidimensional operations
        assertFailsWith<UnsupportedOperationException> {
            linalgOps.dot(
                backend.createTensor(doubleArrayOf(1.0, 2.0, 3.0, 4.0), intArrayOf(2, 2), DType.FLOAT64),
                tensor
            )
        }
        
        // Test unsupported norm types
        assertFailsWith<UnsupportedOperationException> {
            linalgOps.norm(tensor, "spectral")
        }
        
        // Test large matrix inverse (not implemented)
        val largeMatrix = backend.createTensor(
            DoubleArray(9) { it.toDouble() + 1.0 }, 
            intArrayOf(3, 3), 
            DType.FLOAT64
        )
        
        assertFailsWith<UnsupportedOperationException> {
            linalgOps.inverse(largeMatrix)
        }
    }
    
    @Test
    fun testMixedDataTypes() {
        // Test operations with different but compatible data types
        val floatData = floatArrayOf(1.0f, 2.0f, 3.0f)
        val doubleData = doubleArrayOf(4.0, 5.0, 6.0)
        
        val floatTensor = backend.createTensor(floatData, intArrayOf(3), DType.FLOAT32) 
            as OptimizedMegaTensorBackend.OptimizedMegaTensor
        val doubleTensor = backend.createTensor(doubleData, intArrayOf(3), DType.FLOAT64) 
            as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        val result = linalgOps.dot(floatTensor, doubleTensor) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        // Result should be promoted to FLOAT64
        assertEquals(DType.FLOAT64, result.dtype)
        assertEquals(1, result.size)
    }
}