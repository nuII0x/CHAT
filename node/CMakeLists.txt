cmake_minimum_required(VERSION 3.20)

project(nullchat_node LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

include(FetchContent)

FetchContent_Declare(
    cpp_httplib
    GIT_REPOSITORY https://github.com/yhirose/cpp-httplib.git
    GIT_TAG v0.18.3
)

FetchContent_Declare(
    nlohmann_json
    GIT_REPOSITORY https://github.com/nlohmann/json.git
    GIT_TAG v3.11.3
)

FetchContent_MakeAvailable(cpp_httplib nlohmann_json)

find_package(OpenSSL REQUIRED)
find_package(Threads REQUIRED)

add_executable(node
    src/main.cpp
)

target_link_libraries(node
    PRIVATE
        httplib::httplib
        nlohmann_json::nlohmann_json
        OpenSSL::Crypto
        Threads::Threads
)
