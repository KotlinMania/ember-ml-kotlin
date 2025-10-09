/**
 * # Bitwise Operations for Tensor Implementation
 *
 * The `ai.solace.ember.tensor.bitwise` package provides low-level bitwise operations that form the foundation
 * for the tensor implementation in Ember ML Kotlin. These operations are particularly important for handling
 * Float64 limitations in platforms like Apple MLX and Metal.
 *
 * ## Overview
 *
 * This package implements CPU-friendly routines for bitwise operations, which are used to build higher-level
 * tensor operations. The implementation is based on the Python code in `ember_ml/backend/numpy/bitwise` and
 * `ember_ml/backend/numpy/bizarromath`.
 *
 * ## Key Components
 *
 * ### Shift Operations
 *
 * The shift operations module provides implementations of:
 * - `leftShift`: Shift bits to the left
 * - `rightShift`: Shift bits to the right
 * - `rotateLeft`: Rotate bits to the left
 * - `rotateRight`: Rotate bits to the right
 *
 * These operations are fundamental for manipulating bits in tensors and are used extensively in the
 * implementation of higher-level operations.
 *
 * ### Bit Operations
 *
 * The bit operations module provides implementations of:
 * - `getBit`: Get the value of a specific bit
 * - `setBit`: Set the value of a specific bit
 * - `clearBit`: Clear a specific bit
 * - `toggleBit`: Toggle a specific bit
 * - `countBits`: Count the number of set bits
 *
 * ### Basic Operations
 *
 * The basic operations module provides implementations of:
 * - `bitwiseAnd`: Bitwise AND operation
 * - `bitwiseOr`: Bitwise OR operation
 * - `bitwiseXor`: Bitwise XOR operation
 * - `bitwiseNot`: Bitwise NOT operation
 *
 * ### Wave Operations
 *
 * The wave operations module provides implementations of:
 * - `interfere`: Combine multiple waves using bitwise operations
 * - `generateBlockySin`: Generate a blocky sine wave pattern
 * - `createDutyCycle`: Create a binary pattern with a specified duty cycle
 * - `propagate`: Propagate a wave by shifting it
 *
 * ## MegaBinary and MegaNumber
 *
 * The `MegaBinary` and `MegaNumber` classes provide high-precision binary and numeric operations
 * that are used to implement tensor operations. These classes are based on the Python implementations
 * in `ember_ml/backend/numpy/bizarromath`.
 *
 * ### MegaNumber
 *
 * `MegaNumber` is a base class that provides arbitrary-precision numeric operations. It uses a
 * chunked representation of numbers to support operations on very large values.
 *
 * ### MegaBinary
 *
 * `MegaBinary` extends `MegaNumber` to provide binary-specific operations. It includes wave generation,
 * duty-cycle patterns, interference, and optional leading-zero preservation.
 *
 * ## Float64 Workaround
 *
 * One of the key purposes of this package is to provide a workaround for Float64 limitations in
 * platforms like Apple MLX and Metal. By implementing high-precision operations using bitwise
 * manipulations, we can achieve Float64-like precision even on platforms that don't natively
 * support it.
 *
 * The approach involves:
 * 1. Representing floating-point numbers using the `MegaNumber` class
 * 2. Implementing floating-point operations using bitwise operations
 * 3. Providing a tensor implementation that uses these operations
 *
 * ## Usage
 *
 * These bitwise operations are primarily used internally by the tensor implementation, but they
 * can also be used directly for specialized applications:
 *
 * ```kotlin
 * import ai.solace.ember.tensor.bitwise.*
 *
 * // Create a MegaBinary instance
 * val binary = MegaBinary("10101100")
 *
 * // Perform bitwise operations
 * val result = binary.bitwiseAnd(MegaBinary("01010101"))
 *
 * // Shift operations
 * val shifted = binary.shiftLeft(2)
 *
 * // Wave operations
 * val wave = MegaBinary.generateBlockySin(length = 8, halfPeriod = 2)
 * ```
 *
 * ## Implementation Notes
 *
 * - All operations are implemented in pure Kotlin with no JVM dependencies
 * - The implementation is designed to be portable across all supported platforms
 * - Performance-critical operations may have platform-specific optimizations
 * - The API is designed to be consistent with the rest of the Ember ML Kotlin library
 */
package ai.solace.ember.tensor.bitwise
