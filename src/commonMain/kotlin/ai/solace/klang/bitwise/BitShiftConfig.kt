package ai.solace.klang.bitwise

/** Global configuration for BitShiftEngine defaults. */
object BitShiftConfig {
    @kotlin.concurrent.Volatile
    var defaultMode: BitShiftMode = BitShiftMode.ARITHMETIC

    fun withMode(mode: BitShiftMode, block: () -> Unit) {
        val prev = defaultMode
        try {
            defaultMode = mode
            block()
        } finally {
            defaultMode = prev
        }
    }
}
