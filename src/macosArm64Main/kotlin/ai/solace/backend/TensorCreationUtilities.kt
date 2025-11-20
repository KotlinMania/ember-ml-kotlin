package ai.solace.ember.backend

import ai.solace.ember.tensor.common.DType
import ai.solace.ember.backend.storage.TensorStorage

/**
 * Tensor creation utilities for the OptimizedMegaTensorBackend.
 * 
 * This class provides factory methods for creating common tensor types,
 * similar to NumPy's array creation functions.
 */
class TensorCreationUtilities(private val backend: OptimizedMegaTensorBackend) {
    
    /**
     * Creates a tensor filled with zeros.
     * 
     * @param shape The shape of the tensor
     * @param dtype The data type of the tensor
     * @return A tensor filled with zeros
     */
    fun zeros(shape: IntArray, dtype: DType = DType.FLOAT32): Any {
        val totalSize = shape.fold(1) { acc, dim -> acc * dim }
        val storage = TensorStorage.createOptimalStorage(dtype, totalSize)
        
        // Fill with zeros (arrays are already zero-initialized in Kotlin)
        return OptimizedMegaTensorBackend.OptimizedMegaTensor(storage, shape, "cpu")
    }
    
    /**
     * Creates a tensor filled with ones.
     * 
     * @param shape The shape of the tensor
     * @param dtype The data type of the tensor
     * @return A tensor filled with ones
     */
    fun ones(shape: IntArray, dtype: DType = DType.FLOAT32): Any {
        val totalSize = shape.fold(1) { acc, dim -> acc * dim }
        val storage = TensorStorage.createOptimalStorage(dtype, totalSize)
        
        // Fill with ones
        for (i in 0 until totalSize) {
            setStorageValue(storage, i, getOneValue(dtype), dtype)
        }
        
        return OptimizedMegaTensorBackend.OptimizedMegaTensor(storage, shape, "cpu")
    }
    
    /**
     * Creates a tensor filled with a specific value.
     * 
     * @param shape The shape of the tensor
     * @param value The value to fill the tensor with
     * @param dtype The data type of the tensor
     * @return A tensor filled with the specified value
     */
    fun full(shape: IntArray, value: Any, dtype: DType = DType.FLOAT32): Any {
        val totalSize = shape.fold(1) { acc, dim -> acc * dim }
        val storage = TensorStorage.createOptimalStorage(dtype, totalSize)
        
        // Fill with the specified value
        for (i in 0 until totalSize) {
            setStorageValue(storage, i, value, dtype)
        }
        
        return OptimizedMegaTensorBackend.OptimizedMegaTensor(storage, shape, "cpu")
    }
    
    /**
     * Creates a 1D tensor with evenly spaced values.
     * 
     * @param start The starting value (inclusive)
     * @param stop The ending value (exclusive)
     * @param step The step between values (default: 1)
     * @param dtype The data type of the tensor
     * @return A 1D tensor with evenly spaced values
     */
    fun arange(start: Double, stop: Double, step: Double = 1.0, dtype: DType = DType.FLOAT64): Any {
        if (step == 0.0) {
            throw IllegalArgumentException("Step cannot be zero")
        }
        
        if ((step > 0 && start >= stop) || (step < 0 && start <= stop)) {
            // Empty range
            return OptimizedMegaTensorBackend.OptimizedMegaTensor(
                TensorStorage.createOptimalStorage(dtype, 0),
                intArrayOf(0),
                "cpu"
            )
        }
        
        val size = kotlin.math.ceil(kotlin.math.abs(stop - start) / kotlin.math.abs(step)).toInt()
        val storage = TensorStorage.createOptimalStorage(dtype, size)
        
        for (i in 0 until size) {
            val value = start + i * step
            setStorageValue(storage, i, value, dtype)
        }
        
        return OptimizedMegaTensorBackend.OptimizedMegaTensor(storage, intArrayOf(size), "cpu")
    }
    
    /**
     * Creates a 1D tensor with evenly spaced values over a specified interval.
     * 
     * @param start The starting value (inclusive)
     * @param stop The ending value (inclusive)
     * @param num The number of samples to generate
     * @param dtype The data type of the tensor
     * @return A 1D tensor with evenly spaced values
     */
    fun linspace(start: Double, stop: Double, num: Int, dtype: DType = DType.FLOAT64): Any {
        if (num <= 0) {
            throw IllegalArgumentException("Number of samples must be positive")
        }
        
        if (num == 1) {
            return full(intArrayOf(1), start, dtype)
        }
        
        val storage = TensorStorage.createOptimalStorage(dtype, num)
        val step = (stop - start) / (num - 1)
        
        for (i in 0 until num) {
            val value = start + i * step
            setStorageValue(storage, i, value, dtype)
        }
        
        return OptimizedMegaTensorBackend.OptimizedMegaTensor(storage, intArrayOf(num), "cpu")
    }
    
