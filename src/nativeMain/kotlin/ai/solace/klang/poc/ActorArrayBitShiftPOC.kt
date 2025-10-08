@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package ai.solace.klang.poc

import ai.solace.klang.kcoro.KcoroChannel
import ai.solace.klang.kcoro.KcoroInterop
import ai.solace.kcoro.KC_BUFFERED
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.CPointed
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.ptr
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.value
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.round
import kotlin.time.measureTime

/**
 * Proof-of-concept: SWAR-based limb shifting orchestrated with actors/channels.
 *
 * Everything needed to experiment lives in this file—no imports from the main
 * library—so we can iterate freely without risking the production code.
 */
private const val LIMB_BITS = 16
private const val LIMB_MASK = 0xFFFF
private var resultSink: Int = 0

object ActorArrayBitShiftPOC {

    data class ShiftTask(
        val sequence: Int,
        val offset: Int,
        val slice: IntArray,
        val carryIn: Int,
    )

    data class ShiftSegment(
        val sequence: Int,
        val offset: Int,
        val data: IntArray,
        val carryOut: Int,
    )

    fun shiftLeftWithActors(
        input: IntArray,
        bits: Int,
        chunkSize: Int = 512,
        workerCount: Int = 2,
    ): IntArray = runBlocking {
        require(bits >= 0) { "Shift count must be non-negative" }
        require(chunkSize > 0) { "Chunk size must be positive" }
        require(chunkSize % SwAR128.LIMB_COUNT == 0) { "Chunk size must be a multiple of ${SwAR128.LIMB_COUNT}" }

        if (bits == 0 || input.isEmpty()) return@runBlocking input.copyOf()

        val wordShift = bits / LIMB_BITS
        val bitShift = bits % LIMB_BITS

        val workingSize = input.size + wordShift + 1
        val working = IntArray(workingSize)
        input.copyInto(working, wordShift)

        if (bitShift == 0) {
            return@runBlocking trim(working)
        }

        val chunkCount = (workingSize + chunkSize - 1) / chunkSize
        val carryChannel = Channel<Int>(capacity = 1)
        val taskChannel = Channel<ShiftTask>(capacity = workerCount)
        val segmentChannel = Channel<ShiftSegment>(capacity = workerCount)

        carryChannel.send(0)

        val scope = CoroutineScope(Dispatchers.Default + Job())

        val producer = scope.launch {
            for (sequence in 0 until chunkCount) {
                val carryIn = carryChannel.receive()
                val offset = sequence * chunkSize
                val len = min(chunkSize, workingSize - offset)
                val slice = working.copyOfRange(offset, offset + len)
                taskChannel.send(ShiftTask(sequence, offset, slice, carryIn))
            }
            taskChannel.close()
        }

        repeat(workerCount) {
            scope.launch {
                for (task in taskChannel) {
                    val result = processChunk(task.slice, bitShift, task.carryIn)
                    segmentChannel.send(
                        ShiftSegment(
                            sequence = task.sequence,
                            offset = task.offset,
                            data = result.data,
                            carryOut = result.carryOut,
                        ),
                    )
                }
            }
        }

        val consumer = scope.launch {
            val buffer = HashMap<Int, ShiftSegment>()
            var expected = 0
            repeat(chunkCount) {
                val segment = segmentChannel.receive()
                buffer[segment.sequence] = segment
                while (true) {
                    val next = buffer.remove(expected) ?: break
                    next.data.copyInto(working, destinationOffset = next.offset)
                    if (expected + 1 < chunkCount) {
                        carryChannel.send(next.carryOut)
                    } else {
                        working[working.lastIndex] = next.carryOut
                    }
                    expected++
                }
            }
            segmentChannel.close()
            carryChannel.close()
        }

        producer.join()
        consumer.join()
        scope.coroutineContext[Job]?.cancel()

        trim(working)
    }

    private fun trim(limbs: IntArray): IntArray {
        var last = limbs.size - 1
        while (last > 0 && limbs[last] == 0) last--
        return limbs.copyOf(last + 1)
    }
}

