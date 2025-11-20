package ai.solace.klang.mem

import ai.solace.klang.bitwise.BitShiftEngine
import ai.solace.klang.bitwise.BitShiftMode

/**
 * BitTwiddle: Bit-level manipulation operations on [GlobalHeap] memory.
 *
 * Provides fine-grained bit access and manipulation at arbitrary bit offsets within
 * heap memory. Uses little-endian bit numbering where bit 0 is the LSB of byte 0.
 *
 * ## Bit Numbering
 *
 * Little-endian bit layout within bytes:
 * ```
 * Byte at address A:
 * ┌───┬───┬───┬───┬───┬───┬───┬───┐
 * │ 7 │ 6 │ 5 │ 4 │ 3 │ 2 │ 1 │ 0 │  Bit positions
 * └───┴───┴───┴───┴───┴───┴───┴───┘
 * MSB                           LSB
 * ```
 *
 * ## Use Cases
 *
 * - **Bit fields**: Packing multiple flags/values into minimal space
 * - **Compression**: Working with arbitrary-width encoded values
 * - **Network protocols**: Parsing bit-packed protocol headers
 * - **Cryptography**: Bit-level operations on cipher state
 * - **Hardware interfaces**: Accessing packed register fields
 *
 * ## Performance Note
 *
 * These operations involve byte reads/writes for each bit access. For bulk bit
 * operations, consider working at the byte or word level when possible.
 *
 * All bitwise operations use [BitShiftEngine] to ensure correct behavior across
 * all platforms and to avoid Kotlin's type promotion issues.
 *
 * @see GlobalHeap For underlying memory operations
 */
object BitTwiddle {
    // Use 8-bit shifter for byte-level bit operations
    private val shifter8 = BitShiftEngine(BitShiftMode.NATIVE, 8)
    // Use 64-bit shifter for multi-byte operations
    private val shifter64 = BitShiftEngine(BitShiftMode.NATIVE, 64)
    
    /**
     * Reads a single bit from memory.
     *
     * @param addr The base byte address in heap memory.
     * @param bitOffset The bit offset from addr (0 = LSB of first byte).
     * @return 0 or 1.
     */
    fun getBit(addr: Int, bitOffset: Int): Int {
        val byteIndex = addr + shifter64.unsignedRightShift(bitOffset.toLong(), 3).value.toInt()
        val bitInByte = shifter64.bitwiseAnd(bitOffset.toLong(), 7).toInt()
        val b = GlobalHeap.lbu(byteIndex)
        val shifted = shifter8.unsignedRightShift(b.toLong(), bitInByte).value
        return shifter8.bitwiseAnd(shifted, 1).toInt()
    }

    /**
     * Writes a single bit to memory.
     *
     * @param addr The base byte address in heap memory.
     * @param bitOffset The bit offset from addr (0 = LSB of first byte).
     * @param value 0 or 1 (other values are masked).
     */
    fun setBit(addr: Int, bitOffset: Int, value: Int) {
        val byteIndex = addr + shifter64.unsignedRightShift(bitOffset.toLong(), 3).value.toInt()
        val bitInByte = shifter64.bitwiseAnd(bitOffset.toLong(), 7).toInt()
        val b = GlobalHeap.lbu(byteIndex)
        val mask = shifter8.leftShift(1, bitInByte).value.toInt()
        val maskedValue = shifter8.bitwiseAnd(value.toLong(), 1).toInt()
        val newB = if (maskedValue != 0) {
            shifter8.bitwiseOr(b.toLong(), mask.toLong()).toInt()
        } else {
            shifter8.bitwiseAnd(b.toLong(), shifter8.bitwiseNot(mask.toLong())).toInt()
        }
        GlobalHeap.sb(byteIndex, shifter8.bitwiseAnd(newB.toLong(), 0xFF).toByte())
    }

    /**
     * Extracts up to 64 bits from memory at an arbitrary bit offset.
     *
     * Reads bits in little-endian order, packing them into the low bits of a Long.
     * Useful for decoding bit-packed fields that don't align to byte boundaries.
     *
     * @param addr The base byte address in heap memory.
     * @param bitOffset The starting bit offset from addr.
     * @param bitCount The number of bits to extract (1-64).
     * @return The extracted bits as an unsigned value in the low bits of a Long.
     * @throws IllegalArgumentException if bitCount is not in 1..64.
     */
    fun getBitsLE(addr: Int, bitOffset: Int, bitCount: Int): Long {
        require(bitCount in 1..64)
        var out = 0L
        var shift = 0
        var bitsLeft = bitCount
        var cursorBit = bitOffset
        while (bitsLeft > 0) {
            val v = getBit(addr, cursorBit)
            if (v != 0) {
                val bitValue = shifter64.leftShift(1, shift).value
                out = shifter64.bitwiseOr(out, bitValue)
            }
            shift++
            cursorBit++
            bitsLeft--
        }
        return out
    }

    /** Insert up to 64 bits into memory at (addr, bitOffset) from low bits of [value]. */
    fun setBitsLE(addr: Int, bitOffset: Int, bitCount: Int, value: Long) {
        require(bitCount in 1..64)
        var v = value
        var cursorBit = bitOffset
        var bitsLeft = bitCount
        while (bitsLeft > 0) {
            val bit = shifter64.bitwiseAnd(v, 1L).toInt()
            setBit(addr, cursorBit, bit)
            v = shifter64.unsignedRightShift(v, 1).value
            cursorBit++
            bitsLeft--
        }
    }
}

