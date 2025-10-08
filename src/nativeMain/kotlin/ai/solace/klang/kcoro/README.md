# Kotlin/Native kcoro Interop Cheatsheet (Kotlin 2.2.20)

## Kotlin 2.2.20 Interop Rules

- all pointer APIs still sit behind `ExperimentalForeignApi`; new compiler rejects un‑annotated usage.
- `staticCFunction` only accepts capture‑free lambdas; pass Kotlin state via `StableRef` + `COpaquePointer`.
- always scope transient C allocations with `memScoped { … }`; copy back into Kotlin objects outside the scope.
- Gradle’s `linkTask` is gone; use `linkTaskProvider` when wiring native libs.

## StableRef Callbacks

```kotlin
@OptIn(ExperimentalForeignApi::class)
private val workerFn = staticCFunction<COpaquePointer?, Unit> { arg ->
    val context = arg!!.asStableRef<WorkerState>().get()
    memScoped {
        val msg = alloc<COpaquePointerVar>()
        while (KcoroInterop.recv(context.inbox, msg.ptr.reinterpret<CPointed>(), -1) == 0) {
            val taskRef = msg.value!!.asStableRef<Task>()
            val task = taskRef.get()
            task.process()
            msg.value = taskRef.asCPointer()
            KcoroInterop.send(context.outbox, msg.ptr.reinterpret<CPointed>(), -1)
        }
    }
}
```

## Scheduler Contract (kcoro)

- `kc_chan_send/recv` must run inside kcoro tasks; the main thread has `kcoro_current()==NULL` and will trip asserts.
- create a scheduler, spawn producer/worker/consumer tasks inside it, and pump channel traffic through those coroutines.
- when shutting down, send sentinels, drain, and then dispose every `StableRef`.

## Integration Steps

1. Opt in (@file level) to `ExperimentalForeignApi` in every Kotlin file touching C interop.
2. Wrap Kotlin state for callbacks in `StableRef`; dispose them once messages complete.
3. Launch actual work inside kcoro tasks—never call kcoro channel APIs from plain Kotlin threads.
4. Keep kcoro-provided pointers inside `memScoped` blocks for predictable lifetime.
5. Hook Gradle’s native binaries with `linkTaskProvider.configure { dependsOn(buildKcoro, buildKcoroCpp) }`.
6. Rebuild with `./gradlew compileKotlinMacosArm64`; run POC via `./gradlew runPocDebugExecutableMacosArm64 …`.

## Open TODOs

- restructure `ActorArrayBitShiftPOC.kt` to run the producer/consumer loop entirely inside kcoro tasks.
- benchmark kcoro vs coroutine actors once the scheduler assert is resolved.
- propagate this pattern into ArrayBitShifts/EmberScalar after the POC is stable.
