package com.paisano.conteovectorial.nativebridge

object NativeVisionBridge {

    init {
        System.loadLibrary("native_vision_bridge")
    }

    external fun inferKeypointsFromNv21(
        frameNv21: ByteArray,
        offset: Int,
        length: Int,
        width: Int,
        height: Int
    ): FloatArray
}
