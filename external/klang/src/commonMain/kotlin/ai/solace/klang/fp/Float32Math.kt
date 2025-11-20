package ai.solace.klang.fp

import ai.solace.klang.bitwise.BitShiftEngine
import ai.solace.klang.bitwise.BitShiftMode
import kotlin.math.abs
import kotlin.math.floor

/**
 * Software IEEE-754 float32 multiply using integer bit manipulation and
 * round-to-nearest, ties-to-even. Handles zeros, subnormals, infinities, NaNs.
 */
object Float32Math {
    private const val SIGN_MASK = 0x80000000.toInt()
    private const val EXP_MASK  = 0x7F800000.toInt()
    private const val FRAC_MASK = 0x007FFFFF
    private const val EXP_BIAS  = 127
    private const val IMPLICIT_BIT = 1 shl 23
    private const val TYPE_WIDTH = 24 + 3 // 24 significand bits plus R/G/S

    private const val CANONICAL_NAN = 0x7FC00000.toInt()
    private val SHIFT64 = BitShiftEngine(BitShiftMode.NATIVE, 64)

    fun mul(a: Float, b: Float): Float = Float.fromBits(mulBits(a.toRawBits(), b.toRawBits()))

    fun add(a: Float, b: Float): Float = Float.fromBits(addBits(a.toRawBits(), b.toRawBits()))

    fun sub(a: Float, b: Float): Float = Float.fromBits(subBits(a.toRawBits(), b.toRawBits()))

    fun fma(a: Float, b: Float, c: Float): Float = add(mul(a, b), c)

    fun lrint(value: Float): Long = roundToNearestEven(value.toDouble()).toLong()
    fun lrint(value: Double): Long = roundToNearestEven(value).toLong()

    fun nearbyint(value: Float): Float = roundToNearestEven(value.toDouble()).toFloat()

    fun div(a: Float, b: Float): Float = Float.fromBits(divBits(a.toRawBits(), b.toRawBits()))

    // Integer conversions (compiler-rt style, nearest-even rounding where applicable)
    fun intToFloat(a: Int): Float = Float.fromBits(intToFloatBits(a))
    fun uintToFloat(a: UInt): Float = Float.fromBits(uintToFloatBits(a))

    // __floatsisf: 32-bit signed int -> float32
    fun intToFloatBits(a: Int): Int {
        if (a == 0) return 0
        val sign = if (a < 0) SIGN_MASK else 0
        // Work in unsigned magnitude using Long to avoid overflow on Int.MIN_VALUE
        var v = if (a < 0) (-(a.toLong())) else a.toLong()
        // Find highest set bit index n (0..31)
        var n = 63 - v.countLeadingZeroBits()
        // Build exponent
        var exp = n + EXP_BIAS
        // Compose fraction with rounding to nearest-even
        var frac: Int
        if (n <= 23) {
            frac = ((v shl (23 - n)) and FRAC_MASK.toLong()).toInt()
            // no rounding bits
        } else {
            val shift = n - 23
            val shifted = v ushr shift
            frac = (shifted and FRAC_MASK.toLong()).toInt()
            val guard = ((v ushr (shift - 1)) and 1L).toInt()
            val sticky = if (shift > 1) (v and ((1L shl (shift - 1)) - 1L)) != 0L else false
            if (guard == 1 && (sticky || (frac and 1) == 1)) {
                frac += 1
                if (frac > FRAC_MASK) {
                    // Carry into implicit bit -> renormalize
                    frac = 0
                    exp += 1
                }
            }
        }
        // Pack
        return sign or ((exp and 0xFF) shl 23) or frac
    }