private fun runConcurrentSessions() {
    val sessions = 3
    val size = 1000
    val bitCounts = listOf(5, 31, 63, 127)
    val chunkSize = 4096
    val workerCount = 4
    val iterations = 5
    val seedBase = 0xBADC0FFE.toInt()
    val kcoroAvailable = KcoroInterop.isAvailable

    println("Concurrent sessions ($sessions × $size limbs)")
    val header = buildString {
        append("columns: bits | ar-actor | ar-zero")
        if (kcoroAvailable) append(" | kcoro")
        append(" | ar-seq | nat-actor | nat-zero | nat-seq | scalar | ar boost | nat boost")
        if (kcoroAvailable) append(" | kc boost")
    }
    println(header)
    println("----------------------------------------------------------------")

    for (bits in bitCounts) {
        val arActor = measureConcurrentMillis(sessions, size, bits, iterations, seedBase) { data ->
            ActorArrayBitShiftPOC.shiftLeftWithActors(data, bits, chunkSize, workerCount)
        }
        val arZero = measureConcurrentMillis(sessions, size, bits, iterations, seedBase + 9) { data ->
            ZeroCopyActorArithmeticShiftPOC.shiftLeftArithmetic(data, bits, chunkSize, workerCount)
        }
        val kcoro = if (kcoroAvailable) {
            measureConcurrentMillis(sessions, size, bits, iterations, seedBase + 13) { data ->
                KcoroShiftPOC.shiftLeftArithmetic(data, bits, chunkSize, workerCount)
            }
        } else Double.NaN
        val arSeq = measureConcurrentMillis(sessions, size, bits, iterations, seedBase + 17) { data ->
            shiftLeftReference(data, bits)
        }
        val natActor = measureConcurrentMillis(sessions, size, bits, iterations, seedBase + 33) { data ->
            ActorArrayBitShiftNativePOC.shiftLeftWithActors(data, bits, chunkSize, workerCount)
        }
        val natZero = measureConcurrentMillis(sessions, size, bits, iterations, seedBase + 41) { data ->
            ZeroCopyActorShiftPOC.shiftLeftNative(data, bits, chunkSize, workerCount)
        }
        val natSeq = measureConcurrentMillis(sessions, size, bits, iterations, seedBase + 49) { data ->
            NativeShiftSequentialPOC.shiftLeftInPlace(data, bits)
        }
        val scalar = measureConcurrentMillis(sessions, size, bits, iterations, seedBase + 65) { data ->
            shiftLeftScalar(data, bits)
        }
        val arBoost = if (arZero > 0.0) arActor / arZero else Double.NaN
        val natBoost = if (natZero > 0.0) natActor / natZero else Double.NaN
        val kcBoost = if (kcoro > 0.0) arActor / kcoro else Double.NaN
        println(
            buildString {
                append("bits=")
                append(bits.toString().padStart(3))
                append("  ar-actor=")
                append(formatFixed(arActor, 2))
                append(" ms  ar-zero=")
                append(formatFixed(arZero, 2))
                if (kcoroAvailable) {
                    append(" ms  kcoro=")
                    append(formatFixed(kcoro, 2))
                }
                append(" ms  ar-seq=")
                append(formatFixed(arSeq, 2))
                append(" ms  nat-actor=")
                append(formatFixed(natActor, 2))
                append(" ms  nat-zero=")
                append(formatFixed(natZero, 2))
                append(" ms  nat-seq=")
                append(formatFixed(natSeq, 2))
                append(" ms  scalar=")
                append(formatFixed(scalar, 2))
                append(" ms  ar boost=")
                append(formatFixed(arBoost, 2))
                append("  nat boost=")
                append(formatFixed(natBoost, 2))
                if (kcoroAvailable) {
                    append("  kc boost=")
                    append(formatFixed(kcBoost, 2))
                }
            },
        )
    }
    println()
}

private inline fun measureConcurrentMillis(
    sessions: Int,
    size: Int,
    bits: Int,
    iterations: Int,
    seed: Int,
    crossinline runner: suspend (IntArray) -> IntArray,
): Double {
    require(sessions > 0)
    require(size > 0)
    require(iterations > 0)

    val total = measureTime {
        repeat(iterations) { iteration ->
            val random = kotlin.random.Random(seed xor iteration xor bits)
            val inputs = List(sessions) {
                IntArray(size) { random.nextInt(0x10000) }
            }
            runBlocking {
                val outputs = inputs.map { data ->
                    async(Dispatchers.Default) { runner(data) }
                }.awaitAll()
                for (out in outputs) {
                    if (out.isNotEmpty()) {
                        resultSink = resultSink xor out[0]
                    }
                }
            }
        }
    }
    return total.inWholeMilliseconds.toDouble() / iterations
}

object ActorArrayBitShiftNativePOC {

    data class ShiftTask(
        val sequence: Int,
        val offset: Int,
        val slice: IntArray,
        val carryIn: Int,
    )

    data class ShiftSegment(
        val sequence: Int,
        val offset: Int,
        val data: IntArray,
        val carryOut: Int,
    )

    fun shiftLeftWithActors(
        input: IntArray,
        bits: Int,
        chunkSize: Int = 512,
        workerCount: Int = 2,
    ): IntArray = runBlocking {
        require(bits >= 0) { "Shift count must be non-negative" }
        require(chunkSize > 0) { "Chunk size must be positive" }
        require(chunkSize % SwAR128.LIMB_COUNT == 0) { "Chunk size must be a multiple of ${SwAR128.LIMB_COUNT}" }

        if (bits == 0 || input.isEmpty()) return@runBlocking input.copyOf()

        val wordShift = bits / LIMB_BITS
        val bitShift = bits % LIMB_BITS

        val workingSize = input.size + wordShift + 1
        val working = IntArray(workingSize)
        input.copyInto(working, wordShift)

        if (bitShift == 0) {
            return@runBlocking trim(working)
        }

        val chunkCount = (workingSize + chunkSize - 1) / chunkSize
        val carryChannel = Channel<Int>(capacity = 1)
        val taskChannel = Channel<ShiftTask>(capacity = workerCount)
        val segmentChannel = Channel<ShiftSegment>(capacity = workerCount)

        carryChannel.send(0)

        val scope = CoroutineScope(Dispatchers.Default + Job())

        val producer = scope.launch {
            for (sequence in 0 until chunkCount) {
                val carryIn = carryChannel.receive()
                val offset = sequence * chunkSize
                val len = min(chunkSize, workingSize - offset)
                val slice = working.copyOfRange(offset, offset + len)
                taskChannel.send(ShiftTask(sequence, offset, slice, carryIn))
            }
            taskChannel.close()
        }

        repeat(workerCount) {
            scope.launch {
                for (task in taskChannel) {
                    val result = processChunkNative(task.slice, bitShift, task.carryIn)
                    segmentChannel.send(
                        ShiftSegment(
                            sequence = task.sequence,
                            offset = task.offset,
                            data = result.data,
                            carryOut = result.carryOut,
                        ),
                    )
                }
            }
        }

        val consumer = scope.launch {
            val buffer = HashMap<Int, ShiftSegment>()
            var expected = 0
            repeat(chunkCount) {
                val segment = segmentChannel.receive()
                buffer[segment.sequence] = segment
                while (true) {
                    val next = buffer.remove(expected) ?: break
                    next.data.copyInto(working, destinationOffset = next.offset)
                    if (expected + 1 < chunkCount) {
                        carryChannel.send(next.carryOut)
                    } else {
                        working[working.lastIndex] = next.carryOut
                    }
                    expected++
                }
            }
            segmentChannel.close()
            carryChannel.close()
        }

        producer.join()
        consumer.join()
        scope.coroutineContext[Job]?.cancel()

        trim(working)
    }

