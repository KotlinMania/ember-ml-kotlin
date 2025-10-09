/**
 * # Backend Module
 *
 * The `ai.solace.ember.backend` module provides the backend abstraction system for Ember ML Kotlin.
 * It allows the library to work with different computation backends (JS, Native) while providing
 * a consistent API to the user.
 *
 * ## Overview
 *
 * The backend module is designed to abstract away the details of the underlying computation library,
 * allowing users to write code that works with any backend. The module consists of the following components:
 *
 * - Backend interfaces: Define the API that all backends must implement
 * - Backend implementations: Provide concrete implementations for different computation libraries
 * - Backend registry: Manages the available backends and allows switching between them
 * - Backend utilities: Helper functions for working with backends
 *
 * ## Backend Selection
 *
 * Ember ML Kotlin supports multiple backends, and users can select the backend to use at runtime:
 *
 * ```kotlin
 * import ai.solace.ember.backend.*
 *
 * // Get the current backend
 * val backend = getBackend()
 *
 * // Set the backend to use
 * setBackend("native")
 *
 * // Automatically select the best backend based on the available hardware
 * autoSelectBackend()
 * ```
 *
 * ## Backend Implementations
 *
 * Ember ML Kotlin provides backend implementations for different platforms:
 *
 * - JS: Uses TensorFlow.js or similar libraries for computation in JavaScript environments
 * - Native: Uses platform-specific libraries for computation on native platforms
 *
 * Each backend implementation provides the same API, allowing code to be written once and run on any platform.
 *
 * ## Backend Interface
 *
 * All backends implement a common interface that defines the operations they must support:
 *
 * ```kotlin
 * interface Backend {
 *     // Tensor operations
 *     fun createTensor(data: Any, shape: IntArray, dtype: DType): Any
 *     fun getTensorShape(tensor: Any): IntArray
 *     fun getTensorDType(tensor: Any): DType
 *     fun getTensorDevice(tensor: Any): String
 *
 *     // Mathematical operations
 *     fun add(a: Any, b: Any): Any
 *     fun subtract(a: Any, b: Any): Any
 *     fun multiply(a: Any, b: Any): Any
 *     fun divide(a: Any, b: Any): Any
 *     // ... other operations
 *
 *     // Device operations
 *     fun toDevice(tensor: Any, device: String): Any
 *     fun getAvailableDevices(): List<String>
 *     fun setDefaultDevice(device: String)
 *     fun getDefaultDevice(): String
 *
 *     // ... other backend-specific operations
 * }
 * ```
 *
 * ## Backend Registry
 *
 * The backend registry manages the available backends and allows switching between them:
 *
 * ```kotlin
 * object BackendRegistry {
 *     private val backends = mutableMapOf<String, Backend>()
 *     private var currentBackend: Backend? = null
 *
 *     fun registerBackend(name: String, backend: Backend)
 *     fun getBackend(name: String): Backend?
 *     fun setBackend(name: String): Boolean
 *     fun getCurrentBackend(): Backend
 *     fun getAvailableBackends(): List<String>
 * }
 * ```
 *
 * ## Backend Utilities
 *
 * The backend module provides utility functions for working with backends:
 *
 * ```kotlin
 * // Get the current backend
 * fun getBackend(): String
 *
 * // Set the backend to use
 * fun setBackend(name: String): Boolean
 *
 * // Automatically select the best backend based on the available hardware
 * fun autoSelectBackend(): String
 *
 * // Check if a backend is available
 * fun isBackendAvailable(name: String): Boolean
 *
 * // Get a list of available backends
 * fun getAvailableBackends(): List<String>
 * ```
 *
 * ## Backend-Specific Extensions
 *
 * Each backend implementation may provide additional extensions or optimizations specific to that backend:
 *
 * ```kotlin
 * // JS-specific extensions
 * object JsBackendExtensions {
 *     fun useWebGL(): Boolean
 *     fun useWebGPU(): Boolean
 *     fun useWASM(): Boolean
 * }
 *
 * // Native-specific extensions
 * object NativeBackendExtensions {
 *     fun useMetal(): Boolean
 *     fun useVulkan(): Boolean
 *     fun useCUDA(): Boolean
 * }
 * ```
 */
package ai.solace.ember.backend