    // __floatunsisf: 32-bit unsigned int -> float32
    fun uintToFloatBits(a: UInt): Int {
        if (a == 0u) return 0
        val v = a.toLong() and 0xFFFF_FFFFL
        var n = 63 - v.countLeadingZeroBits()
        var exp = n + EXP_BIAS
        var frac: Int
        if (n <= 23) {
            frac = ((v shl (23 - n)) and FRAC_MASK.toLong()).toInt()
        } else {
            val shift = n - 23
            val shifted = v ushr shift
            frac = (shifted and FRAC_MASK.toLong()).toInt()
            val guard = ((v ushr (shift - 1)) and 1L).toInt()
            val sticky = if (shift > 1) (v and ((1L shl (shift - 1)) - 1L)) != 0L else false
            if (guard == 1 && (sticky || (frac and 1) == 1)) {
                frac += 1
                if (frac > FRAC_MASK) {
                    frac = 0
                    exp += 1
                }
            }
        }
        return ((exp and 0xFF) shl 23) or frac
    }

    // __fixsfsi: float32 -> signed int32 (round toward zero), no range checking
    fun floatToInt(a: Float): Int = floatToIntBits(a.toRawBits())
    fun floatToUInt(a: Float): UInt = floatToUIntBits(a.toRawBits()).toUInt()

    fun floatToIntBits(aBits: Int): Int {
        val aAbs = aBits and 0x7FFFFFFF.toInt()
        val e = ((aAbs ushr 23) and 0xFF) - EXP_BIAS
        if (e < 0) return 0
        var r = ((aAbs and FRAC_MASK) or IMPLICIT_BIT).toLong()
        if (e > 23) r = r shl (e - 23) else r = r ushr (23 - e)
        val s = if ((aBits and SIGN_MASK) != 0) -1 else 0
        return ((r.toInt()) xor s) - s
    }

    // __fixunssfsi: float32 -> unsigned int32 (round toward zero), negatives become 0; no range checking
    fun floatToUIntBits(aBits: Int): Int {
        if ((aBits and SIGN_MASK) != 0) return 0
        val aAbs = aBits and 0x7FFFFFFF.toInt()
        val e = ((aAbs ushr 23) and 0xFF) - EXP_BIAS
        if (e < 0) return 0
        var r = ((aAbs and FRAC_MASK) or IMPLICIT_BIT).toLong()
        if (e > 23) r = r shl (e - 23) else r = r ushr (23 - e)
        return r.toInt()
    }

    // ---- Float32 <-> Float64 (Double) conversions ----
    // float32 -> float64 (extendsfdf2)
    fun floatToDouble(f: Float): Double = Double.fromBits(floatToDoubleBits(f.toRawBits()))
    fun floatToDoubleBits(fBits: Int): Long {
        val sign = (fBits.toLong() and 0x80000000L) shl 32
        val exp = (fBits ushr 23) and 0xFF
        val frac = fBits and FRAC_MASK
        if (exp == 0xFF) {
            // Inf/NaN
            val dExp = 0x7FFL shl 52
            val dFrac = if (frac != 0) 0x0008_0000_0000_0000L else 0L // canonical qNaN
            return sign or dExp or dFrac
        }
        if (exp == 0) {
            if (frac == 0) return sign // signed zero
            // subnormal: normalize mantissa
            var m = frac
            var shift = 0
            while ((m and IMPLICIT_BIT) == 0) { m = m shl 1; shift++ }
            m = m and FRAC_MASK
            val eUnb = -126 - shift
            val dExp = ((eUnb + 1023).toLong() and 0x7FF) shl 52
            val dFrac = (m.toLong() shl (52 - 23))
            return sign or dExp or dFrac
        }
        // normal
        val eUnb = exp - 127
        val dExp = ((eUnb + 1023).toLong() and 0x7FF) shl 52
        val dFrac = (frac.toLong() shl (52 - 23))
        return sign or dExp or dFrac
    }

    // float64 -> float32 (truncdfsf2) with nearest-even rounding
    fun doubleToFloat(d: Double): Float = Float.fromBits(doubleToFloatBits(d.toRawBits()))
    fun doubleToFloatBits(dBits: Long): Int {
        val sign = ((dBits ushr 32) and 0x80000000L).toInt()
        val exp = ((dBits ushr 52) and 0x7FF).toInt()
        val frac = dBits and 0x000F_FFFF_FFFF_FFFFL
        if (exp == 0x7FF) {
            // Inf/NaN
            val fExp = 0xFF shl 23
            val fFrac = if (frac != 0L) 0x0040_0000 else 0
            return sign or fExp or fFrac
        }
        if (exp == 0) {
            if (frac == 0L) return sign // signed zero
            // subnormal double: normalize mantissa
            var m = frac
            var eUnb = -1022
            while ((m and (1L shl 52)) == 0L) {
                m = m shl 1
                eUnb -= 1
            }
            // Now treat as normal with m having implicit 1 at bit 52
            return packDoubleToFloat(sign, eUnb, m and ((1L shl 52) - 1))
        }
        // normal
        val eUnb = exp - 1023
        val m = (1L shl 52) or frac
        return packDoubleToFloat(sign, eUnb, m and ((1L shl 52) - 1))
    }

