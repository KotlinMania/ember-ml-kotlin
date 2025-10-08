package ai.solace.klang.buffer

import ai.solace.klang.bitwise.ArithmeticBitwiseOps

/**
 * LimbBuffer: packed little-endian 16-bit limb storage over a ByteArray.
 * - Each limb is exactly 2 bytes (LSB first), tightly packed, no padding.
 * - All combining/splitting uses arithmetic-only (no language shifts) via ArithmeticBitwiseOps.
 */
class LimbBuffer private constructor(
    val bytes: ByteArray,
    val baseByte: Int,
    val limbCount: Int,
) {
    private val a32 = ArithmeticBitwiseOps.BITS_32

    fun getU16(index: Int): Int {
        require(index in 0 until limbCount)
        val off = baseByte + index * 2
        val b0 = (bytes[off].toInt() and 0xFF)
        val b1 = (bytes[off + 1].toInt() and 0xFF)
        // (b1 << 8) | b0 using arithmetic-only
        val hi = a32.leftShift(b1.toLong(), 8).toInt()
        return (hi + b0) and 0xFFFF
    }

    fun setU16(index: Int, value: Int) {
        require(index in 0 until limbCount)
        val v = value and 0xFFFF
        val off = baseByte + index * 2
        // low = v % 256, high = floor(v / 256)
        val low = v % 256
        val hi = v / 256
        bytes[off] = (low and 0xFF).toByte()
        bytes[off + 1] = (hi and 0xFF).toByte()
    }

    fun slice(fromLimb: Int, len: Int): LimbBuffer {
        require(fromLimb >= 0 && len >= 0 && fromLimb + len <= limbCount)
        return LimbBuffer(bytes, baseByte + fromLimb * 2, len)
    }

    fun asUShortArray(): UShortArray {
        val out = UShortArray(limbCount)
        for (i in 0 until limbCount) out[i] = getU16(i).toUShort()
        return out
    }

    companion object {
        fun allocate(limbCount: Int): LimbBuffer = LimbBuffer(ByteArray(limbCount * 2), 0, limbCount)

        fun fromUShorts(vararg limbs: UShort): LimbBuffer {
            val buf = allocate(limbs.size)
            for (i in limbs.indices) buf.setU16(i, limbs[i].toInt() and 0xFFFF)
            return buf
        }

        fun fromUShortArray(arr: UShortArray): LimbBuffer = fromUShorts(*arr)
    }
}

