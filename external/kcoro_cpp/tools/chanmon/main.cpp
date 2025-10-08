#include "kcoro_cpp/scheduler.hpp"
#include "kcoro_cpp/channel.hpp"
#include "kcoro_cpp/select_t.hpp"
#include <cstdio>
#include <cstring>
#include <string>
#include <vector>
#include <atomic>

using namespace kcoro_cpp;

struct Args {
  std::string kind{"buffered"};
  size_t capacity{1024};
  size_t msg_size{512};
  int producers{1};
  int consumers{1};
  int seconds{5};
};

static void usage() {
  std::fprintf(stderr, "kcoro_cpp_chanmon --kind [buffered|rendezvous|unlimited|conflated] --cap N --size BYTES --prod P --cons C --sec S\n");
}

static bool parse(int argc, char** argv, Args& a) {
  for (int i=1;i<argc;i++) {
    if (!std::strcmp(argv[i], "--kind") && i+1<argc) a.kind=argv[++i];
    else if (!std::strcmp(argv[i], "--cap") && i+1<argc) a.capacity=std::stoul(argv[++i]);
    else if (!std::strcmp(argv[i], "--size") && i+1<argc) a.msg_size=std::stoul(argv[++i]);
    else if (!std::strcmp(argv[i], "--prod") && i+1<argc) a.producers=std::stoi(argv[++i]);
    else if (!std::strcmp(argv[i], "--cons") && i+1<argc) a.consumers=std::stoi(argv[++i]);
    else if (!std::strcmp(argv[i], "--sec") && i+1<argc) a.seconds=std::stoi(argv[++i]);
    else { usage(); return false; }
  }
  return true;
}

static void producer_fn(void* arg) {
  auto* tup = static_cast<std::tuple<IChannel<std::pair<void*,size_t>>*, size_t>*>(arg);
  auto* ch = std::get<0>(*tup);
  size_t sz = std::get<1>(*tup);
  std::vector<char> buf(sz, 0xAB);
  std::pair<void*,size_t> msg{buf.data(), sz};
  std::printf("[producer] start sz=%zu\n", sz);
  int cnt=0;
  for(;;) {
    int rc = ch->send(msg, -1);
    if (rc==KC_EPIPE) break;
    if (++cnt<=3) std::printf("[producer] send ok #%d\n", cnt);
  }
}

static void consumer_fn(void* arg) {
  auto* ch = static_cast<IChannel<std::pair<void*,size_t>>*>(arg);
  std::pair<void*,size_t> m;
  std::printf("[consumer] start\n");
  int cnt=0;
  for(;;) {
    int rc = ch->recv(m, -1);
    if (rc==KC_EPIPE) break;
    if (++cnt<=3) std::printf("[consumer] recv ok #%d len=%zu\n", cnt, m.second);
  }
}

static void chanmon_fn(void* arg) {
  auto* pipe = static_cast<IChannel<ChannelMetricsEvent>*>(arg);
  ChannelMetricsEvent ev{};
  ChannelSnapshot prev{};
  for(;;) {
    int rc = pipe->recv(ev, -1);
    if (rc==KC_EPIPE) break;
    ChannelSnapshot curr{};
    curr.total_sends = ev.total_sends;
    curr.total_recvs = ev.total_recvs;
    curr.total_bytes_sent = ev.total_bytes_sent;
    curr.total_bytes_recv = ev.total_bytes_recv;
    curr.last_op_time_ns = ev.emit_time_ns;
    if (prev.last_op_time_ns != 0) {
      double sps=0, rps=0, dt=0; compute_rates(prev, curr, sps, rps, dt);
      double gbps = (ev.delta_bytes_sent + ev.delta_bytes_recv) / dt / 1e9 * 8.0;
      std::printf("t=%.3fs sends=%lu recvs=%lu sps=%.1f rps=%.1f Gbps=%.2f\n", ev.emit_time_ns/1e9, ev.total_sends, ev.total_recvs, sps, rps, gbps);
    }
    prev = curr;
  }
}

int main(int argc, char** argv) {
  Args a; if (!parse(argc, argv, a)) return 1;
  WorkStealingScheduler sched;

  BufferedChannel<ChannelMetricsEvent> metrics_pipe(&sched, 1024);

  std::unique_ptr<IChannel<std::pair<void*,size_t>>> data;
  if (a.kind == "buffered") data.reset(new BufferedChannel<std::pair<void*,size_t>>(&sched, a.capacity));
  else if (a.kind == "rendezvous") data.reset(new RendezvousChannel<std::pair<void*,size_t>>(&sched));
  else if (a.kind == "unlimited") data.reset(new UnlimitedChannel<std::pair<void*,size_t>>(&sched));
  else if (a.kind == "conflated") data.reset(new ConflatedChannel<std::pair<void*,size_t>>(&sched));
  else { std::fprintf(stderr, "unknown kind\n"); return 1; }

  // Enable metrics on channel, if supported
  // Metrics from rendezvous can be very chatty and interact with blocking paths; enable on buffered by default.
  // if (auto* r = dynamic_cast<RendezvousChannel<std::pair<void*,size_t>>*>(data.get())) r->set_metrics_pipe(&metrics_pipe, {});
  if (auto* b = dynamic_cast<BufferedChannel<std::pair<void*,size_t>>*>(data.get())) b->set_metrics_pipe(&metrics_pipe, {});
  // if (auto* u = dynamic_cast<UnlimitedChannel<std::pair<void*,size_t>>*>(data.get())) u->set_metrics_pipe(&metrics_pipe, {});
  if (auto* c = dynamic_cast<ConflatedChannel<std::pair<void*,size_t>>*>(data.get())) c->set_metrics_pipe(&metrics_pipe, {});

  // Chan monitor (blocking recv) must run in a coroutine
  sched.spawn_co([](void* p){ chanmon_fn(p); }, &metrics_pipe);

  // Start producers
  std::vector<std::tuple<IChannel<std::pair<void*,size_t>>*, size_t>> prod_args;
  prod_args.reserve(a.producers);
  for (int i=0;i<a.producers;i++) {
    prod_args.emplace_back(data.get(), a.msg_size);
    // Producers perform blocking sends; run them as coroutines
    sched.spawn_co([](void* p){ producer_fn(p); }, &prod_args.back());
  }
  // Start consumers
  for (int i=0;i<a.consumers;i++) sched.spawn_co([](void* p){ consumer_fn(p); }, data.get());

  // Run for duration
  for (int i=0;i<a.seconds*10;i++) std::this_thread::sleep_for(std::chrono::milliseconds(100));
  data->close(); metrics_pipe.close();
  sched.drain(1000);
  return 0;
}