    private fun roundToNearestEven(value: Double): Double {
        if (value.isNaN() || value.isInfinite()) return value
        val lower = floor(value)
        val diff = value - lower
        return when {
            diff > 0.5 -> lower + 1.0
            diff < 0.5 -> lower
            else -> if ((lower % 2.0) == 0.0) lower else lower + 1.0
        }
    }

    private fun packDoubleToFloat(sign: Int, eUnb: Int, mantNoImplicit: Long): Int {
        // m = implicit1<<52 | mantNoImplicit already accounted by caller
        var m = mantNoImplicit or (1L shl 52)
        var E = eUnb + 127
        // Normal range
        if (E >= 0xFF) return sign or (0xFF shl 23) // overflow -> inf
        if (E > 0) {
            // shift right by 29 to get 24-bit (1+23) with rounding
            val shift = 52 - 23 // 29
            val mant24 = (m ushr shift).toInt()
            val rem = m and ((1L shl shift) - 1)
            val guard = (rem ushr (shift - 1)) and 1L
            val sticky = rem and ((1L shl (shift - 1)) - 1)
            var frac = mant24 and FRAC_MASK
            var expField = E shl 23
            // rounding
            if (guard == 1L && (sticky != 0L || (frac and 1) == 1)) {
                frac += 1
                if (frac > FRAC_MASK) {
                    // carry into implicit bit
                    frac = 0
                    E += 1
                    if (E >= 0xFF) return sign or (0xFF shl 23)
                    expField = E shl 23
                }
            }
            return sign or expField or frac
        }
        // Subnormal or underflow
        // shift = 30 - E (see derivation); E <= 0
        val shift = 30 - E
        if (shift >= 53) return sign // underflow to zero
        var mant = (m ushr (shift)).toInt()
        val rem = m and ((1L shl shift) - 1)
        val guard = (rem ushr (shift - 1)) and 1L
        val sticky = rem and ((1L shl (shift - 1)) - 1)
        var frac = mant and FRAC_MASK
        if (guard == 1L && (sticky != 0L || (frac and 1) == 1)) {
            frac += 1
            if (frac > FRAC_MASK) {
                // becomes smallest normal
                return sign or (1 shl 23) // exp=1, frac=0 with sign already applied later; adjust:
            }
        }
        return sign or frac
    }
    // sqrtf: IEEE‑754 sqrt for float32 using LLVM libc FPUtil algorithm
    fun sqrtBits(aBits: Int): Int {
        val sign = aBits and SIGN_MASK
        val exp = (aBits ushr 23) and 0xFF
        val frac = aBits and FRAC_MASK

        // NaN / Inf ladder
        if (exp == 0xFF) {
            return if (frac != 0) {
                // NaN -> canonical qNaN
                CANONICAL_NAN
            } else {
                // Infinity
                if (sign != 0) CANONICAL_NAN else aBits // -Inf -> NaN, +Inf -> +Inf
            }
        }
        // Zeros: preserve sign of zero
        if ((aBits and 0x7FFFFFFF.toInt()) == 0) return aBits
        // Negative inputs (non-zero): NaN
        if (sign != 0) return CANONICAL_NAN

        // Extract unbiased exponent and mantissa
        var xExp = exp - EXP_BIAS
        var xMant = frac.toUInt()
        val One: UInt = (1u shl 23)

        // Normalize subnormals and add hidden bit for normals
        if (exp == 0) {
            // For subnormals, set exponent to the position of the implicit 1 then normalize mantissa
            xExp += 1 // let xExp be the correct exponent of One bit before normalize
            // Shift mantissa until implicit bit appears
            // Equivalent to internal::normalize<float>(xExp, xMant)
            var m = xMant
            // Binary search steps not necessary; simple loop is fine here
            while (m < One) {
                m = m shl 1
                xExp -= 1
            }
            xMant = m
        } else {
            xMant = xMant or One
        }

        // Ensure exponent is even. If odd, shift mantissa left by 1 and decrement exponent.
        if ((xExp and 1) != 0) {
            xExp -= 1
            xMant = xMant shl 1
        }

        // Shift‑and‑add square root for mantissa in fixed‑point (1.xx with One as 1.0)
        var y: UInt = One // y starts at 1.0 in fixed point
        var r: UInt = (xMant - One) // initial residue
        var current = One shr 1
        while (current != 0u) {
            r = r shl 1
            val tmp = (y shl 1) + current // 2*y(n-1) + 2^(-n-1)
            if (r >= tmp) {
                r -= tmp
                y += current
            }
            current = current shr 1
        }
        // Extra iteration for rounding decision
        val lsb = (y and 1u) != 0u
        var rb = false
        r = r shl 2
        var tmp = (y shl 2) + 1u
        if (r >= tmp) {
            r -= tmp
            rb = true
        }

        // Pack exponent (divide by 2 and bias)
        val outExp = ((xExp shr 1) + EXP_BIAS) and 0xFF
        var outMant: UInt = (y - One) // remove hidden bit
        var outBits = ((outExp shl 23) or (outMant.toInt() and FRAC_MASK))
        // Round to nearest, ties‑to‑even
        if (rb && (lsb || (r != 0u))) {
            outBits += 1
        }
        return outBits
    }

