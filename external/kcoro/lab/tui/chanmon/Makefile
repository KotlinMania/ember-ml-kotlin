# Makefile for KCoro Channel Monitor TUI
# Builds the high-performance channel monitor with real-time visualization

CC ?= gcc
# NOTE: -DHAVE_ARM64_ASM assumes aarch64 build; remove if targeting other arch.
CFLAGS ?= -O2 -g -DKCORO_DEBUG -DHAVE_ARM64_ASM -std=c11 -Wall -Wextra -D_GNU_SOURCE
INCLUDES ?= -I../../../include -I../../../port -I../../../core/src
UNAME_S := $(shell uname -s)
LIBS ?= -lncurses -lpthread -lm
ifeq ($(UNAME_S),Linux)
LIBS += -lrt
endif

# Source files
MAIN_SRC := kcoro_chanmon.c

# Use prebuilt kcoro library instead of recompiling sources
KCORO_LIB := ../../../core/build/lib/libkcoro.a

# All sources combined (uses prebuilt library - no source duplication)
ALL_SRCS := $(MAIN_SRC)

# Build directories
BUILDDIR := build
OBJDIR := $(BUILDDIR)/obj

# Target executable
TARGET := $(BUILDDIR)/kcoro_mon

.PHONY: all clean test run-optimal run-high-throughput help

all: $(TARGET) ## Build the channel monitor TUI

$(BUILDDIR) $(OBJDIR):
	@mkdir -p $@

$(TARGET): $(ALL_SRCS) $(KCORO_LIB) | $(BUILDDIR)
	@echo "Building high-performance channel monitor (using prebuilt libkcoro.a)..."
	$(CC) $(CFLAGS) $(INCLUDES) -o $@ $(ALL_SRCS) $(KCORO_LIB) $(LIBS)
	@echo "Built $@ successfully"
	@echo "Ready to run (channel mode: default, tasks mode: -m tasks)"

# Convenience targets for different performance configurations
run-optimal: $(TARGET) ## Run with optimal configuration (from optimize_pc results)
	@echo "Running with baseline performance configuration..."
	$(TARGET) -P 2 -C 2 -N 200000 -c 16384 -s 4096 -k 1500

run-high-throughput: $(TARGET) ## Run with high-throughput configuration  
	@echo "Running with high-throughput configuration..."
	$(TARGET) -P 4 -C 4 -N 500000 -c 8192 -s 8192 -k 1500

run-low-latency: $(TARGET) ## Run with low-latency configuration
	@echo "Running with low-latency configuration..."
	$(TARGET) -P 2 -C 2 -N 100000 -c 1024 -s 256 -k 64

run-tasks: $(TARGET) ## Run scheduler task benchmark mode
	$(TARGET) -m tasks

test: $(TARGET) ## Quick functionality test
	@echo "Testing channel monitor functionality..."
	timeout 5s $(TARGET) -P 1 -C 1 -N 10000 -c 64 -s 64 -k 64 || true
	@echo "Test completed (monitor ran for 5 seconds)"

clean: ## Clean build artifacts
	rm -rf $(BUILDDIR)

help: ## Show this help message
	@echo "KCoro Channel Monitor TUI Build System"
	@echo "======================================"
	@echo ""
	@echo "This builds the high-performance channel monitor with:"
	@echo "  - Real-time TUI visualization with ncurses"
	@echo "  - aarch64 assembly-optimized context switching (if building on ARM64)"
	@echo "  - Integrated performance benchmarking"
	@echo "  - Smoothed performance metrics display"
	@echo ""
	@echo "Available targets:"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  %-20s %s\n", $$1, $$2}'
	@echo ""
	@echo "Usage Examples:"
	@echo "  make run-optimal     # Run with optimized settings from optimize_pc"
	@echo "  make run-high-throughput  # Maximum throughput configuration"
	@echo "  make run-tasks           # Run scheduler synthetic task benchmark"
	@echo "  make test           # Quick 5-second functionality test"

.DEFAULT_GOAL := help

# Debug target to show the exact build command
show-build-command: ## Show the exact gcc build command
	@echo "Build command that will be executed:"
	@echo "$(CC) $(CFLAGS) $(INCLUDES) -o $(TARGET) $(ALL_SRCS) $(LIBS)"
