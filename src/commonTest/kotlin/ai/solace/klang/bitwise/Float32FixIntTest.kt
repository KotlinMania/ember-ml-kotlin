package ai.solace.klang.bitwise

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class Float32FixIntTest {
    @Test
    fun floatToInt_basic() {
        val values = floatArrayOf(0f, 0.9f, 1.0f, 1.9f, -0.9f, -1.0f, -1.9f, 123456.75f, -654321.5f)
        for (x in values) {
            val got = Float32Math.floatToInt(x)
            val ref = x.toInt() // trunc toward zero in Kotlin
            assertEquals(ref, got, "x=$x")
        }
    }

    @Test
    fun floatToUInt_basic() {
        val positives = floatArrayOf(0f, 0.9f, 1.0f, 1.9f, 65535.9f, 123456.75f)
        for (x in positives) {
            val got = Float32Math.floatToUInt(x)
            val ref = x.toUInt()
            assertEquals(ref, got, "x=$x")
        }
        val negatives = floatArrayOf(-0.1f, -1.0f, -123.45f)
        for (x in negatives) {
            val got = Float32Math.floatToUInt(x)
            assertEquals(0u, got, "x=$x")
        }
    }

    @Test
    fun floatToInt_fuzzSmall() {
        val rnd = Random(2)
        repeat(200) {
            val x = rnd.nextInt(-1_000_000, 1_000_000).toFloat() / 7.0f
            val got = Float32Math.floatToInt(x)
            val ref = x.toInt()
            assertEquals(ref, got, "x=$x")
        }
    }
}

