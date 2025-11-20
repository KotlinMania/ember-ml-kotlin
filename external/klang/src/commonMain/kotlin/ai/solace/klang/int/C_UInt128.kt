package ai.solace.klang.int

import ai.solace.klang.bitwise.BitShiftEngine
import ai.solace.klang.bitwise.BitShiftMode
import ai.solace.klang.mem.GlobalHeap
import ai.solace.klang.mem.KMalloc

/**
 * C_UInt128: C-compatible `unsigned __int128` type with zero-copy heap operations.
 *
 * Models C's compiler extension type `unsigned __int128`, providing exact 128-bit
 * unsigned integer arithmetic with zero-copy semantics on heap memory.
 *
 * ## C Compatibility
 *
 * In C (GCC/Clang extensions):
 * ```c
 * unsigned __int128 a = 100;
 * unsigned __int128 b = 200;
 * unsigned __int128 c = a + b;  // 300
 * ```
 *
 * In Kotlin (Klang):
 * ```kotlin
 * val a = C_UInt128.fromULong(100uL)
 * val b = C_UInt128.fromULong(200uL)
 * val c = a + b  // 300, zero-copy on heap
 * ```
 *
 * ## Naming Convention
 *
 * `C_UInt128` follows Klang's C-type naming convention:
 * - **Leading `C`**: Indicates C-compatible type (like `CDouble`, `CFloat128`)
 * - **Underscore**: Preserves C's `__int128` leading underscores visually
 * - **UInt128**: Unsigned 128-bit integer
 *
 * Related types:
 * - `C_Int128`: Signed 128-bit (future)
 * - `SwAR128`: SIMD-Within-A-Register (Klang innovation, not C-compatible)
 *
 * ## Memory Layout
 *
 * Storage format matches C's little-endian representation:
 * ```
 * Heap: addr
 * ┌────┬────┬────┬────┬────┬────┬────┬────┐
 * │ L0 │ L1 │ L2 │ L3 │ L4 │ L5 │ L6 │ L7 │  (8 limbs × 2 bytes = 16 bytes)
 * └────┴────┴────┴────┴────┴────┴────┴────┘
 *  LSB                                  MSB
 *
 * Value = L0 + L1×2^16 + L2×2^32 + ... + L7×2^112
 * ```
 *
 * ## Range and Precision
 *
 * - **Range**: 0 to 2^128 - 1 (340,282,366,920,938,463,463,374,607,431,768,211,455)
 * - **Precision**: Exact (no floating-point rounding)
 * - **Limb size**: 16 bits (matches SwAR128 for interoperability)
 *
 * ## Zero-Copy Architecture
 *
 * All operations work directly on heap memory without IntArray allocations:
 *
 * ```kotlin
 * val a = C_UInt128.fromULong(1000uL)  // Heap allocation
 * val b = C_UInt128.fromULong(2000uL)  // Heap allocation
 * val sum = a + b                      // Zero-copy: operates on heap directly
 * ```
 *
 * ## Supported Operations
 *
 * | Operation | Method | C Equivalent | Overflow |
 * |-----------|--------|--------------|----------|
 * | Addition | `a + b` | `a + b` | Throws |
 * | Subtraction | `a - b` | `a - b` | Throws |
 * | Left shift | `a.shiftLeft(n)` | `a << n` | Throws |
 * | Right shift | `a.shiftRight(n)` | `a >> n` | Zero-fill |
 * | Comparison | `a.compareTo(b)` | `a < b` | N/A |
 * | Equality | `a == b` | `a == b` | N/A |
 *
 * ## Usage Example
 *
 * ```kotlin
 * // Initialize heap
 * KMalloc.init(1024 * 1024)
 *
 * // Create values (zero-extended from ULong)
 * val max64 = C_UInt128.fromULong(ULong.MAX_VALUE)
 * val one = C_UInt128.one()
 *
 * // Arithmetic (throws on overflow)
 * val overflowSafe = max64 + one  // Works: 2^64
 *
 * // Bitwise shifts
 * val shifted = one.shiftLeft(64)  // 2^64
 *
 * // Comparison
 * if (shifted > max64) {
 *     println("Shifted value exceeds 64-bit max")
 * }
 *
 * // Hex representation
 * println(shifted.toHexString())  // "0x10000000000000000"
 * ```
 *
 * ## Memory Management
 *
 * **Important**: Uses [KMalloc] for allocation. Must be manually freed:
 *
 * ```kotlin
 * val x = C_UInt128.fromULong(100uL)
 * // ... use x ...
 * KMalloc.free(x.addr)  // Required to avoid leaks
 * ```
 *
 * **Recommended**: Use arena pattern or scope-based management (future).
 *
 * ## Performance
 *
 * - **Addition/Subtraction**: ~50-100ns (8 limbs + carry/borrow)
 * - **Shifts**: ~30-80ns (bulk memory operations)
 * - **Comparison**: ~30-50ns (8 limb comparisons)
 * - **Memory overhead**: 16 bytes (value) + 8 bytes (KMalloc metadata)
 *
 * ## Thread Safety
 *
 * **Not thread-safe**. Concurrent access requires external synchronization.
 *
 * ## Implementation
 *
 * Delegates to [SwAR128] for low-level arithmetic operations:
 * - `SwAR128.addHeap`: Addition with carry propagation
 * - `SwAR128.subHeap`: Subtraction with borrow propagation
 * - `SwAR128.shiftLeftHeap`: Left shift with overflow detection
 * - `SwAR128.shiftRightHeap`: Right shift (zero-fill)
 * - `SwAR128.compareHeap`: Lexicographic comparison
 *
 * @property addr Heap address of the 16-byte value
 * @constructor Private; use companion factory methods
 * @see SwAR128 Low-level arithmetic engine
 * @see C_Int128 Signed 128-bit integer (future)
 * @see KMalloc Heap memory allocator
 * @since 0.1.0
 */