    fun mulBits(aBits: Int, bBits: Int): Int {
        val aExponent = (aBits ushr 23) and 0xFF
        val bExponent = (bBits ushr 23) and 0xFF
        var aSignificand = aBits and FRAC_MASK
        var bSignificand = bBits and FRAC_MASK
        val productSign = (aBits xor bBits) and SIGN_MASK
        var scale = 0

        // Special-case ladder (compiler-rt style)
        val maxExpMinus1 = (0xFF - 1).toUInt()
        if ((aExponent - 1).toUInt() >= maxExpMinus1 || (bExponent - 1).toUInt() >= maxExpMinus1) {
            val aAbs = aBits and 0x7FFFFFFF.toInt()
            val bAbs = bBits and 0x7FFFFFFF.toInt()
            // NaNs
            if (aAbs > EXP_MASK) return aBits or 0x00400000
            if (bAbs > EXP_MASK) return bBits or 0x00400000
            // Infinities
            if (aAbs == EXP_MASK) return if (bAbs != 0) productSign or EXP_MASK else 0x7FC00000.toInt()
            if (bAbs == EXP_MASK) return if (aAbs != 0) productSign or EXP_MASK else 0x7FC00000.toInt()
            // Zeros
            if (aAbs == 0) return productSign
            if (bAbs == 0) return productSign
            // Denormals: normalize and accumulate scale
            if (aAbs < IMPLICIT_BIT) {
                val (ns, sc) = normalizeForMul(aSignificand)
                aSignificand = ns
                scale += sc
            }
            if (bAbs < IMPLICIT_BIT) {
                val (ns, sc) = normalizeForMul(bSignificand)
                bSignificand = ns
                scale += sc
            }
        }

        // Add implicit bit
        aSignificand = aSignificand or IMPLICIT_BIT
        bSignificand = bSignificand or IMPLICIT_BIT

        // wideMultiply(aSignificand, bSignificand << exponentBits)
        val exponentBits = 8
        val aU = aSignificand.toLong() and 0xFFFF_FFFFL
        val bShift = (bSignificand.toLong() and 0xFFFF_FFFFL) shl exponentBits
        val full = aU * bShift
        var productHi = ((full ushr 32) and 0xFFFF_FFFFL).toInt()
        var productLo = (full and 0xFFFF_FFFFL).toInt()

        var productExponent = aExponent + bExponent - EXP_BIAS + scale

        // Normalize
        if ((productHi and IMPLICIT_BIT) != 0) {
            productExponent += 1
        } else {
            val s = wideLeftShift1(productHi, productLo)
            productHi = s.first
            productLo = s.second
        }

        // Overflow → infinity
        if (productExponent >= 0xFF) return productSign or EXP_MASK

        if (productExponent <= 0) {
            val shift = 1 - productExponent
            if (shift >= 32) return productSign
            val s = wideRightShiftWithSticky(productHi, productLo, shift)
            productHi = s.first
            productLo = s.second
        } else {
            productHi = (productHi and FRAC_MASK) or ((productExponent and 0xFF) shl 23)
        }

        // Insert sign
        productHi = productHi or productSign

        // Final rounding: nearest, ties-to-even
        val loU = productLo.toUInt()
        val signBitU = 0x80000000u
        if (loU > signBitU) productHi += 1
        if (loU == signBitU) productHi += (productHi and 1)
        return productHi
    }

