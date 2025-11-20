package ai.solace.ember.backend

import ai.solace.ember.tensor.common.DType
import ai.solace.ember.backend.storage.TensorStorage
import kotlin.math.*

/**
 * Linear algebra functions for the OptimizedMegaTensorBackend.
 * 
 * This class provides basic linear algebra operations including matrix operations,
 * decompositions, and numerical linear algebra functions that are critical
 * for machine learning applications.
 */
class LinearAlgebraOperations(private val backend: OptimizedMegaTensorBackend) {
    
    /**
     * Computes the dot product of two vectors or matrix multiplication.
     */
    fun dot(tensor1: Any, tensor2: Any): Any {
        val t1 = tensor1 as OptimizedMegaTensorBackend.OptimizedMegaTensor
        val t2 = tensor2 as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        // For now, implement 1D vector dot product
        if (t1.shape.size != 1 || t2.shape.size != 1) {
            throw UnsupportedOperationException("Only 1D vector dot product is currently implemented")
        }
        
        if (t1.shape[0] != t2.shape[0]) {
            throw IllegalArgumentException("Vector dimensions must match: ${t1.shape[0]} vs ${t2.shape[0]}")
        }
        
        var dotProduct = 0.0
        
        for (i in 0 until t1.size) {
            val val1 = convertToDouble(getStorageValue(t1.storage, i))
            val val2 = convertToDouble(getStorageValue(t2.storage, i))
            dotProduct += val1 * val2
        }
        
        // Create scalar result
        val resultDType = when {
            t1.dtype == DType.FLOAT64 || t2.dtype == DType.FLOAT64 -> DType.FLOAT64
            t1.dtype == DType.FLOAT32 || t2.dtype == DType.FLOAT32 -> DType.FLOAT32
            else -> DType.FLOAT64
        }
        
        val resultStorage = TensorStorage.createOptimalStorage(resultDType, 1)
        setStorageValue(resultStorage, 0, dotProduct, resultDType)
        
        return OptimizedMegaTensorBackend.OptimizedMegaTensor(resultStorage, intArrayOf(), t1.device)
    }
    
    /**
     * Computes the matrix multiplication of two tensors.
     */
    fun matmul(tensor1: Any, tensor2: Any): Any {
        val t1 = tensor1 as OptimizedMegaTensorBackend.OptimizedMegaTensor
        val t2 = tensor2 as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        // Basic 2D matrix multiplication
        if (t1.shape.size != 2 || t2.shape.size != 2) {
            throw UnsupportedOperationException("Only 2D matrix multiplication is currently implemented")
        }
        
        val m = t1.shape[0] // rows of t1
        val n = t1.shape[1] // cols of t1 = rows of t2
        val p = t2.shape[1] // cols of t2
        
        if (t1.shape[1] != t2.shape[0]) {
            throw IllegalArgumentException("Matrix dimensions incompatible: [${t1.shape[0]}, ${t1.shape[1]}] x [${t2.shape[0]}, ${t2.shape[1]}]")
        }
        
        val resultDType = when {
            t1.dtype == DType.FLOAT64 || t2.dtype == DType.FLOAT64 -> DType.FLOAT64
            t1.dtype == DType.FLOAT32 || t2.dtype == DType.FLOAT32 -> DType.FLOAT32
            else -> DType.FLOAT64
        }
        
        val resultStorage = TensorStorage.createOptimalStorage(resultDType, m * p)
        
        // Perform matrix multiplication
        for (i in 0 until m) {
            for (j in 0 until p) {
                var sum = 0.0
                for (k in 0 until n) {
                    val val1 = convertToDouble(getStorageValue(t1.storage, i * n + k))
                    val val2 = convertToDouble(getStorageValue(t2.storage, k * p + j))
                    sum += val1 * val2
                }
                setStorageValue(resultStorage, i * p + j, sum, resultDType)
            }
        }
        
        return OptimizedMegaTensorBackend.OptimizedMegaTensor(resultStorage, intArrayOf(m, p), t1.device)
    }
    
    /**
     * Computes the transpose of a matrix.
     */
    fun transpose(tensor: Any, axes: IntArray? = null): Any {
        val t = tensor as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        if (axes != null) {
            throw UnsupportedOperationException("Custom axes for transpose not yet implemented")
        }
        
        when (t.shape.size) {
            1 -> {
                // For 1D tensors, transpose is the same
                return t
            }
            2 -> {
                // 2D matrix transpose
                val rows = t.shape[0]
                val cols = t.shape[1]
                
                val resultStorage = TensorStorage.createOptimalStorage(t.dtype, t.size)
                
                for (i in 0 until rows) {
                    for (j in 0 until cols) {
                        val value = getStorageValue(t.storage, i * cols + j)
                        setStorageValue(resultStorage, j * rows + i, value, t.dtype)
                    }
                }
                
                return OptimizedMegaTensorBackend.OptimizedMegaTensor(resultStorage, intArrayOf(cols, rows), t.device)
            }
            else -> {
                throw UnsupportedOperationException("Transpose for tensors with ${t.shape.size} dimensions not yet implemented")
            }
        }
    }
    
