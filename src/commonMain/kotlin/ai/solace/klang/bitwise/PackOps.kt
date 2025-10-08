package ai.solace.klang.bitwise

/**
 * Helpers for compact packing/unpacking of small-width lanes (nibbles, bitplanes,
 * and mixed-width fields). These functions keep the nibble/bitplane logic in one
 * place so quantizers can stay focused on math rather than bit fiddling.
 */
object PackOps {
    fun packNibbles(low: Int, high: Int): Int {
        val lo = BitPrimitives.bitFieldExtract32(low, 0, 4)
        val hi = BitPrimitives.bitFieldExtract32(high, 0, 4)
        return (hi shl 4) or lo
    }

    fun unpackLowNibble(value: Int): Int = BitPrimitives.bitFieldExtract32(value, 0, 4)
    fun unpackHighNibble(value: Int): Int = BitPrimitives.bitFieldExtract32(value, 4, 4)

    /** Packs four 2-bit values (q0 lowest) into a single byte. */
    fun packQuads(q0: Int, q1: Int, q2: Int, q3: Int): Int {
        val mask = 0x03
        return ((q3 and mask) shl 6) or ((q2 and mask) shl 4) or ((q1 and mask) shl 2) or (q0 and mask)
    }

    fun unpackQuad(value: Int, index: Int): Int {
        require(index in 0..3) { "index must be 0..3" }
        val shift = index * 2
        return BitPrimitives.bitFieldExtract32(value, shift, 2)
    }

    /**
     * Generic bit-plane write. Width may be 1..8, bitIndex 0..7.
     * Returns the updated byte value without mutating the caller's storage.
     */
    fun bitplaneWrite(base: Int, value: Int, bitIndex: Int, width: Int): Int {
        require(bitIndex in 0..7)
        require(width in 1..(8 - bitIndex))
        val fieldMask = ((1 shl width) - 1)
        val cleared = base and (fieldMask shl bitIndex).inv()
        val toWrite = (value and fieldMask) shl bitIndex
        return (cleared or toWrite) and 0xFF
    }

    fun bitplaneRead(source: Int, bitIndex: Int, width: Int): Int {
        require(bitIndex in 0..7)
        require(width in 1..(8 - bitIndex))
        return BitPrimitives.bitFieldExtract32(source, bitIndex, width)
    }
}
