package ai.solace.klang.int.hpc

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HPC16xDivTests {
    private fun limbsOf(u: ULong): UShortArray {
        val out = UShortArray(4)
        val eng64 = ai.solace.klang.bitwise.BitShiftEngine(ai.solace.klang.bitwise.BitShiftMode.ARITHMETIC, 64)
        val base = 65536L
        var v = u.toLong()
        // normalize to 64-bit unsigned domain
        fun norm(x: Long) = x and 0xFFFFFFFFFFFFFFFFuL.toLong()
        v = norm(v)
        for (i in 0..3) {
            val shifted = eng64.rightShift(v, 16).value
            val low = (v - shifted * base)
            out[i] = ((low % base + base) % base).toUShort()
            v = shifted
        }
        return out
    }
    @Test
    fun div128by64_basic() {
        val hi = HPC16x4.fromULong(0x0000_0000_0000_0001uL)
        val lo = HPC16x4.fromULong(0x0000_0000_FFFF_FFFFuL)
        val num = HPC16x8.mul64x64To128(hi, HPC16x4.fromULong(0x0000_0000_0000_0001uL))
        val num2 = HPC16x8.ofLimbsLE(0xFFFFu, 0xFFFFu, 0u, 0u, 1u, 0u, 0u, 0u)
        val den = HPC16x4.fromULong(0x0000_0001_0000_0000uL)
        val (q, r) = HPC16x8.div128by64(num2, den)
        // For d = 2^32, quotient = (hi<<32) | (lo>>32), remainder = lo & (2^32-1)
        val hiU = 1uL
        val loU = 0x0000_0000_FFFF_FFFFuL
        val refQ = (hiU shl 32) or (loU shr 32)
        val refR = (loU and 0xFFFF_FFFFuL)
        assertEquals(refQ, q.toULong())
        assertEquals(refR, r.toULong())
    }

    @Test
    fun div128by64_selected_cases() {
        val cases = listOf(
            0x8000_0000_0000_0000uL to 0x0000_0000_0000_0001uL,
            0x8000_0000_0000_0000uL to 0xFFFF_FFFF_FFFF_FFFFuL
        )
        for ((denU, loU) in cases) {
            val hiU = denU - 1uL
            val loL = limbsOf(loU)
            val hiL = limbsOf(hiU)
            val num = HPC16x8.ofLimbsLE(loL[0], loL[1], loL[2], loL[3], hiL[0], hiL[1], hiL[2], hiL[3])
            val den = HPC16x4.fromULong(denU)
            val (q, r) = HPC16x8.div128by64(num, den)
            val prod = HPC16x8.mul64x64To128(q, den)
            val recomposed = run {
                val arr = UShortArray(8)
                for (i in 0..7) arr[i] = prod.limb(i)
                var carry = 0u
                for (i in 0..3) {
                    val sum = arr[i].toUInt() + r.limb(i).toUInt() + carry
                    arr[i] = (sum and 0xFFFFu).toUShort()
                    carry = sum shr 16
                }
                var idx = 4
                while (carry != 0u && idx < 8) {
                    val sum = arr[idx].toUInt() + carry
                    arr[idx] = (sum and 0xFFFFu).toUShort()
                    carry = sum shr 16
                    idx++
                }
                HPC16x8.ofLimbsLE(arr[0],arr[1],arr[2],arr[3],arr[4],arr[5],arr[6],arr[7])
            }
            var equal = true
            for (i in 0..7) if (recomposed.limb(i) != num.limb(i)) { equal = false; break }
            if (!equal) {
                fun limb4(a: HPC16x8, i: Int) = a.limb(i).toString(16).padStart(4, '0')
                val msg = buildString {
                    append("den=").append(denU.toString(16)).append('\n')
                    append("q=").append(q.toULong().toString(16)).append(" r=").append(r.toULong().toString(16)).append('\n')
                    append("num=[").append(limb4(num,7)).append(' ').append(limb4(num,6)).append(' ').append(limb4(num,5)).append(' ').append(limb4(num,4)).append(" | ")
                        .append(limb4(num,3)).append(' ').append(limb4(num,2)).append(' ').append(limb4(num,1)).append(' ').append(limb4(num,0)).append("]\n")
                    append("rec=[").append(limb4(recomposed,7)).append(' ').append(limb4(recomposed,6)).append(' ').append(limb4(recomposed,5)).append(' ').append(limb4(recomposed,4)).append(" | ")
                        .append(limb4(recomposed,3)).append(' ').append(limb4(recomposed,2)).append(' ').append(limb4(recomposed,1)).append(' ').append(limb4(recomposed,0)).append("]\n")
                }
                kotlin.test.fail(msg)
            }
            assertTrue(equal)
            assertTrue(r.toULong() < denU)
        }
    }

    @Test
    fun div128by64_randomized_recomposition() {
        val rnd = Random(99)
        repeat(200) {
            val denU = (rnd.nextLong().toULong() or 1uL)
            val hiU = (rnd.nextLong().toULong()) % denU
            val loU = rnd.nextLong().toULong()
            val loL2 = limbsOf(loU)
            val hiL2 = limbsOf(hiU)
            val num = HPC16x8.ofLimbsLE(loL2[0], loL2[1], loL2[2], loL2[3], hiL2[0], hiL2[1], hiL2[2], hiL2[3])
            val den = HPC16x4.fromULong(denU)
            val (q, r) = num.divRem(den)
            // Recompose and check
            val prod = HPC16x8.mul64x64To128(q, den)
            val recomposed = run {
                val arr = UShortArray(8)
                for (i in 0..7) arr[i] = prod.limb(i)
                var carry = 0u
                for (i in 0..3) {
                    val sum = arr[i].toUInt() + r.limb(i).toUInt() + carry
                    arr[i] = (sum and 0xFFFFu).toUShort()
                    carry = sum shr 16
                }
                var idx = 4
                while (carry != 0u && idx < 8) {
                    val sum = arr[idx].toUInt() + carry
                    arr[idx] = (sum and 0xFFFFu).toUShort()
                    carry = sum shr 16
                    idx++
                }
                HPC16x8.ofLimbsLE(arr[0],arr[1],arr[2],arr[3],arr[4],arr[5],arr[6],arr[7])
            }
            var equal = true
            for (i in 0..7) if (recomposed.limb(i) != num.limb(i)) { equal = false; break }
            assertTrue(equal)
            assertTrue(r.toULong() < denU)
        }
    }
}
