@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

@file:Suppress("ktlint:standard:property-naming")

package ai.solace.klang.common

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fputs
import platform.posix.getenv
import platform.posix.localtime
import platform.posix.strftime
import platform.posix.time
import platform.posix.time_tVar

actual var LOG_FILE_PATH: String? = null

actual fun logToFile(line: String) {
    memScoped {
        val path = LOG_FILE_PATH ?: "zlib.log"
        val file = fopen(path, "a")
        if (file != null) {
            fputs(line, file)
            fclose(file)
        }
    }
}

actual fun currentTimestamp(): String {
    memScoped {
        val buf = allocArray<ByteVar>(24)
        val t = alloc<time_tVar>()
        time(t.ptr)
        strftime(buf, 24UL, "%Y-%m-%d %H:%M:%S", localtime(t.ptr))
        return buf.toKString()
    }
}

actual fun getEnv(name: String): String? {
    val v = getenv(name)
    return v?.toKString()
}
