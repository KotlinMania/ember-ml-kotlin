include ../mk/common.mk

AR ?= ar
ARFLAGS ?= rcs

# Public include path
CFLAGS += -I../include -DKC_SCHED=1

# Always kill lingering kcoro test processes before builds (can disable with KILL_BEFORE=0)
KILL_BEFORE ?= 1
PREKILL := ../scripts/kill_kcoro_procs.sh

OBJDIR := build/obj
BINDIR := build/lib

# C sources  
SRCS := src/kc_chan.c src/kc_actor.c src/kc_cancel.c src/kc_sched.c src/kcoro_core.c src/kc_scope.c src/kc_select.c src/kc_zcopy.c src/kc_runtime_config.c src/kc_bench.c src/kc_dispatch.c

# Architecture-specific assembly source selection
UNAME_M := $(shell uname -m)
ifeq ($(UNAME_M),x86_64)
ASM_DIR := ../arch/x86_64
else ifeq ($(UNAME_M),amd64)
ASM_DIR := ../arch/x86_64
else ifeq ($(UNAME_M),arm64)
ASM_DIR := ../arch/aarch64
else ifeq ($(UNAME_M),aarch64)
ASM_DIR := ../arch/aarch64
else
ASM_DIR := ../arch/aarch64
endif
ASM_SRCS := $(ASM_DIR)/kc_ctx_switch.S

OBJS := $(patsubst src/%.c,$(OBJDIR)/%.o,$(SRCS)) $(patsubst $(ASM_DIR)/%.S,$(OBJDIR)/%.o,$(ASM_SRCS))
DEPS := $(OBJS:.o=.d)

LIB := $(BINDIR)/libkcoro.a

all: prekill $(LIB)

$(BINDIR) $(OBJDIR):
	@mkdir -p $@

$(OBJDIR)/%.o: src/%.c | $(OBJDIR)
	$(CC) $(CFLAGS) -c -o $@ $<

$(OBJDIR)/%.o: $(ASM_DIR)/%.S | $(OBJDIR)
	$(CC) $(CFLAGS) -c -o $@ $<

$(LIB): $(BINDIR) $(OBJS)
	$(AR) $(ARFLAGS) $@ $(OBJS)
	@echo "built $@"

clean:
	rm -rf build

.PHONY: prekill kill-tests
prekill:
@if [ "$(KILL_BEFORE)" = "1" ] && [ -x $(PREKILL) ]; then \
  bash $(PREKILL); \
fi

kill-tests:
	@if [ -x $(PREKILL) ]; then bash $(PREKILL); fi

# Verify library freshness: fails if any source newer than built archive
.PHONY: verify-fresh
verify-fresh: $(LIB)
 @latest_src=$(shell find src $(ASM_DIR) -type f \( -name '*.c' -o -name '*.S' \) -printf '%T@\n' | sort -n | tail -1); \
	 lib_time=$(shell stat -c %Y $(LIB) 2>/dev/null || echo 0); \
	 latest_int=$$(printf '%.0f' $$latest_src); \
	 if [ $$lib_time -lt $$latest_int ]; then \
	   echo "ERROR: libkcoro.a is stale vs sources (lib=$$lib_time < src=$$latest_int)" >&2; exit 1; \
	 else echo "Fresh: lib timestamp $$lib_time >= src $$latest_int"; fi

.PHONY: all clean

-include $(DEPS)
