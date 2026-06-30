set(SPIRV-Headers_FOUND TRUE)

set(_nullai_spirv_headers_dir "")
if(DEFINED ANDROID_NDK)
    set(_nullai_spirv_headers_dir "${ANDROID_NDK}/sources/third_party/shaderc/third_party/spirv-tools/external/spirv-headers/include")
elseif(DEFINED CMAKE_ANDROID_NDK)
    set(_nullai_spirv_headers_dir "${CMAKE_ANDROID_NDK}/sources/third_party/shaderc/third_party/spirv-tools/external/spirv-headers/include")
endif()

if(NOT TARGET SPIRV-Headers::SPIRV-Headers)
    add_library(SPIRV-Headers::SPIRV-Headers INTERFACE IMPORTED)
endif()

if(EXISTS "${_nullai_spirv_headers_dir}/spirv/unified1/spirv.hpp")
    set_target_properties(
        SPIRV-Headers::SPIRV-Headers
        PROPERTIES
        INTERFACE_INCLUDE_DIRECTORIES "${_nullai_spirv_headers_dir}"
    )
endif()
