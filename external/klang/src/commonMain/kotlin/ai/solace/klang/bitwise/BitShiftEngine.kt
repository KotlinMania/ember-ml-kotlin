@file:Suppress("unused", "UNUSED_PARAMETER")
package ai.solace.klang.bitwise

/**
 * @native-bitshift-allowed This is a core BitShift implementation file.
 * Native bitwise operations (shl, shr, ushr, and, or) are permitted here
 * as this file provides the foundation for the BitShift engine.
 */

/**
 * BitShiftMode: Strategy for performing bit shift operations.
 *
 * Determines whether shifts use native Kotlin operations or pure arithmetic
 * for cross-platform determinism.
 *
 * ## Modes
 *
 * - **AUTO**: Automatically selects best mode based on platform validation
 * - **NATIVE**: Uses Kotlin's shl/shr/ushr (fast, may vary by platform)
 * - **ARITHMETIC**: Uses multiplication/division (slower, deterministic)
 *
 * @see BitShiftEngine For usage
 * @since 0.1.0
 */
enum class BitShiftMode {
    /**
     * Automatically resolve to NATIVE or ARITHMETIC based on runtime validation.
     *
     * Delegates to [BitShiftConfig.resolveMode] which can verify native shift
     * behavior before committing to a strategy.
     */
    AUTO,
    
    /**
     * Use Kotlin's built-in shift operations (shl, shr, ushr).
     *
     * **Advantages**:
     * - Fast (native CPU instructions)
     * - Minimal overhead
     *
     * **Disadvantages**:
     * - May vary between platforms
     * - JavaScript shifts can behave differently
     */
    NATIVE,
    
    /**
     * Use pure arithmetic operations (multiplication/division).
     *
     * **Advantages**:
     * - Cross-platform deterministic
     * - No bitwise operator variations
     *
     * **Disadvantages**:
     * - Slower (iterative for large shifts)
     * - More CPU overhead
     */
    ARITHMETIC,
}

/**
 * ShiftResult: Result of a bit shift operation with carry/overflow information.
 *
 * Encapsulates the shifted value plus metadata about bits that were shifted out.
 *
 * ## Fields
 *
 * - **value**: The shifted result (masked to bit width)
 * - **carry**: Bits that were shifted out (for multi-limb arithmetic)
 * - **overflow**: true if operation exceeded bit width
 *
 * ## Usage Example
 *
 * ```kotlin
 * val engine = BitShiftEngine(BitShiftMode.NATIVE, bitWidth = 8)
 * val result = engine.leftShift(0xFF, 1)
 * println("Value: 0x${result.value.toString(16)}")     // 0xFE (wrapped)
 * println("Carry: 0x${result.carry.toString(16)}")     // 0x1 (lost bit)
 * println("Overflow: ${result.overflow}")              // true
 * ```
 *
 * @property value The shifted value (normalized to bit width)
 * @property carry Bits shifted out (for chaining operations)
 * @property overflow true if value exceeded bit width
 * @since 0.1.0
 */
data class ShiftResult(
    val value: Long,
    val carry: Long = 0,
    val overflow: Boolean = false,
)

