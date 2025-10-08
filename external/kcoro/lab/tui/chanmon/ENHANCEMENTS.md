# KCoro Monitor Enhancement Backlog

Status Legend: PLANNED | IN-PROGRESS | DONE | DEFERRED

## 1. Instrumentation & Metrics
| Item | Description | Status | Notes |
|------|-------------|--------|-------|
| ZC Counters Pane | Zero-copy channel metrics (enqueue/dequeue, cache misses) | PLANNED | Await zero-copy implementation landing |
| Latency Histogram | Task / channel op latency distribution with log buckets | PLANNED | Use fixed bucket array; export in JSON |
| Worker Heat-Map | Per-worker utilization + steals (success/failure color) | PLANNED | ANSI color gradient / mini-bars |
| Adaptive Sampling | Dynamic UPDATE_INTERVAL_MS based on variance | PLANNED | Lower overhead when stable |
| Backpressure Alerts | Detect sustained inject queue growth | PLANNED | Threshold config via env var |

## 2. Benchmarking & Modes
| Item | Description | Status | Notes |
|------|-------------|--------|-------|
| Coroutine Channel Bench | Replace pthread legacy with coroutine version | PLANNED | Aligns with unified scheduler story |
| Dual Comparison Mode | Split screen Channel vs Tasks concurrently | PLANNED | Memory & CPU overhead trade-offs |
| Configurable Task Batch | CLI flag for task batch size & body complexity | PLANNED | Enables CPU intensity tuning |
| Mixed Mode | Interleave channel ops inside task submissions | PLANNED | Stress interference scenarios |

## 3. Output & Automation
| Item | Description | Status | Notes |
|------|-------------|--------|-------|
| JSON ND Export | NDJSON sample streaming (per sample) | DONE | `-j/--json` implemented |
| Prometheus Export | Expose metrics on localhost HTTP endpoint | PLANNED | Optional libmicrohttpd or minimal socket |
| CSV Output | Optional CSV row emitter | PLANNED | Simple flag `--csv` |
| WebSocket Live Stream | Push samples to browser client | PLANNED | Enables web dashboard |
| Alert Annotations | JSON lines with `type":"alert"` | PLANNED | Trigger on thresholds |
| Run Profile Manifest | YAML describing sequential runs | PLANNED | Batch automation for CI |
| Gzip Output | Compress NDJSON file | PLANNED | Detect `.gz` suffix |

## 4. UI / UX
| Item | Description | Status | Notes |
|------|-------------|--------|-------|
| Graph Zoom/Scroll | Time window navigation | PLANNED | Maintain circular buffer metadata |
| Sparklines | Mini trend lines for ratios | PLANNED | Row of inline glyphs |
| KPI Badges | Color-coded status (fast-path, steal, idle %) | PLANNED | Threshold config | 
| Side-by-side Panels | Resizable layout | PLANNED | Use `resizeterm()` handling |

## 5. Engineering / Infra
| Item | Description | Status | Notes |
|------|-------------|--------|-------|
| CI Headless Bench | CI job exporting NDJSON & charts | PLANNED | Use Python matplotlib script |
| NDJSON Validation Tool | Simple schema checker | PLANNED | Reject malformed lines |
| Regression Threshold Tests | Fail CI if perf drops N% | PLANNED | Baseline file comparison |
| Config File Support | TOML/JSON configuration file | PLANNED | Overrides CLI defaults |

## 6. Quality & Validation
| Item | Description | Status | Notes |
|------|-------------|--------|-------|
| Invariant Checks | Validate monotonic counters, log anomalies | PLANNED | Periodic scan task |
| Sampling Jitter Stats | Track loop jitter distribution | PLANNED | Use high-res timestamps |
| Self-Test Mode | Inject scripted workload + verify metrics | PLANNED | Deterministic seeds |

## 7. Stretch / Research
| Item | Description | Status | Notes |
|------|-------------|--------|-------|
| System Event Correlation | Map OS scheduling events to user metrics | PLANNED | Collect perf events |
| Statistical Outlier Detection | Real-time drift detection | PLANNED | Sliding window, z-score |
| Remote Control | IPC / socket to change params live | PLANNED | JSON-RPC minimal |

## Recently Completed
- JSON headless export (`-j`, `-H`, `-d`)
- Scheduler mode (tasks) with fast-path & steal ratios

## Priorities (Proposed Next Sequence)
1. Coroutine-native channel benchmark (eliminate pthread benchmark skew)
2. Zero-copy metrics pane (once counters exist)
3. Latency histogram + CSV/Prometheus exporters
4. CI headless benchmark workflow + regression guard
5. Alert thresholds + KPI badges

---
Generated initial backlog; update status as items progress.