    private fun processChunkNative(slice: IntArray, bitShift: Int, carryIn: Int): ChunkResult {
        if (bitShift == 0) return ChunkResult(slice.copyOf(), carryIn)
        val mask = (1 shl bitShift) - 1
        var carry = carryIn and mask
        val data = slice.copyOf()
        for (i in 0 until data.size) {
            val cur = data[i] and LIMB_MASK
            val combined = (cur shl bitShift) + carry
            data[i] = combined and LIMB_MASK
            carry = combined ushr LIMB_BITS
        }
        return ChunkResult(data, carry and mask)
    }
}

object NativeShiftSequentialPOC {
    fun shiftLeftInPlace(input: IntArray, bits: Int): IntArray {
        if (bits == 0 || input.isEmpty()) return input.copyOf()
        val wordShift = bits / LIMB_BITS
        val bitShift = bits % LIMB_BITS
        val workingSize = input.size + wordShift + 1
        val working = IntArray(workingSize)
        input.copyInto(working, wordShift)
        if (bitShift == 0) {
            return trim(working)
        }
        val mask = LIMB_MASK
        val length = working.size - 1
        var carry = 0
        for (i in 0 until length) {
            val cur = working[i] and mask
            val combined = (cur shl bitShift) + carry
            working[i] = combined and mask
            carry = combined ushr LIMB_BITS
        }
        working[length] = carry
        return trim(working)
    }
}

object ZeroCopyActorShiftPOC {

    data class ShiftTask(
        val sequence: Int,
        val offset: Int,
        val length: Int,
        val carryIn: Int,
    )

    data class ShiftSegment(
        val sequence: Int,
        val carryOut: Int,
    )

    fun shiftLeftNative(
        input: IntArray,
        bits: Int,
        chunkSize: Int = 512,
        workerCount: Int = 2,
    ): IntArray = runBlocking {
        require(bits >= 0) { "Shift count must be non-negative" }
        require(chunkSize > 0) { "Chunk size must be positive" }
        if (bits == 0 || input.isEmpty()) return@runBlocking input.copyOf()

        val wordShift = bits / LIMB_BITS
        val bitShift = bits % LIMB_BITS
        val workingSize = input.size + wordShift + 1
        val working = IntArray(workingSize)
        input.copyInto(working, wordShift)
        if (bitShift == 0) {
            return@runBlocking trim(working)
        }

        val chunkCount = (workingSize + chunkSize - 1) / chunkSize
        val carryChannel = Channel<Int>(capacity = 1)
        val taskChannel = Channel<ShiftTask>(capacity = workerCount)
        val segmentChannel = Channel<ShiftSegment>(capacity = workerCount)

        carryChannel.send(0)

        val scope = CoroutineScope(Dispatchers.Default + Job())

        val producer = scope.launch {
            for (sequence in 0 until chunkCount) {
                val carryIn = carryChannel.receive()
                val offset = sequence * chunkSize
                val len = min(chunkSize, workingSize - offset)
                taskChannel.send(ShiftTask(sequence, offset, len, carryIn))
            }
            taskChannel.close()
        }

        repeat(workerCount) {
            scope.launch {
                for (task in taskChannel) {
                    val carryOut = shiftChunkNativeInPlace(working, task.offset, task.length, bitShift, task.carryIn)
                    segmentChannel.send(ShiftSegment(task.sequence, carryOut))
                }
            }
        }

        val consumer = scope.launch {
            val buffer = HashMap<Int, ShiftSegment>()
            var expected = 0
            repeat(chunkCount) {
                val segment = segmentChannel.receive()
                buffer[segment.sequence] = segment
                while (true) {
                    val next = buffer.remove(expected) ?: break
                    if (expected + 1 < chunkCount) {
                        carryChannel.send(next.carryOut)
                    } else {
                        working[working.lastIndex] = next.carryOut
                    }
                    expected++
                }
            }
            segmentChannel.close()
            carryChannel.close()
        }

        producer.join()
        consumer.join()
        scope.coroutineContext[Job]?.cancel()

        trim(working)
    }
}

