#include "kcoro_cpp/scheduler.hpp"
#include "kcoro_cpp/zref.hpp"
#include <cstdio>
#include <vector>

using namespace kcoro_cpp;

static void producer(void* arg){
  auto* ch = static_cast<IChannel<ZDesc>*>(arg);
  std::vector<char> buf(1<<16, 0xCD);
  ZDesc d{buf.data(), buf.size(), 0, 0, 0};
  for(int i=0;i<100000;i++){ if (ch->send(d,-1)==KC_EPIPE) break; }
}
static void consumer(void* arg){
  auto* ch = static_cast<IChannel<ZDesc>*>(arg);
  ZDesc d{};
  for(;;){ int rc = ch->recv(d,-1); if (rc==KC_EPIPE) break; }
}

int main(){
  WorkStealingScheduler sched;
  ZRefBufferedChannel ch(&sched, 1024);
  sched.spawn([](void* p){ producer(p); }, &ch);
  sched.spawn([](void* p){ consumer(p); }, &ch);
  sched.drain(5000);
  ch.close();
  sched.drain(500);
  printf("zref buffered bench done\n");
  sched.stop_and_join();
  return 0;
}
