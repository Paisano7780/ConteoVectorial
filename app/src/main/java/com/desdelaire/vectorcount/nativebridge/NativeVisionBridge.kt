package com.desdelaire.vectorcount.nativebridge

object NativeVisionBridge {

    init {
        System.loadLibrary("vectorcount_vision")
    }

    external fun inferKeypointsFromNv21(
        frameNv21: ByteArray,
        offset: Int,
        length: Int,
        width: Int,
        height: Int
    ): FloatArray
}
