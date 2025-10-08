include ../mk/common.mk

CFLAGS += -I../include
LDFLAGS += -L../core/build/lib -lkcoro -pthread

# Always kill lingering kcoro test processes before builds/runs (can disable with KILL_BEFORE=0)
KILL_BEFORE ?= 1
PREKILL := ../scripts/kill_kcoro_procs.sh

BINDIR := build
OBJDIR := build/obj

# Default: run only stable/smoke tests to avoid hangs.
# Exclude only long-running stress by default.
TEST_EXCLUDE := test_chan_zref_stress.c
TEST_SOURCES := $(filter-out $(TEST_EXCLUDE),$(wildcard test_*.c))
TESTS := $(patsubst %.c,$(BINDIR)/%,$(TEST_SOURCES))

all: prekill $(TESTS)

test: prekill all
	@set -e; \
	for t in $(TESTS); do \
	  echo "[kcoro tests] RUN $$t"; \
	  if command -v timeout >/dev/null 2>&1; then timeout 25s "$$t"; \
	  elif command -v gtimeout >/dev/null 2>&1; then gtimeout 25s "$$t"; \
	  else "$$t"; fi || exit $$?; \
	done; \
	echo "[kcoro tests] PASS all";

# Full test run, includes rendezvous/zref tests, each with a timeout to prevent lockups.
.PHONY: test-full
test-full: prekill
	@set -e; \
	all_srcs=$(wildcard test_*.c); \
	for src in $$all_srcs; do \
	  bin="$(BINDIR)/$${src%.c}"; \
	  echo "[kcoro tests] BUILD $$bin"; \
	  $(MAKE) -s $$bin; \
	done; \
	for src in $$all_srcs; do \
	  bin="$(BINDIR)/$${src%.c}"; \
	  echo "[kcoro tests] RUN $$bin"; \
	  if command -v timeout >/dev/null 2>&1; then \
	    case "$$bin" in \
	      *test_chan_zref_stress) timeout 60s "$$bin" ;; \
	      *) timeout 30s "$$bin" ;; \
	    esac; \
	  elif command -v gtimeout >/dev/null 2>&1; then gtimeout 30s "$$bin"; \
	  else "$$bin"; fi || exit $$?; \
	done; \
	echo "[kcoro tests] PASS full";

.PHONY: prekill kill-tests
prekill:
	@if [ "$(KILL_BEFORE)" = "1" ] && [ -x $(PREKILL) ]; then \
	  bash $(PREKILL); \
	fi

kill-tests:
	@if [ -x $(PREKILL) ]; then bash $(PREKILL); fi

$(BINDIR) $(OBJDIR):
	@mkdir -p $@

$(BINDIR)/%: $(OBJDIR)/%.o | $(BINDIR)
	$(CC) -o $@ $< $(LDFLAGS)

$(OBJDIR)/%.o: %.c | $(OBJDIR)
	$(CC) $(CFLAGS) -c -o $@ $<

clean:
	rm -rf $(BINDIR)

.PHONY: all test clean

-include $(OBJDIR)/*.d
