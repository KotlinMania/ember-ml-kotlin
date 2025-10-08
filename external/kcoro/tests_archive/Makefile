CC ?= gcc
CFLAGS += -O2 -Wall -Wextra -Werror -std=c11 -I../include -pthread -MMD -MP
LDFLAGS += -L../core/build/lib -lkcoro -pthread

BINDIR := build
OBJDIR := build/obj

# Run all recipe lines in a single shell (simplifies loops)
.ONESHELL:

# Auto-discover tests and build one binary per source (experiments moved to lab/)
TEST_SOURCES := $(wildcard test_*.c)
TESTS := $(patsubst %.c,$(BINDIR)/%,$(TEST_SOURCES))

BENCHES := \
	$(BINDIR)/bench_latency \
	$(BINDIR)/bench_throughput \
	$(BINDIR)/bench_scheduler_throughput \
	$(BINDIR)/optimize_pc \
	$(BINDIR)/optimize_enhanced \
	$(BINDIR)/bench_async_throughput

# Special target for TUI monitor (built in lab directory)
TUI_MONITOR := ../lab/tui/chanmon/build/kcoro_chanmon

# Enhanced benchmark needs the kc_bench implementation
$(BINDIR)/optimize_enhanced: $(OBJDIR)/optimize_enhanced.o $(OBJDIR)/kc_bench.o | $(BINDIR)
	$(CC) -o $@ $^ $(LDFLAGS)

$(BINDIR)/test_stress_pc: $(OBJDIR)/test_stress_pc.o $(OBJDIR)/kc_bench.o | $(BINDIR)
	$(CC) -o $@ $^ $(LDFLAGS)

all: $(TESTS) $(BENCHES) tui-monitor

# Simple test runner: executes discovered tests (skips extreme by default)
TESTS_RUN := $(filter-out $(BINDIR)/test_100gbps_extreme,$(TESTS))
test: $(TESTS)
	@set -e
	for t in $(TESTS_RUN); do
	  echo "[make test] RUN $$t"
	  $$t || exit $$?
	done
	echo "[make test] PASS all"


$(BINDIR) $(OBJDIR):
	@mkdir -p $@

$(BINDIR)/%: $(OBJDIR)/%.o | $(BINDIR)
	$(CC) -o $@ $< $(LDFLAGS)

# Legacy pthread benchmarks moved to lab (not built here)

# Special build rule for test_unified_api (needs proper linking order)
$(BINDIR)/test_unified_api: $(OBJDIR)/test_unified_api.o | $(BINDIR)
	$(CC) -o $@ $^ $(LDFLAGS)

$(OBJDIR)/%.o: %.c | $(OBJDIR)
	$(CC) $(CFLAGS) -c -o $@ $<

tui-monitor: $(TUI_MONITOR) ## Build the ARM64 TUI channel monitor

$(TUI_MONITOR):
	$(MAKE) -C ../lab/tui/chanmon all

clean:
	rm -rf $(BINDIR)
	$(MAKE) -C ../lab/tui/chanmon clean

.PHONY: all clean

-include $(OBJDIR)/*.d

# Optional sanitizers
ifeq ($(TSAN),1)
CFLAGS += -fsanitize=thread
LDFLAGS += -fsanitize=thread
endif
ifeq ($(UBSAN),1)
CFLAGS += -fsanitize=undefined
LDFLAGS += -fsanitize=undefined
endif
