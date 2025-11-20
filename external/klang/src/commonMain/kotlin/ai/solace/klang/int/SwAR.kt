package ai.solace.klang.int

import ai.solace.klang.bitwise.BitShiftEngine
import ai.solace.klang.bitwise.BitShiftMode

/**
 * SWAR (SIMD Within A Register) helpers for per-lane unsigned averages.
 * Lanes are packed in a 32-bit Int as either 4×u8 or 2×u16.
 * All operations avoid cross-lane carry via lane masks and logical shifts.
 */
object SwAR {
    // 4×u8 lanes in a 32-bit Int
    private const val U8_LSB_CLEAR = 0xFEFEFEFE.toInt() // ~LSB_per_lane
    private const val U8_LSB_MASK  = 0x01010101.toInt() //  LSB_per_lane

    /** Returns per-lane floor((a+b)/2) for 4×u8 lanes (0..255). */
    fun avgU8Trunc(a: Int, b: Int): Int {
        val axb = a xor b
        val half = (axb and U8_LSB_CLEAR) ushr 1
        return (a and b) + half
    }

    /** Returns per-lane round((a+b)/2) for 4×u8 lanes; ties round up. */
    fun avgU8Round(a: Int, b: Int): Int {
        val axb = a xor b
        val half = (axb and U8_LSB_CLEAR) ushr 1
        val base = (a and b) + half
        val round = axb and U8_LSB_MASK // add 1 when per-lane sum is odd
        return base + round
    }

    // Strict arithmetic helpers (no &, ^, <<, >>) -----------------------------------------
    private inline fun udiv(x: UInt, d: UInt): UInt = x / d
    private inline fun umod(x: UInt, d: UInt): UInt = x - d * (x / d)

    // Fast arithmetic-only helpers using exact FP reciprocals for powers of two (no bitwise).
    // Double has a 53-bit mantissa; multiplying a 32-bit unsigned value by these reciprocals
    // followed by truncation is exact (no rounding error) for 256^k divisors.
    private const val INV_256 = 1.0 / 256.0
    private const val INV_65536 = 1.0 / 65536.0

    private inline fun div256(u: UInt): UInt {
        val q = (u.toDouble() * INV_256).toUInt() // trunc toward zero == floor for positive
        return q
    }
    private inline fun rem256(u: UInt, q: UInt): UInt = u - q * 256u

    private inline fun div65536(u: UInt): UInt {
        val q = (u.toDouble() * INV_65536).toUInt()
        return q
    }
    private inline fun rem65536(u: UInt, q: UInt): UInt = u - q * 65536u

    private fun getU8Lane(u: UInt, lane: Int): UInt {
        val pow = when (lane) {
            0 -> 1u
            1 -> 256u
            2 -> 65536u
            else -> 16777216u
        }
        val q = udiv(u, pow)
        return umod(q, 256u)
    }

    private fun packU8(b0: UInt, b1: UInt, b2: UInt, b3: UInt): UInt =
        b0 + 256u * b1 + 65536u * b2 + 16777216u * b3

    /** Arithmetic-only (no bitwise) per-lane average: truncates ((a+b)/2) in u8 lanes. */
    fun avgU8TruncArith(a: Int, b: Int): Int {
        val au = a.toUInt(); val bu = b.toUInt()
        // Decompose using integer division by 256 sequentially (4 divs per value)
        val qa = au / 256u; val a0 = au - qa * 256u
        val qa1 = qa / 256u; val a1 = qa - qa1 * 256u
        val qa2 = qa1 / 256u; val a2 = qa1 - qa2 * 256u
        val a3 = qa2
        val qb = bu / 256u; val b0 = bu - qb * 256u
        val qb1 = qb / 256u; val b1 = qb - qb1 * 256u
        val qb2 = qb1 / 256u; val b2 = qb1 - qb2 * 256u
        val b3 = qb2
        val r0 = udiv(a0 + b0, 2u)
        val r1 = udiv(a1 + b1, 2u)
        val r2 = udiv(a2 + b2, 2u)
        val r3 = udiv(a3 + b3, 2u)
        return packU8(r0, r1, r2, r3).toInt()
    }

    /** Arithmetic-only (no bitwise) per-lane average: rounds to nearest (ties up). */
    fun avgU8RoundArith(a: Int, b: Int): Int {
        val au = a.toUInt(); val bu = b.toUInt()
        val qa = au / 256u; val a0 = au - qa * 256u
        val qa1 = qa / 256u; val a1 = qa - qa1 * 256u
        val qa2 = qa1 / 256u; val a2 = qa1 - qa2 * 256u
        val a3 = qa2
        val qb = bu / 256u; val b0 = bu - qb * 256u
        val qb1 = qb / 256u; val b1 = qb - qb1 * 256u
        val qb2 = qb1 / 256u; val b2 = qb1 - qb2 * 256u
        val b3 = qb2
        val r0 = udiv(a0 + b0 + 1u, 2u)
        val r1 = udiv(a1 + b1 + 1u, 2u)
        val r2 = udiv(a2 + b2 + 1u, 2u)
        val r3 = udiv(a3 + b3 + 1u, 2u)
        return packU8(r0, r1, r2, r3).toInt()
    }

