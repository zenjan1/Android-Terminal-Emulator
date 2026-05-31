cmake_minimum_required(VERSION 3.22.1)
project("termexec")

add_library(jackpal-termexec2 SHARED
    process.cpp
)

target_include_directories(jackpal-termexec2 PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

target_link_libraries(jackpal-termexec2
    android
    log
)
