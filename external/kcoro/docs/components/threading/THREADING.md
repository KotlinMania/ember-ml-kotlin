# Threading (component summary)

Abstract: This document summarizes threading models relevant to the project (M:N, free-threading, standard threading fallbacks), how they interact with the scheduler and Python bindings, and what implementers need to know about worker thread lifecycle, parking/unparking, and offload pools. It is self-contained for the threading component.

Key points
- Threading models observed:
  - M:N worker pool: scheduler spawns pthread workers that run coroutines (kcoro M:N model).
  - Free-threading (Python extension): Python binding detects available threading model and selects optimal behavior.
  - Standard threading: fallback for portability and easier debugging.
- Worker lifecycle: workers start, run work-stealing loop, park on idle (futex/condvar), and unpark on new work.
- Parking/unparking invariants: avoid races between park/unpark; metric counters exist (`park_events`, `unpark_events`) for debugging.
- Interaction with blocking offload: blocking tasks must not starve worker threads; use offload pool or IO dispatcher.

