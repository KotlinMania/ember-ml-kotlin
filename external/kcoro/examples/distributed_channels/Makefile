CC ?= gcc
CFLAGS += -O2 -Wall -Wextra -Werror -std=c11 -I../../include -I../../ipc/posix/include -I../../core/src -I../../proto -MMD -MP -pthread
LDFLAGS += -L../../ipc/posix/build/lib -L../../core/build/lib -lkcoro_ipc_posix -lkcoro -pthread

BINDIR := build
OBJDIR := build/obj

EXAMPLE := $(BINDIR)/distributed_channels

all: $(EXAMPLE)

$(BINDIR) $(OBJDIR):
	@mkdir -p $@

$(OBJDIR)/main.o: main.c | $(OBJDIR)
	$(CC) $(CFLAGS) -c -o $@ $<

$(EXAMPLE): $(BINDIR) $(OBJDIR)/main.o
	$(CC) -o $@ $(OBJDIR)/main.o $(LDFLAGS)

clean:
	rm -rf $(BINDIR)

.PHONY: all clean

-include $(OBJDIR)/main.d
