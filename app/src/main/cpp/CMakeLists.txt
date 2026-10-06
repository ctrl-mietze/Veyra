cmake_minimum_required(VERSION 3.22.1)
project(s25u_native LANGUAGES C)

add_library(s25u_native SHARED native_probe.c)
target_compile_options(s25u_native PRIVATE -O2 -Wall -Wextra -Werror)
target_link_libraries(s25u_native PRIVATE log)

