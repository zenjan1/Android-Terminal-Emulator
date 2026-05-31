cmake_minimum_required(VERSION 3.22.1)
project("termexec")

add_library(jackpal-androidterm5 SHARED
    common.cpp
    termExec.cpp
    fileCompat.cpp
)

target_include_directories(jackpal-androidterm5 PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

target_link_libraries(jackpal-androidterm5
    android
    log
)
