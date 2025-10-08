CC ?= gcc
CFLAGS += -O2 -Wall -Wextra -Werror -std=c11 -I../../include -I../../core/src -MMD -MP -pthread

OBJDIR := build/obj
BINDIR := build/lib

SRCS := src/kcoro_ipc_posix.c src/kcoro_ipc_chan.c src/kcoro_ipc_server.c
OBJS := $(patsubst src/%.c,$(OBJDIR)/%.o,$(SRCS))
DEPS := $(OBJS:.o=.d)

LIB := $(BINDIR)/libkcoro_ipc_posix.a

all: $(LIB)

$(BINDIR) $(OBJDIR):
	@mkdir -p $@

$(OBJDIR)/%.o: src/%.c | $(OBJDIR)
	$(CC) $(CFLAGS) -c -o $@ $<

$(LIB): $(BINDIR) $(OBJS)
	ar rcs $@ $(OBJS)
	@echo "built $@"

clean:
	rm -rf build

.PHONY: all clean

-include $(DEPS)

