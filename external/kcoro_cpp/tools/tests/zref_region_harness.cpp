#include "kcoro_cpp/zref.hpp"
#include <cstdio>

using namespace kcoro_cpp;

int main(){
  auto& reg = ZCopyRegistry::instance();
  char buf[1024];
  auto id = reg.region_register(buf, sizeof(buf));
  void* base=nullptr; size_t len=0; bool ok = reg.region_query(id, base, len);
  printf("reg ok=%d base=%p len=%zu\n", ok?1:0, base, len);
  reg.region_incref(id);
  reg.region_decref(id);
  printf("deregistering...\n");
  bool d = reg.region_deregister(id);
  printf("deregistered=%d\n", d?1:0);
  return 0;
}
