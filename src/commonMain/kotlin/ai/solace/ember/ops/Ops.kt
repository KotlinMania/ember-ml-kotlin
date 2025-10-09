package ai.solace.ember.ops

import ai.solace.ember.backend.BackendRegistry
import ai.solace.ember.tensor.common.EmberTensor
import ai.solace.ember.tensor.common.EmberShape
import ai.solace.ember.tensor.common.DType

/**
 * Main operations dispatch object.
 * 
 * This object provides the primary interface for operations in Ember ML Kotlin.
 * It dispatches calls to the currently active backend while maintaining a 
 * consistent API across different backends.
 */
object Ops {
    
    // === Backend Management ===
    
    /**
     * Get the name of the current active backend.
     */
    fun getBackend(): String = ai.solace.ember.backend.getBackend()
    
    /**
     * Set the active backend.
     */
    fun setBackend(name: String): Boolean = ai.solace.ember.backend.setBackend(name)
    
    /**
     * Automatically select and set the best backend based on hardware.
     */
    fun autoSelectBackend(): String = ai.solace.ember.backend.autoSelectBackend()
    
    // === Helper Functions ===
    
    /**
     * Extract the backend tensor from an EmberTensor.
     */
    private fun extractBackendTensor(tensor: EmberTensor): Any = tensor.backendTensor
    
    /**
     * Wrap a backend tensor in an EmberTensor.
     */
    private fun wrapBackendTensor(backendTensor: Any): EmberTensor {
        val backend = BackendRegistry.getCurrentBackend()
        val shape = EmberShape(backend.getTensorShape(backendTensor))
        val dtype = backend.getTensorDType(backendTensor)
        val device = backend.getTensorDevice(backendTensor)
        return EmberTensor(shape, dtype, device, false, backendTensor)
    }
    
    // === Basic Arithmetic Operations ===
    
    /**
     * Element-wise addition of tensors.
     */
    fun add(x: EmberTensor, y: EmberTensor): EmberTensor {
        val backend = BackendRegistry.getCurrentBackend()
        val result = backend.add(extractBackendTensor(x), extractBackendTensor(y))
        return wrapBackendTensor(result)
    }
    
    /**
     * Element-wise subtraction of tensors.
     */
    fun subtract(x: EmberTensor, y: EmberTensor): EmberTensor {
        val backend = BackendRegistry.getCurrentBackend()
        val result = backend.subtract(extractBackendTensor(x), extractBackendTensor(y))
        return wrapBackendTensor(result)
    }
    
    /**
     * Element-wise multiplication of tensors.
     */
    fun multiply(x: EmberTensor, y: EmberTensor): EmberTensor {
        val backend = BackendRegistry.getCurrentBackend()
        val result = backend.multiply(extractBackendTensor(x), extractBackendTensor(y))
        return wrapBackendTensor(result)
    }
    
    /**
     * Element-wise division of tensors.
     */
    fun divide(x: EmberTensor, y: EmberTensor): EmberTensor {
        val backend = BackendRegistry.getCurrentBackend()
        val result = backend.divide(extractBackendTensor(x), extractBackendTensor(y))
        return wrapBackendTensor(result)
    }
    
    /**
     * Matrix multiplication of tensors.
     */
    fun matmul(x: EmberTensor, y: EmberTensor): EmberTensor {
        val backend = BackendRegistry.getCurrentBackend()
        val result = backend.matmul(extractBackendTensor(x), extractBackendTensor(y))
        return wrapBackendTensor(result)
    }
    
    // === Tensor Manipulation ===
    
    /**
     * Cast a tensor to a different data type.
     */
    fun cast(tensor: EmberTensor, dtype: DType): EmberTensor {
        val backend = BackendRegistry.getCurrentBackend()
        val result = backend.cast(extractBackendTensor(tensor), dtype)
        return wrapBackendTensor(result)
    }
    
    /**
     * Reshape a tensor to a new shape.
     */
    fun reshape(tensor: EmberTensor, newShape: IntArray): EmberTensor {
        val backend = BackendRegistry.getCurrentBackend()
        val result = backend.reshape(extractBackendTensor(tensor), newShape)
        return wrapBackendTensor(result)
    }
    
    /**
     * Transpose a tensor.
     */
    fun transpose(tensor: EmberTensor, axes: IntArray? = null): EmberTensor {
        val backend = BackendRegistry.getCurrentBackend()
        val result = backend.transpose(extractBackendTensor(tensor), axes)
        return wrapBackendTensor(result)
    }
    
    // === Device Operations ===
    
    /**
     * Move a tensor to a different device.
     */
    fun toDevice(tensor: EmberTensor, device: String): EmberTensor {
        val backend = BackendRegistry.getCurrentBackend()
        val result = backend.toDevice(extractBackendTensor(tensor), device)
        return wrapBackendTensor(result)
    }
    
    /**
     * Get a list of available devices.
     */
    fun getAvailableDevices(): List<String> {
        val backend = BackendRegistry.getCurrentBackend()
        return backend.getAvailableDevices()
    }
    
    /**
     * Set the default device for tensor operations.
     */
    fun setDefaultDevice(device: String) {
        val backend = BackendRegistry.getCurrentBackend()
        backend.setDefaultDevice(device)
    }
    
    /**
     * Get the default device for tensor operations.
     */
    fun getDefaultDevice(): String {
        val backend = BackendRegistry.getCurrentBackend()
        return backend.getDefaultDevice()
    }
}