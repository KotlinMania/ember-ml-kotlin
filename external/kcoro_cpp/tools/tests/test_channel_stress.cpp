// Stress tests for Channel<T> implementations: Rendezvous, Buffered, Unlimited
#include "kcoro_cpp/scheduler.hpp"
#include "kcoro_cpp/channel.hpp"
#include "kcoro_cpp/select_t.hpp"
#include <cstdio>
#include <vector>
#include <atomic>
#include <random>
#include <chrono>

using namespace kcoro_cpp;

struct Msg {
  uint32_t pid;
  uint32_t seq;
};

static void prod_int(void* arg){
  auto* tup = static_cast<std::tuple<IChannel<int>*, int, int>*>(arg);
  auto* ch = std::get<0>(*tup); int pid = std::get<1>(*tup); int rounds = std::get<2>(*tup);
  int v = pid;
  for (int i=0;i<rounds;i++) {
    int rc = ch->send(v, -1); if (rc==KC_EPIPE) break;
  }
}

static void cons_int(void* arg){
  auto* tup = static_cast<std::tuple<IChannel<int>*, std::atomic<long>*>*>(arg);
  auto* ch = std::get<0>(*tup); auto* cnt = std::get<1>(*tup);
  int v=0; for(;;){ int rc = ch->recv(v, -1); if (rc==KC_EPIPE) break; cnt->fetch_add(1, std::memory_order_relaxed); }
}

static void prod_pair(void* arg){
  using P = std::pair<void*,size_t>;
  auto* tup = static_cast<std::tuple<IChannel<P>*, int, int>*>(arg);
  auto* ch = std::get<0>(*tup); int pid = std::get<1>(*tup); int rounds = std::get<2>(*tup);
  std::vector<char> buf(512, (char)(0xA0 + (pid&0x1F)));
  P p{buf.data(), buf.size()};
  for (int i=0;i<rounds;i++) { int rc = ch->send(p, -1); if (rc==KC_EPIPE) break; }
}

static void cons_pair(void* arg){
  using P = std::pair<void*,size_t>;
  auto* tup = static_cast<std::tuple<IChannel<P>*, std::atomic<long>*>*>(arg);
  auto* ch = std::get<0>(*tup); auto* cnt = std::get<1>(*tup);
  P p{}; for(;;){ int rc = ch->recv(p, -1); if (rc==KC_EPIPE) break; cnt->fetch_add(1, std::memory_order_relaxed); }
}

static void test_buffered_int_mpmc(){
  std::puts("[test] buffered<int> 2x2");
  WorkStealingScheduler sched(2);
  BufferedChannel<int> ch(&sched, 128);
  std::atomic<long> consumed{0};
  int rounds = 100000;
  std::tuple<IChannel<int>*, int, int> p1{&ch, 1, rounds}, p2{&ch, 2, rounds};
  std::tuple<IChannel<int>*, std::atomic<long>*> c1{&ch, &consumed}, c2{&ch, &consumed};
  sched.spawn_co([](void* p){ prod_int(p); }, &p1);
  sched.spawn_co([](void* p){ prod_int(p); }, &p2);
  sched.spawn_co([](void* p){ cons_int(p); }, &c1);
  sched.spawn_co([](void* p){ cons_int(p); }, &c2);
  sched.drain(3000);
  ch.close(); sched.drain(500); sched.stop_and_join();
  std::printf("[ok] buffered<int> consumed=%ld\n", consumed.load());
}

static void test_buffered_pair_mpmc(){
  std::puts("[test] buffered<pair> 2x2");
  using P = std::pair<void*,size_t>;
  WorkStealingScheduler sched(2);
  BufferedChannel<P> ch(&sched, 128);
  std::atomic<long> consumed{0};
  int rounds = 20000;
  std::tuple<IChannel<P>*, int, int> p1{&ch, 1, rounds}, p2{&ch, 2, rounds};
  std::tuple<IChannel<P>*, std::atomic<long>*> c1{&ch, &consumed}, c2{&ch, &consumed};
  sched.spawn_co([](void* p){ prod_pair(p); }, &p1);
  sched.spawn_co([](void* p){ prod_pair(p); }, &p2);
  sched.spawn_co([](void* p){ cons_pair(p); }, &c1);
  sched.spawn_co([](void* p){ cons_pair(p); }, &c2);
  sched.drain(4000);
  ch.close(); sched.drain(500); sched.stop_and_join();
  std::printf("[ok] buffered<pair> consumed=%ld\n", consumed.load());
}

static void test_unlimited_pair_mpmc(){
  std::puts("[test] unlimited<pair> 2x2");
  using P = std::pair<void*,size_t>;
  WorkStealingScheduler sched(2);
  UnlimitedChannel<P> ch(&sched);
  std::atomic<long> consumed{0};
  int rounds = 100000;
  std::tuple<IChannel<P>*, int, int> p1{&ch, 1, rounds}, p2{&ch, 2, rounds};
  std::tuple<IChannel<P>*, std::atomic<long>*> c1{&ch, &consumed}, c2{&ch, &consumed};
  sched.spawn_co([](void* p){ prod_pair(p); }, &p1);
  sched.spawn_co([](void* p){ prod_pair(p); }, &p2);
  sched.spawn_co([](void* p){ cons_pair(p); }, &c1);
  sched.spawn_co([](void* p){ cons_pair(p); }, &c2);
  sched.drain(4000);
  ch.close(); sched.drain(500); sched.stop_and_join();
  std::printf("[ok] unlimited<pair> consumed=%ld\n", consumed.load());
}

static void test_rendezvous_pair_mpmc(){
  std::puts("[test] rendezvous<pair> 2x2");
  using P = std::pair<void*,size_t>;
  WorkStealingScheduler sched(2);
  RendezvousChannel<P> ch(&sched);
  std::atomic<long> consumed{0};
  int rounds = 50000;
  std::tuple<IChannel<P>*, int, int> p1{&ch, 1, rounds}, p2{&ch, 2, rounds};
  std::tuple<IChannel<P>*, std::atomic<long>*> c1{&ch, &consumed}, c2{&ch, &consumed};
  sched.spawn_co([](void* p){ prod_pair(p); }, &p1);
  sched.spawn_co([](void* p){ prod_pair(p); }, &p2);
  sched.spawn_co([](void* p){ cons_pair(p); }, &c1);
  sched.spawn_co([](void* p){ cons_pair(p); }, &c2);
  sched.drain(4000);
  ch.close(); sched.drain(500); sched.stop_and_join();
  std::printf("[ok] rendezvous<pair> consumed=%ld\n", consumed.load());
}

int main(){
  test_buffered_int_mpmc();
  test_buffered_pair_mpmc();
  test_unlimited_pair_mpmc();
  test_rendezvous_pair_mpmc();
  return 0;
}

