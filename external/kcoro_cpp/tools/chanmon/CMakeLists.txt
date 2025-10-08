cmake_minimum_required(VERSION 3.18)
project(kcoro_cpp_chanmon C CXX)

add_executable(kcoro_cpp_chanmon main.cpp)
target_include_directories(kcoro_cpp_chanmon PRIVATE ${CMAKE_CURRENT_LIST_DIR}/../../include)
target_link_libraries(kcoro_cpp_chanmon PRIVATE kcoro_cpp pthread)

install(TARGETS kcoro_cpp_chanmon RUNTIME DESTINATION bin)

