cmake_minimum_required(VERSION 3.22.1)
project("vectorcount")

# --- OpenCV (prefab desde la dependencia gradle org.opencv:opencv:4.10.0) ---
find_package(OpenCV REQUIRED)

# --- NCNN: prebuilt estatico local (sin Vulkan), resuelto por ABI ---
# Trae lib/cmake/ncnn/ncnnConfig.cmake + libncnn.a en app/src/main/cpp/ncnn/<ABI>/
set(ncnn_DIR "${CMAKE_CURRENT_SOURCE_DIR}/ncnn/${ANDROID_ABI}/lib/cmake/ncnn")
find_package(ncnn REQUIRED)

# --- Libreria nativa de vision (nombre que cargan VisionProcessor/NativeVisionBridge) ---
add_library(vectorcount_vision SHARED native-lib.cpp)

target_link_libraries(vectorcount_vision
    ncnn
    OpenCV::opencv_java4
    jnigraphics
    android
    log
)
