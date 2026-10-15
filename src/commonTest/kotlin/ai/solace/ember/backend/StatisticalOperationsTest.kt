package ai.solace.ember.backend

import ai.solace.ember.tensor.common.DType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Test suite for the statistical operations implementation.
 *
 * These tests validate the statistical operations added to the backend
 * including descriptive statistics, aggregation functions, and data analysis operations.
 */
class StatisticalOperationsTest {
    private val backend = DefaultCpuBackend()
    private val statsOps = StatisticalOperations(backend)

    @Test
    fun testMeanOperation() {
        val data = doubleArrayOf(1.0, 2.0, 3.0, 4.0, 5.0)
        val tensor = backend.createTensor(data, intArrayOf(5), DType.FLOAT64)

        val result = statsOps.mean(tensor)

        assertEquals(DType.FLOAT64, result.dtype)
        assertEquals(1, result.size)
        assertTrue(result.shape.contentEquals(intArrayOf()))
    }

    @Test
    fun testVarianceAndStd() {
        val data = doubleArrayOf(1.0, 2.0, 3.0, 4.0, 5.0)
        val tensor = backend.createTensor(data, intArrayOf(5), DType.FLOAT64)

        // Test variance
        val varianceResult = statsOps.variance(tensor)
        assertEquals(DType.FLOAT64, varianceResult.dtype)
        assertEquals(1, varianceResult.size)

        // Test standard deviation
        val stdResult = statsOps.std(tensor)
        assertEquals(DType.FLOAT64, stdResult.dtype)
        assertEquals(1, stdResult.size)
    }

    @Test
    fun testMedianOperation() {
        // Test with odd number of elements
        val oddData = doubleArrayOf(3.0, 1.0, 4.0, 1.0, 5.0)
        val oddTensor = backend.createTensor(oddData, intArrayOf(5), DType.FLOAT64)

        val oddResult = statsOps.median(oddTensor)
        assertEquals(DType.FLOAT64, oddResult.dtype)
        assertEquals(1, oddResult.size)

        // Test with even number of elements
        val evenData = doubleArrayOf(1.0, 2.0, 3.0, 4.0)
        val evenTensor = backend.createTensor(evenData, intArrayOf(4), DType.FLOAT64)

        val evenResult = statsOps.median(evenTensor)
        assertEquals(DType.FLOAT64, evenResult.dtype)
        assertEquals(1, evenResult.size)
    }

    @Test
    fun testMinMaxOperations() {
        val data = intArrayOf(5, 2, 8, 1, 9, 3)
        val tensor = backend.createTensor(data, intArrayOf(6), DType.INT32)

        // Test min
        val minResult = statsOps.min(tensor)
        assertEquals(DType.INT32, minResult.dtype)
        assertEquals(1, minResult.size)

        // Test max
        val maxResult = statsOps.max(tensor)
        assertEquals(DType.INT32, maxResult.dtype)
        assertEquals(1, maxResult.size)
    }

    @Test
    fun testSumOperation() {
        val data = intArrayOf(1, 2, 3, 4, 5)
        val tensor = backend.createTensor(data, intArrayOf(5), DType.INT32)

        val result = statsOps.sum(tensor)

        // Sum should promote to INT64 for INT32 input
        assertEquals(DType.INT64, result.dtype)
        assertEquals(1, result.size)
        assertTrue(result.shape.contentEquals(intArrayOf()))
    }

    @Test
    fun testCumSumOperation() {
        val data = intArrayOf(1, 2, 3, 4)
        val tensor = backend.createTensor(data, intArrayOf(4), DType.INT32)

        val result = statsOps.cumSum(tensor)

        assertEquals(DType.INT64, result.dtype)
        assertEquals(4, result.size)
        assertTrue(result.shape.contentEquals(intArrayOf(4)))
    }

