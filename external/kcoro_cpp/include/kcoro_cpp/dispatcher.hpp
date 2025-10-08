#pragma once

#include "kcoro_cpp/scheduler.hpp"

namespace kcoro_cpp {

WorkStealingScheduler* dispatcher_default();
WorkStealingScheduler* dispatcher_io();
WorkStealingScheduler* dispatcher_new(int workers);

} // namespace kcoro_cpp
