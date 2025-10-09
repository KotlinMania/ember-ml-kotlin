package ai.solace.ember.backend

import ai.solace.ember.tensor.common.DType
import ai.solace.ember.backend.storage.TensorStorage
import kotlin.math.*

/**
 * Statistical functions for the OptimizedMegaTensorBackend.
 * 
 * This class provides statistical operations including descriptive statistics,
 * aggregation functions, and data analysis operations that were missing
 * in the original implementation.
 */
class StatisticalOperations(private val backend: OptimizedMegaTensorBackend) {
    
    /**
     * Computes the mean (average) of tensor elements.
     */
    fun mean(tensor: Any, axis: IntArray? = null, keepDims: Boolean = false): Any {
        val t = tensor as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        if (axis != null) {
            // TODO: Implement axis-specific mean for multi-dimensional tensors
            throw UnsupportedOperationException("Axis-specific mean not yet implemented")
        }
        
        // Compute overall mean
        var sum = 0.0
        var count = 0
        
        for (i in 0 until t.size) {
            val value = getStorageValue(t.storage, i)
            sum += convertToDouble(value)
            count++
        }
        
        if (count == 0) {
            throw IllegalArgumentException("Cannot compute mean of empty tensor")
        }
        
        val meanValue = sum / count
        
        // Create scalar result tensor
        val resultShape = if (keepDims) t.shape else intArrayOf()
        val resultStorage = TensorStorage.createOptimalStorage(DType.FLOAT64, 1)
        setStorageValue(resultStorage, 0, meanValue, DType.FLOAT64)
        
        return OptimizedMegaTensorBackend.OptimizedMegaTensor(resultStorage, resultShape, t.device)
    }
    
    /**
     * Computes the variance of tensor elements.
     */
    fun variance(tensor: Any, axis: IntArray? = null, keepDims: Boolean = false, ddof: Int = 0): Any {
        val t = tensor as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        if (axis != null) {
            throw UnsupportedOperationException("Axis-specific variance not yet implemented")
        }
        
        // First compute mean
        val meanTensor = mean(tensor, axis, false) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        val meanValue = convertToDouble(getStorageValue(meanTensor.storage, 0))
        
        // Compute variance
        var sumSquaredDiff = 0.0
        var count = 0
        
        for (i in 0 until t.size) {
            val value = getStorageValue(t.storage, i)
            val diff = convertToDouble(value) - meanValue
            sumSquaredDiff += diff * diff
            count++
        }
        
        if (count <= ddof) {
            throw IllegalArgumentException("Cannot compute variance: insufficient data points relative to ddof")
        }
        
        val varianceValue = sumSquaredDiff / (count - ddof)
        
        // Create scalar result tensor
        val resultShape = if (keepDims) t.shape else intArrayOf()
        val resultStorage = TensorStorage.createOptimalStorage(DType.FLOAT64, 1)
        setStorageValue(resultStorage, 0, varianceValue, DType.FLOAT64)
        
        return OptimizedMegaTensorBackend.OptimizedMegaTensor(resultStorage, resultShape, t.device)
    }
    
    /**
     * Computes the standard deviation of tensor elements.
     */
    fun std(tensor: Any, axis: IntArray? = null, keepDims: Boolean = false, ddof: Int = 0): Any {
        val varianceTensor = variance(tensor, axis, keepDims, ddof) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        val varianceValue = convertToDouble(getStorageValue(varianceTensor.storage, 0))
        
        val stdValue = sqrt(varianceValue)
        
        // Update the storage with std value
        setStorageValue(varianceTensor.storage, 0, stdValue, DType.FLOAT64)
        
        return varianceTensor
    }
    
