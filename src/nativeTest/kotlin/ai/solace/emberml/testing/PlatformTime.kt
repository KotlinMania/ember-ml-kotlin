package ai.solace.ember.testing

import kotlin.time.TimeSource

// Static reference point for time measurements
private val startMark = TimeSource.Monotonic.markNow()

/**
 * Native implementation of cross-platform time measurement using TimeSource.Monotonic
 * 
 * This implementation uses a static reference point and measures the elapsed time
 * from that point, which is sufficient for performance measurements where we only
 * need relative time differences.
 */
actual fun getCurrentTimeMs(): Long {
    // Measure elapsed time from the static reference point
    return startMark.elapsedNow().inWholeMilliseconds
}
