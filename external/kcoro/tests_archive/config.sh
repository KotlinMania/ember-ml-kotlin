# kcoro test runner configuration (sourced by run.sh)
# Keep test runs reproducible by pinning key tunables here.

# Spin iterations before yielding in non-blocking stress (passed as --spin)
SPIN=4096

# Whether to pass --pin to stress (CPU affinity)
PIN=1

# Max console lines (logs always capture full output)
MAX_CONSOLE_LINES=200

# Enable or disable suite under --long
SUITE100G=1
