# Makefile for kcoro Lab Experiments

CC ?= gcc
CFLAGS += -O2 -Wall -Wextra -std=c11 -I../include -Imirror/include -pthread -g
LDFLAGS += -pthread

# Select architecture-specific assembly
UNAME_M := $(shell uname -m)
ifeq ($(UNAME_M),x86_64)
ASM_SRC := ../arch/x86_64/kc_ctx_switch.S
else ifeq ($(UNAME_M),amd64)
ASM_SRC := ../arch/x86_64/kc_ctx_switch.S
else ifeq ($(UNAME_M),arm64)
ASM_SRC := ../arch/aarch64/kc_ctx_switch.S
else ifeq ($(UNAME_M),aarch64)
ASM_SRC := ../arch/aarch64/kc_ctx_switch.S
else
ASM_SRC := ../arch/aarch64/kc_ctx_switch.S
endif

BINDIR := build
OBJDIR := build/obj

.PHONY: all clean

all: $(BINDIR)/lab_minimal_switch $(BINDIR)/lab_stack_test $(BINDIR)/lab_debug_setup $(BINDIR)/lab_waiter_token $(BINDIR)/lab_token_vm $(BINDIR)/lab_token_vm_bench $(BINDIR)/lab_token_vm_mirror $(BINDIR)/lab_token_kernel_demo $(BINDIR)/lab_simple_park_demo tui-monitor

$(BINDIR) $(OBJDIR):
	@mkdir -p $@

# Build the minimal context switching lab
$(BINDIR)/lab_minimal_switch: $(OBJDIR)/lab_minimal_switch.o $(OBJDIR)/kc_ctx_switch.o | $(BINDIR)
	$(CC) -o $@ $^ $(LDFLAGS)
	@echo "Built $@ - Ready to test minimal context switching"

# Build the stack save/restore lab
$(BINDIR)/lab_stack_test: $(OBJDIR)/lab_stack_test.o $(OBJDIR)/kc_ctx_switch.o | $(BINDIR)
	$(CC) -o $@ $^ $(LDFLAGS)
	@echo "Built $@ - Ready to test stack save/restore"

# Build the rendezvous waiter/token prototype
$(BINDIR)/lab_waiter_token: $(OBJDIR)/lab_waiter_token.o | $(BINDIR)
	$(CC) -o $@ $^ $(LDFLAGS)
	@echo "Built $@ - Rendezvous token prototype"

$(BINDIR)/lab_token_vm: $(OBJDIR)/token_vm_demo.o $(OBJDIR)/token_vm_resume.o $(OBJDIR)/token_vm_apply.o | $(BINDIR)
	$(CC) -o $@ $^ $(LDFLAGS)
	@echo "Built $@ - Token VM resume prototype"

$(BINDIR)/lab_token_vm_bench: $(OBJDIR)/token_vm_bench.o $(OBJDIR)/token_vm_resume.o $(OBJDIR)/token_vm_apply.o | $(BINDIR)
	$(CC) -o $@ $^ $(LDFLAGS)
	@echo "Built $@ - Token VM throughput bench"

$(BINDIR)/lab_token_vm_mirror: $(OBJDIR)/token_vm_mirror.o $(OBJDIR)/token_vm_resume.o $(OBJDIR)/token_vm_apply.o | $(BINDIR)
	$(CC) -o $@ $^ $(LDFLAGS)
	@echo "Built $@ - Token VM coroutine mirror"

$(BINDIR)/lab_token_kernel_demo: $(OBJDIR)/token_kernel_demo.o mirror/core/build/lib/libkcoro.a | $(BINDIR)
	$(CC) -o $@ $^ $(LDFLAGS)
	@echo "Built $@ - Token kernel demo"

$(BINDIR)/lab_simple_park_demo: $(OBJDIR)/simple_park_demo.o mirror/core/build/lib/libkcoro.a | $(BINDIR)
	$(CC) -o $@ $^ $(LDFLAGS)
	@echo "Built $@ - Simple park/resume demo"

mirror/core/build/lib/libkcoro.a:
	$(MAKE) -C mirror/core all KILL_BEFORE=0

# Build the debug setup lab
$(BINDIR)/lab_debug_setup: $(OBJDIR)/lab_debug_setup.o $(OBJDIR)/kc_ctx_switch.o | $(BINDIR)
	$(CC) -o $@ $^ $(LDFLAGS)
	@echo "Built $@ - Ready to debug context setup"

# Compile lab sources
$(OBJDIR)/lab_minimal_switch.o: lab_minimal_switch.c | $(OBJDIR)
	$(CC) $(CFLAGS) -c -o $@ $<

$(OBJDIR)/lab_stack_test.o: lab_stack_test.c | $(OBJDIR)
	$(CC) $(CFLAGS) -c -o $@ $<

$(OBJDIR)/lab_waiter_token.o: lab_waiter_token.c | $(OBJDIR)
	$(CC) $(CFLAGS) -c -o $@ $<

$(OBJDIR)/token_vm_resume.o: token_vm_resume.c | $(OBJDIR)
	$(CC) $(CFLAGS) -c -o $@ $<

$(OBJDIR)/token_vm_bench.o: token_vm_bench.c | $(OBJDIR)
	$(CC) $(CFLAGS) -c -o $@ $<

$(OBJDIR)/token_vm_mirror.o: token_vm_mirror.c | $(OBJDIR)
	$(CC) $(CFLAGS) -c -o $@ $<

$(OBJDIR)/token_vm_apply.o: token_vm_apply.S | $(OBJDIR)
	$(CC) $(CFLAGS) -c -o $@ $<

$(OBJDIR)/token_vm_demo.o: token_vm_demo.c | $(OBJDIR)
	$(CC) $(CFLAGS) -c -o $@ $<

$(OBJDIR)/token_kernel_demo.o: token_kernel_demo.c | $(OBJDIR)
	$(CC) $(CFLAGS) -c -o $@ $<

$(OBJDIR)/simple_park_demo.o: simple_park_demo.c | $(OBJDIR)
	$(CC) $(CFLAGS) -c -o $@ $<

# legacy compatibility experiment removed from active builds

$(OBJDIR)/lab_debug_setup.o: lab_debug_setup.c | $(OBJDIR)
	$(CC) $(CFLAGS) -c -o $@ $<

# Compile ARM64 assembly
$(OBJDIR)/kc_ctx_switch.o: $(ASM_SRC) | $(OBJDIR)
	$(CC) $(CFLAGS) -c -o $@ $<

# Test target
test: $(BINDIR)/lab_minimal_switch $(BINDIR)/lab_stack_test
	@echo "=== Running Lab Experiments ==="
	@echo "--- Experiment 1: Minimal Context Switching ---"
	$(BINDIR)/lab_minimal_switch
	@echo ""
	@echo "--- Experiment 2: Stack Save/Restore ---" 
	$(BINDIR)/lab_stack_test
	@echo ""
	@echo "--- Archived experiments omitted (legacy compatibility removed) ---"

tui-monitor: ## Build the ARM64 high-performance TUI channel monitor
	$(MAKE) -C tui/chanmon all
	@echo "Built TUI Channel Monitor - Ready for ARM64 performance visualization"

clean:
	rm -rf $(BINDIR)
	$(MAKE) -C tui/chanmon clean