    // Count leading zeros in a 24-bit value (bits in positions 23..0). Returns 24 if zero.
    private fun clz24(xIn: Long): Int {
        var x = xIn and 0xFFFFFF
        if (x == 0L) return 24
        var n = 0
        var v = x
        var bit = 1 shl 23
        while ((v and bit.toLong()) == 0L) {
            n++
            bit = bit ushr 1
        }
        return n
    }
    // Normalize for multiply: set implicit bit, return (newSig, scaleAdj)
    private fun normalizeForMul(sigIn: Int): Pair<Int, Int> {
        var sig = sigIn and FRAC_MASK
        if (sig == 0) return 0 to 0
        var shift = 0
        while ((sig and IMPLICIT_BIT) == 0 && shift < 24) {
            sig = sig shl 1
            shift++
        }
        val scale = 1 - shift
        return sig to scale
    }

    private fun wideLeftShift1(hi: Int, lo: Int): Pair<Int, Int> {
        val full = ((hi.toLong() and 0xFFFF_FFFFL) shl 32) or (lo.toLong() and 0xFFFF_FFFFL)
        val shifted = full shl 1
        val newHi = ((shifted ushr 32) and 0xFFFF_FFFFL).toInt()
        val newLo = (shifted and 0xFFFF_FFFFL).toInt()
        return newHi to newLo
    }

    private fun wideRightShiftWithSticky(hiIn: Int, loIn: Int, count: Int): Pair<Int, Int> {
        var hi = hiIn
        var lo = loIn
        var sticky = false
        if (count == 0) return hi to lo
        if (count < 32) {
            val dropped = lo and ((1 shl count) - 1)
            sticky = dropped != 0
            val newLo = (hi shl (32 - count)) or (lo ushr count)
            val newHi = hi ushr count
            lo = newLo
            hi = newHi
        } else {
            val k = count - 32
            val droppedLow = lo != 0
            val droppedHigh = if (k == 0) 0 else (hi and ((1 shl k) - 1))
            sticky = droppedLow || droppedHigh != 0
            val newLo = if (k == 0) hi else (hi ushr k)
            lo = newLo
            hi = 0
        }
        if (sticky) lo = lo or 1
        return hi to lo
    }
    // NOTE: Minimal positive-only add for our quant accumulations.
    // Assumes a,b are finite and non-negative. Returns IEEE-754 round-to-nearest-even.
    // Positive-only fast path used in some accumulator experiments; for
    // now, defer to host Float addition to match Kotlin's behavior exactly.
    fun addPos(a: Float, b: Float): Float = a + b

