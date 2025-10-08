@file:Suppress("ktlint:standard:property-naming")

package ai.solace.klang.common

object ZlibLogger {
    // Logging flags (configurable via CLI/env)
    var ENABLE_LOGGING: Boolean = false
    var DEBUG_ENABLED: Boolean = false
    var BITWISE_VERBOSE: Boolean = false

    private var initialized = false

    private fun initFromEnv() {
        if (initialized) return
        initialized = true
        try {
            getEnv("ZLIB_LOG_ENABLE")?.let { ENABLE_LOGGING = it == "1" || it.equals("true", true) }
            getEnv("ZLIB_LOG_DEBUG")?.let { DEBUG_ENABLED = it == "1" || it.equals("true", true) }
            getEnv("ZLIB_LOG_BITWISE")?.let { BITWISE_VERBOSE = it == "1" || it.equals("true", true) }
            getEnv("ZLIB_LOG_PATH")?.let { LOG_FILE_PATH = it }
        } catch (_: Throwable) {
        }
    }

    fun debug(
        message: String,
        className: String = "",
        functionName: String = "",
    ) = log(message, className, functionName)

    fun log(
        message: String,
        className: String = "",
        functionName: String = "",
    ) {
        initFromEnv()
        // Global logging gate
        if (!ENABLE_LOGGING) return
        // Suppress verbose debug logs unless explicitly enabled
        if (!DEBUG_ENABLED && message.startsWith("[DEBUG_LOG]")) return

        val timestamp = currentTimestamp()
        val location =
            if (className.isNotEmpty() || functionName.isNotEmpty()) {
                val cls = className.ifEmpty { "Unknown" }
                val func = functionName.ifEmpty { "unknown" }
                "[$cls::$func] "
            } else {
                ""
            }
        val line = "[$timestamp] $location$message\n"
        try {
            logToFile(line)
        } catch (_: Exception) {
            // Ignore logging errors
        }
    }

    // Convenience methods for specific classes to make logging easier
    fun logInflate(
        message: String,
        functionName: String = "",
    ) = log(message, "Inflate", functionName)

    fun logDeflate(
        message: String,
        functionName: String = "",
    ) = log(message, "Deflate", functionName)

    fun logZStream(
        message: String,
        functionName: String = "",
    ) = log(message, "ZStream", functionName)

    fun logInfBlocks(
        message: String,
        functionName: String = "",
    ) = log(message, "InfBlocks", functionName)

    fun logInfCodes(
        message: String,
        functionName: String = "",
    ) = log(message, "InfCodes", functionName)

    fun logInfTree(
        message: String,
        functionName: String = "",
    ) = log(message, "InfTree", functionName)

    // Mathematical algorithm specific loggers
    fun logBitwise(
        message: String,
        functionName: String = "",
    ) {
        // Check both ENABLE_LOGGING and BITWISE_VERBOSE
        if (!ENABLE_LOGGING || !BITWISE_VERBOSE) return
        log(message, "BitwiseOps", functionName)
    }

    fun logAdler32(
        message: String,
        functionName: String = "",
    ) = log(message, "Adler32", functionName)

    fun logHuffman(
        message: String,
        functionName: String = "",
    ) = log(message, "Huffman", functionName)

    fun logTree(
        message: String,
        functionName: String = "",
    ) = log(message, "Tree", functionName)

    fun logCRC32(
        message: String,
        functionName: String = "",
    ) = log(message, "CRC32", functionName)

    // Detailed mathematical operation loggers
    fun logBitwiseOp(
        operation: String,
        input: Any,
        shift: Int? = null,
        result: Any,
        functionName: String = "",
    ) {
        val shiftStr = if (shift != null) ", shift=$shift" else ""
        logBitwise("$operation(input=$input$shiftStr) -> $result", functionName)
    }

    fun logAdler32Calc(
        s1: Long,
        s2: Long,
        byte: Int? = null,
        index: Int? = null,
        functionName: String = "",
    ) {
        val byteStr = if (byte != null) ", byte=$byte" else ""
        val indexStr = if (index != null) ", index=$index" else ""
        logAdler32("s1=$s1, s2=$s2$byteStr$indexStr", functionName)
    }

    fun logHuffmanCode(
        symbol: Int,
        code: Int,
        bits: Int,
        functionName: String = "",
    ) {
        logHuffman("symbol=$symbol -> code=$code ($bits bits) [0x${code.toString(16)}]", functionName)
    }

    // Runtime configuration helpers (to be called from CLI)
    fun setEnabled(enabled: Boolean) {
        ENABLE_LOGGING = enabled
    }

    fun setDebug(enabled: Boolean) {
        DEBUG_ENABLED = enabled
    }

    fun setBitwiseVerbose(enabled: Boolean) {
        BITWISE_VERBOSE = enabled
    }

    fun setLogFilePath(path: String?) {
        LOG_FILE_PATH = path
    }
}

/**
 * Platform-specific file append implementation
 */
expect fun logToFile(line: String)

expect fun getEnv(name: String): String?

expect var LOG_FILE_PATH: String?

/**
 * Platform-specific timestamp string (e.g. yyyy-MM-dd HH:mm:ss)
 */
expect fun currentTimestamp(): String

// Allow enabling logs in CI by setting ZLIB_LOGGING=1
// platformEnable via env was removed to keep native interop simple