    /**
     * Computes the determinant of a square matrix.
     */
    fun determinant(tensor: Any): Any {
        val t = tensor as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        if (t.shape.size != 2) {
            throw IllegalArgumentException("Determinant requires a 2D matrix")
        }
        
        if (t.shape[0] != t.shape[1]) {
            throw IllegalArgumentException("Determinant requires a square matrix, got shape: [${t.shape[0]}, ${t.shape[1]}]")
        }
        
        val n = t.shape[0]
        
        val det = when (n) {
            1 -> {
                convertToDouble(getStorageValue(t.storage, 0))
            }
            2 -> {
                val a = convertToDouble(getStorageValue(t.storage, 0))
                val b = convertToDouble(getStorageValue(t.storage, 1))
                val c = convertToDouble(getStorageValue(t.storage, 2))
                val d = convertToDouble(getStorageValue(t.storage, 3))
                a * d - b * c
            }
            3 -> {
                // Use cofactor expansion for 3x3
                val matrix = Array(3) { i ->
                    Array(3) { j ->
                        convertToDouble(getStorageValue(t.storage, i * 3 + j))
                    }
                }
                
                matrix[0][0] * (matrix[1][1] * matrix[2][2] - matrix[1][2] * matrix[2][1]) -
                matrix[0][1] * (matrix[1][0] * matrix[2][2] - matrix[1][2] * matrix[2][0]) +
                matrix[0][2] * (matrix[1][0] * matrix[2][1] - matrix[1][1] * matrix[2][0])
            }
            else -> {
                throw UnsupportedOperationException("Determinant for matrices larger than 3x3 not yet implemented")
            }
        }
        
        // Create scalar result
        val resultStorage = TensorStorage.createOptimalStorage(DType.FLOAT64, 1)
        setStorageValue(resultStorage, 0, det, DType.FLOAT64)
        
        return OptimizedMegaTensorBackend.OptimizedMegaTensor(resultStorage, intArrayOf(), t.device)
    }
    
    /**
     * Computes the trace (sum of diagonal elements) of a matrix.
     */
    fun trace(tensor: Any): Any {
        val t = tensor as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        if (t.shape.size != 2) {
            throw IllegalArgumentException("Trace requires a 2D matrix")
        }
        
        val rows = t.shape[0]
        val cols = t.shape[1]
        val minDim = minOf(rows, cols)
        
        var traceSum = 0.0
        
        for (i in 0 until minDim) {
            val value = convertToDouble(getStorageValue(t.storage, i * cols + i))
            traceSum += value
        }
        
        // Create scalar result with same dtype as input
        val resultStorage = TensorStorage.createOptimalStorage(t.dtype, 1)
        setStorageValue(resultStorage, 0, traceSum, t.dtype)
        
        return OptimizedMegaTensorBackend.OptimizedMegaTensor(resultStorage, intArrayOf(), t.device)
    }
    
    /**
     * Computes the Frobenius norm of a matrix.
     */
    fun norm(tensor: Any, ord: String = "fro"): Any {
        val t = tensor as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        when (ord) {
            "fro" -> {
                // Frobenius norm: sqrt(sum of squares of all elements)
                var sumSquares = 0.0
                
                for (i in 0 until t.size) {
                    val value = convertToDouble(getStorageValue(t.storage, i))
                    sumSquares += value * value
                }
                
                val normValue = sqrt(sumSquares)
                
                // Create scalar result
                val resultStorage = TensorStorage.createOptimalStorage(DType.FLOAT64, 1)
                setStorageValue(resultStorage, 0, normValue, DType.FLOAT64)
                
                return OptimizedMegaTensorBackend.OptimizedMegaTensor(resultStorage, intArrayOf(), t.device)
            }
            else -> {
                throw UnsupportedOperationException("Norm type '$ord' not yet implemented")
            }
        }
    }
    
