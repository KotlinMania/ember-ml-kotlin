package ai.solace.klang.mem

import ai.solace.klang.bitwise.BitShiftEngine
import ai.solace.klang.bitwise.BitShiftMode

/**
 * GlobalHeap: A single, deterministic heap for all C-like memory operations.
 *
 * KLang's foundation is a pure Kotlin [ByteArray] that serves as the entire program's heap memory.
 * All "pointers" are [Int] byte offsets into this array, providing C-like memory semantics
 * across all Kotlin multiplatform targets (JavaScript, Native, JVM).
 *
 * ## Key Features
 *
 * - **Deterministic Layout**: All loads/stores use little-endian byte ordering
 * - **Zero Dependencies**: No native interop, GMP, or platform-specific code required
 * - **Cross-Platform**: Identical behavior on JS, Native, and JVM targets
 * - **Simple Addressing**: Pointers are Int offsets, array indices equal addresses
 * - **Auto-Growing**: Heap expands automatically (1.5x growth factor)
 *
 * ## Design Philosophy
 *
 * Unlike language runtimes with garbage collection, GlobalHeap provides explicit control
 * over memory layout. This enables:
 * - Porting C programs that expect contiguous memory
 * - Zero-copy operations (modify data in-place)
 * - Deterministic serialization (dump/restore entire heap state)
 * - Predictable performance (no GC pauses)
 *
 * ## Memory Model
 *
 * ```
 * GlobalHeap Memory Layout:
 * ┌─────────────────────────────────────────────┐
 * │  Used Memory (0 to hp)  │  Free Space       │
 * └─────────────────────────────────────────────┘
 *                           ↑
 *                          hp (heap pointer)
 * ```
 *
 * ## Usage Example
 *
 * ```kotlin
 * // Initialize heap with 1MB
 * GlobalHeap.init(1024 * 1024)
 *
 * // Allocate 128 bytes
 * val addr = GlobalHeap.malloc(128)
 *
 * // Write an integer at address (little-endian)
 * GlobalHeap.sw(addr, 42)
 *
 * // Read it back
 * val value = GlobalHeap.lw(addr)  // returns 42
 *
 * // Allocate zero-initialized array
 * val arr = GlobalHeap.calloc(10, 4)  // 10 ints = 40 bytes
 * ```
 *
 * ## Thread Safety
 *
 * **Not thread-safe**. External synchronization required for concurrent access.
 *
 * ## Performance
 *
 * - **Allocation**: O(1) bump allocator (may trigger O(n) resize)
 * - **Load/Store**: O(1) direct array access
 * - **Growth**: O(n) when doubling, amortized O(1)
 *
 * @see KMalloc For free-list allocator with malloc/free semantics
 * @see CScalars For type-safe scalar variables on the heap
 * @since 0.1.0
 */
object GlobalHeap {
    /** The backing memory array. Access is private to ensure all I/O goes through typed accessors. */
    private var mem: ByteArray = ByteArray(0)
    
    /** Heap pointer: next free address. Simple bump allocator. */
    private var hp: Int = 0
    
    // BitShift engines for multi-byte operations
    private val shifter8 = BitShiftEngine(BitShiftMode.NATIVE, 8)
    private val shifter16 = BitShiftEngine(BitShiftMode.NATIVE, 16)
    private val shifter32 = BitShiftEngine(BitShiftMode.NATIVE, 32)
    private val shifter64 = BitShiftEngine(BitShiftMode.NATIVE, 64)

    /**
     * Total heap size in bytes.
     *
     * This is the current capacity, not the amount used. See [used] for allocated bytes.
     */
    val size: Int get() = mem.size
    
    /**
     * Number of bytes currently allocated (used).
     *
     * This tracks the bump allocator position, not individual malloc/free operations.
     * Use [KMalloc] for tracking individual allocations.
     */
    val used: Int get() = hp

    /**
     * Initialize or reinitialize the heap with specified size.
     *
     * This allocates a new [ByteArray] and resets the heap pointer to 0.
     * Any existing heap data is discarded.
     *
     * @param bytes Initial heap size in bytes. Must be >= 0.
     * @throws IllegalArgumentException if bytes < 0
     *
     * ## Example
     * ```kotlin
     * GlobalHeap.init(1 shl 20)  // 1MB heap
     * ```
     */
    fun init(bytes: Int) {
        require(bytes >= 0) { "Heap size must be non-negative" }
        mem = ByteArray(bytes)
        hp = 0
    }

