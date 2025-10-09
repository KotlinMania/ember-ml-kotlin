/**
 * # Tensor Module
 *
 * The `ai.solace.ember.tensor` module provides a backend-agnostic tensor implementation that works with any backend
 * using the backend abstraction layer.
 *
 * ## Overview
 *
 * The tensor module is designed to provide a consistent API for tensor operations across different backends.
 * It consists of the following components:
 *
 * - `EmberTensor`: A backend-agnostic tensor class that delegates operations to the current backend
 * - `DType`: A backend-agnostic data type class that represents data types across different backends
 * - `EmberShape`: A class representing the shape of a tensor
 * - Common tensor operations: Creation, manipulation, and conversion functions
 *
 * ## Architecture
 *
 * The tensor module follows the backend abstraction architecture of Ember ML:
 *
 * 1. **Frontend Abstractions**: The `ai.solace.ember.tensor` module provides abstract interfaces and common implementations
 * 2. **Backend Implementations**: The actual implementations reside in the backend directory, with specific implementations for each supported backend
 * 3. **Dispatch Mechanism**: The frontend abstractions dispatch calls to the appropriate backend implementation based on the currently selected backend
 *
 * ## Function-First Design
 *
 * The tensor operations in Ember ML Kotlin follow a function-first design pattern, where each operation
 * is implemented as a standalone function that can be called directly or through a method on a tensor class.
 *
 * For example, the `cast()` operation can be called in two ways:
 *
 * ```kotlin
 * // As a standalone function
 * import ai.solace.ember.tensor.ops.casting.cast
 * val result = cast(tensor, dtype)
 *
 * // As a method on EmberTensor
 * val result = tensor.cast(dtype)
 * ```
 *
 * ## EmberTensor
 *
 * The `EmberTensor` class is a backend-agnostic tensor implementation that delegates operations to the current backend.
 * It provides a consistent API for tensor operations across different backends.
 *
 * ### Creating Tensors
 *
 * ```kotlin
 * import ai.solace.ember.tensor.common.EmberTensor
 * import ai.solace.ember.tensor.common.float32
 *
 * // Create a tensor from a list
 * val tensor = EmberTensor(listOf(listOf(1, 2, 3), listOf(4, 5, 6)))
 *
 * // Create a tensor with a specific data type
 * val tensor = EmberTensor(listOf(listOf(1, 2, 3), listOf(4, 5, 6)), dtype = float32)
 *
 * // Create a tensor on a specific device
 * val tensor = EmberTensor(listOf(listOf(1, 2, 3), listOf(4, 5, 6)), device = "cuda")
 *
 * // Create a tensor that requires gradients
 * val tensor = EmberTensor(listOf(listOf(1, 2, 3), listOf(4, 5, 6)), requiresGrad = true)
 * ```
 *
 * ### Tensor Properties
 *
 * ```kotlin
 * // Get the shape of a tensor
 * val shape = tensor.shape  // EmberShape(2, 3)
 *
 * // Get the data type of a tensor
 * val dtype = tensor.dtype  // int64
 *
 * // Get the device of a tensor
 * val device = tensor.device  // "cpu"
 *
 * // Get whether the tensor requires gradients
 * val requiresGrad = tensor.requiresGrad  // false
 *
 * // Get the number of dimensions of a tensor
 * val ndim = tensor.ndim  // 2
 *
 * // Get the total number of elements in a tensor
 * val size = tensor.size  // 6
 * ```
 *
 * ### Tensor Operations
 *
 * ```kotlin
 * // Cast a tensor to a different data type
 * val floatTensor = tensor.cast(float32)
 *
 * // Reshape a tensor
 * val reshapedTensor = tensor.reshape(EmberShape(3, 2))
 *
 * // Transpose a tensor
 * val transposedTensor = tensor.transpose()
 * ```
 */
package ai.solace.ember.tensor
