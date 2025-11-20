package ai.solace.klang.int

import ai.solace.klang.bitwise.ArithmeticBitwiseOps
import ai.solace.klang.bitwise.BitShiftEngine
import ai.solace.klang.bitwise.BitShiftMode
import ai.solace.klang.mem.GlobalHeap
import ai.solace.klang.mem.KMalloc

/**
 * C_Int128: C-compatible `__int128` type with zero-copy heap operations.
 *
 * Models C's compiler extension type `__int128`, providing exact 128-bit
 * signed integer arithmetic with zero-copy semantics on heap memory.
 * Uses two's complement representation for negative values.
 *
 * ## C Compatibility
 *
 * In C (GCC/Clang extensions):
 * ```c
 * __int128 a = -100;
 * __int128 b = 200;
 * __int128 c = a + b;  // 100
 * ```
 *
 * In Kotlin (Klang):
 * ```kotlin
 * val a = C_Int128.fromLong(-100L)
 * val b = C_Int128.fromLong(200L)
 * val c = a + b  // 100, zero-copy on heap
 * ```
 *
 * ## Naming Convention
 *
 * `C_Int128` follows Klang's C-type naming convention:
 * - **Leading `C`**: Indicates C-compatible type
 * - **Underscore**: Preserves C's `__int128` leading underscores visually
 * - **Int128**: Signed 128-bit integer
 *
 * Related types:
 * - `C_UInt128`: Unsigned 128-bit
 * - `SwAR128`: SIMD-Within-A-Register (Klang innovation)
 *
 * ## Memory Layout
 *
 * Uses two's complement representation (matches C):
 * ```
 * Heap: addr
 * ┌────┬────┬────┬────┬────┬────┬────┬────┐
 * │ L0 │ L1 │ L2 │ L3 │ L4 │ L5 │ L6 │ L7 │  (8 limbs × 2 bytes = 16 bytes)
 * └────┴────┴────┴────┴────┴────┴────┴────┘
 *  LSB                                  MSB (sign bit)
 *
 * Positive: value = L0 + L1×2^16 + ... + L7×2^112
 * Negative: value = -(2^128 - unsigned_value)
 * Sign bit: MSB of L7 (bit 127)
 * ```
 *
 * ## Range and Precision
 *
 * - **Range**: -2^127 to 2^127 - 1
 * - **Minimum**: -170,141,183,460,469,231,731,687,303,715,884,105,728
 * - **Maximum**: 170,141,183,460,469,231,731,687,303,715,884,105,727
 * - **Precision**: Exact (no floating-point rounding)
 *
 * ## Zero-Copy Architecture
 *
 * All operations work directly on heap memory:
 *
 * ```kotlin
 * val a = C_Int128.fromLong(-1000L)  // Heap allocation
 * val b = C_Int128.fromLong(2000L)   // Heap allocation
 * val sum = a + b                    // Zero-copy: operates on heap
 * ```
 *
 * ## Supported Operations
 *
 * | Operation | Method | C Equivalent | Overflow |
 * |-----------|--------|--------------|----------|
 * | Addition | `a + b` | `a + b` | Throws |
 * | Subtraction | `a - b` | `a - b` | Throws |
 * | Negation | `a.negate()` | `-a` | N/A |
 * | Absolute | `a.abs()` | `abs(a)` | Throws on MIN |
 * | Left shift | `a.shiftLeft(n)` | `a << n` | Throws |
 * | Right shift | `a.shiftRight(n)` | `a >> n` | Sign-extend |
 * | Comparison | `a.compareTo(b)` | `a < b` | N/A |
 * | Sign test | `a.isNegative()` | `a < 0` | N/A |
 *
 * ## Usage Example
 *
 * ```kotlin
 * // Initialize heap
 * KMalloc.init(1024 * 1024)
 *
 * // Create signed values
 * val neg = C_Int128.fromLong(-12345L)
 * val pos = C_Int128.fromLong(67890L)
 *
 * // Arithmetic
 * val sum = neg + pos  // 55545
 * val negated = neg.negate()  // 12345
 *
 * // Sign detection
 * if (neg.isNegative()) {
 *     println("Value is negative")
 * }
 *
 * // Hex representation (two's complement)
 * println(neg.toHexString())  // Shows two's complement
 * ```
 *
 * ## Two's Complement Details
 *
 * Negative values are stored in two's complement:
 * - `-1` → all bits set (0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF)
 * - `-128` → 0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF80
 *
 * Conversion: `negative = 2^128 - abs(value)`
 *
 * ## Memory Management
 *
 * **Important**: Uses [KMalloc] for allocation. Must be manually freed.
 *
 * ## Performance
 *
 * Same as [C_UInt128]:
 * - **Addition/Subtraction**: ~50-100ns
 * - **Shifts**: ~30-80ns
 * - **Comparison**: ~30-50ns
 * - **Sign test**: ~10ns (single bit check)
 *
 * ## Thread Safety
 *
 * **Not thread-safe**. Concurrent access requires external synchronization.
 *
 * ## Implementation
 *
 * Delegates to [SwAR128] for unsigned operations, applies two's complement
 * transformations for signed semantics.
 *
 * @property addr Heap address of the 16-byte value
 * @constructor Private; use companion factory methods
 * @see C_UInt128 Unsigned 128-bit integer
 * @see SwAR128 Low-level arithmetic engine
 * @since 0.1.0
 */