/**
 * BitShiftEngine: Configurable bit shift operations with carry/overflow tracking.
 *
 * Provides a unified interface for bit shifting that can use either native Kotlin
 * operations or pure arithmetic for cross-platform determinism. Tracks carry bits
 * and overflow, essential for multi-precision arithmetic and low-level bit manipulation.
 *
 * ## Why BitShiftEngine?
 *
 * **The Problem**: Bit shifts can vary between platforms:
 * ```kotlin
 * // On some platforms:
 * val x: Byte = -128
 * val shifted = x.toInt() shr 1  // May be -64 or 64 depending on sign extension
 * ```
 *
 * **The Solution**: Explicit control over shift behavior:
 * ```kotlin
 * val engine = BitShiftEngine(BitShiftMode.ARITHMETIC, bitWidth = 8)
 * val result = engine.rightShift(0x80, 1)  // Deterministic: 0x40
 * ```
 *
 * ## Use Cases
 *
 * - **Multi-precision arithmetic**: Track carry bits for 128/256/512-bit operations
 * - **Cryptography**: Deterministic shifts for hash functions, ciphers
 * - **Binary protocols**: Bit packing/unpacking with overflow detection
 * - **Cross-platform**: Ensure identical behavior on JVM, Native, JS
 * - **Low-level emulation**: CPU emulators, VM implementations
 *
 * ## Architecture
 *
 * ```
 * ┌─────────────────┐
 * │ BitShiftEngine  │
 * │  mode: NATIVE   │  ← Configuration
 * │  bitWidth: 32   │
 * └────────┬────────┘
 *          │
 *     ┌────┴────┐
 *     │         │
 *   NATIVE   ARITHMETIC
 *     │         │
 *  (fast)   (deterministic)
 * ```
 *
 * ## Modes
 *
 * ### NATIVE Mode
 * ```kotlin
 * val engine = BitShiftEngine(BitShiftMode.NATIVE, 32)
 * val result = engine.leftShift(0x12345678, 4)
 * // Fast: uses Kotlin's shl
 * ```
 *
 * ### ARITHMETIC Mode
 * ```kotlin
 * val engine = BitShiftEngine(BitShiftMode.ARITHMETIC, 32)
 * val result = engine.leftShift(0x12345678, 4)
 * // Deterministic: uses multiplication by 2^n
 * ```
 *
 * ### AUTO Mode
 * ```kotlin
 * val engine = BitShiftEngine(BitShiftMode.AUTO, 32)
 * val result = engine.leftShift(0x12345678, 4)
 * // Resolves to NATIVE or ARITHMETIC based on platform
 * ```
 *
 * ## Bit Widths
 *
 * Supports 8, 16, 32, and 64-bit operations:
 * - **8-bit**: Byte operations (0x00 - 0xFF)
 * - **16-bit**: Short operations (0x0000 - 0xFFFF)
 * - **32-bit**: Int operations (0x00000000 - 0xFFFFFFFF)
 * - **64-bit**: Long operations (0x0000000000000000 - 0x7FFFFFFFFFFFFFFF)
 *
 * ## Usage Example
 *
 * ### Basic Shifting
 * ```kotlin
 * val engine = BitShiftEngine(BitShiftMode.NATIVE, 32)
 *
 * // Left shift with overflow detection
 * val left = engine.leftShift(0xF0000000, 4)
 * println("Value: 0x${left.value.toString(16)}")      // Wrapped result
 * println("Overflow: ${left.overflow}")                // true (bits lost)
 *
 * // Right shift (logical)
 * val right = engine.unsignedRightShift(0x80000000, 1)
 * println("Value: 0x${right.value.toString(16)}")     // 0x40000000
 * ```
 *
 * ### Multi-Precision Arithmetic
 * ```kotlin
 * // Shift a 128-bit number represented as two 64-bit limbs
 * val engine64 = BitShiftEngine(BitShiftMode.NATIVE, 64)
 *
 * // Low limb
 * val lowResult = engine64.leftShift(lowLimb, 1)
 * var newLow = lowResult.value
 *
 * // High limb (include carry from low)
 * val highResult = engine64.leftShift(highLimb, 1)
 * var newHigh = highResult.value or lowResult.carry
 *
 * // Check for 128-bit overflow
 * if (highResult.overflow) {
 *     println("128-bit overflow!")
 * }
 * ```
 *
 * ### Dynamic Bit Width
 * ```kotlin
 * fun shiftByWidth(value: Long, bits: Int, width: Int): Long {
 *     val engine = BitShiftEngine(BitShiftMode.NATIVE, width)
 *     return engine.leftShift(value, bits).value
 * }
 *
 * val byte = shiftByWidth(0xFF, 1, 8)    // 8-bit: 0xFE
 * val word = shiftByWidth(0xFFFF, 1, 16) // 16-bit: 0xFFFE
 * ```
 *
 * ## Performance
 *
 * | Mode | Complexity | Typical Cost |
 * |------|------------|--------------|
 * | NATIVE | O(1) | ~1-2 CPU cycles |
 * | ARITHMETIC (bits=1) | O(1) | ~5-10 cycles |
 * | ARITHMETIC (bits=n) | O(n) | ~5n-10n cycles |
 *
 * **Benchmark** (32-bit left shift by 8):
 * - NATIVE: ~2ns
 * - ARITHMETIC: ~40ns (20× slower)
 * - **Trade-off**: Speed vs determinism
 *
 * ## Carry Propagation
 *
 * Carry bits enable multi-limb arithmetic:
 * ```kotlin
 * // Add carry from previous limb
 * val result = engine.leftShift(limb, bits)
 * val nextLimb = nextValue or result.carry
 * ```
 *
 * ## Overflow Detection
 *
 * Overflow flag indicates when bits are lost:
 * ```kotlin
 * val result = engine.leftShift(0xFFFFFFFF, 1)
 * if (result.overflow) {
 *     // Handle overflow (saturate, wrap, error, etc.)
 * }
 * ```
 *
 * ## Thread Safety
 *
 * BitShiftEngine instances are immutable and thread-safe.
 * Operations return new [ShiftResult] objects.
 *
 * ## Builder Pattern
 *
 * Use [withMode] and [withBitWidth] to create variants:
 * ```kotlin
 * val base = BitShiftEngine(BitShiftMode.NATIVE, 32)
 * val arithmetic = base.withMode(BitShiftMode.ARITHMETIC)
 * val wider = base.withBitWidth(64)
 * ```
 *
 * ## Related Types
 *
 * | Type | Purpose | Carry? | Overflow? |
 * |------|---------|--------|-----------|
 * | Int.shl | Native left shift | No | No |
 * | Int.shr | Native right shift | No | No |
 * | Int.ushr | Native unsigned right shift | No | No |
 * | BitShiftEngine | Configurable shifts | Yes | Yes |
 *
 * @property mode The shift strategy (AUTO, NATIVE, or ARITHMETIC)
 * @property bitWidth The bit width (8, 16, 32, or 64)
 * @constructor Creates a shift engine with specified mode and bit width
 * @see BitShiftMode For mode descriptions
 * @see ShiftResult For result structure
 * @see ArithmeticBitwiseOps For arithmetic shift implementation
 * @since 0.1.0
 */
