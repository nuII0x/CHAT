cmake_minimum_required(VERSION 3.22.1)

project(null_ai LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

set(LLAMA_CPP_DIR ${CMAKE_CURRENT_LIST_DIR}/../../../../external/llama.cpp)

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_UI OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_BLAS OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(GGML_CPU_HBM OFF CACHE BOOL "" FORCE)
set(GGML_CPU_KLEIDIAI OFF CACHE BOOL "" FORCE)
set(GGML_CPU_ALL_VARIANTS OFF CACHE BOOL "" FORCE)
set(GGML_CUDA OFF CACHE BOOL "" FORCE)
set(GGML_HIP OFF CACHE BOOL "" FORCE)
set(GGML_METAL OFF CACHE BOOL "" FORCE)
set(GGML_MUSA OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN OFF CACHE BOOL "" FORCE)
set(GGML_RPC OFF CACHE BOOL "" FORCE)
set(GGML_SYCL OFF CACHE BOOL "" FORCE)
set(GGML_WEBGPU OFF CACHE BOOL "" FORCE)
set(GGML_OPENCL OFF CACHE BOOL "" FORCE)
set(GGML_OPENVINO OFF CACHE BOOL "" FORCE)

set(NULLAI_NATIVE_SUPPORTED OFF)
if(DEFINED ANDROID_ABI)
    if(ANDROID_ABI STREQUAL "arm64-v8a")
        set(GGML_SYSTEM_ARCH "ARM" CACHE STRING "" FORCE)
        set(NULLAI_NATIVE_SUPPORTED ON)
    endif()
else()
    set(NULLAI_NATIVE_SUPPORTED ON)
endif()

if(ANDROID)
    set_property(GLOBAL PROPERTY JOB_POOLS nullai_native_compile=1 nullai_native_link=1)
    set(CMAKE_JOB_POOL_COMPILE nullai_native_compile)
    set(CMAKE_JOB_POOL_LINK nullai_native_link)
endif()

if(NULLAI_NATIVE_SUPPORTED)
    add_subdirectory(${LLAMA_CPP_DIR} llama-build)

    add_library(llama_jni SHARED
        llama_jni.cpp
    )

    target_include_directories(llama_jni PRIVATE
        ${LLAMA_CPP_DIR}/include
        ${LLAMA_CPP_DIR}/ggml/include
    )

    target_link_libraries(llama_jni
        llama
        android
        log
    )
else()
    message(STATUS "Null IA native runtime disabled for ${ANDROID_ABI}; building compatibility stub.")

    add_library(llama_jni SHARED
        llama_jni_unavailable.cpp
    )

    target_link_libraries(llama_jni
        android
        log
    )
endif()
