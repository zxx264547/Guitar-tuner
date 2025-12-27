cmake_minimum_required(VERSION 3.22.1)

project(tuner)

add_library(tuner SHARED
        tuner_jni.cpp)

find_package(oboe REQUIRED CONFIG)

target_compile_features(tuner PRIVATE cxx_std_17)
target_link_libraries(tuner
        oboe::oboe
        log)