    /**
     * Creates an identity matrix.
     * 
     * @param n The size of the identity matrix (n x n)
     * @param dtype The data type of the tensor
     * @return An identity matrix
     */
    fun eye(n: Int, dtype: DType = DType.FLOAT32): Any {
        if (n <= 0) {
            throw IllegalArgumentException("Matrix size must be positive")
        }
        
        val totalSize = n * n
        val storage = TensorStorage.createOptimalStorage(dtype, totalSize)
        
        // Fill with zeros first (default), then set diagonal to ones
        for (i in 0 until n) {
            val diagonalIndex = i * n + i
            setStorageValue(storage, diagonalIndex, getOneValue(dtype), dtype)
        }
        
        return OptimizedMegaTensorBackend.OptimizedMegaTensor(storage, intArrayOf(n, n), "cpu")
    }
    
    /**
     * Creates a tensor with random values from a uniform distribution.
     * 
     * @param shape The shape of the tensor
     * @param low The lower bound (inclusive)
     * @param high The upper bound (exclusive)
     * @param dtype The data type of the tensor
     * @return A tensor with random values
     */
    fun randomUniform(shape: IntArray, low: Double = 0.0, high: Double = 1.0, dtype: DType = DType.FLOAT32): Any {
        val totalSize = shape.fold(1) { acc, dim -> acc * dim }
        val storage = TensorStorage.createOptimalStorage(dtype, totalSize)
        
        val random = kotlin.random.Random.Default
        val range = high - low
        
        for (i in 0 until totalSize) {
            val value = low + random.nextDouble() * range
            setStorageValue(storage, i, value, dtype)
        }
        
        return OptimizedMegaTensorBackend.OptimizedMegaTensor(storage, shape, "cpu")
    }
    
    /**
     * Creates a tensor with random values from a normal distribution.
     * 
     * @param shape The shape of the tensor
     * @param mean The mean of the distribution
     * @param std The standard deviation of the distribution
     * @param dtype The data type of the tensor
     * @return A tensor with normally distributed random values
     */
    fun randomNormal(shape: IntArray, mean: Double = 0.0, std: Double = 1.0, dtype: DType = DType.FLOAT32): Any {
        val totalSize = shape.fold(1) { acc, dim -> acc * dim }
        val storage = TensorStorage.createOptimalStorage(dtype, totalSize)
        
        val random = kotlin.random.Random.Default
        
        for (i in 0 until totalSize) {
            // Box-Muller transform for normal distribution
            val u1 = random.nextDouble()
            val u2 = random.nextDouble()
            val z0 = kotlin.math.sqrt(-2.0 * kotlin.math.ln(u1)) * kotlin.math.cos(2.0 * kotlin.math.PI * u2)
            val value = mean + std * z0
            setStorageValue(storage, i, value, dtype)
        }
        
        return OptimizedMegaTensorBackend.OptimizedMegaTensor(storage, shape, "cpu")
    }
    
    /**
     * Creates a tensor with random integer values.
     * 
     * @param shape The shape of the tensor
     * @param low The lower bound (inclusive)
     * @param high The upper bound (exclusive)
     * @param dtype The data type of the tensor (should be an integer type)
     * @return A tensor with random integer values
     */
    fun randomInt(shape: IntArray, low: Int, high: Int, dtype: DType = DType.INT32): Any {
        if (low >= high) {
            throw IllegalArgumentException("Low must be less than high")
        }
        
        val totalSize = shape.fold(1) { acc, dim -> acc * dim }
        val storage = TensorStorage.createOptimalStorage(dtype, totalSize)
        
        val random = kotlin.random.Random.Default
        val range = high - low
        
        for (i in 0 until totalSize) {
            val value = low + random.nextInt(range)
            setStorageValue(storage, i, value, dtype)
        }
        
        return OptimizedMegaTensorBackend.OptimizedMegaTensor(storage, shape, "cpu")
    }
    
    /**
     * Creates a tensor like another tensor (same shape) but filled with zeros.
     */
    fun zerosLike(tensor: Any): Any {
        val t = tensor as OptimizedMegaTensorBackend.OptimizedMegaTensor
        return zeros(t.shape, t.dtype)
    }
    
    /**
     * Creates a tensor like another tensor (same shape) but filled with ones.
     */
    fun onesLike(tensor: Any): Any {
        val t = tensor as OptimizedMegaTensorBackend.OptimizedMegaTensor
        return ones(t.shape, t.dtype)
    }
    
    /**
     * Creates a tensor like another tensor (same shape) but filled with a specific value.
     */
    fun fullLike(tensor: Any, value: Any): Any {
        val t = tensor as OptimizedMegaTensorBackend.OptimizedMegaTensor
        return full(t.shape, value, t.dtype)
    }
    
    // Helper functions
    
    private fun getOneValue(dtype: DType): Any {
        return when (dtype) {
            DType.BOOL -> true
            DType.UINT8 -> 1u.toUByte()
            DType.INT32 -> 1
            DType.INT64 -> 1L
            DType.FLOAT32 -> 1.0f
            DType.FLOAT64 -> 1.0
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
                throw UnsupportedOperationException("MegaNumber storage not yet implemented for tensor creation")
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