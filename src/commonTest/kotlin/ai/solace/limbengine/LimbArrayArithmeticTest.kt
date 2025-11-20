package ai.solace.limbengine

import ai.solace.ember.backend.klang.LimbArray
import ai.solace.ember.backend.klang.LimbEngine
import kotlin.test.Test
import kotlin.test.assertEquals

class LimbArrayArithmeticTest {
    @Test
    fun `addition with constant operand yields same results DAG vs eager`() {
        val a = LimbEngine(mantissa = intArrayOf(0x0001))
        val b = LimbEngine(mantissa = intArrayOf(0x00F0))
        val addend = LimbEngine(mantissa = intArrayOf(0x0002))

        val dag = LimbArray.of(a, b, dagEnabled = true, autoGate = false)
        dag.add(addend).flush()
        val d = dag.toList().map { it.mantissaLimbs()[0] }

        val eager = LimbArray.of(a, b, dagEnabled = false, autoGate = false)
        eager.add(addend).flush()
        val e = eager.toList().map { it.mantissaLimbs()[0] }

        assertEquals(d, e)
        assertEquals(0x0003, d[0])
        assertEquals(0x00F2, d[1])
    }
}

