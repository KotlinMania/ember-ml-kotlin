# Shared build fragment for kcoro components
# Usage: in Makefile add: include ../mk/common.mk (adjust relative path)

CC ?= gcc
# Base warnings & language standard
KC_BASE_CFLAGS := -std=c11 -Wall -Wextra -Werror -Wshadow -Wundef -Wpointer-arith \
  -Wstrict-prototypes -Wno-missing-field-initializers -fno-common

# Optimization / debug defaults (override with OPT?=... or DEBUG=1)
DEBUG ?= 0
ifeq ($(DEBUG),1)
  KC_OPTFLAGS := -O0 -g3 -fno-omit-frame-pointer
else
  KC_OPTFLAGS := -O2 -g
endif

# Thread + position independent (for static + potential shared builds)
KC_PLATFORM_FLAGS := -pthread -fPIC -MMD -MP -D_GNU_SOURCE

# Consumers can append to EXTRA_CFLAGS / EXTRA_LDFLAGS
CFLAGS += $(KC_BASE_CFLAGS) $(KC_OPTFLAGS) $(KC_PLATFORM_FLAGS) $(EXTRA_CFLAGS)
LDFLAGS += $(KC_PLATFORM_FLAGS) $(EXTRA_LDFLAGS)

# Provide a helper target to show final flags
.PHONY: kc-flags
kc-flags:
	@echo 'CFLAGS=$(CFLAGS)'
	@echo 'LDFLAGS=$(LDFLAGS)'
