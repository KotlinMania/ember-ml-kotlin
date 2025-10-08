#include "kcoro_cpp/scheduler.hpp"
#include "kcoro_cpp/channel.hpp"
#include <cstdio>
#include <chrono>

using namespace kcoro_cpp;

struct Msg { int n; };

static void pinger(void* arg){
  auto* pair = static_cast<std::pair<IChannel<Msg>*,IChannel<Msg>*>*>(arg);
  auto* tx = pair->first; auto* rx = pair->second;
  Msg m{0};
  for(int i=0;i<100000;i++){ tx->send(m,-1); rx->recv(m,-1); }
}

static void ponger(void* arg){
  auto* pair = static_cast<std::pair<IChannel<Msg>*,IChannel<Msg>*>*>(arg);
  auto* tx = pair->second; auto* rx = pair->first;
  Msg m{};
  for(;;){ if (rx->recv(m,-1)==KC_EPIPE) break; tx->send(m,-1); }
}

int main(){
  WorkStealingScheduler sched(1);
  RendezvousChannel<Msg> a(&sched), b(&sched);
  std::pair<IChannel<Msg>*,IChannel<Msg>*> pair{&a,&b};
  auto t0 = std::chrono::high_resolution_clock::now();
  sched.spawn([](void* p){ pinger(p); }, &pair);
  sched.spawn([](void* p){ ponger(p); }, &pair);
  sched.drain(5000);
  a.close(); b.close();
  sched.drain(500);
  auto t1 = std::chrono::high_resolution_clock::now();
  double us = std::chrono::duration<double,std::micro>(t1-t0).count();
  printf("pingpong 100k roundtrips: %.1fus total\n", us);
  sched.stop_and_join();
  return 0;
}
