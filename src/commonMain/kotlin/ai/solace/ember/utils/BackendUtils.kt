package ai.solace.ember.utils

import ai.solace.ember.backend.BackendRegistry
import ai.solace.ember.tensor.common.EmberTensor
import ai.solace.ember.tensor.common.DType
import ai.solace.ember.tensor.common.EmberShape

/**
 * Backend utilities for Ember ML Kotlin.
 * 
 * This object provides utility functions for working with backends,
 * converting tensors, and performing common operations in a backend-agnostic way.
 */
object BackendUtils {
    
    /**
     * Get the current backend name.
     */
    fun getCurrentBackend(): String {
        return ai.solace.ember.backend.getBackend()
    }
    
    /**
     * Set the preferred backend if available.
     * 
     * @param backendName Name of the backend to set
     * @return The name of the actually set backend
     */
    fun setPreferredBackend(backendName: String? = null): String {
        if (backendName != null) {
            if (ai.solace.ember.backend.setBackend(backendName)) {
                return backendName
            }
        }
        
        // Fall back to auto-selection
        return ai.solace.ember.backend.autoSelectBackend()
    }
    
    /**
     * Create a tensor from primitive data.
     * 
     * @param data The data to create the tensor from
     * @param shape The shape of the tensor
     * @param dtype The data type of the tensor
     * @param device The device to create the tensor on
     * @return The created EmberTensor
     */
    fun createTensor(
        data: Any,
        shape: IntArray,
        dtype: DType,
        device: String = "cpu"
    ): EmberTensor {
        val backend = BackendRegistry.getCurrentBackend()
        val backendTensor = backend.createTensor(data, shape, dtype)
        return EmberTensor(
            shape = EmberShape(shape),
            dtype = dtype,
            device = device,
            requiresGrad = false,
            backendTensor = backendTensor
        )
    }
    
    /**
     * Create a zero tensor.
     * 
     * @param shape The shape of the tensor
     * @param dtype The data type of the tensor
     * @param device The device to create the tensor on
     * @return A zero tensor with the specified shape and dtype
     */
    fun zeros(shape: IntArray, dtype: DType, device: String = "cpu"): EmberTensor {
        val totalSize = shape.fold(1) { acc, dim -> acc * dim }
        val data = when (dtype) {
            DType.FLOAT32 -> FloatArray(totalSize) { 0.0f }
            DType.FLOAT64 -> DoubleArray(totalSize) { 0.0 }
            DType.INT32 -> IntArray(totalSize) { 0 }
            DType.INT64 -> LongArray(totalSize) { 0L }
            DType.UINT8 -> ByteArray(totalSize) { 0 }
            DType.BOOL -> BooleanArray(totalSize) { false }
        }
        return createTensor(data, shape, dtype, device)
    }
    
    /**
     * Create a ones tensor.
     * 
     * @param shape The shape of the tensor
     * @param dtype The data type of the tensor
     * @param device The device to create the tensor on
     * @return A ones tensor with the specified shape and dtype
     */
    fun ones(shape: IntArray, dtype: DType, device: String = "cpu"): EmberTensor {
        val totalSize = shape.fold(1) { acc, dim -> acc * dim }
        val data = when (dtype) {
            DType.FLOAT32 -> FloatArray(totalSize) { 1.0f }
            DType.FLOAT64 -> DoubleArray(totalSize) { 1.0 }
            DType.INT32 -> IntArray(totalSize) { 1 }
            DType.INT64 -> LongArray(totalSize) { 1L }
            DType.UINT8 -> ByteArray(totalSize) { 1 }
            DType.BOOL -> BooleanArray(totalSize) { true }
        }
        return createTensor(data, shape, dtype, device)
    }
    
    /**
     * Create an identity matrix.
     * 
     * @param n The size of the identity matrix (n x n)
     * @param dtype The data type of the tensor
     * @param device The device to create the tensor on
     * @return An identity matrix
     */
    fun eye(n: Int, dtype: DType = DType.FLOAT32, device: String = "cpu"): EmberTensor {
        val totalSize = n * n
        val data = when (dtype) {
            DType.FLOAT32 -> FloatArray(totalSize) { i -> if (i % (n + 1) == 0) 1.0f else 0.0f }
            DType.FLOAT64 -> DoubleArray(totalSize) { i -> if (i % (n + 1) == 0) 1.0 else 0.0 }
            DType.INT32 -> IntArray(totalSize) { i -> if (i % (n + 1) == 0) 1 else 0 }
            DType.INT64 -> LongArray(totalSize) { i -> if (i % (n + 1) == 0) 1L else 0L }
            else -> throw IllegalArgumentException("Identity matrix only supports numeric types")
        }
        return createTensor(data, intArrayOf(n, n), dtype, device)
    }
    
    /**
     * Check if a backend is available.
     * 
     * @param backendName The name of the backend to check
     * @return True if the backend is available, false otherwise
     */
    fun isBackendAvailable(backendName: String): Boolean {
        return ai.solace.ember.backend.isBackendAvailable(backendName)
    }
    
    /**
     * Get a list of all available backends.
     * 
     * @return List of available backend names
     */
    fun getAvailableBackends(): List<String> {
        return ai.solace.ember.backend.getAvailableBackends()
    }
}