object ZeroCopyActorArithmeticShiftPOC {

    data class ShiftTask(
        val sequence: Int,
        val offset: Int,
        val length: Int,
        val carryIn: Int,
    )

    data class ShiftSegment(
        val sequence: Int,
        val carryOut: Int,
    )

    fun shiftLeftArithmetic(
        input: IntArray,
        bits: Int,
        chunkSize: Int = 512,
        workerCount: Int = 2,
    ): IntArray = runBlocking {
        require(bits >= 0) { "Shift count must be non-negative" }
        require(chunkSize > 0) { "Chunk size must be positive" }
        require(chunkSize % SwAR128.LIMB_COUNT == 0) { "Chunk size must be a multiple of ${SwAR128.LIMB_COUNT}" }
        if (bits == 0 || input.isEmpty()) return@runBlocking input.copyOf()

        val wordShift = bits / LIMB_BITS
        val bitShift = bits % LIMB_BITS
        val workingSize = input.size + wordShift + 1
        val working = IntArray(workingSize)
        input.copyInto(working, wordShift)
        if (bitShift == 0) {
            return@runBlocking trim(working)
        }

        val chunkCount = (workingSize + chunkSize - 1) / chunkSize
        val carryChannel = Channel<Int>(capacity = 1)
        val taskChannel = Channel<ShiftTask>(capacity = workerCount)
        val segmentChannel = Channel<ShiftSegment>(capacity = workerCount)

        carryChannel.send(0)

        val scope = CoroutineScope(Dispatchers.Default + Job())

        val producer = scope.launch {
            for (sequence in 0 until chunkCount) {
                val carryIn = carryChannel.receive()
                val offset = sequence * chunkSize
                val len = min(chunkSize, workingSize - offset)
                taskChannel.send(ShiftTask(sequence, offset, len, carryIn))
            }
            taskChannel.close()
        }

        repeat(workerCount) {
            scope.launch {
                for (task in taskChannel) {
                    val carryOut = shiftChunkArithmeticInPlace(
                        working,
                        task.offset,
                        task.length,
                        bitShift,
                        task.carryIn,
                    )
                    segmentChannel.send(ShiftSegment(task.sequence, carryOut))
                }
            }
        }

        val consumer = scope.launch {
            val buffer = HashMap<Int, ShiftSegment>()
            var expected = 0
            repeat(chunkCount) {
                val segment = segmentChannel.receive()
                buffer[segment.sequence] = segment
                while (true) {
                    val next = buffer.remove(expected) ?: break
                    if (expected + 1 < chunkCount) {
                        carryChannel.send(next.carryOut)
                    } else {
                        working[working.lastIndex] = next.carryOut
                    }
                    expected++
                }
            }
            segmentChannel.close()
            carryChannel.close()
        }

        producer.join()
        consumer.join()
        scope.coroutineContext[Job]?.cancel()

        trim(working)
    }
}

object KcoroShiftPOC {
    private data class ShiftTask(
        val sequence: Int,
        val offset: Int,
        val length: Int,
        var carryIn: Int,
        var carryOut: Int = 0,
    )

    private data class WorkerContext(
        val working: IntArray,
        val bitShift: Int,
        val taskChannel: KcoroChannel,
        val resultChannel: KcoroChannel,
    )

    private data class ProducerContext(
        val working: IntArray,
        val bitShift: Int,
        val chunkSize: Int,
        val workingSize: Int,
        val chunkCount: Int,
        val workerCount: Int,
        val taskChannel: KcoroChannel,
        val resultChannel: KcoroChannel,
    )

    private val workerFn = staticCFunction<COpaquePointer?, Unit> { arg ->
        if (arg == null) return@staticCFunction
        val context = arg.asStableRef<WorkerContext>().get()
        memScoped {
            val msg = alloc<COpaquePointerVar>()
            while (true) {
                val recvRc = KcoroInterop.recv(context.taskChannel, msg.ptr.reinterpret<CPointed>(), -1)
                if (recvRc != 0) break
                val payload = msg.value ?: break
                val taskRef = payload.asStableRef<ShiftTask>()
                val task = taskRef.get()
                val carry = shiftChunkArithmeticInPlace(
                    context.working,
                    task.offset,
                    task.length,
                    context.bitShift,
                    task.carryIn,
                )
                task.carryOut = carry
                msg.value = payload
                val sendRc = KcoroInterop.send(context.resultChannel, msg.ptr.reinterpret<CPointed>(), -1)
                if (sendRc != 0) {
                    taskRef.dispose()
                    break
                }
            }
        }
    }