    /**
     * Reset heap pointer to 0, effectively freeing all allocations.
     *
     * The backing [ByteArray] is retained, so the heap capacity remains unchanged.
     * This is useful for resetting between test runs or program phases.
     *
     * **Warning**: Any pointers to previous allocations become invalid.
     */
    fun reset() { hp = 0 }

    /**
     * Dispose of the heap, releasing the backing memory.
     *
     * Sets the heap to an empty [ByteArray] and resets the pointer.
     * Call [init] to use the heap again.
     */
    fun dispose() { 
        mem = ByteArray(0)
        hp = 0 
    }

    /**
     * Allocate uninitialized memory (like C's malloc).
     *
     * Returns the address (byte offset) of the allocated block.
     * Memory contents are undefined (may contain previous data).
     *
     * If the heap is too small, it automatically grows (1.5x until sufficient).
     *
     * @param bytes Number of bytes to allocate. Must be >= 0.
     * @return Address of allocated block (Int offset into heap)
     * @throws IllegalArgumentException if bytes < 0
     *
     * ## Example
     * ```kotlin
     * val addr = GlobalHeap.malloc(256)  // Allocate 256 bytes
     * ```
     *
     * ## Complexity
     * O(1) typical, O(n) if heap resize needed
     *
     * @see calloc For zero-initialized allocation
     * @see KMalloc For proper malloc/free with coalescing
     */
    fun malloc(bytes: Int): Int {
        require(bytes >= 0) { "Cannot allocate negative bytes" }
        val base = hp
        val end = base + bytes
        ensure(end)
        hp = end
        return base
    }

    /**
     * Allocate zero-initialized memory (like C's calloc).
     *
     * Allocates `count * elemSize` bytes and zeroes all bytes.
     *
     * @param count Number of elements to allocate
     * @param elemSize Size of each element in bytes
     * @return Address of zero-initialized block
     * @throws IllegalArgumentException if count or elemSize < 0
     *
     * ## Example
     * ```kotlin
     * val arr = GlobalHeap.calloc(10, 4)  // 10 ints (40 bytes), all zero
     * ```
     *
     * @see malloc For uninitialized allocation
     */
    fun calloc(count: Int, elemSize: Int): Int {
        require(count >= 0 && elemSize >= 0) { "Count and elemSize must be non-negative" }
        val total = count * elemSize
        val p = malloc(total)
        memset(p, 0, total)
        return p
    }

    /**
     * Free memory (no-op in bump allocator).
     *
     * GlobalHeap uses a simple bump allocator, so individual frees have no effect.
     * Use [reset] to reclaim all memory, or use [KMalloc] for proper free-list management.
     *
     * @param ptr Address to free (ignored)
     *
     * @see KMalloc.free For actual deallocation
     */
    fun free(@Suppress("UNUSED_PARAMETER") ptr: Int) { 
        /* no-op bump allocator */ 
    }

    /**
     * Ensure heap capacity, growing if necessary.
     *
     * Internal method used by malloc/calloc. Grows heap by 1.5x until [minSize] is reached.
     *
     * @param minSize Minimum required capacity in bytes
     */
    private fun ensure(minSize: Int) {
        if (minSize <= mem.size) return
        var newSize = mem.size.coerceAtLeast(1024)
        // 1.5x growth: newSize + (newSize / 2)
        while (newSize < minSize) {
            val half = shifter32.unsignedRightShift(newSize.toLong(), 1).value.toInt()
            newSize = newSize + half
        }
        val next = ByteArray(newSize)
        mem.copyInto(next, 0, 0, mem.size)
        mem = next
    }

    /**
     * Public API to ensure heap capacity.
     *
     * Allows allocators like [KMalloc] to pre-grow the heap while preserving contents.
     *
     * @param minSize Minimum required capacity in bytes
     */
    fun ensureCapacity(minSize: Int) = ensure(minSize)

    // ========== Typed Load/Store Operations (Little-Endian) ==========

    /**
     * Load byte (signed).
     *
     * @param addr Address to load from
     * @return Signed byte value
     * @throws IndexOutOfBoundsException if addr is invalid
     */
    fun lb(addr: Int): Byte = mem[addr]
    
