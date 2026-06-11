cmake_minimum_required(VERSION 3.22.1)
project(native_vision_bridge)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if(NOT OpenCV_DIR AND OPENCV_DIR)
    set(OpenCV_DIR ${OPENCV_DIR})
endif()
find_package(OpenCV REQUIRED)

add_library(native_vision_bridge SHARED native_vision_bridge.cpp)
add_library(vectorcount_vision SHARED native-lib.cpp)

target_link_libraries(native_vision_bridge log)
target_link_libraries(vectorcount_vision OpenCV::opencv_java4 log jnigraphics)