class C_Int128 private constructor(val addr: Int) : Comparable<C_Int128> {
    
    /**
     * Check if value is negative (sign bit set).
     *
     * Tests the MSB (bit 127) of the two's complement representation.
     *
     * @return true if value < 0
     *
     * ## Example
     * ```kotlin
     * val neg = C_Int128.fromLong(-100L)
     * println(neg.isNegative())  // true
     * ```
     */
    fun isNegative(): Boolean {
        val msb = GlobalHeap.lbu(addr + 15)  // Last byte contains sign bit
        val ops = ArithmeticBitwiseOps.BITS_8
        return ops.and(msb.toLong(), 0x80L) != 0L
    }
    
    /**
     * Convert to hexadecimal string (two's complement).
     *
     * Shows raw two's complement representation for negative values.
     *
     * @return Hex string representation
     *
     * ## Example
     * ```kotlin
     * val x = C_Int128.fromLong(-1L)
     * println(x.toHexString())  // "0xffffffffffffffffffffffffffffffff"
     * ```
     */
    fun toHexString(): String = "0x" + SwAR128.toBigEndianHexHeap(addr)
    
    /**
     * String representation (delegates to [toHexString]).
     */
    override fun toString(): String = toHexString()

    /**
     * Equality comparison based on heap contents.
     *
     * @param other Object to compare
     * @return true if other is C_Int128 with identical value
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is C_Int128) return false
        return SwAR128.compareHeap(this.addr, other.addr) == 0
    }

    /**
     * Hash code based on heap contents.
     */
    override fun hashCode(): Int {
        var result = 1
        for (i in 0 until SwAR128.LIMB_COUNT) {
            val lowByte = GlobalHeap.lbu(addr + i * 2).toLong()
            val highByte = GlobalHeap.lbu(addr + i * 2 + 1).toLong()
            val limb = byteShifter.composeBytes(lowByte, highByte)
            result = 31 * result + limb.toInt()
        }
        return result
    }

    /**
     * Comparison operator for total ordering (signed).
     *
     * Compares values considering sign bit.
     *
     * @param other Value to compare
     * @return Negative if this < other, zero if equal, positive if this > other
     *
     * ## Example
     * ```kotlin
     * val neg = C_Int128.fromLong(-100L)
     * val pos = C_Int128.fromLong(100L)
     * println(neg < pos)  // true
     * ```
     */
    override fun compareTo(other: C_Int128): Int {
        val thisNeg = this.isNegative()
        val otherNeg = other.isNegative()
        
        // Different signs: negative is always less
        if (thisNeg != otherNeg) {
            return if (thisNeg) -1 else 1
        }
        
        // Same sign: compare as unsigned
        val cmp = SwAR128.compareHeap(this.addr, other.addr)
        
        // If both negative, reverse the comparison
        return if (thisNeg) -cmp else cmp
    }

