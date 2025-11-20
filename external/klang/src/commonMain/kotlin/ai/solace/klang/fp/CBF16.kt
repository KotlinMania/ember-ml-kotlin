package ai.solace.klang.fp

import ai.solace.klang.bitwise.BitShiftEngine
import ai.solace.klang.bitwise.BitShiftConfig

/**
 * CBF16: bfloat16 (1 sign, 8 exp, 7 frac, bias = 127) stored in 16 bits.
 * Arithmetic is defined as: upconvert to float32, compute via Float32Math (bit‑exact),
 * then round back to bf16 (nearest, ties‑to‑even).
 */
class CBF16 private constructor(private val bits: Short) {
    // --- basic access ---
    fun toBits(): Short = bits
    fun toFloat(): Float {
        val engine = BitShiftEngine(BitShiftConfig.defaultMode, 32)
        return Float.fromBits(engine.leftShift((bits.toInt() and 0xFFFF).toLong(), 16).value.toInt())
    }

    // operators (compute in f32, then round to bf16)
    operator fun plus(other: CBF16): CBF16 = fromFloat(Float32Math.add(this.toFloat(), other.toFloat()))
    operator fun minus(other: CBF16): CBF16 = fromFloat(Float32Math.sub(this.toFloat(), other.toFloat()))
    operator fun times(other: CBF16): CBF16 = fromFloat(Float32Math.mul(this.toFloat(), other.toFloat()))
    operator fun div(other: CBF16): CBF16 = fromFloat(Float32Math.div(this.toFloat(), other.toFloat()))

    fun sqrt(): CBF16 = fromFloat(Float.fromBits(Float32Math.sqrtBits(this.toFloat().toRawBits())))

    fun isNaN(): Boolean {
        val engine = BitShiftEngine(BitShiftConfig.defaultMode, 32)
        val e = (engine.unsignedRightShift(bits.toInt().toLong(), 7).value.toInt() and 0xFF)
        val f = bits.toInt() and 0x7F
        return e == 0xFF && f != 0
    }
    fun isInf(): Boolean {
        val engine = BitShiftEngine(BitShiftConfig.defaultMode, 32)
        val e = (engine.unsignedRightShift(bits.toInt().toLong(), 7).value.toInt() and 0xFF)
        val f = bits.toInt() and 0x7F
        return e == 0xFF && f == 0
    }
    fun isZero(): Boolean = (bits.toInt() and 0x7FFF) == 0
    fun signBit(): Boolean = (bits.toInt() and 0x8000) != 0

    override fun toString(): String = toFloat().toString()

    companion object {
        fun fromBits(bits: Short): CBF16 = CBF16(bits)

        fun fromFloat(value: Float): CBF16 = fromFloatBits(value.toRawBits())

        // Round float32 -> bf16 using nearest‑even
        fun fromFloatBits(fBits: Int): CBF16 {
            val engine = BitShiftEngine(BitShiftConfig.defaultMode, 32)
            val signExpFrac = fBits
            val sign = signExpFrac and 0x80000000.toInt()
            val exp = (engine.unsignedRightShift(signExpFrac.toLong(), 23).value.toInt() and 0xFF)
            val frac = signExpFrac and 0x007FFFFF

            // NaN: preserve payload; ensure quiet NaN
            if (exp == 0xFF) {
                if (frac == 0) {
                    // Infinity: just shift sign|exp
                    val out = ((engine.unsignedRightShift(sign.toLong(), 16).value.toInt() and 0x8000) or 
                               engine.leftShift(0xFF.toLong(), 7).value.toInt())
                    return CBF16(out.toShort())
                }
                // NaN: propagate payload; ensure quiet
                val outTop = (engine.unsignedRightShift(sign.toLong(), 16).value.toInt() and 0x8000)
                var outBody = (engine.leftShift(0xFF.toLong(), 7).value.toInt() or 
                              (engine.unsignedRightShift(frac.toLong(), 16).value.toInt() and 0x7F))
                if ((outBody and 0x7F) == 0) {
                    outBody = (engine.leftShift(0xFF.toLong(), 7).value.toInt() or 0x40)
                }
                return CBF16((outTop or outBody).toShort())
            }

            // Regular rounding
            val roundBias = 0x7FFF + ((engine.unsignedRightShift(signExpFrac.toLong(), 16).value.toInt() and 1))
            val rounded = signExpFrac + roundBias
            val bf = (engine.unsignedRightShift(rounded.toLong(), 16).value.toInt() and 0xFFFF)
            return CBF16(bf.toShort())
        }
    }
}
