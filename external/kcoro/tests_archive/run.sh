#!/usr/bin/env bash
set -uo pipefail

cd "$(dirname "$0")"
# Optional configuration file for reproducible settings
if [[ -f config.sh ]]; then
  # shellcheck source=/dev/null
  . ./config.sh
fi

cleanup_enabled=${CLEANUP:-1}
cleanup_script_rel="../scripts/cleanup.sh"

# Best-effort cleanup of lingering demo/test processes and stale sockets
_runner_cleanup() {
  [[ ${cleanup_enabled} -eq 1 ]] || return 0
  if [[ -x ${cleanup_script_rel} ]]; then
    bash "${cleanup_script_rel}" >/dev/null 2>&1 || true
    return 0
  fi
  # Fallback inline cleanup if script missing
  pkill -f 'kcoro-go/kcoro_srv' 2>/dev/null || true
  pkill -f 'kcoro/examples/oesnn_client/build/oesnn_client' 2>/dev/null || true
  pkill -f 'python3 -m unittest -q coroutines.python_kcoro.tests.test_kcoro.TestPerformance.test_actor_throughput' 2>/dev/null || true
  pkill -f 'coroutines/kcoro/examples/posix_echo/build/server' 2>/dev/null || true
  pkill -f 'coroutines/kcoro/examples/posix_echo/build/client' 2>/dev/null || true
  pkill -f 'coroutines/kcoro/examples/distributed_channels/build/distributed_channels' 2>/dev/null || true
  rm -f /tmp/kcoro.sock /tmp/kcoro_*.sock 2>/dev/null || true
}

echo "[runner] pre-cleanup (set CLEANUP=0 to disable)"
_runner_cleanup
trap _runner_cleanup EXIT

FILTER_KCORO=1
MAX_CONSOLE_LINES=${MAX_CONSOLE_LINES:-200}
LONG=0
BENCH=${BENCH:-1}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --full) FILTER_KCORO=0; MAX_CONSOLE_LINES=100000; shift;;
    --long) LONG=1; shift;;
    --bench) BENCH=1; shift;;
    *) echo "Usage: $0 [--full] [--long] [--bench]"; exit 2;;
  esac
done

export KCORO_DEBUG=1
echo "[runner] KCORO_DEBUG=1 (console filtered, max $MAX_CONSOLE_LINES lines; use --full to disable)"

echo "[runner] build libkcoro"
make -C ../user >/dev/null || { echo "[runner] build lib failed"; exit 1; }
echo "[runner] build tests"
make -C . TSAN=${TSAN:-0} UBSAN=${UBSAN:-0} >/dev/null || { echo "[runner] build tests failed"; exit 1; }

ts=$(date +%Y%m%d-%H%M%S)
logdir="logs/$ts"
mkdir -p "$logdir"
echo "[runner] logs -> $logdir"

tests=(
  build/test_chan_basic
  build/test_cancel_recv
  build/test_actor_cancel
  build/test_cancel_ctx
)
 
# Always run scheduler test (async-only)
tests+=( build/test_scheduler_basic )

if [[ $LONG -eq 1 ]]; then
  tests+=( build/test_stress_pc )
  if [[ ${EXTREME:-0} -eq 1 ]]; then
    tests+=( build/test_100gbps_extreme )
  fi
fi

if [[ ${BENCH:-1} -eq 1 ]]; then
  echo "[runner] BENCHMARKS"
  benches=(
    "build/bench_latency 200000"
    "build/bench_throughput 4 4 250000 1024"
    "build/bench_async_throughput 2 2 100000 1024"
  )
  for cmd in "${benches[@]}"; do
    name=$(echo "$cmd" | awk '{print $1}')
    base=$(basename "$name")
    echo "[runner] RUN $cmd"
    if command -v stdbuf >/dev/null 2>&1; then
      stdbuf -oL -eL bash -lc "$cmd" 2>&1 | tee "$logdir/${base}.log"
    else
      bash -lc "$cmd" 2>&1 | tee "$logdir/${base}.log"
    fi
  done
fi

run_and_log() {
  local cmd="$1"; local logfile="$2"; local max_lines=${MAX_CONSOLE_LINES}
  local test_name=$(basename "$1")
  # Ensure parent directory for logfile exists (handles nested paths)
  mkdir -p "$(dirname "$logfile")"
  
  # Disable debug output for stress tests to reduce noise
  local env_prefix=""
  if [[ "$test_name" == *"stress"* ]]; then
    env_prefix="KCORO_DEBUG=0"
  fi
  
  if command -v stdbuf >/dev/null 2>&1; then
    stdbuf -oL -eL bash -lc "$env_prefix $cmd" 2>&1 \
      | tee "$logfile" \
      | awk -v max="$max_lines" '
          function out_line(s){ if(printed<max){ print s; printed++ } }
          function flush(){ if(cnt>0){ out_line(last); if (cnt>1) out_line(sprintf("<Message repeated %d times>", cnt-1)); cnt=0 } }
          { if ($0==last) cnt++; else { flush(); last=$0; cnt=1 } }
          END{ flush() }
        '
  else
    bash -lc "$env_prefix $cmd" 2>&1 \
      | tee "$logfile" \
      | awk -v max="$max_lines" '
          function out_line(s){ if(printed<max){ print s; printed++ } }
          function flush(){ if(cnt>0){ out_line(last); if (cnt>1) out_line(sprintf("<Message repeated %d times>", cnt-1)); cnt=0 } }
          { if ($0==last) cnt++; else { flush(); last=$0; cnt=1 } }
          END{ flush() }
        '
  fi
  return ${PIPESTATUS[0]}
}

fail=0
for t in "${tests[@]}"; do
  name=$(basename "$t")
  echo "[runner] RUN $name"
  run_and_log "$t" "$logdir/$name.log"
  rc=${PIPESTATUS[0]}
  if [[ $rc -eq 0 ]]; then
    echo "[runner] PASS $name"
  else
    echo "[runner] FAIL $name rc=$rc"
    fail=1
  fi
done

## Legacy actor/pthread suites have been moved to lab and are not run here

echo "[runner] summary: $( ( [[ $fail -eq 0 ]] && echo PASS ) || echo FAIL )"

 

exit $fail
