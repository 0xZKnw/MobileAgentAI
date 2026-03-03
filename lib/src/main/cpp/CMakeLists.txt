cmake_minimum_required(VERSION 3.22.1)
project(llama_android)

# llama.cpp submodule root (relative to this CMakeLists.txt: go up 4 levels to project root, then into llama.cpp)
set(LLAMA_ROOT ${CMAKE_SOURCE_DIR}/../../../../llama.cpp)

# Per-ABI KleidiAI + OpenMP (arm64-v8a only)
if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(GGML_CPU_KLEIDIAI ON)
    set(GGML_OPENMP ON)
endif()

# Include llama.cpp build system
add_subdirectory(${LLAMA_ROOT} llama_build)

# JNI bridge library
add_library(llama_jni SHARED ai_chat.cpp)

target_include_directories(llama_jni PRIVATE
    ${LLAMA_ROOT}/include
    ${LLAMA_ROOT}/ggml/include
    ${LLAMA_ROOT}/common
    ${CMAKE_SOURCE_DIR}
)

target_link_libraries(llama_jni
    llama
    ggml
    android
    log
)

target_compile_options(llama_jni PRIVATE
    -O3
    -ffast-math
    -funroll-loops
)
