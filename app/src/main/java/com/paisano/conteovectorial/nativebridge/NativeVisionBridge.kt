package com.desdelaire.vectorcount.nativebridge

/**
 * Puente JNI para visión computacional.
 *
 * Diseño de memoria para ARM:
 * - Recibe ByteArray + ventana (offset/length) del frame NV21.
 * - JNI utiliza GetPrimitiveArrayCritical para minimizar copias entre JVM y C++.
 * - Delega en C++ la creación/gestión de cv::Mat y la liberación explícita de recursos nativos.
 */
object NativeVisionBridge {

    init {
        System.loadLibrary("native_vision_bridge")
    }

    /**
     * Envía un frame NV21 al código nativo y recibe keypoints [ax, ay, bx, by] normalizados [0..1].
     */
    external fun inferKeypointsFromNv21(
        frameNv21: ByteArray,
        offset: Int,
        length: Int,
        width: Int,
        height: Int
    ): FloatArray
}