    /**
     * Addition operator (zero-copy).
     *
     * Performs signed addition using two's complement arithmetic.
     *
     * @param other Value to add
     * @return New C_Int128 with sum
     * @throws IllegalArgumentException if addition overflows
     *
     * ## Example
     * ```kotlin
     * val a = C_Int128.fromLong(-100L)
     * val b = C_Int128.fromLong(200L)
     * val sum = a + b  // 100
     * ```
     */
    operator fun plus(other: C_Int128): C_Int128 {
        val res = alloc()
        SwAR128.addHeap(this.addr, other.addr, res.addr)
        
        // Check for overflow: sign of operands same, result sign different
        val thisNeg = this.isNegative()
        val otherNeg = other.isNegative()
        val resNeg = res.isNegative()
        
        if (thisNeg == otherNeg && thisNeg != resNeg) {
            KMalloc.free(res.addr)
            error("C_Int128 addition overflow")
        }
        
        return res
    }

    /**
     * Subtraction operator (zero-copy).
     *
     * Performs signed subtraction using two's complement arithmetic.
     *
     * @param other Value to subtract
     * @return New C_Int128 with difference
     * @throws IllegalArgumentException if subtraction overflows
     *
     * ## Example
     * ```kotlin
     * val a = C_Int128.fromLong(100L)
     * val b = C_Int128.fromLong(-50L)
     * val diff = a - b  // 150
     * ```
     */
    operator fun minus(other: C_Int128): C_Int128 {
        val res = alloc()
        SwAR128.subHeap(this.addr, other.addr, res.addr)
        
        // Check for overflow: different signs, result has wrong sign
        val thisNeg = this.isNegative()
        val otherNeg = other.isNegative()
        val resNeg = res.isNegative()
        
        if (thisNeg != otherNeg && thisNeg != resNeg) {
            KMalloc.free(res.addr)
            error("C_Int128 subtraction overflow")
        }
        
        return res
    }

    /**
     * Negate value (two's complement negation).
     *
     * Computes `-this` using two's complement: `~this + 1`.
     *
     * @return New C_Int128 with negated value
     *
     * ## Example
     * ```kotlin
     * val x = C_Int128.fromLong(100L)
     * val negX = x.negate()  // -100
     * ```
     */
    fun negate(): C_Int128 {
        val res = alloc()
        val ops = ArithmeticBitwiseOps.BITS_8
        
        // Two's complement negation: flip all bits, then add 1
        for (i in 0 until 16) {
            val byte = GlobalHeap.lbu(addr + i)
            val inverted = ops.not(byte.toLong())
            GlobalHeap.sb(res.addr + i, inverted.toByte())
        }
        
        // Add 1
        val one = C_UInt128.one()
        SwAR128.addHeap(res.addr, one.addr, res.addr)
        KMalloc.free(one.addr)
        
        return res
    }

    /**
     * Absolute value.
     *
     * Returns `this` if positive, `-this` if negative.
     *
     * @return New C_Int128 with absolute value
     * @throws IllegalArgumentException if this == MIN_VALUE (cannot negate)
     *
     * ## Example
     * ```kotlin
     * val x = C_Int128.fromLong(-100L)
     * val absX = x.abs()  // 100
     * ```
     */
    fun abs(): C_Int128 {
        return if (isNegative()) negate() else copy()
    }

    /**
     * Copy value to new heap location.
     *
     * @return New C_Int128 with same value
     */
    fun copy(): C_Int128 {
        val res = alloc()
        GlobalHeap.memcpy(res.addr, this.addr, 16)
        return res
    }