    private val producerFn = staticCFunction<COpaquePointer?, Unit> { arg ->
        if (arg == null) return@staticCFunction
        val context = arg.asStableRef<ProducerContext>().get()
        memScoped {
            var carry = 0
            repeat(context.chunkCount) { sequence ->
                val offset = sequence * context.chunkSize
                val length = kotlin.math.min(context.chunkSize, context.workingSize - offset)
                val task = ShiftTask(sequence, offset, length, carry)
                val taskRef = StableRef.create(task)
                val sendMsg = alloc<COpaquePointerVar>()
                sendMsg.value = taskRef.asCPointer()
                val sendRc = KcoroInterop.send(context.taskChannel, sendMsg.ptr.reinterpret<CPointed>(), -1)
                if (sendRc != 0) {
                    taskRef.dispose()
                    return@memScoped
                }

                val recvMsg = alloc<COpaquePointerVar>()
                val recvRc = KcoroInterop.recv(context.resultChannel, recvMsg.ptr.reinterpret<CPointed>(), -1)
                if (recvRc != 0) {
                    taskRef.dispose()
                    return@memScoped
                }
                val payload = recvMsg.value ?: return@memScoped
                val resultRef = payload.asStableRef<ShiftTask>()
                val resultTask = resultRef.get()
                carry = resultTask.carryOut
                resultRef.dispose()
            }

            context.working[context.working.lastIndex] = carry

            repeat(context.workerCount) {
                val msg = alloc<COpaquePointerVar>()
                msg.value = null
                KcoroInterop.send(context.taskChannel, msg.ptr.reinterpret<CPointed>(), -1)
            }
        }
    }

    fun shiftLeftArithmetic(
        input: IntArray,
        bits: Int,
        chunkSize: Int = 512,
        workerCount: Int = 2,
    ): IntArray {
        if (!KcoroInterop.isAvailable || bits < 0 || input.isEmpty()) {
            return ZeroCopyActorArithmeticShiftPOC.shiftLeftArithmetic(input, bits, chunkSize, workerCount)
        }
        require(chunkSize > 0) { "Chunk size must be positive" }

        val wordShift = bits / LIMB_BITS
        val bitShift = bits % LIMB_BITS
        val workingSize = input.size + wordShift + 1
        val working = IntArray(workingSize)
        input.copyInto(working, wordShift)
        if (bitShift == 0) {
            return trim(working)
        }

        val pointerSize = sizeOf<COpaquePointerVar>().toULong()
        val scheduler = KcoroInterop.createScheduler(workerCount)
            ?: return ZeroCopyActorArithmeticShiftPOC.shiftLeftArithmetic(input, bits, chunkSize, workerCount)

        var taskChannel: KcoroChannel? = null
        var resultChannel: KcoroChannel? = null
        var workerContextRef: StableRef<WorkerContext>? = null
        var producerContextRef: StableRef<ProducerContext>? = null
        val chunkCount = (workingSize + chunkSize - 1) / chunkSize

        try {
            taskChannel = KcoroInterop.createChannel(KC_BUFFERED, pointerSize, workerCount.toULong())
        } catch (t: Throwable) {
            KcoroInterop.shutdownScheduler(scheduler)
            throw t
        }
        if (taskChannel == null) {
            KcoroInterop.shutdownScheduler(scheduler)
            return ZeroCopyActorArithmeticShiftPOC.shiftLeftArithmetic(input, bits, chunkSize, workerCount)
        }
        try {
            resultChannel = KcoroInterop.createChannel(KC_BUFFERED, pointerSize, workerCount.toULong())
        } catch (t: Throwable) {
            KcoroInterop.destroyChannel(taskChannel)
            KcoroInterop.shutdownScheduler(scheduler)
            throw t
        }
        if (resultChannel == null) {
            KcoroInterop.destroyChannel(taskChannel)
            KcoroInterop.shutdownScheduler(scheduler)
            return ZeroCopyActorArithmeticShiftPOC.shiftLeftArithmetic(input, bits, chunkSize, workerCount)
        }

        workerContextRef = StableRef.create(
            WorkerContext(
                working = working,
                bitShift = bitShift,
                taskChannel = taskChannel,
                resultChannel = resultChannel,
            ),
        )

        repeat(workerCount) {
            val rc = KcoroInterop.spawnTask(scheduler, workerFn, workerContextRef.asCPointer())
            require(rc == 0) { "kcoro spawnTask failed rc=$rc" }
        }

        producerContextRef = StableRef.create(
            ProducerContext(
                working = working,
                bitShift = bitShift,
                chunkSize = chunkSize,
                workingSize = workingSize,
                chunkCount = chunkCount,
                workerCount = workerCount,
                taskChannel = taskChannel,
                resultChannel = resultChannel,
            ),
        )

        val producerRc = KcoroInterop.spawnTask(scheduler, producerFn, producerContextRef.asCPointer())
        require(producerRc == 0) { "kcoro producer spawn failed rc=$producerRc" }

        KcoroInterop.drainScheduler(scheduler, -1)
        KcoroInterop.shutdownScheduler(scheduler)
        KcoroInterop.closeChannel(taskChannel)
        KcoroInterop.closeChannel(resultChannel)
        workerContextRef?.dispose()
        producerContextRef?.dispose()
        KcoroInterop.destroyChannel(taskChannel)
        KcoroInterop.destroyChannel(resultChannel)

        return trim(working)
    }

    private fun receiveCarry(
        resultChannel: KcoroChannel,
    ): Int {
        var carryOut = 0
        memScoped {
            val msg = alloc<COpaquePointerVar>()
            val rc = KcoroInterop.recv(resultChannel, msg.ptr.reinterpret<CPointed>(), -1)
            require(rc == 0) { "kcoro recv failed rc=$rc" }
            val ptr = msg.value ?: error("kcoro result pointer null")
            val taskRef = ptr.asStableRef<ShiftTask>()
            val task = taskRef.get()
            carryOut = task.carryOut
            taskRef.dispose()
        }
        return carryOut
    }
}

