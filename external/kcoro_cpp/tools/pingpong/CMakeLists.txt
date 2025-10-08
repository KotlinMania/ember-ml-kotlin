cmake_minimum_required(VERSION 3.18)
project(kcoro_cpp_pingpong C CXX)
add_executable(kcoro_cpp_pingpong main.cpp)
target_include_directories(kcoro_cpp_pingpong PRIVATE ${CMAKE_CURRENT_LIST_DIR}/../../include)
target_link_libraries(kcoro_cpp_pingpong PRIVATE kcoro_cpp pthread)
install(TARGETS kcoro_cpp_pingpong RUNTIME DESTINATION bin)