    // Full IEEE-754 float32 addition with round-to-nearest, ties-to-even,
    // transliterated from LLVM compiler-rt fp_add_impl.inc for binary32.
    fun addBits(aBitsIn: Int, bBitsIn: Int): Int {
        var aRep = aBitsIn
        var bRep = bBitsIn
        val aAbs = aRep and 0x7FFFFFFF.toInt()
        val bAbs = bRep and 0x7FFFFFFF.toInt()

        // Detect zero/inf/NaN ranges quickly like LLVM (abs - 1 >= inf - 1)
        if ((aAbs - 1 >= EXP_MASK - 1) || (bAbs - 1 >= EXP_MASK - 1)) {
            // NaNs
            if (aAbs > EXP_MASK) return aRep or 0x00400000 // quietBit
            if (bAbs > EXP_MASK) return bRep or 0x00400000
            // Infinities
            if (aAbs == EXP_MASK) {
                if ((aRep xor bRep) == SIGN_MASK) return 0x7FC00000.toInt() // qNaN
                return aRep
            }
            if (bAbs == EXP_MASK) return bRep
            // Zeros
            if (aAbs == 0) {
                if (bAbs == 0) return aRep and bRep // sign of +0 + -0 is +0 by AND
                return bRep
            }
            if (bAbs == 0) return aRep
        }

        // Ensure |a| >= |b|
        if (bAbs > aAbs) {
            val t = aRep; aRep = bRep; bRep = t
        }

        var aExp = (aRep ushr 23) and 0xFF
        var bExp = (bRep ushr 23) and 0xFF
        var aSig = (aRep and FRAC_MASK).toLong()
        var bSig = (bRep and FRAC_MASK).toLong()

        // Normalize denormals
        if (aExp == 0) {
            val norm = normalizeSig(aSig)
            aSig = norm.first
            aExp = norm.second
        }
        if (bExp == 0) {
            val norm = normalizeSig(bSig)
            bSig = norm.first
            bExp = norm.second
        }

        // Sign of result is sign of larger magnitude (a)
        val resultSign = aRep and SIGN_MASK
        val subtraction = ((aRep xor bRep) and SIGN_MASK) != 0

        // Add implicit bit and shift by 3 to get R/G/S room
        aSig = ((aSig or IMPLICIT_BIT.toLong()) shl 3)
        bSig = ((bSig or IMPLICIT_BIT.toLong()) shl 3)

        // Align b by exponent diff with sticky
        val align = aExp - bExp
        if (align != 0) {
            if (align < TYPE_WIDTH) {
                val sticky = (SHIFT64.leftShift(bSig, TYPE_WIDTH - align).value != 0L)
                bSig = SHIFT64.rightShift(bSig, align).value or if (sticky) 1L else 0L
            } else {
                bSig = 1 // sticky only
            }
        }

        if (subtraction) {
            aSig -= bSig
            if (aSig == 0L) return 0 // +0 on exact cancel
            val threshold = (IMPLICIT_BIT.toLong() shl 3)
            while ((aSig and threshold) == 0L && aExp > 0) {
                aSig = aSig shl 1
                aExp -= 1
            }
        } else {
            aSig += bSig
            // carry into bit (implicitBit<<4)? then shift right with sticky
            if ((aSig and (IMPLICIT_BIT.toLong() shl 4)) != 0L) {
                val sticky = (aSig and 1L) != 0L
                aSig = (aSig ushr 1) or (if (sticky) 1L else 0L)
                aExp += 1
            }
        }

        // Overflow to infinity
        if (aExp >= 0xFF) return EXP_MASK or resultSign

        // Subnormal before rounding
        if (aExp <= 0) {
            val shift = 1 - aExp
            val sticky = if (shift < TYPE_WIDTH) (SHIFT64.leftShift(aSig, TYPE_WIDTH - shift).value != 0L) else (aSig != 0L)
            aSig = SHIFT64.rightShift(aSig, shift).value or (if (sticky) 1L else 0L)
            aExp = 0
        }

        val roundGuardSticky = (aSig and 0x7).toInt()
        var result = ((aSig ushr 3) and FRAC_MASK.toLong()).toInt()
        result = result or (aExp shl 23)
        result = result or resultSign

        // Final rounding: nearest, ties-to-even
        if (roundGuardSticky > 0x4) {
            result += 1
        } else if (roundGuardSticky == 0x4) {
            if ((result and 1) != 0) result += 1
        }

        // Handle rounding overflow into exponent
        if ((result and EXP_MASK) == EXP_MASK && (result and FRAC_MASK) == 0) {
            // became infinity
            return (result and (SIGN_MASK or EXP_MASK))
        }
        return result
    }