class C_UInt128 private constructor(val addr: Int) : Comparable<C_UInt128> {
    
    /**
     * Convert to hexadecimal string.
     *
     * Format: Big-endian hex (e.g., "0x123456789ABCDEF...").
     *
     * @return Hex string representation
     *
     * ## Example
     * ```kotlin
     * val x = C_UInt128.fromULong(255uL)
     * println(x.toHexString())  // "ff" (leading zeros trimmed)
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
     * Uses zero-copy comparison via [SwAR128.compareHeap].
     *
     * @param other Object to compare
     * @return true if other is C_UInt128 with identical value
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is C_UInt128) return false
        return SwAR128.compareHeap(this.addr, other.addr) == 0
    }

    /**
     * Hash code based on heap contents.
     *
     * Polynomial rolling hash over all 8 limbs.
     *
     * @return Hash code
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
     * Comparison operator for total ordering.
     *
     * Compares values lexicographically from MSB to LSB.
     *
     * @param other Value to compare
     * @return Negative if this < other, zero if equal, positive if this > other
     *
     * ## Example
     * ```kotlin
     * val a = C_UInt128.fromULong(100uL)
     * val b = C_UInt128.fromULong(200uL)
     * println(a < b)  // true
     * ```
     */
    override fun compareTo(other: C_UInt128): Int = SwAR128.compareHeap(this.addr, other.addr)

    /**
     * Addition operator (zero-copy).
     *
     * Performs `this + other` with carry propagation.
     * Result is stored in newly allocated heap memory.
     *
     * @param other Value to add
     * @return New C_UInt128 with sum
     * @throws IllegalArgumentException if addition overflows 128 bits
     *
     * ## Example
     * ```kotlin
     * val a = C_UInt128.fromULong(100uL)
     * val b = C_UInt128.fromULong(200uL)
     * val sum = a + b  // 300 (zero-copy)
     * ```
     */
    operator fun plus(other: C_UInt128): C_UInt128 {
        val res = alloc()
        val carry = SwAR128.addHeap(this.addr, other.addr, res.addr)
        require(carry == 0) { "C_UInt128 addition overflow" }
        return res
    }