    /**
     * Computes the median of tensor elements.
     */
    fun median(tensor: Any, axis: IntArray? = null, keepDims: Boolean = false): Any {
        val t = tensor as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        if (axis != null) {
            throw UnsupportedOperationException("Axis-specific median not yet implemented")
        }
        
        // Collect all values and sort them
        val values = mutableListOf<Double>()
        for (i in 0 until t.size) {
            val value = getStorageValue(t.storage, i)
            values.add(convertToDouble(value))
        }
        
        if (values.isEmpty()) {
            throw IllegalArgumentException("Cannot compute median of empty tensor")
        }
        
        values.sort()
        
        val medianValue = if (values.size % 2 == 0) {
            // Even number of elements - average of middle two
            val mid1 = values[values.size / 2 - 1]
            val mid2 = values[values.size / 2]
            (mid1 + mid2) / 2.0
        } else {
            // Odd number of elements - middle element
            values[values.size / 2]
        }
        
        // Create scalar result tensor
        val resultShape = if (keepDims) t.shape else intArrayOf()
        val resultStorage = TensorStorage.createOptimalStorage(DType.FLOAT64, 1)
        setStorageValue(resultStorage, 0, medianValue, DType.FLOAT64)
        
        return OptimizedMegaTensorBackend.OptimizedMegaTensor(resultStorage, resultShape, t.device)
    }
    
    /**
     * Computes the minimum value of tensor elements.
     */
    fun min(tensor: Any, axis: IntArray? = null, keepDims: Boolean = false): Any {
        val t = tensor as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        if (axis != null) {
            throw UnsupportedOperationException("Axis-specific min not yet implemented")
        }
        
        if (t.size == 0) {
            throw IllegalArgumentException("Cannot compute min of empty tensor")
        }
        
        var minValue = convertToDouble(getStorageValue(t.storage, 0))
        
        for (i in 1 until t.size) {
            val value = getStorageValue(t.storage, i)
            val doubleValue = convertToDouble(value)
            if (doubleValue < minValue) {
                minValue = doubleValue
            }
        }
        
        // Create scalar result tensor with appropriate type
        val resultShape = if (keepDims) t.shape else intArrayOf()
        val resultStorage = TensorStorage.createOptimalStorage(t.dtype, 1)
        setStorageValue(resultStorage, 0, minValue, t.dtype)
        
        return OptimizedMegaTensorBackend.OptimizedMegaTensor(resultStorage, resultShape, t.device)
    }
    
    /**
     * Computes the maximum value of tensor elements.
     */
    fun max(tensor: Any, axis: IntArray? = null, keepDims: Boolean = false): Any {
        val t = tensor as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        if (axis != null) {
            throw UnsupportedOperationException("Axis-specific max not yet implemented")
        }
        
        if (t.size == 0) {
            throw IllegalArgumentException("Cannot compute max of empty tensor")
        }
        
        var maxValue = convertToDouble(getStorageValue(t.storage, 0))
        
        for (i in 1 until t.size) {
            val value = getStorageValue(t.storage, i)
            val doubleValue = convertToDouble(value)
            if (doubleValue > maxValue) {
                maxValue = doubleValue
            }
        }
        
        // Create scalar result tensor with appropriate type
        val resultShape = if (keepDims) t.shape else intArrayOf()
        val resultStorage = TensorStorage.createOptimalStorage(t.dtype, 1)
        setStorageValue(resultStorage, 0, maxValue, t.dtype)
        
        return OptimizedMegaTensorBackend.OptimizedMegaTensor(resultStorage, resultShape, t.device)
    }
    
    /**
     * Computes the sum of tensor elements.
     */
    fun sum(tensor: Any, axis: IntArray? = null, keepDims: Boolean = false): Any {
        val t = tensor as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        if (axis != null) {
            throw UnsupportedOperationException("Axis-specific sum not yet implemented")
        }
        
        var sum = 0.0
        
        for (i in 0 until t.size) {
            val value = getStorageValue(t.storage, i)
            sum += convertToDouble(value)
        }
        
        // Create scalar result tensor - promote to appropriate type
        val resultDType = when (t.dtype) {
            DType.BOOL, DType.UINT8, DType.INT32 -> DType.INT64
            DType.INT64 -> DType.INT64
            DType.FLOAT32 -> DType.FLOAT32
            DType.FLOAT64 -> DType.FLOAT64
        }
        
        val resultShape = if (keepDims) t.shape else intArrayOf()
        val resultStorage = TensorStorage.createOptimalStorage(resultDType, 1)
        setStorageValue(resultStorage, 0, sum, resultDType)
        
        return OptimizedMegaTensorBackend.OptimizedMegaTensor(resultStorage, resultShape, t.device)
    }
    