    fun subBits(aBits: Int, bBits: Int): Int {
        val flipped = bBits xor SIGN_MASK
        return addBits(aBits, flipped)
    }

    // IEEE-754 float32 divide (nearest, ties-to-even), transliterated in spirit from
    // compiler-rt fp_div_impl.inc. Uses 24-bit significands with an extra R/G/S lane (<<3).
    fun divBits(aBits: Int, bBits: Int): Int {
        val aSign = aBits and SIGN_MASK
        val bSign = bBits and SIGN_MASK
        val sign = aSign xor bSign

        val aAbs = aBits and 0x7FFFFFFF.toInt()
        val bAbs = bBits and 0x7FFFFFFF.toInt()

        val aExp = (aBits ushr 23) and 0xFF
        val bExp = (bBits ushr 23) and 0xFF
        val aFrac = aBits and FRAC_MASK
        val bFrac = bBits and FRAC_MASK

        // NaNs
        val aNaN = aExp == 0xFF && aFrac != 0
        val bNaN = bExp == 0xFF && bFrac != 0
        if (aNaN) return (aBits or 0x00400000)
        if (bNaN) return (bBits or 0x00400000)

        // Infinities and zeros
        val aInf = aExp == 0xFF && aFrac == 0
        val bInf = bExp == 0xFF && bFrac == 0
        val aZero = aExp == 0 && aFrac == 0
        val bZero = bExp == 0 && bFrac == 0

        // Cases
        if (aInf && bInf) return CANONICAL_NAN
        if (aInf) return sign or EXP_MASK
        if (bInf) return sign // zero with sign
        if (aZero && bZero) return CANONICAL_NAN
        if (bZero) return sign or EXP_MASK
        if (aZero) return sign // signed zero

        // Normalize significands
        var ea = aExp
        var eb = bExp
        var sa = aFrac.toLong()
        var sb = bFrac.toLong()
        if (ea == 0) {
            val norm = normalizeSig(sa)
            sa = norm.first
            ea = norm.second
        }
        if (eb == 0) {
            val norm = normalizeSig(sb)
            sb = norm.first
            eb = norm.second
        }

        // Add implicit bit
        sa = sa or IMPLICIT_BIT.toLong()
        sb = sb or IMPLICIT_BIT.toLong()

        // Compute exponent (will be further adjusted during normalization)
        var exp = ea - eb + EXP_BIAS

        // Produce 27-bit quotient (24+R/G/S) by scaling numerator
        // q = ((sa << (3+24)) / sb), so that top 24+3 bits are in quotient
        val SHIFT_NUM = 26 // 23 + 3; ensures (quo>>3)/2^23 ~= sa/sb
        var num = sa shl SHIFT_NUM
        var quo = if (sb != 0L) num / sb else 0L
        var rem = if (sb != 0L) num % sb else 0L

        // Normalize quotient around [IMPLICIT_BIT<<3, (IMPLICIT_BIT<<4))
        val topThreshold = (IMPLICIT_BIT.toLong() shl 3)
        if (quo > (topThreshold shl 1)) {
            // Too large: shift right one, increment exponent
            val sticky = (quo and 1L) != 0L || rem != 0L
            quo = (quo ushr 1) or if (sticky) 1L else 0L
            exp += 1
        } else if (quo < topThreshold) {
            // Too small: shift left until threshold reached, decrement exponent
            while (quo < topThreshold) {
                quo = quo shl 1
                exp -= 1
            }
        }

        // Handle overflow
        if (exp >= 0xFF) return sign or EXP_MASK
        // Subnormal pack before rounding
        if (exp <= 0) {
            val shift = 1 - exp
            val sticky = SHIFT64.leftShift(quo, shift).value != 0L || rem != 0L
            quo = SHIFT64.rightShift(quo, shift).value or if (sticky) 1L else 0L
            exp = 0
        }

        // Rounding (nearest, ties-to-even) using R/G/S
        val rgs = (quo and 0x7).toInt()
        var result = ((quo ushr 3) and FRAC_MASK.toLong()).toInt()
        result = result or (exp shl 23)
        result = result or sign
        // Merge remainder into sticky for rounding decision
        val more = if (rem != 0L) 1 else 0
        val rgsAdj = rgs or more
        if (rgsAdj > 0x4) {
            result += 1
        } else if (rgsAdj == 0x4) {
            if ((result and 1) != 0) result += 1
        }
        // Handle rounding overflow
        if ((result and EXP_MASK) == EXP_MASK && (result and FRAC_MASK) == 0) {
            return (sign or EXP_MASK)
        }
        return result
    }