    /**
     * Subtraction operator (zero-copy).
     *
     * Performs `this - other` with borrow propagation.
     * Result is stored in newly allocated heap memory.
     *
     * @param other Value to subtract
     * @return New C_UInt128 with difference
     * @throws IllegalArgumentException if subtraction underflows (this < other)
     *
     * ## Example
     * ```kotlin
     * val a = C_UInt128.fromULong(300uL)
     * val b = C_UInt128.fromULong(100uL)
     * val diff = a - b  // 200 (zero-copy)
     * ```
     */
    operator fun minus(other: C_UInt128): C_UInt128 {
        val res = alloc()
        val borrow = SwAR128.subHeap(this.addr, other.addr, res.addr)
        require(borrow == 0) { "C_UInt128 subtraction underflow" }
        return res
    }

    /**
     * Logical left shift (zero-copy).
     *
     * Shifts bits left by specified amount, zero-filling from right.
     * Throws if any bits would be shifted out (overflow).
     *
     * @param bits Number of bit positions to shift (0-127)
     * @return New C_UInt128 with shifted value
     * @throws IllegalArgumentException if shift causes overflow
     *
     * ## Example
     * ```kotlin
     * val x = C_UInt128.fromULong(1uL)
     * val shifted = x.shiftLeft(64)  // 2^64
     * ```
     */
    fun shiftLeft(bits: Int): C_UInt128 {
        val res = alloc()
        val spill = SwAR128.shiftLeftHeap(this.addr, res.addr, bits)
        require(spill == 0uL) { "C_UInt128 shift left overflow beyond 128 bits" }
        return res
    }

    /**
     * Logical right shift (zero-copy).
     *
     * Shifts bits right by specified amount, zero-filling from left.
     * Bits shifted out are discarded.
     *
     * @param bits Number of bit positions to shift (0-127)
     * @return New C_UInt128 with shifted value
     *
     * ## Example
     * ```kotlin
     * val x = C_UInt128.fromULong(256uL)
     * val shifted = x.shiftRight(8)  // 1 (256 >> 8)
     * ```
     */
    fun shiftRight(bits: Int): C_UInt128 {
        val res = alloc()
        SwAR128.shiftRightHeap(this.addr, res.addr, bits)
        return res
    }

    companion object {
        /** BitShiftEngine for 8-bit byte operations (reading limbs from heap). */
        private val byteShifter = BitShiftEngine(BitShiftMode.NATIVE, 8)
        
        /**
         * Allocate uninitialized C_UInt128.
         *
         * Allocates 16 bytes on heap via [KMalloc].
         * Contents are undefined (may contain garbage).
         *
         * @return New C_UInt128 pointing to allocated memory
         */
        fun alloc(): C_UInt128 = C_UInt128(KMalloc.malloc(SwAR128.LIMB_COUNT * 2))
        
        /**
         * Create zero-initialized value.
         *
         * @return C_UInt128 with value 0
         *
         * ## Example
         * ```kotlin
         * val zero = C_UInt128.zero()
         * println(zero.toHexString())  // "0x0"
         * ```
         */
        fun zero(): C_UInt128 = alloc().also { SwAR128.zeroHeap(it.addr) }
        
        /**
         * Create value 1.
         *
         * @return C_UInt128 with value 1
         *
         * ## Example
         * ```kotlin
         * val one = C_UInt128.one()
         * println(one.toHexString())  // "0x1"
         * ```
         */
        fun one(): C_UInt128 = alloc().also {
            SwAR128.zeroHeap(it.addr)
            GlobalHeap.sb(it.addr, 1)
        }

        /**
         * Create from ULong (zero-extended to 128 bits).
         *
         * Most common factory method.
         *
         * @param value ULong value (0 to 2^64-1)
         * @return C_UInt128 with specified value
         *
         * ## Example
         * ```kotlin
         * val x = C_UInt128.fromULong(12345678901234567890uL)
         * println(x.toHexString())  // "0xab54a98ceb1f0ad2"
         * ```
         */
        fun fromULong(value: ULong): C_UInt128 = alloc().also {
            SwAR128.writeULongToHeap(it.addr, value)
        }
    }
}