    /**
     * Store byte.
     *
     * @param addr Address to store at
     * @param value Byte value to store
     * @throws IndexOutOfBoundsException if addr is invalid
     */
    fun sb(addr: Int, value: Byte) { mem[addr] = value }

    /**
     * Load byte (unsigned, zero-extended to Int).
     *
     * @param addr Address to load from
     * @return Unsigned byte value (0..255)
     * @throws IndexOutOfBoundsException if addr is invalid
     */
    fun lbu(addr: Int): Int {
        val byte = lb(addr).toInt()
        return shifter8.bitwiseAnd(byte.toLong(), 0xFF).toInt()
    }

    /**
     * Load half-word (16-bit signed short, little-endian).
     *
     * @param addr Address to load from (should be 2-byte aligned for best performance)
     * @return Signed short value
     * @throws IndexOutOfBoundsException if addr or addr+1 is invalid
     */
    fun lh(addr: Int): Short {
        val b0 = lbu(addr)
        val b1 = lbu(addr + 1)
        val shifted = shifter16.leftShift(b1.toLong(), 8)
        val combined = shifter16.bitwiseOr(b0.toLong(), shifted.value)
        return combined.toShort()
    }
    
    /**
     * Store half-word (16-bit short, little-endian).
     *
     * @param addr Address to store at
     * @param value Short value to store
     * @throws IndexOutOfBoundsException if addr or addr+1 is invalid
     */
    fun sh(addr: Int, value: Short) {
        val v = shifter16.bitwiseAnd(value.toLong(), 0xFFFF)
        val byte0 = shifter16.bitwiseAnd(v, 0xFF)
        val byte1Shifted = shifter16.unsignedRightShift(v, 8)
        val byte1 = shifter16.bitwiseAnd(byte1Shifted.value, 0xFF)
        sb(addr + 0, byte0.toByte())
        sb(addr + 1, byte1.toByte())
    }

    /**
     * Load word (32-bit signed int, little-endian).
     *
     * @param addr Address to load from (should be 4-byte aligned for best performance)
     * @return Signed int value
     * @throws IndexOutOfBoundsException if addr to addr+3 is invalid
     */
    fun lw(addr: Int): Int {
        val bytes = LongArray(4) { i -> lbu(addr + i).toLong() }
        return shifter32.composeBytes(bytes).toInt()
    }
    
    fun sw(addr: Int, value: Int) {
        for (i in 0 until 4) {
            val byte = shifter32.extractByte(value.toLong(), i)
            sb(addr + i, byte.toByte())
        }
    }

    fun ld(addr: Int): Long {
        val bytes = LongArray(8) { i -> lbu(addr + i).toLong() }
        return shifter64.composeBytes(bytes)
    }
    
    fun sd(addr: Int, value: Long) {
        for (i in 0 until 8) {
            val byte = shifter64.extractByte(value, i)
            sb(addr + i, byte.toByte())
        }
    }

    fun lwf(addr: Int): Float = Float.fromBits(lw(addr))
    fun swf(addr: Int, value: Float) = sw(addr, value.toRawBits())

    fun ldf(addr: Int): Double = Double.fromBits(ld(addr))
    fun sdf(addr: Int, value: Double) = sd(addr, value.toRawBits())

    /** memcpy: undefined for overlap; fast word-at-a-time. */
    fun memcpy(dst: Int, src: Int, bytes: Int) = FastMem.memcpy(dst, src, bytes)

    /** memmove: overlap-safe, fast word-at-a-time. */
    fun memmove(dst: Int, src: Int, bytes: Int) = FastMem.memmove(dst, src, bytes)

    fun memset(addr: Int, value: Int, bytes: Int) = FastMem.memset(addr, value, bytes)

    /** Copy an IntArray into heap as 32-bit little-endian words. */
    fun copyFromIntArray(dst: Int, src: IntArray, count: Int = src.size) {
        require(count <= src.size) { "count exceeds src size" }
        var d = dst
        for (i in 0 until count) {
            sw(d, src[i])
            d += 4
        }
    }

    /** Copy heap 32-bit words into an IntArray. */
    fun copyToIntArray(src: Int, dst: IntArray, count: Int = dst.size) {
        require(count <= dst.size) { "count exceeds dst size" }
        var s = src
        for (i in 0 until count) {
            dst[i] = lw(s)
            s += 4
        }
    }
}

// (Pointer extension utilities live in default-package PointerExtensions.kt to avoid package visibility issues)
