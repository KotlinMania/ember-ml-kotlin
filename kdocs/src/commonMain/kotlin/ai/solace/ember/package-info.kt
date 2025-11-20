/**
 * # Ember ML Kotlin
 *
 * Ember ML Kotlin is a backend-agnostic machine learning library for Kotlin Multiplatform.
 * It provides a consistent API for tensor operations across different backends and platforms.
 *
 * ## Core Modules
 *
 * - **`ai.solace.ember.tensor`**: Core tensor implementation and operations
 *   - `common`: Common tensor classes and utilities
 *   - `interfaces`: Tensor interfaces
 *   - `ops`: Tensor operations
 *     - `casting`: Type conversion operations
 *     - `creation`: Tensor creation operations
 *     - `indexing`: Tensor indexing operations
 *     - `manipulation`: Tensor manipulation operations
 *     - `random`: Random number generation
 *     - `utility`: Utility operations
 *
 * - **`ai.solace.ember.ops`**: Core operations for tensor manipulation
 *   - `math`: Mathematical operations
 *   - `linearalg`: Linear algebra operations
 *   - `stats`: Statistical operations
 *   - `bitwise`: Bitwise operations
 *
 * - **`ai.solace.ember.backend`**: Backend abstraction system
 *   - `common`: Common backend interfaces and utilities
 *   - `jvm`: JVM-specific backend implementations
 *
 * ## Neural Network Components
 *
 * - **`ai.solace.ember.nn`**: Neural network components
 *   - `activations`: Activation functions
 *   - `containers`: Container modules
 *   - `features`: Feature extraction and processing
 *   - `modules`: Neural network modules
 *     - `rnn`: Recurrent neural network modules
 *     - `quantum`: Quantum neural network modules
 *     - `wiring`: Network connectivity patterns
 *
 * ## Utility Modules
 *
 * - **`ai.solace.ember.utils`**: Utility functions
 *   - `math`: Mathematical utilities
 *   - `metrics`: Evaluation metrics
 *   - `visualization`: Plotting tools
 *
 * - **`ai.solace.ember.training`**: Training utilities
 *   - `optimizers`: Optimization algorithms
 *   - `schedulers`: Learning rate schedulers
 *   - `callbacks`: Training callbacks
 *
 * ## Function-First Design
 *
 * The operations in Ember ML Kotlin follow a function-first design pattern, where each operation
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
 * This design provides flexibility and consistency across the framework.
 */
package ai.solace.ember