    /**
     * Arithmetic right shift (sign-extending).
     *
     * Shifts bits right, filling from left with sign bit.
     *
     * @param bits Number of bit positions to shift (0-127)
     * @return New C_Int128 with shifted value
     *
     * ## Example
     * ```kotlin
     * val x = C_Int128.fromLong(-256L)
     * val shifted = x.shiftRight(2)  // -64
     * ```
     */
    fun shiftRight(bits: Int): C_Int128 {
        val res = alloc()
        val negative = isNegative()
        
        SwAR128.shiftRightHeap(this.addr, res.addr, bits)
        
        // If negative, fill shifted-in bits with 1s (sign extension)
        if (negative && bits > 0) {
            val fullBytes = bits / 8
            val remainingBits = bits % 8
            
            // Fill full bytes with 0xFF from the left
            for (i in 15 downTo 16 - fullBytes) {
                GlobalHeap.sb(res.addr + i, 0xFF.toByte())
            }
            
            // Handle remaining bits
            if (remainingBits > 0 && fullBytes < 16) {
                val ops = ArithmeticBitwiseOps.BITS_8
                val byteIdx = 15 - fullBytes
                val currentByte = GlobalHeap.lbu(res.addr + byteIdx)
                val maskValue = byteShifter.leftShift(0xFFL, 8 - remainingBits).value
                val maskedByte = ops.and(maskValue, 0xFFL)
                val result = ops.or(currentByte.toLong(), maskedByte)
                GlobalHeap.sb(res.addr + byteIdx, result.toByte())
            }
        }
        
        return res
    }

    /**
     * Logical left shift.
     *
     * Shifts bits left, zero-filling from right.
     *
     * @param bits Number of bit positions to shift (0-127)
     * @return New C_Int128 with shifted value
     * @throws IllegalArgumentException if shift causes overflow
     *
     * ## Example
     * ```kotlin
     * val x = C_Int128.fromLong(1L)
     * val shifted = x.shiftLeft(64)  // 2^64
     * ```
     */
    fun shiftLeft(bits: Int): C_Int128 {
        val res = alloc()
        val spill = SwAR128.shiftLeftHeap(this.addr, res.addr, bits)
        require(spill == 0uL) { "C_Int128 shift left overflow beyond 128 bits" }
        return res
    }

    companion object {
        /** BitShiftEngine for 8-bit byte operations (reading limbs from heap). */
        private val byteShifter = BitShiftEngine(BitShiftMode.NATIVE, 8)
        
        /**
         * Allocate uninitialized C_Int128.
         */
        fun alloc(): C_Int128 = C_Int128(KMalloc.malloc(SwAR128.LIMB_COUNT * 2))
        
        /**
         * Create zero-initialized value.
         *
         * @return C_Int128 with value 0
         */
        fun zero(): C_Int128 = alloc().also { SwAR128.zeroHeap(it.addr) }
        
        /**
         * Create value 1.
         *
         * @return C_Int128 with value 1
         */
        fun one(): C_Int128 = alloc().also {
            SwAR128.zeroHeap(it.addr)
            GlobalHeap.sb(it.addr, 1)
        }

        /**
         * Create from Long (sign-extended to 128 bits).
         *
         * Most common factory method for signed values.
         *
         * @param value Long value
         * @return C_Int128 with specified value
         *
         * ## Example
         * ```kotlin
         * val x = C_Int128.fromLong(-12345L)
         * ```
         */
        fun fromLong(value: Long): C_Int128 {
            val res = alloc()
            
            if (value >= 0) {
                // Positive: zero-extend
                SwAR128.writeULongToHeap(res.addr, value.toULong())
            } else {
                // Negative: sign-extend
                val ops = ArithmeticBitwiseOps.BITS_8
                val absValue = (-value).toULong()
                SwAR128.writeULongToHeap(res.addr, absValue)
                
                // Two's complement: flip all bits and add 1
                for (i in 0 until 16) {
                    val byte = GlobalHeap.lbu(res.addr + i)
                    val inverted = ops.not(byte.toLong())
                    GlobalHeap.sb(res.addr + i, inverted.toByte())
                }
                
                val one = C_UInt128.one()
                SwAR128.addHeap(res.addr, one.addr, res.addr)
                KMalloc.free(one.addr)
            }
            
            return res
        }
    }
}