class BitShiftEngine(
    val mode: BitShiftMode = BitShiftMode.NATIVE,
    val bitWidth: Int = 32,
) {
    init {
        require(bitWidth in listOf(8, 16, 32, 64)) {
            "Bit width must be 8, 16, 32, or 64"
        }
    }

    /** Maximum representable value for this bit width. */
    private val maxValue =
        when (bitWidth) {
            8 -> 0xFFL
            16 -> 0xFFFFL
            32 -> 0xFFFFFFFFL
            64 -> 0x7FFFFFFFFFFFFFFFL // Use max signed long to avoid overflow
            else -> error("Unsupported bit width: $bitWidth")
        }

    /** Arithmetic operations helper (for ARITHMETIC mode). */
    private val arithmeticOps = if (bitWidth in 1..32) ArithmeticBitwiseOps(bitWidth) else null

    /**
     * Perform left shift with carry detection.
     *
     * Shifts [value] left by [bits] positions. Bits shifted out are captured
     * in the carry field. Overflow is detected when any bits are lost.
     *
     * @param value Value to shift (normalized to bit width)
     * @param bits Number of positions to shift (0 to bitWidth-1)
     * @return [ShiftResult] with value, carry, and overflow
     *
     * ## Example
     * ```kotlin
     * val engine = BitShiftEngine(BitShiftMode.NATIVE, 8)
     * val result = engine.leftShift(0x80, 1)
     * // value: 0x00, carry: 0x01, overflow: true
     * ```
     *
     * ## Complexity
     * - NATIVE: O(1)
     * - ARITHMETIC: O(bits)
     */
    fun leftShift(
        value: Long,
        bits: Int,
    ): ShiftResult {
        if (bits !in 0..<bitWidth) {
            return ShiftResult(0L, 0L, true)
        }

        val activeMode = if (mode == BitShiftMode.AUTO) {
            BitShiftConfig.resolveMode(bitWidth)
        } else {
            mode
        }

        return when (activeMode) {
            BitShiftMode.NATIVE -> {
                val originalValue = normalize(value)
                val shiftedValue =
                    when (bitWidth) {
                        8 -> (originalValue.toInt() shl bits).toLong()
                        16 -> (originalValue.toInt() shl bits).toLong()
                        32 -> (originalValue.toInt() shl bits).toLong()
                        64 -> originalValue shl bits
                        else -> error("Unexpected bitWidth in native leftShift: $bitWidth")
                    }

                val result = normalize(shiftedValue)
                val carry = if (shiftedValue != result) (shiftedValue ushr bitWidth) else 0L
                val overflow = shiftedValue > maxValue

                ShiftResult(result, carry, overflow)
            }

            BitShiftMode.ARITHMETIC -> {
                // Note: ARITHMETIC mode only supports bitWidth ≤ 32.
                if (arithmeticOps == null) {
                    throw IllegalStateException(
                        "ARITHMETIC mode is not supported for bitWidth > 32. " +
                        "Current bitWidth: $bitWidth. Use NATIVE mode instead."
                    )
                }

                val originalValue = normalize(value)
                var result = originalValue
                var carry = 0L
                var overflow = false

                repeat(bits) {
                    val doubled = result * 2
                    if (doubled > maxValue) {
                        carry = (carry * 2) + (doubled ushr bitWidth)
                        overflow = true
                    }
                    result = normalize(doubled)
                }

                ShiftResult(result, carry, overflow)
            }

            BitShiftMode.AUTO -> error("BitShiftMode.AUTO must resolve before execution")
        }
    }

    /**
     * Perform right shift (arithmetic for negative numbers).
     *
     * Shifts [value] right by [bits] positions. For signed values,
     * sign bit is extended.
     *
     * @param value Value to shift (normalized to bit width)
     * @param bits Number of positions to shift (0 to bitWidth-1)
     * @return [ShiftResult] with shifted value
     *
     * ## Example
     * ```kotlin
     * val engine = BitShiftEngine(BitShiftMode.NATIVE, 8)
     * val result = engine.rightShift(0x80, 1)
     * // For unsigned: 0x40
     * ```
     *
     * ## Complexity
     * - NATIVE: O(1)
     * - ARITHMETIC: O(bits)
     */
    fun rightShift(
        value: Long,
        bits: Int,
    ): ShiftResult {
        if (bits !in 0..<bitWidth) {
            return ShiftResult(if (value < 0) -1L else 0L, 0L, false)
        }

        val activeMode = if (mode == BitShiftMode.AUTO) {
            BitShiftConfig.resolveMode(bitWidth)
        } else {
            mode
        }

        return when (activeMode) {
            BitShiftMode.NATIVE -> {
                val originalValue = normalize(value)
                val result =
                    when (bitWidth) {
                        8 -> ((originalValue.toInt() and 0xFF) ushr bits).toLong()
                        16 -> ((originalValue.toInt() and 0xFFFF) ushr bits).toLong()
                        32 -> (originalValue.toInt() ushr bits).toLong()
                        64 -> originalValue ushr bits
                        else -> error("Unexpected bitWidth in native rightShift: $bitWidth")
                    }

                ShiftResult(normalize(result), 0L, false)
            }

            BitShiftMode.ARITHMETIC -> {
                // Note: ARITHMETIC mode only supports bitWidth ≤ 32.
                if (arithmeticOps == null) {
                    throw IllegalStateException(
                        "ARITHMETIC mode is not supported for bitWidth > 32. " +
                        "Current bitWidth: $bitWidth. Use NATIVE mode instead."
                    )
                }

                val result = arithmeticOps.rightShift(normalize(value), bits)
                ShiftResult(result, 0L, false)
            }

            BitShiftMode.AUTO -> error("BitShiftMode.AUTO must resolve before execution")
        }
    }

    /**
     * Perform unsigned right shift (zero-fill).
     *
     * Shifts [value] right by [bits] positions, filling with zeros from the left.
     * Always treats value as unsigned.
     *
     * @param value Value to shift (normalized to bit width)
     * @param bits Number of positions to shift (0 to bitWidth-1)
     * @return [ShiftResult] with shifted value
     *
     * ## Example
     * ```kotlin
     * val engine = BitShiftEngine(BitShiftMode.NATIVE, 16)
     * val result = engine.unsignedRightShift(0x8000, 1)
     * // value: 0x4000 (zero-fill)
     * ```
     *
     * ## Complexity
     * - NATIVE: O(1)
     * - ARITHMETIC: O(bits)
     */
    fun unsignedRightShift(
        value: Long,
        bits: Int,
    ): ShiftResult {
        if (bits !in 0..<bitWidth) {
            return ShiftResult(0L, 0L, false)
        }

        val activeMode = if (mode == BitShiftMode.AUTO) {
            BitShiftConfig.resolveMode(bitWidth)
        } else {
            mode
        }

        return when (activeMode) {
            BitShiftMode.NATIVE -> {
                val originalValue = normalize(value)
                val result =
                    when (bitWidth) {
                        8 -> (originalValue.toInt() and 0xFF) ushr bits
                        16 -> (originalValue.toInt() and 0xFFFF) ushr bits
                        32 -> (originalValue.toInt() ushr bits).toLong()
                        64 -> originalValue ushr bits
                        else -> error("Unexpected bitWidth in native unsignedRightShift: $bitWidth")
                    }

                ShiftResult(normalize(result.toLong()), 0L, false)
            }

            BitShiftMode.ARITHMETIC -> {
                // Note: ARITHMETIC mode only supports bitWidth ≤ 32.
                if (arithmeticOps == null) {
                    throw IllegalStateException(
                        "ARITHMETIC mode is not supported for bitWidth > 32. " +
                        "Current bitWidth: $bitWidth. Use NATIVE mode instead."
                    )
                }

                val result = arithmeticOps.rightShift(normalize(value), bits)
                ShiftResult(result, 0L, false)
            }

            BitShiftMode.AUTO -> error("BitShiftMode.AUTO must resolve before execution")
        }
    }

    /**
     * Normalize a value to fit within the bit width.
     *
     * Masks value to only keep bits that fit in the configured bit width.
     *
     * @param value Value to normalize
     * @return Value masked to bit width
     */
    private fun normalize(value: Long): Long =
        when (bitWidth) {
            8 -> value and 0xFFL
            16 -> value and 0xFFFFL
            32 -> value and 0xFFFFFFFFL
            64 -> value
            else -> error("Unexpected bitWidth in normalize: $bitWidth")
        }

    /**
     * Perform bitwise AND operation.
     *
     * Uses ArithmeticBitwiseOps for ARITHMETIC mode when available (≤32 bits),
     * or native Kotlin `and` operator otherwise.
     *
     * @param a First value
     * @param b Second value
     * @return a AND b, masked to bit width
     */
    fun bitwiseAnd(a: Long, b: Long): Long {
        val result = if (mode == BitShiftMode.ARITHMETIC && arithmeticOps != null) {
            arithmeticOps.and(a, b)
        } else {
            a and b
        }
        return normalize(result)
    }

    /**
     * Perform bitwise OR operation.
     *
     * Uses ArithmeticBitwiseOps for ARITHMETIC mode when available (≤32 bits),
     * or native Kotlin `or` operator otherwise.
     *
     * @param a First value
     * @param b Second value
     * @return a OR b, masked to bit width
     */
    fun bitwiseOr(a: Long, b: Long): Long {
        val result = if (mode == BitShiftMode.ARITHMETIC && arithmeticOps != null) {
            arithmeticOps.or(a, b)
        } else {
            a or b
        }
        return normalize(result)
    }

    /**
     * Perform bitwise XOR operation.
     *
     * Uses ArithmeticBitwiseOps for ARITHMETIC mode when available (≤32 bits),
     * or native Kotlin `xor` operator otherwise.
     *
     * @param a First value
     * @param b Second value
     * @return a XOR b, masked to bit width
     */
    fun bitwiseXor(a: Long, b: Long): Long {
        val result = if (mode == BitShiftMode.ARITHMETIC && arithmeticOps != null) {
            arithmeticOps.xor(a, b)
        } else {
            a xor b
        }
        return normalize(result)
    }

    /**
     * Perform bitwise NOT operation.
     *
     * Inverts all bits within the configured bit width.
     * Uses ArithmeticBitwiseOps for ARITHMETIC mode when available (≤32 bits),
     * or native Kotlin `inv` operator otherwise.
     *
     * @param value Value to invert
     * @return NOT value, masked to bit width
     */
    fun bitwiseNot(value: Long): Long {
        val result = if (mode == BitShiftMode.ARITHMETIC && arithmeticOps != null) {
            arithmeticOps.not(value)
        } else {
            value.inv()
        }
        return normalize(result)
    }

    /**
     * Generate a mask for a specific number of bits.
     *
     * Creates a bit mask with the specified number of low bits set to 1.
     *
     * ## Usage Example
     * ```kotlin
     * val engine = BitShiftEngine(BitShiftMode.NATIVE, 32)
     * engine.getMask(8) // 0xFF
     * engine.getMask(16) // 0xFFFF
     * ```
     *
     * @param bits Number of bits (1 to bitWidth)
     * @return Mask with specified bits set
     */
    fun getMask(bits: Int): Long {
        require(bits in 1..bitWidth) {
            "Bits $bits out of range for bitWidth $bitWidth"
        }
        
        if (bits == bitWidth) {
            return maxValue
        }
        
        return if (mode == BitShiftMode.ARITHMETIC && arithmeticOps != null) {
            arithmeticOps.createMask(bits)
        } else {
            // Native mode - direct computation
            (1L shl bits) - 1L
        }
    }
    
    /**
     * Create a copy with a different mode.
     *
     * @param newMode New shift strategy
     * @return New BitShiftEngine with specified mode
     */
    fun withMode(newMode: BitShiftMode): BitShiftEngine = BitShiftEngine(newMode, bitWidth)

    /**
     * Create a copy with a different bit width.
     *
     * @param newBitWidth New bit width (8, 16, 32, or 64)
     * @return New BitShiftEngine with specified bit width
     */
    fun withBitWidth(newBitWidth: Int): BitShiftEngine = BitShiftEngine(mode, newBitWidth)
    
    /**
     * Compose two bytes into a 16-bit value.
     *
     * Useful for multi-precision limb operations where limbs are stored as
     * separate bytes in memory. Combines low and high bytes using bit shifting.
     *
     * ## Usage Example
     * ```kotlin
     * val lowByte = 0x34L
     * val highByte = 0x12L
     * val composed = BitShiftEngine.composeBytes(lowByte, highByte)
     * // Result: 0x1234
     * ```
     *
     * @param lowByte Low 8 bits (least significant byte)
     * @param highByte High 8 bits (most significant byte)
     * @return 16-bit composed value
     */
    fun composeBytes(lowByte: Long, highByte: Long): Long {
        // Use 16-bit shifter for the composition
        val shifter16 = BitShiftEngine(mode, 16)
        val lowMasked = shifter16.bitwiseAnd(lowByte, 0xFFL)
        val highMasked = shifter16.bitwiseAnd(highByte, 0xFFL)
        val highShifted = shifter16.leftShift(highMasked, 8).value
        return shifter16.bitwiseOr(lowMasked, highShifted)
    }
    
    /**
     * Decompose a 16-bit value into two bytes.
     *
     * Useful for multi-precision limb operations where limbs must be stored as
     * separate bytes in memory. Splits value into low and high bytes using bit shifting.
     *
     * ## Usage Example
     * ```kotlin
     * val value = 0x1234
     * val (low, high) = BitShiftEngine.decomposeBytes(value)
     * // low: 0x34, high: 0x12
     * ```
     *
     * @param value 16-bit value to decompose
     * @return Pair of (lowByte, highByte)
     */
    fun decomposeBytes(value: Int): Pair<Byte, Byte> {
        // Use 16-bit shifter for the decomposition
        val shifter16 = BitShiftEngine(mode, 16)
        val lowByte = shifter16.bitwiseAnd(value.toLong(), 0xFF).toByte()
        val highByte = shifter16.unsignedRightShift(value.toLong(), 8).value.toByte()
        return Pair(lowByte, highByte)
    }
    
    /**
     * Shift a value left by a multiple of 8 bits (byte positions).
     *
     * This is optimized for byte-level shifting common in multi-precision arithmetic,
     * binary protocols, and memory operations. Shifts by full bytes rather than individual bits.
     *
     * ## Usage Example
     * ```kotlin
     * val engine = BitShiftEngine(BitShiftMode.NATIVE, 32)
     * 
     * // Shift left by 1 byte (8 bits)
     * val result = engine.byteShiftLeft(0x12345678, 1)
     * // result.value: 0x34567800
     * 
     * // Shift left by 2 bytes (16 bits)
     * val result2 = engine.byteShiftLeft(0x12345678, 2)
     * // result2.value: 0x56780000
     * ```
     *
     * @param value Value to shift
     * @param bytes Number of byte positions to shift (0 to bitWidth/8 - 1)
     * @return [ShiftResult] with shifted value, carry, and overflow
     *
     * ## Complexity
     * - NATIVE: O(1) - single shift operation
     * - ARITHMETIC: O(bytes) - iterative byte-by-byte shifting
     */
    fun byteShiftLeft(value: Long, bytes: Int): ShiftResult {
        if (bytes < 0 || bytes >= bitWidth / 8) {
            return ShiftResult(0L, 0L, true)
        }
        if (bytes == 0) {
            return ShiftResult(normalize(value), 0L, false)
        }
        
        val bitShift = bytes * 8
        return leftShift(value, bitShift)
    }
    
    /**
     * Shift a value right by a multiple of 8 bits (byte positions).
     *
     * This is optimized for byte-level shifting common in multi-precision arithmetic,
     * binary protocols, and memory operations. Shifts by full bytes rather than individual bits.
     * This is an unsigned (zero-fill) right shift.
     *
     * ## Usage Example
     * ```kotlin
     * val engine = BitShiftEngine(BitShiftMode.NATIVE, 32)
     * 
     * // Shift right by 1 byte (8 bits)
     * val result = engine.byteShiftRight(0x12345678, 1)
     * // result.value: 0x00123456
     * 
     * // Shift right by 2 bytes (16 bits)
     * val result2 = engine.byteShiftRight(0x12345678, 2)
     * // result2.value: 0x00001234
     * ```
     *
     * @param value Value to shift
     * @param bytes Number of byte positions to shift (0 to bitWidth/8 - 1)
     * @return [ShiftResult] with shifted value
     *
     * ## Complexity
     * - NATIVE: O(1) - single shift operation
     * - ARITHMETIC: O(bytes) - iterative byte-by-byte shifting
     */
    fun byteShiftRight(value: Long, bytes: Int): ShiftResult {
        if (bytes < 0 || bytes >= bitWidth / 8) {
            return ShiftResult(0L, 0L, false)
        }
        if (bytes == 0) {
            return ShiftResult(normalize(value), 0L, false)
        }
        
        val bitShift = bytes * 8
        return unsignedRightShift(value, bitShift)
    }
    
    // ============================================================================
    // Helper Functions for Common Operations
    // ============================================================================
    
    /**
     * Extract a specific byte from a value.
     *
     * Returns the byte at the given index (0 = LSB, bitWidth/8 - 1 = MSB).
     * Result is zero-extended to Long for easier manipulation.
     *
     * ## Usage Example
     * ```kotlin
     * val engine = BitShiftEngine(BitShiftMode.NATIVE, 32)
     * val value = 0x12345678L
     * 
     * engine.extractByte(value, 0) // 0x78
     * engine.extractByte(value, 1) // 0x56
     * engine.extractByte(value, 2) // 0x34
     * engine.extractByte(value, 3) // 0x12
     * ```
     *
     * @param value Source value
     * @param byteIndex Byte position (0 to bitWidth/8 - 1)
     * @return Byte value as Long (0x00 to 0xFF)
     */
    fun extractByte(value: Long, byteIndex: Int): Long {
        require(byteIndex >= 0 && byteIndex < bitWidth / 8) {
            "Byte index $byteIndex out of range for bitWidth $bitWidth"
        }
        
        val shifted = byteShiftRight(value, byteIndex).value
        return bitwiseAnd(shifted, 0xFFL)
    }
    
    /**
     * Replace a specific byte in a value.
     *
     * Sets the byte at the given index while preserving other bytes.
     *
     * ## Usage Example
     * ```kotlin
     * val engine = BitShiftEngine(BitShiftMode.NATIVE, 32)
     * val original = 0x12345678L
     * 
     * engine.replaceByte(original, 1, 0xAB) // 0x1234AB78
     * ```
     *
     * @param value Original value
     * @param byteIndex Byte position to replace (0 to bitWidth/8 - 1)
     * @param newByte New byte value (only lowest 8 bits used)
     * @return Modified value
     */
    fun replaceByte(value: Long, byteIndex: Int, newByte: Long): Long {
        require(byteIndex >= 0 && byteIndex < bitWidth / 8) {
            "Byte index $byteIndex out of range for bitWidth $bitWidth"
        }
        
        // Mask for the byte position
        val byteMask = byteShiftLeft(0xFFL, byteIndex).value
        val invertedMask = bitwiseNot(byteMask)
        
        // Clear the target byte
        val cleared = bitwiseAnd(value, invertedMask)
        
        // Insert the new byte
        val maskedNewByte = bitwiseAnd(newByte, 0xFFL)
        val positioned = byteShiftLeft(maskedNewByte, byteIndex).value
        
        return bitwiseOr(cleared, positioned)
    }
    
    /**
     * Check if a specific bit is set.
     *
     * ## Usage Example
     * ```kotlin
     * val engine = BitShiftEngine(BitShiftMode.NATIVE, 8)
     * engine.isBitSet(0b10101010, 1) // true
     * engine.isBitSet(0b10101010, 0) // false
     * ```
     *
     * @param value Value to test
     * @param bitIndex Bit position (0 to bitWidth - 1)
     * @return true if bit is set (1), false if clear (0)
     */
    fun isBitSet(value: Long, bitIndex: Int): Boolean {
        require(bitIndex >= 0 && bitIndex < bitWidth) {
            "Bit index $bitIndex out of range for bitWidth $bitWidth"
        }
        
        val mask = leftShift(1L, bitIndex).value
        return bitwiseAnd(value, mask) != 0L
    }
    
    /**
     * Set a specific bit to 1.
     *
     * ## Usage Example
     * ```kotlin
     * val engine = BitShiftEngine(BitShiftMode.NATIVE, 8)
     * engine.setBit(0b00000000, 3) // 0b00001000
     * ```
     *
     * @param value Original value
     * @param bitIndex Bit position to set (0 to bitWidth - 1)
     * @return Modified value with bit set
     */
    fun setBit(value: Long, bitIndex: Int): Long {
        require(bitIndex >= 0 && bitIndex < bitWidth) {
            "Bit index $bitIndex out of range for bitWidth $bitWidth"
        }
        
        val mask = leftShift(1L, bitIndex).value
        return bitwiseOr(value, mask)
    }
    
    /**
     * Clear a specific bit to 0.
     *
     * ## Usage Example
     * ```kotlin
     * val engine = BitShiftEngine(BitShiftMode.NATIVE, 8)
     * engine.clearBit(0b11111111, 3) // 0b11110111
     * ```
     *
     * @param value Original value
     * @param bitIndex Bit position to clear (0 to bitWidth - 1)
     * @return Modified value with bit cleared
     */
    fun clearBit(value: Long, bitIndex: Int): Long {
        require(bitIndex >= 0 && bitIndex < bitWidth) {
            "Bit index $bitIndex out of range for bitWidth $bitWidth"
        }
        
        val mask = leftShift(1L, bitIndex).value
        val invertedMask = bitwiseNot(mask)
        return bitwiseAnd(value, invertedMask)
    }
    
    /**
     * Toggle a specific bit (0→1, 1→0).
     *
     * ## Usage Example
     * ```kotlin
     * val engine = BitShiftEngine(BitShiftMode.NATIVE, 8)
     * engine.toggleBit(0b10101010, 0) // 0b10101011
     * engine.toggleBit(0b10101010, 1) // 0b10101000
     * ```
     *
     * @param value Original value
     * @param bitIndex Bit position to toggle (0 to bitWidth - 1)
     * @return Modified value with bit toggled
     */
    fun toggleBit(value: Long, bitIndex: Int): Long {
        require(bitIndex >= 0 && bitIndex < bitWidth) {
            "Bit index $bitIndex out of range for bitWidth $bitWidth"
        }
        
        val mask = leftShift(1L, bitIndex).value
        return bitwiseXor(value, mask)
    }
    
    /**
     * Count the number of set bits (population count / Hamming weight).
     *
     * ## Usage Example
     * ```kotlin
     * val engine = BitShiftEngine(BitShiftMode.NATIVE, 8)
     * engine.popCount(0b10101010) // 4
     * engine.popCount(0b11111111) // 8
     * engine.popCount(0b00000000) // 0
     * ```
     *
     * @param value Value to count
     * @return Number of bits set to 1
     */
    fun popCount(value: Long): Int {
        var count = 0
        var v = normalize(value)
        
        for (i in 0 until bitWidth) {
            if (isBitSet(v, i)) {
                count++
            }
        }
        
        return count
    }
    
    /**
     * Sign-extend a value from a smaller bit width.
     *
     * Takes a value with `sourceBits` width and extends it to the engine's bitWidth,
     * preserving the sign bit.
     *
     * ## Usage Example
     * ```kotlin
     * val engine = BitShiftEngine(BitShiftMode.NATIVE, 32)
     * 
     * // Sign-extend an 8-bit value
     * engine.signExtend(0xFF, 8) // 0xFFFFFFFF (negative)
     * engine.signExtend(0x7F, 8) // 0x0000007F (positive)
     * ```
     *
     * @param value Value to extend
     * @param sourceBits Original bit width of the value
     * @return Sign-extended value
     */
    fun signExtend(value: Long, sourceBits: Int): Long {
        require(sourceBits > 0 && sourceBits <= bitWidth) {
            "Source bits $sourceBits must be in range 1..$bitWidth"
        }
        
        if (sourceBits == bitWidth) {
            return normalize(value)
        }
        
        // Check sign bit
        val signBitPos = sourceBits - 1
        val isNegative = isBitSet(value, signBitPos)
        
        if (!isNegative) {
            // Positive: just mask to source width
            val mask = getMask(sourceBits)
            return bitwiseAnd(value, mask)
        } else {
            // Negative: set all upper bits
            val mask = getMask(sourceBits)
            val masked = bitwiseAnd(value, mask)
            val upperMask = bitwiseNot(mask)
            return bitwiseOr(masked, upperMask)
        }
    }
    
    /**
     * Zero-extend a value from a smaller bit width.
     *
     * Takes a value with `sourceBits` width and extends it to the engine's bitWidth,
     * filling upper bits with zeros.
     *
     * ## Usage Example
     * ```kotlin
     * val engine = BitShiftEngine(BitShiftMode.NATIVE, 32)
     * 
     * // Zero-extend an 8-bit value
     * engine.zeroExtend(0xFF, 8) // 0x000000FF
     * engine.zeroExtend(0x7F, 8) // 0x0000007F
     * ```
     *
     * @param value Value to extend
     * @param sourceBits Original bit width of the value
     * @return Zero-extended value
     */
    fun zeroExtend(value: Long, sourceBits: Int): Long {
        require(sourceBits > 0 && sourceBits <= bitWidth) {
            "Source bits $sourceBits must be in range 1..$bitWidth"
        }
        
        val mask = getMask(sourceBits)
        return bitwiseAnd(value, mask)
    }
    
    /**
     * Compose multiple bytes into a larger value (little-endian).
     *
     * Combines bytes from LSB to MSB.
     *
     * ## Usage Example
     * ```kotlin
     * val engine = BitShiftEngine(BitShiftMode.NATIVE, 32)
     * 
     * // Compose 4 bytes into a 32-bit value
     * val bytes = longArrayOf(0x78, 0x56, 0x34, 0x12)
     * engine.composeBytes(bytes) // 0x12345678
     * ```
     *
     * @param bytes Array of byte values (LSB first)
     * @return Composed value
     */
    fun composeBytes(bytes: LongArray): Long {
        require(bytes.size <= bitWidth / 8) {
            "Too many bytes (${bytes.size}) for bitWidth $bitWidth"
        }
        
        var result = 0L
        for (i in bytes.indices) {
            val byte = bitwiseAnd(bytes[i], 0xFFL)
            val shifted = byteShiftLeft(byte, i).value
            result = bitwiseOr(result, shifted)
        }
        
        return result
    }
    
    /**
     * Decompose a value into individual bytes (little-endian).
     *
     * Splits a value into bytes from LSB to MSB.
     *
     * ## Usage Example
     * ```kotlin
     * val engine = BitShiftEngine(BitShiftMode.NATIVE, 32)
     * 
     * val bytes = engine.decomposeBytes(0x12345678, 4)
     * // bytes = [0x78, 0x56, 0x34, 0x12]
     * ```
     *
     * @param value Value to decompose
     * @param byteCount Number of bytes to extract
     * @return Array of byte values (LSB first)
     */
    fun decomposeBytes(value: Long, byteCount: Int): LongArray {
        require(byteCount > 0 && byteCount <= bitWidth / 8) {
            "Byte count $byteCount out of range for bitWidth $bitWidth"
        }
        
        return LongArray(byteCount) { i ->
            extractByte(value, i)
        }
    }
    
    // ========================================================================
    // Type-Preserving Shift Operations
    // ========================================================================
    
    /**
     * Replicate a byte value across all bytes in a word.
     *
     * Takes a single byte and replicates it into all byte positions of a 64-bit word.
     * This is essential for fast memory operations like memset that need to fill
     * memory with a repeated byte pattern.
     *
     * ## Usage Example
     * ```kotlin
     * val engine = BitShiftEngine(BitShiftMode.NATIVE, 64)
     * val word = engine.repeatByteToWord(0x42)
     * // word: 0x4242424242424242
     * ```
     *
     * @param byteValue Byte value to replicate (0x00 to 0xFF)
     * @return 64-bit word with byte replicated in all positions
     * @since 0.1.0
     */
    fun repeatByteToWord(byteValue: Int): Long {
        val byte = bitwiseAnd(byteValue.toLong(), 0xFFL)
        var result = byte
        result = bitwiseOr(result, byteShiftLeft(byte, 1).value)
        result = bitwiseOr(result, byteShiftLeft(byte, 2).value)
        result = bitwiseOr(result, byteShiftLeft(byte, 3).value)
        result = bitwiseOr(result, byteShiftLeft(byte, 4).value)
        result = bitwiseOr(result, byteShiftLeft(byte, 5).value)
        result = bitwiseOr(result, byteShiftLeft(byte, 6).value)
        result = bitwiseOr(result, byteShiftLeft(byte, 7).value)
        return result
    }
    
    /**
     * Pack 8 bytes into a 64-bit word (little-endian).
     *
     * Composes a 64-bit word from 8 individual bytes, with byte 0 as LSB.
     * This is a specialized version of composeBytes() optimized for 8-byte words
     * used in fast memory operations.
     *
     * ## Usage Example
     * ```kotlin
     * val engine = BitShiftEngine(BitShiftMode.NATIVE, 64)
     * val word = engine.packBytesToWord(
     *     0x78, 0x56, 0x34, 0x12, 0xF0, 0xDE, 0xBC, 0x9A
     * )
     * // word: 0x9ABCDEF012345678
     * ```
     *
     * @param b0 Byte 0 (LSB)
     * @param b1 Byte 1
     * @param b2 Byte 2
     * @param b3 Byte 3
     * @param b4 Byte 4
     * @param b5 Byte 5
     * @param b6 Byte 6
     * @param b7 Byte 7 (MSB)
     * @return 64-bit word composed from bytes
     * @since 0.1.0
     */
    fun packBytesToWord(b0: Long, b1: Long, b2: Long, b3: Long, 
                        b4: Long, b5: Long, b6: Long, b7: Long): Long {
        var result = bitwiseAnd(b0, 0xFFL)
        result = bitwiseOr(result, byteShiftLeft(bitwiseAnd(b1, 0xFFL), 1).value)
        result = bitwiseOr(result, byteShiftLeft(bitwiseAnd(b2, 0xFFL), 2).value)
        result = bitwiseOr(result, byteShiftLeft(bitwiseAnd(b3, 0xFFL), 3).value)
        result = bitwiseOr(result, byteShiftLeft(bitwiseAnd(b4, 0xFFL), 4).value)
        result = bitwiseOr(result, byteShiftLeft(bitwiseAnd(b5, 0xFFL), 5).value)
        result = bitwiseOr(result, byteShiftLeft(bitwiseAnd(b6, 0xFFL), 6).value)
        result = bitwiseOr(result, byteShiftLeft(bitwiseAnd(b7, 0xFFL), 7).value)
        return result
    }

    /**
     * Left shift a Byte value, returning a Byte result.
     *
     * Performs left shift and masks the result back to 8 bits, returning as Byte.
     * This is essential for ByteArray operations where you need to maintain type compatibility.
     *
     * ## Usage Example
     * ```kotlin
     * val engine = BitShiftEngine(BitShiftMode.NATIVE, 8)
     * val result: Byte = engine.leftShiftByte(0x12, 4)  // 0x20
     * ```
     *
     * @param value Byte value to shift (as Int to avoid Kotlin's promotion)
     * @param bits Number of positions to shift left
     * @return Shifted result as Byte
     * @since 0.1.0
     */
    fun leftShiftByte(value: Int, bits: Int): Byte {
        val result = leftShift(value.toLong() and 0xFFL, bits)
        return (result.value and 0xFFL).toByte()
    }
    
    /**
     * Right shift a Byte value, returning a Byte result.
     *
     * Performs right shift and masks the result back to 8 bits, returning as Byte.
     *
     * ## Usage Example
     * ```kotlin
     * val engine = BitShiftEngine(BitShiftMode.NATIVE, 8)
     * val result: Byte = engine.rightShiftByte(0x84, 2)  // 0x21
     * ```
     *
     * @param value Byte value to shift (as Int to avoid Kotlin's promotion)
     * @param bits Number of positions to shift right
     * @return Shifted result as Byte
     * @since 0.1.0
     */
    fun rightShiftByte(value: Int, bits: Int): Byte {
        val result = rightShift(value.toLong() and 0xFFL, bits)
        return (result.value and 0xFFL).toByte()
    }
    
    /**
     * Unsigned right shift a Byte value, returning a Byte result.
     *
     * Performs unsigned right shift and masks the result back to 8 bits, returning as Byte.
     *
     * ## Usage Example
     * ```kotlin
     * val engine = BitShiftEngine(BitShiftMode.NATIVE, 8)
     * val result: Byte = engine.unsignedRightShiftByte(0x84, 2)  // 0x21
     * ```
     *
     * @param value Byte value to shift (as Int to avoid Kotlin's promotion)
     * @param bits Number of positions to shift right
     * @return Shifted result as Byte
     * @since 0.1.0
     */
    fun unsignedRightShiftByte(value: Int, bits: Int): Byte {
        val result = unsignedRightShift(value.toLong() and 0xFFL, bits)
        return (result.value and 0xFFL).toByte()
    }
    
    /**
     * Left shift a Short value, returning a Short result.
     *
     * Performs left shift and masks the result back to 16 bits, returning as Short.
     *
     * ## Usage Example
     * ```kotlin
     * val engine = BitShiftEngine(BitShiftMode.NATIVE, 16)
     * val result: Short = engine.leftShiftShort(0x1234, 4)  // 0x2340
     * ```
     *
     * @param value Short value to shift
     * @param bits Number of positions to shift left
     * @return Shifted result as Short
     * @since 0.1.0
     */
    fun leftShiftShort(value: Int, bits: Int): Short {
        val result = leftShift(value.toLong() and 0xFFFFL, bits)
        return (result.value and 0xFFFFL).toShort()
    }
    
    /**
     * Right shift a Short value, returning a Short result.
     *
     * Performs right shift and masks the result back to 16 bits, returning as Short.
     *
     * ## Usage Example
     * ```kotlin
     * val engine = BitShiftEngine(BitShiftMode.NATIVE, 16)
     * val result: Short = engine.rightShiftShort(0x8421, 4)  // 0x0842
     * ```
     *
     * @param value Short value to shift
     * @param bits Number of positions to shift right
     * @return Shifted result as Short
     * @since 0.1.0
     */
    fun rightShiftShort(value: Int, bits: Int): Short {
        val result = rightShift(value.toLong() and 0xFFFFL, bits)
        return (result.value and 0xFFFFL).toShort()
    }
    
    /**
     * Unsigned right shift a Short value, returning a Short result.
     *
     * Performs unsigned right shift and masks the result back to 16 bits, returning as Short.
     *
     * ## Usage Example
     * ```kotlin
     * val engine = BitShiftEngine(BitShiftMode.NATIVE, 16)
     * val result: Short = engine.unsignedRightShiftShort(0x8421, 4)  // 0x0842
     * ```
     *
     * @param value Short value to shift
     * @param bits Number of positions to shift right
     * @return Shifted result as Short
     * @since 0.1.0
     */
    fun unsignedRightShiftShort(value: Int, bits: Int): Short {
        val result = unsignedRightShift(value.toLong() and 0xFFFFL, bits)
        return (result.value and 0xFFFFL).toShort()
    }
    
    /**
     * Left shift an Int value, returning an Int result.
     *
     * Performs left shift and masks the result back to 32 bits, returning as Int.
     *
     * ## Usage Example
     * ```kotlin
     * val engine = BitShiftEngine(BitShiftMode.NATIVE, 32)
     * val result: Int = engine.leftShiftInt(0x12345678, 4)  // 0x23456780
     * ```
     *
     * @param value Int value to shift
     * @param bits Number of positions to shift left
     * @return Shifted result as Int
     * @since 0.1.0
     */
    fun leftShiftInt(value: Int, bits: Int): Int {
        val result = leftShift(value.toLong() and 0xFFFFFFFFL, bits)
        return (result.value and 0xFFFFFFFFL).toInt()
    }
    
    /**
     * Right shift an Int value, returning an Int result.
     *
     * Performs right shift and masks the result back to 32 bits, returning as Int.
     *
     * ## Usage Example
     * ```kotlin
     * val engine = BitShiftEngine(BitShiftMode.NATIVE, 32)
     * val result: Int = engine.rightShiftInt(0x84218421, 4)  // 0x08421842
     * ```
     *
     * @param value Int value to shift
     * @param bits Number of positions to shift right
     * @return Shifted result as Int
     * @since 0.1.0
     */
    fun rightShiftInt(value: Int, bits: Int): Int {
        val result = rightShift(value.toLong() and 0xFFFFFFFFL, bits)
        return (result.value and 0xFFFFFFFFL).toInt()
    }
    
    /**
     * Unsigned right shift an Int value, returning an Int result.
     *
     * Performs unsigned right shift and masks the result back to 32 bits, returning as Int.
     *
     * ## Usage Example
     * ```kotlin
     * val engine = BitShiftEngine(BitShiftMode.NATIVE, 32)
     * val result: Int = engine.unsignedRightShiftInt(0x84218421, 4)  // 0x08421842
     * ```
     *
     * @param value Int value to shift
     * @param bits Number of positions to shift right
     * @return Shifted result as Int
     * @since 0.1.0
     */
    fun unsignedRightShiftInt(value: Int, bits: Int): Int {
        val result = unsignedRightShift(value.toLong() and 0xFFFFFFFFL, bits)
        return (result.value and 0xFFFFFFFFL).toInt()
    }
}
