# lab/ipc/sim — Impairment Simulator

Purpose
- Inject latency/jitter/drop/dup/out‑of‑order on top of a transport to validate sequence, credit windows, and repair policies without involving real networks.

Usage
- Wraps a pair of connected sockets; adds probabilistic impairments; exposes the same `send_frame/recv_frame` API.