    // 2×u16 lanes in a 32-bit Int
    private const val U16_LSB_CLEAR = 0xFFFEFFFE.toInt()
    private const val U16_LSB_MASK  = 0x00010001.toInt()

    /** Returns per-lane floor((a+b)/2) for 2×u16 lanes (0..65535). */
    fun avgU16Trunc(a: Int, b: Int): Int {
        val axb = a xor b
        val half = (axb and U16_LSB_CLEAR) ushr 1
        return (a and b) + half
    }

    /** Returns per-lane round((a+b)/2) for 2×u16 lanes; ties round up. */
    fun avgU16Round(a: Int, b: Int): Int {
        val axb = a xor b
        val half = (axb and U16_LSB_CLEAR) ushr 1
        val base = (a and b) + half
        val round = axb and U16_LSB_MASK
        return base + round
    }

    private fun getU16Lane(u: UInt, lane: Int): UInt {
        val pow = if (lane == 0) 1u else 65536u
        val q = udiv(u, pow)
        return umod(q, 65536u)
    }

    private fun packU16(w0: UInt, w1: UInt): UInt = w0 + 65536u * w1

    // Optional LUTs for u8 lane average (strict arithmetic usage via decomposition + idx=a*256+b)
    private val LUT_U8_TRUNC: UIntArray by lazy {
        val t = UIntArray(256 * 256)
        var i = 0
        while (i < 256) {
            var j = 0
            while (j < 256) {
                t[i * 256 + j] = ((i + j) / 2).toUInt()
                j++
            }
            i++
        }
        t
    }
    private val LUT_U8_ROUND: UIntArray by lazy {
        val t = UIntArray(256 * 256)
        var i = 0
        while (i < 256) {
            var j = 0
            while (j < 256) {
                t[i * 256 + j] = ((i + j + 1) / 2).toUInt()
                j++
            }
            i++
        }
        t
    }

    /** Arithmetic-only using LUT for per-lane avg (removes /2 inside the hot path). */
    fun avgU8TruncLutArith(a: Int, b: Int): Int {
        val au = a.toUInt(); val bu = b.toUInt()
        val qa = au / 256u; val a0 = au - qa * 256u
        val qa1 = qa / 256u; val a1 = qa - qa1 * 256u
        val qa2 = qa1 / 256u; val a2 = qa1 - qa2 * 256u
        val a3 = qa2
        val qb = bu / 256u; val b0 = bu - qb * 256u
        val qb1 = qb / 256u; val b1 = qb - qb1 * 256u
        val qb2 = qb1 / 256u; val b2 = qb1 - qb2 * 256u
        val b3 = qb2
        val r0 = LUT_U8_TRUNC[(a0 * 256u + b0).toInt()].toUInt()
        val r1 = LUT_U8_TRUNC[(a1 * 256u + b1).toInt()].toUInt()
        val r2 = LUT_U8_TRUNC[(a2 * 256u + b2).toInt()].toUInt()
        val r3 = LUT_U8_TRUNC[(a3 * 256u + b3).toInt()].toUInt()
        return packU8(r0, r1, r2, r3).toInt()
    }

    fun avgU8RoundLutArith(a: Int, b: Int): Int {
        val au = a.toUInt(); val bu = b.toUInt()
        val qa = au / 256u; val a0 = au - qa * 256u
        val qa1 = qa / 256u; val a1 = qa - qa1 * 256u
        val qa2 = qa1 / 256u; val a2 = qa1 - qa2 * 256u
        val a3 = qa2
        val qb = bu / 256u; val b0 = bu - qb * 256u
        val qb1 = qb / 256u; val b1 = qb - qb1 * 256u
        val qb2 = qb1 / 256u; val b2 = qb1 - qb2 * 256u
        val b3 = qb2
        val r0 = LUT_U8_ROUND[(a0 * 256u + b0).toInt()].toUInt()
        val r1 = LUT_U8_ROUND[(a1 * 256u + b1).toInt()].toUInt()
        val r2 = LUT_U8_ROUND[(a2 * 256u + b2).toInt()].toUInt()
        val r3 = LUT_U8_ROUND[(a3 * 256u + b3).toInt()].toUInt()
        return packU8(r0, r1, r2, r3).toInt()
    }

