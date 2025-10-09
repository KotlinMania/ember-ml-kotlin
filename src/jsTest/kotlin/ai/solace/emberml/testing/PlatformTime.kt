package ai.solace.ember.testing

import kotlin.js.Date

/**
 * JavaScript implementation of cross-platform time measurement
 */
actual fun getCurrentTimeMs(): Long = Date.now().toLong()