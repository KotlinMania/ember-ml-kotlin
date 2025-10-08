# KCoro Top-Level Makefile
# Aggregates build of core coroutine library, IPC libraries, tools, examples, and tests.
# Usage: make [all|core|ipc|tools|examples|tests|clean|distclean|help]

# Default compiler overridable by environment
CC ?= gcc

# Subdirectory paths
CORE_DIR := core
IPC_POSIX_DIR := ipc/posix
CHANMON_DIR := lab/tui/chanmon
EXAMPLES_DIR := examples

# Phony targets
.PHONY: all core ipc tools examples tests clean distclean help verify docs

all: core ipc tools ## Build everything (core + IPC + tools)

core: ## Build core coroutine static library
	$(MAKE) -C $(CORE_DIR) all

ipc: core ## Build IPC (depends on core)
	$(MAKE) -C $(IPC_POSIX_DIR) all

# Build chanmon tool (depends on core library)
tools: core ## Build developer / diagnostic tools
	$(MAKE) -C $(CHANMON_DIR) all

docs: ## Generate HTML API documentation (requires doxygen)
	@command -v doxygen >/dev/null 2>&1 || { echo "doxygen not found"; exit 1; }
	@echo "[docs] generating API documentation..."
	@$(MAKE) -C docs >/dev/null 2>&1 || true
	@(cd docs && doxygen Doxyfile >/dev/null 2>&1 || doxygen Doxyfile)
	@echo "[docs] done: kcoro/docs/build/html/index.html"

examples: core ipc ## Build example programs (if their Makefiles exist)
	@if [ -d $(EXAMPLES_DIR)/posix_echo ]; then \
	  $(MAKE) -C $(EXAMPLES_DIR)/posix_echo all; \
	fi

# Placeholder for a future unified test harness
tests: core ipc ## Build / run tests
	@echo "[tests] (placeholder) invoke specific test binaries under tests/" \
	; if [ -f tests/Makefile ]; then $(MAKE) -C tests all; fi

verify: ## Verify freshness of core library
	$(MAKE) -C $(CORE_DIR) verify-fresh

clean: ## Clean all build artifacts (but keep library archives if needed)
	$(MAKE) -C $(CORE_DIR) clean || true
	$(MAKE) -C $(IPC_POSIX_DIR) clean || true
	$(MAKE) -C $(CHANMON_DIR) clean || true
	@if [ -d $(EXAMPLES_DIR)/posix_echo ]; then $(MAKE) -C $(EXAMPLES_DIR)/posix_echo clean || true; fi

# Deep clean
.distclean-internal:
	$(MAKE) -C $(CORE_DIR) clean || true
	$(MAKE) -C $(IPC_POSIX_DIR) clean || true
	$(MAKE) -C $(CHANMON_DIR) clean || true
	@if [ -d $(EXAMPLES_DIR)/posix_echo ]; then $(MAKE) -C $(EXAMPLES_DIR)/posix_echo clean || true; fi

# Add more directories as they standardize on Makefiles

distclean: .distclean-internal ## Remove all build products
	@echo "Distclean complete"

help: ## Show this help
	@echo "KCoro Build System"
	@echo "==================="
	@echo "Targets:"; \
	grep -E '^[a-zA-Z0-9_.-]+:.*?## ' $(MAKEFILE_LIST) | \
		awk 'BEGIN {FS=":.*?## "}; {printf "  %-15s %s\n", $$1, $$2}' | sort
	@echo ""; \
	echo "Examples:"; \
	echo "  make            # Build core + IPC + tools"; \
	echo "  make core       # Just libkcoro.a"; \
	echo "  make ipc        # libkcoro_ipc_posix.a (after core)"; \
	echo "  make tools      # chanmon monitor"; \
	echo "  make examples   # example programs"; \
	echo "  make clean      # Clean artifacts"; \
	echo "  make distclean  # Deep clean"; \
	echo "  make verify     # Freshness check core lib";

# Default goal
.DEFAULT_GOAL := all
