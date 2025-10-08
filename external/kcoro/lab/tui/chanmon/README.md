# KCoro Monitor (Channel + Scheduler Task Benchmark)

An ncurses TUI providing live visualization of:

1. Channel producer/consumer throughput (packets per second, Gbps) – original mode.
2. Unified work-stealing scheduler synthetic task throughput (tasks/sec) – new mode.

## Features

- Real-time graph (pps or tasks/sec depending on mode)
- Smoothed (5-sample) and instantaneous metrics
- Peak and average tracking
- Scheduler internals (tasks mode):
  - Fast-path hit ratio
  - Steal success ratio
  - Tasks submitted / completed totals
- Channel benchmark configuration (producers, consumers, packets, capacity, size, spin)
- Mode toggle at runtime (`t`)

## Build

```
make -C kcoro/lab/tui/chanmon all
```
Produces executable: `kcoro/lab/tui/chanmon/build/kcoro_mon`.

Data source: the monitor consumes `kc_chan_snapshot()` from the core library and derives per‑second rates with `kc_chan_compute_rate()`. This means the PPS/GBPS numbers reflect the exact byte totals accounted by the channel (including zero‑copy paths such as pointer descriptors and zref rendezvous).

On aarch64 systems an assembly-optimized context switch (`kc_ctx_switch.S`) is included automatically. On other architectures remove `-DHAVE_ARM64_ASM` and substitute an appropriate portable implementation if needed.

## Run Examples

Channel (default):
```
./kcoro/lab/tui/chanmon/build/kcoro_mon -P 2 -C 2 -N 200000 -c 16384 -s 4096 -k 1500
```

Tasks mode (scheduler benchmark):
```
./kcoro/lab/tui/chanmon/build/kcoro_mon -m tasks
```

Headless tasks mode with NDJSON to stdout for 5 seconds:
```
./kcoro/lab/tui/chanmon/build/kcoro_mon -m tasks -H -d 5 -j -
```

Channel mode JSON to file (3s run):
```
./kcoro/lab/tui/chanmon/build/kcoro_mon -P 2 -C 2 -N 100000 -m channel -H -d 3 -j channel_samples.ndjson
```

Quick task benchmark (headless for 3s):
```
timeout 3s ./kcoro/lab/tui/chanmon/build/kcoro_mon -m tasks
```

## Command Line Options

```
-P, --producers N    (channel) producers
-C, --consumers N    (channel) consumers
-N, --packets N      (channel) packets per run
-c, --capacity N     (channel) channel capacity
-k, --packet-size N  (channel) message size bytes
-s, --spin N         (channel) spin iterations before yield
-m, --mode M         channel|tasks (default: channel)
-j, --json PATH      NDJSON output path (use - for stdout)
-H, --headless       Disable ncurses UI (export only)
-d, --duration S     Duration (seconds) for headless run
```

## Key Bindings

Inside the TUI:

- `q` Quit
- `r` Toggle run/stop
- `c` Clear statistics (resets peaks & history)
- `t` Toggle mode (Channel <-> Tasks) – resets statistics
- `h` Toggle help pane

## Interpretation (Tasks Mode)

- Tasks/s: Completed tasks per second (synthetic no-op bodies) – focuses on scheduling overhead.
- Fast-path hit: Ratio of tasks executed from per-worker fast slot vs falling back to deque or inject queue.
- Steal success: Successful steals / steal probes. Low ratio may indicate load imbalance or insufficient work in victim deques.
- Graph: Uses tasks/sec mapped onto existing PPS graph scaling.

Note: `gbps` and `smooth_gbps` are `null` in tasks mode JSON output (bandwidth is channel-only). Any averaging of `gbps` should ignore `null` entries.

## Design Notes

The synthetic scheduler benchmark submits batches (`2048`) of trivial tasks in a loop to saturate the scheduler without channel overhead. A brief `sched_yield()` prevents monopolizing CPU entirely.

Channel mode now uses a fully coroutine-native producer/consumer benchmark (legacy pthread benchmark removed). At program start a warm-up task is submitted so worker threads are active before the first iteration; each channel iteration also has a small safety timeout (2s) to prevent hangs if no progress occurs (partial progress is still recorded).

## Future Enhancements

Instrumentation & Metrics:
- Integrate zero-copy channel counters (zref enqueue/dequeue, cache miss indicators) with dedicated pane.
- Latency histogram (task completion & channel op latency) using fixed-size ring + logarithmic buckets.
- Per-worker utilization & steal heat-map (ANSI color gradient or side mini-bars).
- Adaptive sampling interval based on variance (reduce overhead under stable load).
- Backpressure / saturation detector (warn when inject queue growth sustained).

Benchmarking & Modes:
- Coroutine-native channel benchmark to replace legacy pthread implementation.
- Dual comparison mode (split screen: channel vs tasks same runtime window).
- Configurable task batch size & body complexity (simulate varied workload cost).
- Mixed mode: interleave channel ops inside scheduled tasks to test interference.

Output & Automation:
- Pluggable output backends: CSV, Prometheus exposition, WebSocket live stream.
- Alert thresholds producing JSON annotations (fast-path regression, steal failure spike, latency SLA breach).
- Run profile manifest (YAML) to execute a scripted sequence of modes + parameter sweeps auto-exporting NDJSON.
- Compression option for JSON output (gzip) when writing to file.

UI / UX:
- Zoom & scroll in graph history (keys for left/right, +/- for time scale).
- Sparkline mini-panels for each key ratio.
- Color-coded status badges (GREEN/YELLOW/RED) for KPIs (fast-path ratio, steal ratio, idle worker %, backlog depth).

Engineering / Infra:
- ENHANCEMENTS.md backlog with status tags (Planned / In Progress / Done) for stakeholder tracking.
- CI job to run headless tasks + channel benchmarks, archive NDJSON + generate charts (gnuplot or Python script).
- Test harness injecting synthetic steal failure scenarios to validate alerting.

Quality & Validation:
- Invariant checks: tasks_completed should monotonically increase; log anomalies.
- Self-diagnostics panel with sampling loop jitter statistics.

Stretch (Future Research):
- System instrumentation to correlate OS scheduling events with user-level scheduler metrics.
- Statistical outlier detection on live metric streams (flag unexpected drops in performance ratios).

## License
BSD-3-Clause (see project root LICENSE).
