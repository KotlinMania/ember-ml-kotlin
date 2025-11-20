package ai.solace.klang.mem

import ai.solace.klang.bitwise.BitShiftEngine
import ai.solace.klang.bitwise.BitShiftConfig

/**
 * Typed views over [GlobalHeap] memory with little-endian semantics.
 *
 * Provides safe, bounds-checked access to heap memory through typed abstractions.
 * Each view wraps a base address and length, offering array-like indexing without
 * the overhead of actual array allocations.
 *
 * ## View Types
 *
 * - [U8View]: Unsigned 8-bit (byte) view
 * - [U16View]: Unsigned 16-bit (short/limb) view, little-endian
 * - [U32View]: Unsigned 32-bit (word) view, little-endian
 *
 * ## Zero-Copy Architecture
 *
 * Views operate directly on heap memory without copying:
 * ```kotlin
 * val view = U32View(addr, count = 100)  // No allocation, just wraps address
 * view[5] = 42                            // Direct heap write
 * val x = view[5]                         // Direct heap read
 * ```
 *
 * ## Safety
 *
 * All operations include bounds checking. Out-of-bounds access throws
 * [IllegalArgumentException]. For performance-critical code that has already
 * validated indices, consider using [GlobalHeap] directly.
 *
 * ## Use Cases
 *
 * - **Structured data**: Treating raw memory as typed arrays
 * - **Serialization**: Reading/writing binary formats
 * - **Buffers**: Managing reusable scratch space
 * - **FFI**: Preparing data for native library calls
 *
 * @see GlobalHeap For underlying memory operations
 */

/**
 * U8View: Unsigned 8-bit byte view over heap memory.
 *
 * Provides array-like access to a contiguous region of heap memory as unsigned bytes.
 * All accesses are bounds-checked for safety.
 *
 * @property base The starting heap address (byte offset).
 * @property length The number of bytes in this view.
 */
data class U8View(val base: Int, val length: Int) {
    /**
     * Reads an unsigned byte at index [i].
     *
     * @param i The index (0-based).
     * @return The byte value (0-255).
     * @throws IllegalArgumentException if i is out of bounds.
     */
    fun get(i: Int): Int { require(i in 0 until length); return GlobalHeap.lbu(base + i) }
    
    /**
     * Writes an unsigned byte at index [i].
     *
     * @param i The index (0-based).
     * @param v The byte value to write (masked to 0-255).
     * @throws IllegalArgumentException if i is out of bounds.
     */
    fun set(i: Int, v: Int) { 
        require(i in 0 until length)
        GlobalHeap.sb(base + i, (v and 0xFF).toByte())
    }
    
    /**
     * Fills the entire view with a constant byte value.
     *
     * @param v The byte value to fill with (masked to 0-255).
     */
    fun fill(v: Int) { GlobalHeap.memset(base, v, length) }
    
    /**
     * Copies all bytes from another view into this view.
     *
     * @param src The source view (must have same length).
     * @throws IllegalArgumentException if lengths don't match.
     */
    fun copyFrom(src: U8View) { require(src.length == length); GlobalHeap.memcpy(base, src.base, length) }
    
    /**
     * Creates a sub-view (slice) of this view.
     *
     * @param offset The starting offset within this view.
     * @param len The length of the slice.
     * @return A new view over the sliced region.
     * @throws IllegalArgumentException if slice parameters are invalid.
     */
    fun slice(offset: Int, len: Int): U8View { require(offset>=0 && len>=0 && offset+len<=length); return U8View(base+offset,len) }
}

/**
 * U16View: Unsigned 16-bit limb view over heap memory (little-endian).
 *
 * Provides array-like access to a contiguous region of heap memory as unsigned 16-bit values.
 * Each value is stored in little-endian byte order (low byte first).
 *
 * @property base The starting heap address (byte offset).
 * @property limbCount The number of 16-bit limbs in this view.
 */
data class U16View(val base: Int, val limbCount: Int) {
    /**
     * Reads an unsigned 16-bit value at index [i] (little-endian).
     *
     * @param i The limb index (0-based).
     * @return The 16-bit value (0-65535).
     * @throws IllegalArgumentException if i is out of bounds.
     */
    fun get(i: Int): Int {
        require(i in 0 until limbCount)
        val eng8 = BitShiftEngine(BitShiftConfig.defaultMode, 8)
        val lowByte = GlobalHeap.lbu(base + i*2).toLong()
        val highByte = GlobalHeap.lbu(base + i*2 + 1).toLong()
        return ((lowByte or eng8.byteShiftLeft(highByte, 1).value).toInt() and 0xFFFF)
    }
    
    /**
     * Writes an unsigned 16-bit value at index [i] (little-endian).
     *
     * @param i The limb index (0-based).
     * @param v The 16-bit value to write (masked to 0-65535).
     * @throws IllegalArgumentException if i is out of bounds.
     */
    fun set(i: Int, v: Int) {
        require(i in 0 until limbCount)
        val eng8 = BitShiftEngine(BitShiftConfig.defaultMode, 8)
        val vv = v and 0xFFFF
        GlobalHeap.sb(base+i*2, (vv and 0xFF).toByte())
        val highByte = eng8.byteShiftRight(vv.toLong(), 1)
        GlobalHeap.sb(base+i*2+1, (highByte.value.toInt() and 0xFF).toByte())
    }
    
    /**
     * Fills the entire view with zeros.
     */
    fun fillZero() { GlobalHeap.memset(base, 0, limbCount*2) }
    
    /**
     * Creates a sub-view (slice) of this view.
     *
     * @param offset The starting limb offset within this view.
     * @param len The number of limbs in the slice.
     * @return A new view over the sliced region.
     * @throws IllegalArgumentException if slice parameters are invalid.
     */
    fun slice(offset: Int, len: Int): U16View { require(offset>=0 && len>=0 && offset+len<=limbCount); return U16View(base+offset*2, len) }
}

/**
 * U32View: Unsigned 32-bit word view over heap memory (little-endian).
 *
 * Provides array-like access to a contiguous region of heap memory as unsigned 32-bit values.
 * Each value is stored in little-endian byte order.
 *
 * @property base The starting heap address (byte offset).
 * @property wordCount The number of 32-bit words in this view.
 */
data class U32View(val base: Int, val wordCount: Int) {
    /**
     * Reads a signed 32-bit value at index [i] (little-endian).
     *
     * @param i The word index (0-based).
     * @return The 32-bit value (as Kotlin Int, which is signed).
     * @throws IllegalArgumentException if i is out of bounds.
     */
    fun get(i: Int): Int { require(i in 0 until wordCount); return GlobalHeap.lw(base + i*4) }
    
    /**
     * Writes a signed 32-bit value at index [i] (little-endian).
     *
     * @param i The word index (0-based).
     * @param v The 32-bit value to write.
     * @throws IllegalArgumentException if i is out of bounds.
     */
    fun set(i: Int, v: Int) { require(i in 0 until wordCount); GlobalHeap.sw(base + i*4, v) }
    
    /**
     * Fills the entire view with zeros.
     */
    fun fillZero() { GlobalHeap.memset(base, 0, wordCount*4) }
    
    /**
     * Creates a sub-view (slice) of this view.
     *
     * @param offset The starting word offset within this view.
     * @param len The number of words in the slice.
     * @return A new view over the sliced region.
     * @throws IllegalArgumentException if slice parameters are invalid.
     */
    fun slice(offset: Int, len: Int): U32View { require(offset>=0 && len>=0 && offset+len<=wordCount); return U32View(base+offset*4, len) }
}