    // ---- Classification and utility ops ----
    fun isNaNBits(bits: Int): Boolean {
        val exp = bits and EXP_MASK
        val frac = bits and FRAC_MASK
        return exp == EXP_MASK && frac != 0
    }

    fun isInfBits(bits: Int): Boolean = (bits and EXP_MASK) == EXP_MASK && (bits and FRAC_MASK) == 0

    fun isZeroBits(bits: Int): Boolean = (bits and 0x7FFFFFFF.toInt()) == 0

    fun isNegativeBits(bits: Int): Boolean = (bits and SIGN_MASK) != 0

    fun isSubnormalBits(bits: Int): Boolean {
        val exp = (bits and EXP_MASK) ushr 23
        val frac = bits and FRAC_MASK
        return exp == 0 && frac != 0
    }

    fun isNormalBits(bits: Int): Boolean {
        val exp = (bits and EXP_MASK) ushr 23
        return exp in 1..254
    }

    fun isSignalingNaNBits(bits: Int): Boolean {
        val exp = (bits and EXP_MASK) ushr 23
        val frac = bits and FRAC_MASK
        val quietBit = 0x00400000
        return exp == 0xFF && frac != 0 && (frac and quietBit) == 0
    }

    fun copysign(a: Float, b: Float): Float = Float.fromBits(copysignBits(a.toRawBits(), b.toRawBits()))

    fun copysignBits(aBits: Int, bBits: Int): Int = (aBits and 0x7FFFFFFF.toInt()) or (bBits and SIGN_MASK)

    fun fmin(a: Float, b: Float): Float = Float.fromBits(fminBits(a.toRawBits(), b.toRawBits()))
    fun fmax(a: Float, b: Float): Float = Float.fromBits(fmaxBits(a.toRawBits(), b.toRawBits()))

    fun fminBits(aBits: Int, bBits: Int): Int {
        val aNaN = isNaNBits(aBits)
        val bNaN = isNaNBits(bBits)
        if (aNaN && bNaN) return CANONICAL_NAN
        if (aNaN) return bBits
        if (bNaN) return aBits
        if (isZeroBits(aBits) && isZeroBits(bBits)) {
            // If either is negative zero, return negative zero
            return if (isNegativeBits(aBits or bBits)) SIGN_MASK else 0
        }
        val a = Float.fromBits(aBits)
        val b = Float.fromBits(bBits)
        return if (a < b) aBits else bBits
    }

    fun fmaxBits(aBits: Int, bBits: Int): Int {
        val aNaN = isNaNBits(aBits)
        val bNaN = isNaNBits(bBits)
        if (aNaN && bNaN) return CANONICAL_NAN
        if (aNaN) return bBits
        if (bNaN) return aBits
        if (isZeroBits(aBits) && isZeroBits(bBits)) {
            // If either is positive zero, return positive zero
            return if (!isNegativeBits(aBits and bBits)) 0 else SIGN_MASK
        }
        val a = Float.fromBits(aBits)
        val b = Float.fromBits(bBits)
        return if (a > b) aBits else bBits
    }
    private fun normalizeSig(fracIn: Long): Pair<Long, Int> {
        var sig = fracIn
        if (sig == 0L) return 0L to 0
        var shift = 0
        val implicit = IMPLICIT_BIT.toLong()
        while ((sig and implicit) == 0L) {
            sig = sig shl 1
            shift++
        }
        // Return new significand with implicit bit set (still at bit23), exponent = 1 - shift
        return sig to (1 - shift)
    }
}