    /**
     * Computes the inverse of a square matrix (basic implementation for small matrices).
     */
    fun inverse(tensor: Any): Any {
        val t = tensor as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        if (t.shape.size != 2) {
            throw IllegalArgumentException("Inverse requires a 2D matrix")
        }
        
        if (t.shape[0] != t.shape[1]) {
            throw IllegalArgumentException("Inverse requires a square matrix")
        }
        
        val n = t.shape[0]
        
        when (n) {
            1 -> {
                val value = convertToDouble(getStorageValue(t.storage, 0))
                if (abs(value) < 1e-10) {
                    throw ArithmeticException("Matrix is singular (not invertible)")
                }
                
                val resultStorage = TensorStorage.createOptimalStorage(DType.FLOAT64, 1)
                setStorageValue(resultStorage, 0, 1.0 / value, DType.FLOAT64)
                
                return OptimizedMegaTensorBackend.OptimizedMegaTensor(resultStorage, intArrayOf(1, 1), t.device)
            }
            2 -> {
                val a = convertToDouble(getStorageValue(t.storage, 0))
                val b = convertToDouble(getStorageValue(t.storage, 1))
                val c = convertToDouble(getStorageValue(t.storage, 2))
                val d = convertToDouble(getStorageValue(t.storage, 3))
                
                val det = a * d - b * c
                
                if (abs(det) < 1e-10) {
                    throw ArithmeticException("Matrix is singular (not invertible)")
                }
                
                val resultStorage = TensorStorage.createOptimalStorage(DType.FLOAT64, 4)
                setStorageValue(resultStorage, 0, d / det, DType.FLOAT64)
                setStorageValue(resultStorage, 1, -b / det, DType.FLOAT64)
                setStorageValue(resultStorage, 2, -c / det, DType.FLOAT64)
                setStorageValue(resultStorage, 3, a / det, DType.FLOAT64)
                
                return OptimizedMegaTensorBackend.OptimizedMegaTensor(resultStorage, intArrayOf(2, 2), t.device)
            }
            else -> {
                throw UnsupportedOperationException("Matrix inversion for ${n}x${n} matrices not yet implemented")
            }
        }
    }
    
    // Helper functions
    
    private fun getStorageValue(storage: TensorStorage, index: Int): Any {
        return when (storage) {
            is TensorStorage.PackedBooleanStorage -> storage.get(index)
            is TensorStorage.NativeUByteStorage -> storage.get(index)
            is TensorStorage.NativeIntStorage -> storage.get(index)
            is TensorStorage.NativeLongStorage -> storage.get(index)
            is TensorStorage.NativeFloatStorage -> storage.get(index)
            is TensorStorage.NativeDoubleStorage -> storage.get(index)
            is TensorStorage.MegaNumberStorage -> storage.get(index)
        }
    }
    
    private fun setStorageValue(storage: TensorStorage, index: Int, value: Any, dtype: DType) {
        when (storage) {
            is TensorStorage.PackedBooleanStorage -> {
                storage.set(index, convertToBoolean(value))
            }
            is TensorStorage.NativeUByteStorage -> {
                storage.set(index, convertToUByte(value))
            }
            is TensorStorage.NativeIntStorage -> {
                storage.set(index, convertToInt(value))
            }
            is TensorStorage.NativeLongStorage -> {
                storage.set(index, convertToLong(value))
            }
            is TensorStorage.NativeFloatStorage -> {
                storage.set(index, convertToFloat(value))
            }
            is TensorStorage.NativeDoubleStorage -> {
                storage.set(index, convertToDouble(value))
            }
            is TensorStorage.MegaNumberStorage -> {
                throw UnsupportedOperationException("MegaNumber storage not yet implemented for linear algebra operations")
            }
        }
    }
    
    private fun convertToBoolean(value: Any): Boolean {
        return when (value) {
            is Boolean -> value
            is Number -> value.toDouble() != 0.0
            else -> false
        }
    }
    
    private fun convertToUByte(value: Any): UByte {
        return when (value) {
            is Number -> value.toInt().coerceIn(0, 255).toUByte()
            is Boolean -> if (value) 1u else 0u
            else -> 0u
        }
    }
    
    private fun convertToInt(value: Any): Int {
        return when (value) {
            is Number -> value.toInt()
            is Boolean -> if (value) 1 else 0
            else -> 0
        }
    }
    
    private fun convertToLong(value: Any): Long {
        return when (value) {
            is Number -> value.toLong()
            is Boolean -> if (value) 1L else 0L
            else -> 0L
        }
    }
    
    private fun convertToFloat(value: Any): Float {
        return when (value) {
            is Number -> value.toFloat()
            is Boolean -> if (value) 1f else 0f
            else -> 0f
        }
    }
    
    private fun convertToDouble(value: Any): Double {
        return when (value) {
            is Number -> value.toDouble()
            is Boolean -> if (value) 1.0 else 0.0
            else -> 0.0
        }
    }
}