cmake_minimum_required(VERSION 3.22.1)

project("cinematic_engine")

add_library(
    cinematic_engine
    SHARED
    src/main/cpp/kalman_filter.cpp
    src/main/cpp/dolly_zoom_controller.cpp
    src/main/cpp/rack_focus_controller.cpp
    src/main/cpp/jni_bridge.cpp
)

find_library(
    log-lib
    log
)

find_library(
    android-lib
    android
)

target_include_directories(
    cinematic_engine
    PRIVATE
    src/main/cpp
)

target_link_libraries(
    cinematic_engine
    ${log-lib}
    ${android-lib}
)

target_link_options(
    cinematic_engine
    PRIVATE
    "-Wl,-z,max-page-size=16384"
)

