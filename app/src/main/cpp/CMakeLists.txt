cmake_minimum_required(VERSION 3.22.1)
project(native_vision_bridge)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(native_vision_bridge SHARED native_vision_bridge.cpp)

find_library(log-lib log)

# OpenCV Android SDK:
# Defina OpenCV_DIR desde gradle/cmake cache apuntando al SDK Android de OpenCV
# para activar linkeo real en etapa de integración.
# find_package(OpenCV REQUIRED)
# target_link_libraries(native_vision_bridge ${OpenCV_LIBS})

# NCNN/TFLite (opcional de etapa):
# Inyectar include/library dirs del framework elegido (NCNN o TFLite C++).
# target_include_directories(native_vision_bridge PRIVATE ${NCNN_INCLUDE_DIRS})
# target_link_libraries(native_vision_bridge ${NCNN_LIBS})

target_link_libraries(native_vision_bridge ${log-lib})
