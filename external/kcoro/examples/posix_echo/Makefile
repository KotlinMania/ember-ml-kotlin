include ../../mk/common.mk

CFLAGS += -I../../include -I../../ipc/posix/include
LDFLAGS += -L../../ipc/posix/build/lib -lkcoro_ipc_posix

BINDIR := build
OBJDIR := build/obj

SRV := $(BINDIR)/server
CLI := $(BINDIR)/client

all: $(SRV) $(CLI)

$(BINDIR) $(OBJDIR):
	@mkdir -p $@

$(OBJDIR)/server.o: server.c | $(OBJDIR)
	$(CC) $(CFLAGS) -c -o $@ $<
$(OBJDIR)/client.o: client.c | $(OBJDIR)
	$(CC) $(CFLAGS) -c -o $@ $<

$(SRV): $(BINDIR) $(OBJDIR)/server.o
	$(CC) -o $@ $(OBJDIR)/server.o $(LDFLAGS)
$(CLI): $(BINDIR) $(OBJDIR)/client.o
	$(CC) -o $@ $(OBJDIR)/client.o $(LDFLAGS)

clean:
	rm -rf $(BINDIR)

.PHONY: all clean

-include $(OBJDIR)/server.d $(OBJDIR)/client.d

