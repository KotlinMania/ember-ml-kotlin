package ai.solace.emberml.backend

import ai.solace.emberml.tensor.common.EmberDType
import ai.solace.emberml.backend.storage.TensorStorage

/**
 * Array manipulation functions for the OptimizedMegaTensorBackend.
 * 
 * This class provides array manipulation operations including stacking,
 * concatenation, splitting, and reshaping operations that are essential
 * for tensor data manipulation.
 */
class ArrayManipulationOperations(private val backend: OptimizedMegaTensorBackend) {
    
    /**
     * Stacks tensors vertically (row-wise).
     */
    fun vstack(tensors: List<Any>): Any {
        if (tensors.isEmpty()) {
            throw IllegalArgumentException("Cannot vstack empty list of tensors")
        }
        
        val tensorList = tensors.map { it as OptimizedMegaTensorBackend.OptimizedMegaTensor }
        
        // Validate that all tensors have compatible shapes for vertical stacking
        val firstTensor = tensorList[0]
        val expectedDType = firstTensor.dtype
        
        for (i in 1 until tensorList.size) {
            val tensor = tensorList[i]
            if (tensor.dtype != expectedDType) {
                throw IllegalArgumentException("All tensors must have the same dtype for vstack")
            }
            
            // For vstack, all dimensions except the first must match
            if (firstTensor.shape.size != tensor.shape.size) {
                throw IllegalArgumentException("All tensors must have the same number of dimensions")
            }
            
            for (j in 1 until firstTensor.shape.size) {
                if (firstTensor.shape[j] != tensor.shape[j]) {
                    throw IllegalArgumentException("Tensors have incompatible shapes for vstack")
                }
            }
        }
        
        // Calculate result shape
        val totalRows = tensorList.sumOf { it.shape[0] }
        val resultShape = intArrayOf(totalRows, *firstTensor.shape.sliceArray(1 until firstTensor.shape.size))
        val totalSize = resultShape.fold(1) { acc, dim -> acc * dim }
        
        // Create result storage
        val resultStorage = TensorStorage.createOptimalStorage(expectedDType, totalSize)
        
        // Copy data from all tensors
        var currentOffset = 0
        for (tensor in tensorList) {
            for (i in 0 until tensor.size) {
                val value = getStorageValue(tensor.storage, i)
                setStorageValue(resultStorage, currentOffset + i, value, expectedDType)
            }
            currentOffset += tensor.size
        }
        
        return OptimizedMegaTensorBackend.OptimizedMegaTensor(resultStorage, resultShape, firstTensor.device)
    }
    
    /**
     * Stacks tensors horizontally (column-wise).
     */
    fun hstack(tensors: List<Any>): Any {
        if (tensors.isEmpty()) {
            throw IllegalArgumentException("Cannot hstack empty list of tensors")
        }
        
        val tensorList = tensors.map { it as OptimizedMegaTensorBackend.OptimizedMegaTensor }
        
        // Handle 1D tensors (concatenate them)
        if (tensorList[0].shape.size == 1) {
            return concatenate(tensors, axis = 0)
        }
        
        // For 2D+ tensors, validate compatible shapes
        val firstTensor = tensorList[0]
        val expectedDType = firstTensor.dtype
        
        for (i in 1 until tensorList.size) {
            val tensor = tensorList[i]
            if (tensor.dtype != expectedDType) {
                throw IllegalArgumentException("All tensors must have the same dtype for hstack")
            }
            
            if (firstTensor.shape.size != tensor.shape.size) {
                throw IllegalArgumentException("All tensors must have the same number of dimensions")
            }
            
            // For hstack, all dimensions except the second must match
            for (j in firstTensor.shape.indices) {
                if (j != 1 && firstTensor.shape[j] != tensor.shape[j]) {
                    throw IllegalArgumentException("Tensors have incompatible shapes for hstack")
                }
            }
        }
        
        // Calculate result shape
        val totalCols = tensorList.sumOf { it.shape[1] }
        val resultShape = intArrayOf(
            firstTensor.shape[0],
            totalCols,
            *firstTensor.shape.sliceArray(2 until firstTensor.shape.size)
        )
        val totalSize = resultShape.fold(1) { acc, dim -> acc * dim }
        
        // Create result storage
        val resultStorage = TensorStorage.createOptimalStorage(expectedDType, totalSize)
        
        // Copy data with proper column offsets for 2D case
        val rows = firstTensor.shape[0]
        var colOffset = 0
        
        for (tensor in tensorList) {
            val cols = tensor.shape[1]
            for (row in 0 until rows) {
                for (col in 0 until cols) {
                    val srcIndex = row * cols + col
                    val dstIndex = row * totalCols + (colOffset + col)
                    val value = getStorageValue(tensor.storage, srcIndex)
                    setStorageValue(resultStorage, dstIndex, value, expectedDType)
                }
            }
            colOffset += cols
        }
        
        return OptimizedMegaTensorBackend.OptimizedMegaTensor(resultStorage, resultShape, firstTensor.device)
    }
    
