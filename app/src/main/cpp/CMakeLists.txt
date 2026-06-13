cmake_minimum_required(VERSION 3.22.1)
project("vectorcount")

# 1. Rutas a las cabeceras (Los archivos que acabas de subir)
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/ncnn/include)
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/opencv/include)

# 2. Importar librería NCNN precompilada
add_library(ncnn STATIC IMPORTED)
set_target_properties(ncnn PROPERTIES 
    IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/ncnn/lib/libncnn.a
)

# 3. Importar librería OpenCV precompilada
add_library(opencv_java4 SHARED IMPORTED)
set_target_properties(opencv_java4 PROPERTIES 
    IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/opencv/libs/${ANDROID_ABI}/libopencv_java4.so
)

# 4. Compilar tu código nativo con el nombre correcto para Kotlin
add_library(vectorcount_vision SHARED native-lib.cpp)

# 5. Enlazar todo (Tu código + NCNN + OpenCV + Logs de Android)
target_link_libraries(vectorcount_vision
    ncnn
    opencv_java4
    log
)