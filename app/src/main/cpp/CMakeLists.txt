cmake_minimum_required(VERSION 3.22.1)
project("hypnovibe_audio")

add_library(hypnovibe_audio SHARED
    audio_engine.cpp
    jni_bridge.cpp
)

find_library(log log)

# Oboe via Prefab (provided by com.google.oboe:oboe AAR dependency)
find_package(oboe REQUIRED CONFIG)

target_link_libraries(hypnovibe_audio
    log
    oboe::oboe
)