fun main() {
    val sample = intArrayOf(0x1234, 0xABCD, 0x00FF, 0xC0DE)
    val bits = 37

    val actorResult = ActorArrayBitShiftPOC.shiftLeftWithActors(sample, bits, chunkSize = 16, workerCount = 2)
    val arithZero = ZeroCopyActorArithmeticShiftPOC.shiftLeftArithmetic(sample, bits, chunkSize = 16, workerCount = 2)
    val kcoroResult = if (KcoroInterop.isAvailable) {
        KcoroShiftPOC.shiftLeftArithmetic(sample, bits, chunkSize = 16, workerCount = 2)
    } else null
    val nativeActor = ActorArrayBitShiftNativePOC.shiftLeftWithActors(sample, bits, chunkSize = 16, workerCount = 2)
    val nativeSequential = NativeShiftSequentialPOC.shiftLeftInPlace(sample, bits)
    val nativeZero = ZeroCopyActorShiftPOC.shiftLeftNative(sample, bits, chunkSize = 16, workerCount = 2)
    val reference = shiftLeftReference(sample, bits)

    val inputFmt = sample.joinToString(prefix = "[", postfix = "]") { it.toString(16).padStart(4, '0') }
    val actorFmt = actorResult.joinToString(prefix = "[", postfix = "]") { it.toString(16).padStart(4, '0') }
    val arithZeroFmt = arithZero.joinToString(prefix = "[", postfix = "]") { it.toString(16).padStart(4, '0') }
    val kcoroFmt = kcoroResult?.joinToString(prefix = "[", postfix = "]") { it.toString(16).padStart(4, '0') } ?: "n/a"
    val nativeActorFmt = nativeActor.joinToString(prefix = "[", postfix = "]") { it.toString(16).padStart(4, '0') }
    val nativeSeqFmt = nativeSequential.joinToString(prefix = "[", postfix = "]") { it.toString(16).padStart(4, '0') }
    val nativeZeroFmt = nativeZero.joinToString(prefix = "[", postfix = "]") { it.toString(16).padStart(4, '0') }
    val referenceFmt = reference.joinToString(prefix = "[", postfix = "]") { it.toString(16).padStart(4, '0') }

    println("Input       : $inputFmt")
    println("Actor result: $actorFmt")
    println("Ar-zero     : $arithZeroFmt")
    println("Kcoro       : $kcoroFmt")
    println("Native actor: $nativeActorFmt")
    println("Native seq  : $nativeSeqFmt")
    println("Native zero : $nativeZeroFmt")
    println("Reference   : $referenceFmt")
    println("Matches?    : ${actorResult.contentEquals(reference)}")
    println("Ar-zero matches reference?      ${arithZero.contentEquals(reference)}")
    println(
        "Kcoro matches reference?        " +
            (kcoroResult?.contentEquals(reference)?.toString() ?: "N/A"),
    )
    println("Native actor matches reference? ${nativeActor.contentEquals(reference)}")
    println("Native seq matches reference?   ${nativeSequential.contentEquals(reference)}")
    println("Zero-copy native matches reference? ${nativeZero.contentEquals(reference)}")
    println()

    runBenchmarks()
    runConcurrentSessions()
}

private fun shiftLeftReference(input: IntArray, bits: Int): IntArray {
    if (bits == 0 || input.isEmpty()) return input.copyOf()
    val wordShift = bits / LIMB_BITS
    val bitShift = bits % LIMB_BITS
    val working = IntArray(input.size + wordShift + 1)
    input.copyInto(working, wordShift)
    if (bitShift == 0) {
        return trim(working)
    }
    val chunk = processChunk(working, bitShift, 0)
    chunk.data.copyInto(working)
    working[working.lastIndex] = chunk.carryOut
    return trim(working)
}

private fun shiftLeftScalar(input: IntArray, bits: Int): IntArray {
    if (bits == 0 || input.isEmpty()) return input.copyOf()
    val wordShift = bits / LIMB_BITS
    val bitShift = bits % LIMB_BITS
    val working = IntArray(input.size + wordShift + 1)
    input.copyInto(working, wordShift)
    if (bitShift == 0) {
        return trim(working)
    }
    val mask = 0xFFFF
    val dataLength = working.size - 1
    var carry = 0
    for (i in 0 until dataLength) {
        val cur = working[i] and mask
        val combined = (cur shl bitShift) + carry
        working[i] = combined and mask
        carry = combined ushr 16
    }
    working[dataLength] = carry
    return trim(working)
}

