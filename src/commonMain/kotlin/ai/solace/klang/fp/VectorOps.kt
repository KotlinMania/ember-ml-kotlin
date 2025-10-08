package ai.solace.klang.fp

import ai.solace.klang.bitwise.Float32Math

/** Deterministic floating point reduction helpers backed by the soft-float math. */
object VectorOps {
    fun dotAccumulate(length: Int, lhs: FloatArray, lhsOffset: Int = 0, rhs: FloatArray, rhsOffset: Int = 0): Float {
        require(length >= 0)
        require(lhsOffset >= 0 && lhsOffset + length <= lhs.size)
        require(rhsOffset >= 0 && rhsOffset + length <= rhs.size)
        var acc = 0.0f
        for (i in 0 until length) {
            val prod = Float32Math.mul(lhs[lhsOffset + i], rhs[rhsOffset + i])
            acc = Float32Math.add(acc, prod)
        }
        return acc
    }

    fun axpy(length: Int, a: Float, x: FloatArray, xOffset: Int, y: FloatArray, yOffset: Int, out: FloatArray, outOffset: Int) {
        require(length >= 0)
        require(xOffset >= 0 && xOffset + length <= x.size)
        require(yOffset >= 0 && yOffset + length <= y.size)
        require(outOffset >= 0 && outOffset + length <= out.size)
        for (i in 0 until length) {
            val scaled = Float32Math.mul(a, x[xOffset + i])
            out[outOffset + i] = Float32Math.add(scaled, y[yOffset + i])
        }
    }
}