    /**
     * Computes the cumulative sum of tensor elements.
     */
    fun cumSum(tensor: Any, axis: Int? = null): Any {
        val t = tensor as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        if (axis != null) {
            throw UnsupportedOperationException("Axis-specific cumsum not yet implemented")
        }
        
        // For flattened cumulative sum
        val resultDType = when (t.dtype) {
            DType.BOOL, DType.UINT8, DType.INT32 -> DType.INT64
            DType.INT64 -> DType.INT64
            DType.FLOAT32 -> DType.FLOAT32
            DType.FLOAT64 -> DType.FLOAT64
        }
        
        val resultStorage = TensorStorage.createOptimalStorage(resultDType, t.size)
        var cumulativeSum = 0.0
        
        for (i in 0 until t.size) {
            val value = getStorageValue(t.storage, i)
            cumulativeSum += convertToDouble(value)
            setStorageValue(resultStorage, i, cumulativeSum, resultDType)
        }
        
        return OptimizedMegaTensorBackend.OptimizedMegaTensor(resultStorage, t.shape, t.device)
    }
    
    /**
     * Finds the indices of the maximum values along an axis.
     */
    fun argMax(tensor: Any, axis: Int? = null): Any {
        val t = tensor as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        if (axis != null) {
            throw UnsupportedOperationException("Axis-specific argmax not yet implemented")
        }
        
        if (t.size == 0) {
            throw IllegalArgumentException("Cannot compute argmax of empty tensor")
        }
        
        var maxValue = convertToDouble(getStorageValue(t.storage, 0))
        var maxIndex = 0
        
        for (i in 1 until t.size) {
            val value = getStorageValue(t.storage, i)
            val doubleValue = convertToDouble(value)
            if (doubleValue > maxValue) {
                maxValue = doubleValue
                maxIndex = i
            }
        }
        
        // Create scalar result tensor with index
        val resultStorage = TensorStorage.createOptimalStorage(DType.INT32, 1)
        setStorageValue(resultStorage, 0, maxIndex, DType.INT32)
        
        return OptimizedMegaTensorBackend.OptimizedMegaTensor(resultStorage, intArrayOf(), t.device)
    }
    
    /**
     * Computes specified percentile of tensor elements.
     */
    fun percentile(tensor: Any, q: Double, axis: IntArray? = null, keepDims: Boolean = false): Any {
        val t = tensor as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        if (axis != null) {
            throw UnsupportedOperationException("Axis-specific percentile not yet implemented")
        }
        
        if (q < 0.0 || q > 100.0) {
            throw IllegalArgumentException("Percentile must be between 0 and 100, got: $q")
        }
        
        // Collect all values and sort them
        val values = mutableListOf<Double>()
        for (i in 0 until t.size) {
            val value = getStorageValue(t.storage, i)
            values.add(convertToDouble(value))
        }
        
        if (values.isEmpty()) {
            throw IllegalArgumentException("Cannot compute percentile of empty tensor")
        }
        
        values.sort()
        
        // Calculate percentile using linear interpolation
        val index = (q / 100.0) * (values.size - 1)
        val percentileValue = if (index.isInteger()) {
            values[index.toInt()]
        } else {
            val lowerIndex = floor(index).toInt()
            val upperIndex = ceil(index).toInt()
            val weight = index - lowerIndex
            values[lowerIndex] * (1 - weight) + values[upperIndex] * weight
        }
        
        // Create scalar result tensor
        val resultShape = if (keepDims) t.shape else intArrayOf()
        val resultStorage = TensorStorage.createOptimalStorage(DType.FLOAT64, 1)
        setStorageValue(resultStorage, 0, percentileValue, DType.FLOAT64)
        
        return OptimizedMegaTensorBackend.OptimizedMegaTensor(resultStorage, resultShape, t.device)
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
                throw UnsupportedOperationException("MegaNumber storage not yet implemented for statistical operations")
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
    
    private fun Double.isInteger(): Boolean {
        return this == floor(this)
    }
}