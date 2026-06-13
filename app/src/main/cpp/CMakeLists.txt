cmake_minimum_required(VERSION 3.22.1)
project("vectorcount")

# Find OpenCV
find_package(OpenCV REQUIRED)

# Find NCNN via Prefab
find_package(ncnn REQUIRED CONFIG)

# Compilar tu código nativo con el nombre correcto para Kotlin
add_library(vectorcount_vision SHARED native-lib.cpp)

# Enlazar todo (Tu código + NCNN + OpenCV + Logs de Android)
target_link_libraries(vectorcount_vision
    ncnn::ncnn
    ${OpenCV_LIBS}
    log
)