private fun runBenchmarks() {
    val randomSeed = 0xCAFE_F00DL
    val random = kotlin.random.Random(randomSeed)
    val sizes = listOf(1 shl 12, 1 shl 16, 1 shl 18)
    val bitCounts = listOf(5, 31, 63, 127)
    val chunkSize = 4096
    val workerCount = 4
    val iterations = 5
    val kcoroAvailable = KcoroInterop.isAvailable

    println("Benchmarking SWAR vs Actors")
    println("Chunk size=$chunkSize, workers=$workerCount, iterations=$iterations")
    val header = buildString {
        append("columns: size | bits | swar | ar-actor | ar-zero")
        if (kcoroAvailable) append(" | kcoro")
        append(" | nat-actor | nat-zero | nat-seq | scalar | ar ratio | ar0 ratio")
        if (kcoroAvailable) append(" | kc ratio")
        append(" | nat ratio | nat0 ratio | ar boost | nat boost")
        if (kcoroAvailable) append(" | kc boost")
    }
    println(header)
    println("----------------------------------------------------------------")

    for (size in sizes) {
        val base = IntArray(size) { random.nextInt(0x10000) }
        for (bits in bitCounts) {
            val swarTime = measureMillis(iterations) {
                shiftLeftReference(base, bits)
            }
            val actorTime = measureMillis(iterations) {
                ActorArrayBitShiftPOC.shiftLeftWithActors(base, bits, chunkSize, workerCount)
            }
            val arZeroTime = measureMillis(iterations) {
                ZeroCopyActorArithmeticShiftPOC.shiftLeftArithmetic(base, bits, chunkSize, workerCount)
            }
            val kcoroTime = if (kcoroAvailable) {
                measureMillis(iterations) {
                    KcoroShiftPOC.shiftLeftArithmetic(base, bits, chunkSize, workerCount)
                }
            } else Double.NaN
            val nativeActorTime = measureMillis(iterations) {
                ActorArrayBitShiftNativePOC.shiftLeftWithActors(base, bits, chunkSize, workerCount)
            }
            val nativeZeroTime = measureMillis(iterations) {
                ZeroCopyActorShiftPOC.shiftLeftNative(base, bits, chunkSize, workerCount)
            }
            val nativeSeqTime = measureMillis(iterations) {
                NativeShiftSequentialPOC.shiftLeftInPlace(base, bits)
            }
            val scalarTime = measureMillis(iterations) {
                shiftLeftScalar(base, bits)
            }
            val ratio = if (actorTime > 0.0) swarTime / actorTime else Double.NaN
            val arZeroRatio = if (arZeroTime > 0.0) swarTime / arZeroTime else Double.NaN
            val kcRatio = if (kcoroTime > 0.0) scalarTime / kcoroTime else Double.NaN
            val nativeRatio = if (nativeActorTime > 0.0) scalarTime / nativeActorTime else Double.NaN
            val nativeZeroRatio = if (nativeZeroTime > 0.0) scalarTime / nativeZeroTime else Double.NaN
            val arBoost = if (arZeroTime > 0.0) actorTime / arZeroTime else Double.NaN
            val kcBoost = if (kcoroTime > 0.0) actorTime / kcoroTime else Double.NaN
            val natBoost = if (nativeZeroTime > 0.0) nativeActorTime / nativeZeroTime else Double.NaN
            println(
                buildString {
                    append("size=")
                    append(size.toString().padStart(7))
                    append(" bits=")
                    append(bits.toString().padStart(3))
                    append("  swar=")
                    append(formatFixed(swarTime, 1))
                    append(" ms  ar-actor=")
                    append(formatFixed(actorTime, 1))
                    append(" ms  ar-zero=")
                    append(formatFixed(arZeroTime, 1))
                    if (kcoroAvailable) {
                        append(" ms  kcoro=")
                        append(formatFixed(kcoroTime, 1))
                    }
                    append(" ms  nat-actor=")
                    append(formatFixed(nativeActorTime, 1))
                    append(" ms  nat-zero=")
                    append(formatFixed(nativeZeroTime, 1))
                    append(" ms  nat-seq=")
                    append(formatFixed(nativeSeqTime, 1))
                    append(" ms  scalar=")
                    append(formatFixed(scalarTime, 1))
                    append(" ms  ar ratio=")
                    append(formatFixed(ratio, 2))
                    append("  ar0 ratio=")
                    append(formatFixed(arZeroRatio, 2))
                    if (kcoroAvailable) {
                        append("  kc ratio=")
                        append(formatFixed(kcRatio, 2))
                    }
                    append("  nat ratio=")
                    append(formatFixed(nativeRatio, 2))
                    append("  nat0 ratio=")
                    append(formatFixed(nativeZeroRatio, 2))
                    append("  ar boost=")
                    append(formatFixed(arBoost, 2))
                    append("  nat boost=")
                    append(formatFixed(natBoost, 2))
                    if (kcoroAvailable) {
                        append("  kc boost=")
                        append(formatFixed(kcBoost, 2))
                    }
                },
            )
        }
        println()
    }
}

private inline fun measureMillis(iterations: Int, crossinline block: () -> Unit): Double {
    require(iterations > 0)
    val total = measureTime {
        repeat(iterations) { block() }
    }
    return total.inWholeMilliseconds.toDouble() / iterations
}

private fun formatFixed(value: Double, decimals: Int): String {
    if (value.isNaN()) return "NaN"
    if (value.isInfinite()) return if (value > 0) "Inf" else "-Inf"
    if (decimals <= 0) return value.toLong().toString()
    var factor = 1L
    repeat(decimals) { factor *= 10 }
    val scaled = round(value * factor).toLong()
    val absScaled = abs(scaled)
    val whole = absScaled / factor
    val frac = absScaled % factor
    val sign = if (scaled < 0) "-" else ""
    val fracStr = frac.toString().padStart(decimals, '0')
    return sign + whole.toString() + "." + fracStr
}