    @Test
    fun testArgMaxOperation() {
        val data = doubleArrayOf(1.0, 5.0, 3.0, 9.0, 2.0)
        val tensor = backend.createTensor(data, intArrayOf(5), DType.FLOAT64)

        val result = statsOps.argMax(tensor)

        assertEquals(DType.INT32, result.dtype)
        assertEquals(1, result.size)
        assertTrue(result.shape.contentEquals(intArrayOf()))
    }

    @Test
    fun testPercentileOperation() {
        val data = doubleArrayOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0)
        val tensor = backend.createTensor(data, intArrayOf(10), DType.FLOAT64)

        // Test 50th percentile (median)
        val median = statsOps.percentile(tensor, 50.0)
        assertEquals(DType.FLOAT64, median.dtype)
        assertEquals(1, median.size)

        // Test 25th percentile
        val q1 = statsOps.percentile(tensor, 25.0)
        assertEquals(DType.FLOAT64, q1.dtype)
        assertEquals(1, q1.size)

        // Test 75th percentile
        val q3 = statsOps.percentile(tensor, 75.0)
        assertEquals(DType.FLOAT64, q3.dtype)
        assertEquals(1, q3.size)
    }

    @Test
    fun testKeepDimsParameter() {
        val data = doubleArrayOf(1.0, 2.0, 3.0, 4.0)
        val tensor = backend.createTensor(data, intArrayOf(4), DType.FLOAT64)

        // Test without keepDims (default)
        val resultNormal = statsOps.mean(tensor, keepDims = false)
        assertTrue(resultNormal.shape.contentEquals(intArrayOf()))

        // Test with keepDims
        val resultKeepDims = statsOps.mean(tensor, keepDims = true)
        assertTrue(resultKeepDims.shape.contentEquals(intArrayOf(4)))
    }

    @Test
    fun testDifferentDataTypes() {
        // Test with boolean data
        val boolData = booleanArrayOf(true, false, true, true, false)
        val boolTensor = backend.createTensor(boolData, intArrayOf(5), DType.BOOL)

        val boolMean = statsOps.mean(boolTensor)
        assertEquals(DType.FLOAT64, boolMean.dtype)

        // Test with float data
        val floatData = floatArrayOf(1.0f, 2.0f, 3.0f)
        val floatTensor = backend.createTensor(floatData, intArrayOf(3), DType.FLOAT32)

        val floatSum = statsOps.sum(floatTensor)
        assertEquals(DType.FLOAT32, floatSum.dtype)
    }

    @Test
    fun testErrorHandling() {
        // Test empty tensor
        val emptyData = doubleArrayOf()
        val emptyTensor = backend.createTensor(emptyData, intArrayOf(0), DType.FLOAT64)

        assertFailsWith<IllegalArgumentException> {
            statsOps.mean(emptyTensor)
        }

        assertFailsWith<IllegalArgumentException> {
            statsOps.min(emptyTensor)
        }

        // Test invalid percentile
        val data = doubleArrayOf(1.0, 2.0, 3.0)
        val tensor = backend.createTensor(data, intArrayOf(3), DType.FLOAT64)

        assertFailsWith<IllegalArgumentException> {
            statsOps.percentile(tensor, -10.0)
        }

        assertFailsWith<IllegalArgumentException> {
            statsOps.percentile(tensor, 110.0)
        }
    }

    @Test
    fun testVarianceWithDdof() {
        val data = doubleArrayOf(1.0, 2.0, 3.0, 4.0, 5.0)
        val tensor = backend.createTensor(data, intArrayOf(5), DType.FLOAT64)

        // Test with different ddof values
        val variance0 = statsOps.variance(tensor, ddof = 0)
        assertEquals(DType.FLOAT64, variance0.dtype)

        val variance1 = statsOps.variance(tensor, ddof = 1)
        assertEquals(DType.FLOAT64, variance1.dtype)

        // Test error with ddof too large
        assertFailsWith<IllegalArgumentException> {
            statsOps.variance(tensor, ddof = 5)
        }
    }
}
