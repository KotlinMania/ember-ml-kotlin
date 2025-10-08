package ai.solace.klang.bitwise

import kotlin.test.Test
import kotlin.test.assertEquals

class FloatKlangExtensionsTest {
    @Test
    fun floatOnLhsOperators() {
        val a = 2.5f
        val b = CFloat32.fromFloat(1.25f)

        val sum = a + b
        val diff = a - b
        val prod = a * b
        val quot = a / b

        assertEquals((2.5f + 1.25f).toRawBits(), sum.toFloat().toRawBits())
        assertEquals((2.5f - 1.25f).toRawBits(), diff.toFloat().toRawBits())
        assertEquals((2.5f * 1.25f).toRawBits(), prod.toFloat().toRawBits())
        assertEquals((2.5f / 1.25f).toRawBits(), quot.toFloat().toRawBits())
    }
}