private data class ChunkResult(val data: IntArray, val carryOut: Int)

private fun processChunk(slice: IntArray, bitShift: Int, carryIn: Int): ChunkResult {
    if (bitShift == 0) return ChunkResult(slice.copyOf(), carryIn)
    val wordCount = (slice.size + SwAR128.LIMB_COUNT - 1) / SwAR128.LIMB_COUNT
    val words = Array(wordCount) { wordIndex ->
        val limbs = IntArray(SwAR128.LIMB_COUNT)
        for (limbIndex in 0 until SwAR128.LIMB_COUNT) {
            val idx = wordIndex * SwAR128.LIMB_COUNT + limbIndex
            limbs[limbIndex] = if (idx < slice.size) slice[idx] and LIMB_MASK else 0
        }
        SwAR128.UInt128(limbs)
    }
    val mask = (1 shl bitShift) - 1
    var carry = carryIn and mask
    for (i in 0 until wordCount) {
        val result = SwAR128.shiftLeft(words[i], bitShift)
        val shifted = result.value
        shifted.limbs[0] = (shifted.limbs[0] and LIMB_MASK) or (carry and mask)
        words[i] = shifted
        carry = result.spill.toInt() and mask
    }
    val data = IntArray(slice.size)
    for (i in 0 until wordCount) {
        val word = words[i]
        for (limbIndex in 0 until SwAR128.LIMB_COUNT) {
            val idx = i * SwAR128.LIMB_COUNT + limbIndex
            if (idx < data.size) {
                data[idx] = word.limbs[limbIndex] and LIMB_MASK
            }
        }
    }
    return ChunkResult(data, carry)
}

private fun shiftChunkNativeInPlace(data: IntArray, offset: Int, length: Int, bitShift: Int, carryIn: Int): Int {
    if (bitShift == 0 || length == 0) return carryIn
    val mask = (1 shl bitShift) - 1
    var carry = carryIn and mask
    val end = offset + length
    for (i in offset until end) {
        val cur = data[i] and LIMB_MASK
        val combined = (cur shl bitShift) + carry
        data[i] = combined and LIMB_MASK
        carry = combined ushr LIMB_BITS
    }
    return carry and mask
}

private fun shiftChunkArithmeticInPlace(
    data: IntArray,
    offset: Int,
    length: Int,
    bitShift: Int,
    carryIn: Int,
): Int {
    if (bitShift == 0 || length == 0) return carryIn
    val mask = (1 shl bitShift) - 1
    var carry = carryIn and mask
    val end = offset + length
    val words = (length + SwAR128.LIMB_COUNT - 1) / SwAR128.LIMB_COUNT
    var base = offset
    for (w in 0 until words) {
        val limbs = IntArray(SwAR128.LIMB_COUNT)
        for (i in 0 until SwAR128.LIMB_COUNT) {
            val idx = base + i
            limbs[i] = if (idx < end) data[idx] and LIMB_MASK else 0
        }
        val shifted = SwAR128.shiftLeft(SwAR128.UInt128(limbs), bitShift)
        val word = shifted.value
        word.limbs[0] = (word.limbs[0] and LIMB_MASK) or (carry and mask)
        for (i in 0 until SwAR128.LIMB_COUNT) {
            val idx = base + i
            if (idx < end) {
                data[idx] = word.limbs[i] and LIMB_MASK
            }
        }
        carry = shifted.spill.toInt() and mask
        base += SwAR128.LIMB_COUNT
    }
    return carry
}

private object SwAR128 {
    const val LIMB_COUNT = 8
    private const val LIMB_BITS = 16
    private const val LIMB_BASE = 1 shl LIMB_BITS
    private val LIMB_MASK = LIMB_BASE - 1
    private val LIMB_BASE_UL = LIMB_BASE.toULong()

    data class UInt128(val limbs: IntArray) {
        init {
            require(limbs.size == LIMB_COUNT) { "UInt128 requires $LIMB_COUNT limbs" }
        }

        fun copy(): UInt128 = UInt128(limbs.copyOf())
    }

    data class ShiftResult(val value: UInt128, val spill: ULong)

    fun shiftLeft(value: UInt128, bits: Int): ShiftResult {
        require(bits in 0 until LIMB_BITS) { "bits must be in 0..${LIMB_BITS - 1}" }
        if (bits == 0) return ShiftResult(value.copy(), 0u)
        val out = IntArray(LIMB_COUNT)
        var carry = 0uL
        for (i in 0 until LIMB_COUNT) {
            val raw = ((value.limbs[i] and LIMB_MASK).toULong() shl bits) + carry
            out[i] = (raw % LIMB_BASE_UL).toInt()
            carry = raw / LIMB_BASE_UL
        }
        val spill = carry and ((1uL shl bits) - 1uL)
        return ShiftResult(UInt128(out), spill)
    }
}

private fun trim(limbs: IntArray): IntArray {
    var last = limbs.size - 1
    while (last > 0 && limbs[last] == 0) last--
    return limbs.copyOf(last + 1)
}
