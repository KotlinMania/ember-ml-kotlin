// Minimal tests for Rendezvous/Unlimited with coroutines
#include "kcoro_cpp/scheduler.hpp"
#include "kcoro_cpp/channel.hpp"
#include <cassert>
#include <cstdio>
using namespace kcoro_cpp;

struct P { void* p; size_t n; };

static void prod(void* arg){
  auto* ch = static_cast<IChannel<P>*>(arg);
  char buf[256]; P m{buf, sizeof(buf)};
  for (int i=0;i<1000;i++) { int rc = ch->send(m, -1); if (rc==KC_EPIPE) break; }
}
static void cons(void* arg){
  auto* ch = static_cast<IChannel<P>*>(arg);
  P m{}; int got=0; for(;;){ int rc = ch->recv(m, -1); if (rc==KC_EPIPE) break; got++; if(got==1000) break; }
}

int main(){
  {
    WorkStealingScheduler s(2);
    RendezvousChannel<int> c(&s);
    auto pfn = [](void* p){ auto* ch = static_cast<IChannel<int>*>(p); int v=0; for(int i=0;i<1000;i++){ int rc=ch->send(v,-1); if(rc==KC_EPIPE) break; } };
    auto cfn = [](void* p){ auto* ch = static_cast<IChannel<int>*>(p); int v=0; int got=0; for(;;){ int rc=ch->recv(v,-1); if(rc==KC_EPIPE) break; if(++got==1000) break; } };
    s.spawn_co(pfn, &c);
    s.spawn_co(cfn, &c);
    s.drain(2000); c.close(); s.drain(200); s.stop_and_join();
    std::puts("rv ok");
  }
  {
    WorkStealingScheduler s(2);
    UnlimitedChannel<int> c(&s);
    auto pfn = [](void* p){ auto* ch = static_cast<IChannel<int>*>(p); int v=0; for(int i=0;i<1000;i++){ int rc=ch->send(v,-1); if(rc==KC_EPIPE) break; } };
    auto cfn = [](void* p){ auto* ch = static_cast<IChannel<int>*>(p); int v=0; int got=0; for(;;){ int rc=ch->recv(v,-1); if(rc==KC_EPIPE) break; if(++got==1000) break; } };
    s.spawn_co(pfn, &c);
    s.spawn_co(cfn, &c);
    s.drain(2000); c.close(); s.drain(200); s.stop_and_join();
    std::puts("unlim ok");
  }

  // Rendezvous with pair<void*,size_t>
  {
    using P = std::pair<void*, size_t>;
    WorkStealingScheduler s(2);
    RendezvousChannel<P> c(&s);
    auto pfn = [](void* p){ auto* ch = static_cast<IChannel<P>*>(p); char buf[64]; P v{buf, sizeof(buf)}; for(int i=0;i<1000;i++){ int rc=ch->send(v,-1); if(rc==KC_EPIPE) break; } };
    auto cfn = [](void* p){ auto* ch = static_cast<IChannel<P>*>(p); P v{}; int got=0; for(;;){ int rc=ch->recv(v,-1); if(rc==KC_EPIPE) break; if(++got==1000) break; } };
    s.spawn_co(pfn, &c);
    s.spawn_co(cfn, &c);
    s.drain(2000); c.close(); s.drain(200); s.stop_and_join();
    std::puts("rv_pair ok");
  }
  return 0;
}
