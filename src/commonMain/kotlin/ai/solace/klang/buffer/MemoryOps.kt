package ai.solace.klang.buffer

/** Portable equivalents of common C memory primitives. */
object MemoryOps {
    fun memset(buffer: ByteArray, offset: Int, length: Int, value: Int) {
        require(offset >= 0 && length >= 0 && offset + length <= buffer.size)
        val v = (value and 0xFF).toByte()
        for (i in 0 until length) {
            buffer[offset + i] = v
        }
    }

    fun memset(buffer: ShortArray, offset: Int, length: Int, value: Int) {
        require(offset >= 0 && length >= 0 && offset + length <= buffer.size)
        val v = value.toShort()
        for (i in 0 until length) {
            buffer[offset + i] = v
        }
    }

    fun memset(buffer: IntArray, offset: Int, length: Int, value: Int) {
        require(offset >= 0 && length >= 0 && offset + length <= buffer.size)
        for (i in 0 until length) {
            buffer[offset + i] = value
        }
    }

    fun memset(buffer: FloatArray, offset: Int, length: Int, value: Float) {
        require(offset >= 0 && length >= 0 && offset + length <= buffer.size)
        for (i in 0 until length) {
            buffer[offset + i] = value
        }
    }

    fun memcpy(dest: ByteArray, destOffset: Int, src: ByteArray, srcOffset: Int, length: Int) {
        require(destOffset >= 0 && srcOffset >= 0 && length >= 0)
        require(destOffset + length <= dest.size)
        require(srcOffset + length <= src.size)
        if (length == 0 || (dest === src && destOffset == srcOffset)) return
        if (dest === src && destOffset > srcOffset && destOffset < srcOffset + length) {
            for (i in length - 1 downTo 0) {
                dest[destOffset + i] = src[srcOffset + i]
            }
        } else {
            for (i in 0 until length) {
                dest[destOffset + i] = src[srcOffset + i]
            }
        }
    }

    fun memcpy(dest: ShortArray, destOffset: Int, src: ShortArray, srcOffset: Int, length: Int) {
        require(destOffset >= 0 && srcOffset >= 0 && length >= 0)
        require(destOffset + length <= dest.size)
        require(srcOffset + length <= src.size)
        if (length == 0 || (dest === src && destOffset == srcOffset)) return
        if (dest === src && destOffset > srcOffset && destOffset < srcOffset + length) {
            for (i in length - 1 downTo 0) {
                dest[destOffset + i] = src[srcOffset + i]
            }
        } else {
            for (i in 0 until length) {
                dest[destOffset + i] = src[srcOffset + i]
            }
        }
    }

    fun memcpy(dest: IntArray, destOffset: Int, src: IntArray, srcOffset: Int, length: Int) {
        require(destOffset >= 0 && srcOffset >= 0 && length >= 0)
        require(destOffset + length <= dest.size)
        require(srcOffset + length <= src.size)
        if (length == 0 || (dest === src && destOffset == srcOffset)) return
        if (dest === src && destOffset > srcOffset && destOffset < srcOffset + length) {
            for (i in length - 1 downTo 0) {
                dest[destOffset + i] = src[srcOffset + i]
            }
        } else {
            for (i in 0 until length) {
                dest[destOffset + i] = src[srcOffset + i]
            }
        }
    }

    fun memcpy(dest: FloatArray, destOffset: Int, src: FloatArray, srcOffset: Int, length: Int) {
        require(destOffset >= 0 && srcOffset >= 0 && length >= 0)
        require(destOffset + length <= dest.size)
        require(srcOffset + length <= src.size)
        if (length == 0 || (dest === src && destOffset == srcOffset)) return
        if (dest === src && destOffset > srcOffset && destOffset < srcOffset + length) {
            for (i in length - 1 downTo 0) {
                dest[destOffset + i] = src[srcOffset + i]
            }
        } else {
            for (i in 0 until length) {
                dest[destOffset + i] = src[srcOffset + i]
            }
        }
    }

    fun memmove(dest: ByteArray, destOffset: Int, src: ByteArray, srcOffset: Int, length: Int) {
        memcpy(dest, destOffset, src, srcOffset, length)
    }

    fun memcmp(a: ByteArray, aOffset: Int, b: ByteArray, bOffset: Int, length: Int): Int {
        require(aOffset >= 0 && bOffset >= 0 && length >= 0)
        require(aOffset + length <= a.size)
        require(bOffset + length <= b.size)
        for (i in 0 until length) {
            val av = a[aOffset + i].toInt() and 0xFF
            val bv = b[bOffset + i].toInt() and 0xFF
            if (av != bv) return av - bv
        }
        return 0
    }

    fun zero(bytes: ByteArray) = memset(bytes, 0, bytes.size, 0)
    fun zero(shorts: ShortArray) = memset(shorts, 0, shorts.size, 0)
    fun zero(ints: IntArray) = memset(ints, 0, ints.size, 0)
    fun zero(floats: FloatArray) = memset(floats, 0, floats.size, 0f)
}
