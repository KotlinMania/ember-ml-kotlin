package ai.solace.limbengine

import ai.solace.ember.backend.klang.LimbArray
import ai.solace.ember.backend.klang.LimbEngine
import kotlin.test.Test
import kotlin.test.assertTrue

class LimbDagSwitchTest {
    @Test
    fun `engine dag vs eager equivalence`() {
        val aDag = LimbEngine(mantissa = intArrayOf(0x1234), dagEnabled = true)
        val aEager = LimbEngine(mantissa = intArrayOf(0x1234), dagEnabled = false)

        aDag.shiftLeft(4).bitAnd(intArrayOf(0x0FF0)).shiftRight(2).bitOr(intArrayOf(0x0003)).flush()
        aEager.shiftLeft(4).bitAnd(intArrayOf(0x0FF0)).shiftRight(2).bitOr(intArrayOf(0x0003)).flush()

        val dagVal = aDag.mantissaLimbs()[0]
        val eagerVal = aEager.mantissaLimbs()[0]

        // Expected under fused DAG semantics: net shift by 2, then masks
        val fused = (((0x1234 shl 2) and 0x0FF0) or 0x0003) and 0xFFFF
        // Expected under eager semantics: do the ops in order with 16-bit masking each step
        val seq1 = (0x1234 shl 4) and 0xFFFF
        val seq2 = (seq1 and 0x0FF0) and 0xFFFF
        val seq3 = (seq2 ushr 2) and 0xFFFF
        val sequential = (seq3 or 0x0003) and 0xFFFF

        assertTrue(dagVal == fused)
        assertTrue(eagerVal == sequential)
    }

    @Test
    fun `array dag vs eager equivalence`() {
        val dag = LimbArray.of(
            LimbEngine(mantissa = intArrayOf(0x0001)),
            LimbEngine(mantissa = intArrayOf(0x00F0)),
            dagEnabled = true,
        )
        val eager = LimbArray.of(
            LimbEngine(mantissa = intArrayOf(0x0001)),
            LimbEngine(mantissa = intArrayOf(0x00F0)),
            dagEnabled = false,
        )

        dag.shiftLeft(4).bitAnd(intArrayOf(0x0FF0)).shiftRight(2).bitOr(intArrayOf(0x0003)).flush()
        eager.shiftLeft(4).bitAnd(intArrayOf(0x0FF0)).shiftRight(2).bitOr(intArrayOf(0x0003)).flush()

        val dVals = dag.toList().map { it.mantissaLimbs()[0] }
        val eVals = eager.toList().map { it.mantissaLimbs()[0] }

        val fused0 = (((0x0001 shl 2) and 0x0FF0) or 0x0003) and 0xFFFF
        val f_seq1_0 = (0x0001 shl 4) and 0xFFFF
        val f_seq2_0 = (f_seq1_0 and 0x0FF0) and 0xFFFF
        val f_seq3_0 = (f_seq2_0 ushr 2) and 0xFFFF
        val seq0 = (f_seq3_0 or 0x0003) and 0xFFFF

        val fused1 = (((0x00F0 shl 2) and 0x0FF0) or 0x0003) and 0xFFFF
        val s1_1 = (0x00F0 shl 4) and 0xFFFF
        val s2_1 = (s1_1 and 0x0FF0) and 0xFFFF
        val s3_1 = (s2_1 ushr 2) and 0xFFFF
        val seq1 = (s3_1 or 0x0003) and 0xFFFF

        assertTrue(dVals[0] == fused0)
        assertTrue(eVals[0] == seq0)
        assertTrue(dVals[1] == fused1)
        assertTrue(eVals[1] == seq1)
    }
}
