package ai.solace.klang.bitwise

// Precomputed constants for 16-bit limb shifts
internal object ShiftTables16 {
    // POW2[s] = 1 shl s, for s in 0..16
    val POW2 = IntArray(17)
    // LOW_MASK[s] = (1 shl s) - 1, for s in 0..16
    val LOW_MASK = IntArray(17)
    // SHIFT_IN[s] = 16 - s, for s in 0..16
    val SHIFT_IN = IntArray(17)
    const val MASK16: Int = 0xFFFF

    init {
        for (s in 0..16) {
            POW2[s] = if (s == 0) 1 else (1 shl s)
            LOW_MASK[s] = if (s == 0) 0 else (1 shl s) - 1
            SHIFT_IN[s] = 16 - s
        }
    }
}