    /**
     * Concatenates tensors along a specified axis.
     */
    fun concatenate(tensors: List<Any>, axis: Int = 0): Any {
        if (tensors.isEmpty()) {
            throw IllegalArgumentException("Cannot concatenate empty list of tensors")
        }
        
        val tensorList = tensors.map { it as OptimizedMegaTensorBackend.OptimizedMegaTensor }
        val firstTensor = tensorList[0]
        
        if (axis < 0 || axis >= firstTensor.shape.size) {
            throw IllegalArgumentException("Axis $axis is out of bounds for tensor with ${firstTensor.shape.size} dimensions")
        }
        
        val expectedDType = firstTensor.dtype
        
        // Validate all tensors have compatible shapes
        for (i in 1 until tensorList.size) {
            val tensor = tensorList[i]
            if (tensor.dtype != expectedDType) {
                throw IllegalArgumentException("All tensors must have the same dtype")
            }
            
            if (firstTensor.shape.size != tensor.shape.size) {
                throw IllegalArgumentException("All tensors must have the same number of dimensions")
            }
            
            for (j in firstTensor.shape.indices) {
                if (j != axis && firstTensor.shape[j] != tensor.shape[j]) {
                    throw IllegalArgumentException("Tensors have incompatible shapes for concatenation along axis $axis")
                }
            }
        }
        
        // Calculate result shape
        val totalAlongAxis = tensorList.sumOf { it.shape[axis] }
        val resultShape = firstTensor.shape.copyOf()
        resultShape[axis] = totalAlongAxis
        val totalSize = resultShape.fold(1) { acc, dim -> acc * dim }
        
        // Create result storage
        val resultStorage = TensorStorage.createOptimalStorage(expectedDType, totalSize)
        
        // For simple 1D concatenation
        if (firstTensor.shape.size == 1) {
            var currentOffset = 0
            for (tensor in tensorList) {
                for (i in 0 until tensor.size) {
                    val value = getStorageValue(tensor.storage, i)
                    setStorageValue(resultStorage, currentOffset + i, value, expectedDType)
                }
                currentOffset += tensor.size
            }
        } else {
            // For multi-dimensional concatenation, this is a simplified implementation
            // A full implementation would require more complex indexing logic
            throw UnsupportedOperationException("Multi-dimensional concatenation not yet fully implemented")
        }
        
        return OptimizedMegaTensorBackend.OptimizedMegaTensor(resultStorage, resultShape, firstTensor.device)
    }
    
    /**
     * Repeats elements of a tensor.
     */
    fun repeat(tensor: Any, repeats: Int, axis: Int? = null): Any {
        val t = tensor as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        if (repeats < 0) {
            throw IllegalArgumentException("Repeats must be non-negative, got: $repeats")
        }
        
        if (repeats == 0) {
            // Return empty tensor with appropriate shape
            val resultStorage = TensorStorage.createOptimalStorage(t.dtype, 0)
            val resultShape = if (axis == null) intArrayOf(0) else {
                val newShape = t.shape.copyOf()
                newShape[axis] = 0
                newShape
            }
            return OptimizedMegaTensorBackend.OptimizedMegaTensor(resultStorage, resultShape, t.device)
        }
        
        if (axis == null) {
            // Repeat the flattened tensor
            val resultStorage = TensorStorage.createOptimalStorage(t.dtype, t.size * repeats)
            
            for (rep in 0 until repeats) {
                for (i in 0 until t.size) {
                    val value = getStorageValue(t.storage, i)
                    setStorageValue(resultStorage, rep * t.size + i, value, t.dtype)
                }
            }
            
            return OptimizedMegaTensorBackend.OptimizedMegaTensor(resultStorage, intArrayOf(t.size * repeats), t.device)
        } else {
            if (axis < 0 || axis >= t.shape.size) {
                throw IllegalArgumentException("Axis $axis is out of bounds for tensor with ${t.shape.size} dimensions")
            }
            
            // Repeat along specific axis (simplified implementation for 1D)
            if (t.shape.size == 1 && axis == 0) {
                val resultStorage = TensorStorage.createOptimalStorage(t.dtype, t.size * repeats)
                
                for (i in 0 until t.size) {
                    val value = getStorageValue(t.storage, i)
                    for (rep in 0 until repeats) {
                        setStorageValue(resultStorage, i * repeats + rep, value, t.dtype)
                    }
                }
                
                return OptimizedMegaTensorBackend.OptimizedMegaTensor(resultStorage, intArrayOf(t.size * repeats), t.device)
            } else {
                throw UnsupportedOperationException("Repeat along axis for multi-dimensional tensors not yet implemented")
            }
        }
    }
    
    /**
     * Tiles a tensor by repeating it along multiple axes.
     */
    fun tile(tensor: Any, reps: IntArray): Any {
        val t = tensor as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        if (reps.any { it < 0 }) {
            throw IllegalArgumentException("All repetition counts must be non-negative")
        }
        
        // For now, implement simple 1D tiling
        if (t.shape.size == 1 && reps.size == 1) {
            val repeats = reps[0]
            if (repeats == 0) {
                val resultStorage = TensorStorage.createOptimalStorage(t.dtype, 0)
                return OptimizedMegaTensorBackend.OptimizedMegaTensor(resultStorage, intArrayOf(0), t.device)
            }
            
            val resultStorage = TensorStorage.createOptimalStorage(t.dtype, t.size * repeats)
            
            for (rep in 0 until repeats) {
                for (i in 0 until t.size) {
                    val value = getStorageValue(t.storage, i)
                    setStorageValue(resultStorage, rep * t.size + i, value, t.dtype)
                }
            }
            
            return OptimizedMegaTensorBackend.OptimizedMegaTensor(resultStorage, intArrayOf(t.size * repeats), t.device)
        } else {
            throw UnsupportedOperationException("Multi-dimensional tiling not yet implemented")
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
    
    private fun setStorageValue(storage: TensorStorage, index: Int, value: Any, dtype: EmberDType) {
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
                throw UnsupportedOperationException("MegaNumber storage not yet implemented for array manipulation operations")
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