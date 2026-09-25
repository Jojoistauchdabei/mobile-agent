cmake_minimum_required(VERSION 3.22)

project(mobileagent_whisper CXX)

set(WHISPER_DIR "" CACHE PATH "Path to a whisper.cpp checkout")
if(NOT EXISTS "${WHISPER_DIR}/CMakeLists.txt")
    message(FATAL_ERROR "WHISPER_DIR does not point to whisper.cpp")
endif()

set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

add_subdirectory("${WHISPER_DIR}" "${CMAKE_BINARY_DIR}/whisper" EXCLUDE_FROM_ALL)

add_library(mobileagent-whisper SHARED whisper_jni.cpp)
target_compile_features(mobileagent-whisper PRIVATE cxx_std_17)
target_include_directories(mobileagent-whisper PRIVATE
    "${WHISPER_DIR}/include"
    "${WHISPER_DIR}/ggml/include"
)
target_link_libraries(mobileagent-whisper PRIVATE whisper android log)
set_target_properties(mobileagent-whisper PROPERTIES OUTPUT_NAME "mobileagent-whisper")
