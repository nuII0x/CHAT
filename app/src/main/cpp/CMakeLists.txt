cmake_minimum_required(VERSION 3.22.1)

project(null_ai LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

set(LLAMA_CPP_DIR ${CMAKE_CURRENT_LIST_DIR}/../../../../external/llama.cpp)
set(VULKAN_HEADERS_DIR ${CMAKE_CURRENT_LIST_DIR}/../../../../external/Vulkan-Headers)

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_CPU_KLEIDIAI OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN ON CACHE BOOL "" FORCE)
set(GGML_VULKAN_CHECK_RESULTS OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN_DEBUG OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN_MEMORY_DEBUG OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN_SHADER_DEBUG_INFO OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN_VALIDATE OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN_RUN_TESTS OFF CACHE BOOL "" FORCE)

if(EXISTS "${VULKAN_HEADERS_DIR}/include/vulkan/vulkan.hpp")
    include_directories(SYSTEM "${VULKAN_HEADERS_DIR}/include")
endif()

set(NULLAI_NDK_SPIRV_HEADERS_DIR "")
if(DEFINED ANDROID_NDK)
    set(NULLAI_NDK_SPIRV_HEADERS_DIR "${ANDROID_NDK}/sources/third_party/shaderc/third_party/spirv-tools/external/spirv-headers")
elseif(DEFINED CMAKE_ANDROID_NDK)
    set(NULLAI_NDK_SPIRV_HEADERS_DIR "${CMAKE_ANDROID_NDK}/sources/third_party/shaderc/third_party/spirv-tools/external/spirv-headers")
endif()

if(EXISTS "${NULLAI_NDK_SPIRV_HEADERS_DIR}/include/spirv/unified1/spirv.hpp")
    include_directories(SYSTEM "${NULLAI_NDK_SPIRV_HEADERS_DIR}/include")
    set(SPIRV-Headers_DIR "${CMAKE_CURRENT_LIST_DIR}/cmake/spirv-headers" CACHE PATH "" FORCE)
endif()

if(DEFINED ANDROID_NDK AND EXISTS "${ANDROID_NDK}/shader-tools/linux-x86_64/glslc")
    set(Vulkan_GLSLC_EXECUTABLE "${ANDROID_NDK}/shader-tools/linux-x86_64/glslc" CACHE FILEPATH "" FORCE)
elseif(DEFINED CMAKE_ANDROID_NDK AND EXISTS "${CMAKE_ANDROID_NDK}/shader-tools/linux-x86_64/glslc")
    set(Vulkan_GLSLC_EXECUTABLE "${CMAKE_ANDROID_NDK}/shader-tools/linux-x86_64/glslc" CACHE FILEPATH "" FORCE)
endif()

find_program(NULLAI_HOST_C_COMPILER NAMES gcc clang NO_CMAKE_FIND_ROOT_PATH)
find_program(NULLAI_HOST_CXX_COMPILER NAMES g++ clang++ NO_CMAKE_FIND_ROOT_PATH)
if(NULLAI_HOST_C_COMPILER AND NULLAI_HOST_CXX_COMPILER AND CMAKE_MAKE_PROGRAM)
    set(NULLAI_VULKAN_SHADERS_HOST_TOOLCHAIN "${CMAKE_CURRENT_BINARY_DIR}/nullai-vulkan-shaders-host-toolchain.cmake")
    configure_file(
        "${CMAKE_CURRENT_LIST_DIR}/cmake/vulkan-shaders-host-toolchain.cmake.in"
        "${NULLAI_VULKAN_SHADERS_HOST_TOOLCHAIN}"
        @ONLY
    )
    set(GGML_VULKAN_SHADERS_GEN_TOOLCHAIN "${NULLAI_VULKAN_SHADERS_HOST_TOOLCHAIN}" CACHE FILEPATH "" FORCE)
endif()

if(DEFINED ANDROID_ABI)
    if(ANDROID_ABI STREQUAL "arm64-v8a")
        set(GGML_SYSTEM_ARCH "ARM" CACHE STRING "" FORCE)
    else()
        message(FATAL_ERROR "Unsupported ABI for Null IA: ${ANDROID_ABI}. Use arm64-v8a.")
    endif()
endif()

add_subdirectory(${LLAMA_CPP_DIR} llama-build)

add_library(llama_jni SHARED
    llama_jni.cpp
    vulkan_android_compat.cpp
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
