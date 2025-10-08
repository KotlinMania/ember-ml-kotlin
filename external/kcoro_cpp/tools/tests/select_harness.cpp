#include "kcoro_cpp/scheduler.hpp"
#include "kcoro_cpp/channel.hpp"
#include "kcoro_cpp/select_t.hpp"
#include "kcoro_cpp/cancel.hpp"
#include <cstdio>

using namespace kcoro_cpp;

static void prod_after(void* arg){
  auto* p = static_cast<std::pair<WorkStealingScheduler*, IChannel<int>*>*>(arg);
  // light spin ~0.5-1ms to let select register; avoids timer thread
  uint64_t t0 = kcoro_cpp::platform::now_ns();
  while (kcoro_cpp::platform::now_ns() - t0 < 800000ULL) {}
  int x=42; p->second->send(x,-1);
}

static void harness(void* arg){
  auto& env = *static_cast<std::tuple<WorkStealingScheduler*, BufferedChannel<int>*, BufferedChannel<int>*>*>(arg);
  auto* sched = std::get<0>(env); auto* a = std::get<1>(env); auto* b = std::get<2>(env);
  int outA=-1, outB=-1, idx=-1;

  // Case 1
  sched->spawn([](void* arg){ prod_after(arg); }, new std::pair<WorkStealingScheduler*, IChannel<int>*>(sched, a));
  { SelectT<int> sel1(nullptr, SelectPolicy::FirstWins); sel1.add_recv(a, &outA); sel1.add_recv(b, &outB); int rc = sel1.wait(200, &idx); printf("select case1 rc=%d idx=%d outA=%d outB=%d\n", rc, idx, outA, outB); }

  // Case 2: timeout
  { SelectT<int> sel2; sel2.add_recv(b, &outB); int rc = sel2.wait(20, &idx); printf("select timeout rc=%d idx=%d\n", rc, idx); }

  // Case 3: cancellation
  { CancellationToken tok; SelectT<int> sel3(&tok); sel3.add_recv(b, &outB); sched->spawn([](void* t){ static_cast<CancellationToken*>(t)->trigger(); }, &tok); int rc = sel3.wait(100, &idx); printf("select canceled rc=%d idx=%d\n", rc, idx); }
}

int main(){
  WorkStealingScheduler sched(1);
  RendezvousChannel<int> a(&sched), b(&sched);
  std::tuple<WorkStealingScheduler*, RendezvousChannel<int>*, RendezvousChannel<int>*> env{&sched, &a, &b};
  sched.spawn([](void* p){ harness(p); }, &env);
  sched.drain(1000);
  a.close(); b.close();
  sched.drain(100);
  sched.stop_and_join();
  return 0;
}