    /** Arithmetic-only u16 variant (no bitwise). */
    fun avgU16TruncArith(a: Int, b: Int): Int {
        val au = a.toUInt(); val bu = b.toUInt()
        val qa = div65536(au); val a0 = rem65536(au, qa); val a1 = qa
        val qb = div65536(bu); val b0 = rem65536(bu, qb); val b1 = qb
        val r0 = udiv(a0 + b0, 2u)
        val r1 = udiv(a1 + b1, 2u)
        return packU16(r0, r1).toInt()
    }

    fun avgU16RoundArith(a: Int, b: Int): Int {
        val au = a.toUInt(); val bu = b.toUInt()
        val qa = div65536(au); val a0 = rem65536(au, qa); val a1 = qa
        val qb = div65536(bu); val b0 = rem65536(bu, qb); val b1 = qb
        val r0 = udiv(a0 + b0 + 1u, 2u)
        val r1 = udiv(a1 + b1 + 1u, 2u)
        return packU16(r0, r1).toInt()
    }

    // Scalar references (for tests) ---------------------------------------------------------

    /** Scalar per-lane u8 trunc average (reference). */
    fun refAvgU8Trunc(a: Int, b: Int): Int {
        val engine = BitShiftEngine(BitShiftMode.ARITHMETIC, 32)
        var out = 0
        var shift = 0
        val mask8 = engine.getMask(8)
        repeat(4) {
            val av = engine.bitwiseAnd(engine.rightShift(a.toLong(), shift).value, mask8)
            val bv = engine.bitwiseAnd(engine.rightShift(b.toLong(), shift).value, mask8)
            val r = (av + bv) / 2
            val maskedR = engine.bitwiseAnd(r, mask8)
            out = engine.bitwiseOr(out.toLong(), engine.leftShift(maskedR, shift).value).toInt()
            shift += 8
        }
        return out
    }

    /** Scalar per-lane u8 round-to-nearest (ties up). */
    fun refAvgU8Round(a: Int, b: Int): Int {
        val engine = BitShiftEngine(BitShiftMode.ARITHMETIC, 32)
        var out = 0
        var shift = 0
        val mask8 = engine.getMask(8)
        repeat(4) {
            val av = engine.bitwiseAnd(engine.rightShift(a.toLong(), shift).value, mask8)
            val bv = engine.bitwiseAnd(engine.rightShift(b.toLong(), shift).value, mask8)
            val r = (av + bv + 1) / 2
            val maskedR = engine.bitwiseAnd(r, mask8)
            out = engine.bitwiseOr(out.toLong(), engine.leftShift(maskedR, shift).value).toInt()
            shift += 8
        }
        return out
    }

    /** Scalar per-lane u16 trunc average (reference). */
    fun refAvgU16Trunc(a: Int, b: Int): Int {
        val engine = BitShiftEngine(BitShiftMode.ARITHMETIC, 32)
        val mask16 = engine.getMask(16)
        val a0 = engine.bitwiseAnd(a.toLong(), mask16)
        val a1 = engine.bitwiseAnd(engine.rightShift(a.toLong(), 16).value, mask16)
        val b0 = engine.bitwiseAnd(b.toLong(), mask16)
        val b1 = engine.bitwiseAnd(engine.rightShift(b.toLong(), 16).value, mask16)
        val r0 = (a0 + b0) / 2
        val r1 = (a1 + b1) / 2
        val maskedR0 = engine.bitwiseAnd(r0, mask16)
        val maskedR1 = engine.bitwiseAnd(r1, mask16)
        return engine.bitwiseOr(maskedR0, engine.leftShift(maskedR1, 16).value).toInt()
    }

    /** Scalar per-lane u16 round-to-nearest (ties up). */
    fun refAvgU16Round(a: Int, b: Int): Int {
        val engine = BitShiftEngine(BitShiftMode.ARITHMETIC, 32)
        val mask16 = engine.getMask(16)
        val a0 = engine.bitwiseAnd(a.toLong(), mask16)
        val a1 = engine.bitwiseAnd(engine.rightShift(a.toLong(), 16).value, mask16)
        val b0 = engine.bitwiseAnd(b.toLong(), mask16)
        val b1 = engine.bitwiseAnd(engine.rightShift(b.toLong(), 16).value, mask16)
        val r0 = (a0 + b0 + 1) / 2
        val r1 = (a1 + b1 + 1) / 2
        val maskedR0 = engine.bitwiseAnd(r0, mask16)
        val maskedR1 = engine.bitwiseAnd(r1, mask16)
        return engine.bitwiseOr(maskedR0, engine.leftShift(maskedR1, 16).value).toInt()
    }
}
