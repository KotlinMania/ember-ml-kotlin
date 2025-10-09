/**
 * # Metal Backend Package
 *
 * The `ai.solace.ember.backend.metal` package provides Metal GPU acceleration for
 * tensor operations on Apple platforms (macOS, iOS).
 *
 * ## Overview
 *
 * This package implements a high-performance backend for Ember ML Kotlin that leverages
 * Apple's Metal framework for GPU computation. It provides:
 *
 * - GPU-accelerated tensor operations
 * - Metal kernel implementations for common operations
 * - SVD decomposition using the power method
 * - Platform abstraction for cross-platform compatibility
 *
 * ## Key Components
 *
 * ### Backend Implementation
 * - [MetalBackend]: Main backend implementation that integrates with the Ember ML backend system
 * - [MetalContext]: Abstraction over Metal device and command queue management
 * - [MetalTensor]: Tensor implementation backed by Metal buffers
 *
 * ### Operations
 * - [MetalOperations]: Basic tensor operations (add, multiply, matmul, etc.)
 * - [MetalLinearAlgebra]: Advanced linear algebra operations including SVD
 * - [MetalKernelSource]: Metal Shading Language kernel source code
 *
 * ### Platform Support
 * - [PlatformMetalContext]: Platform-specific Metal context creation
 * - Cross-platform compatibility with graceful fallback for non-Apple platforms
 *
 * ## Usage
 *
 * The Metal backend integrates seamlessly with the Ember ML backend system:
 *
 * ```kotlin
 * // Register and set the Metal backend
 * BackendRegistry.registerBackend("metal", MetalBackend())
 * BackendRegistry.setBackend("metal")
 *
 * // Use tensors normally - operations will be GPU-accelerated
 * val tensor1 = EmberTensor.create(floatArrayOf(1f, 2f, 3f, 4f), intArrayOf(2, 2))
 * val tensor2 = EmberTensor.create(floatArrayOf(5f, 6f, 7f, 8f), intArrayOf(2, 2))
 * val result = tensor1 + tensor2  // Executed on GPU via Metal
 * ```
 *
 * ## Platform Considerations
 *
 * ### Apple Platforms (macOS, iOS)
 * - Full Metal support with GPU acceleration
 * - Native Metal kernel compilation and execution
 * - Optimized for Apple Silicon and Intel-based Macs
 *
 * ### Other Platforms
 * - Graceful fallback to CPU backends
 * - Metal backend reports as unavailable
 * - No runtime errors when Metal is not present
 *
 * ## Performance
 *
 * The Metal backend is designed for high performance:
 * - Direct GPU memory management
 * - Optimized Metal kernels for common operations
 * - Minimal CPU-GPU data transfer
 * - Batch operations for improved throughput
 *
 * ## SVD Implementation
 *
 * The package includes a GPU-accelerated SVD implementation based on the power method,
 * ported from the MLX reference implementation:
 *
 * ```kotlin
 * val matrix = MetalTensor.create(data, intArrayOf(100, 50), DType.FLOAT32, context)
 * val (u, s, vt) = MetalLinearAlgebra.svd(matrix, fullMatrices = true, computeUv = true, context)
 * ```
 *
 * ## Future Directions
 *
 * - Support for additional data types (Float16, Int32, etc.)
 * - More advanced linear algebra operations
 * - Optimized kernels for specific Apple hardware
 * - Integration with Apple's MLX framework
 */
package ai.solace.ember.backend.metal