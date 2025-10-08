cmake_minimum_required(VERSION 3.18)
project(kcoro_cpp_zref_bench C CXX)
add_executable(kcoro_cpp_zref_bench main.cpp)
target_include_directories(kcoro_cpp_zref_bench PRIVATE ${CMAKE_CURRENT_LIST_DIR}/../../include)
target_link_libraries(kcoro_cpp_zref_bench PRIVATE kcoro_cpp pthread)
install(TARGETS kcoro_cpp_zref_bench RUNTIME DESTINATION bin)

