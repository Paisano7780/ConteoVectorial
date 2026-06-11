package com.desdelaire.vectorcount.vision

class VisionProcessor {
    init {
        System.loadLibrary("vectorcount_vision")
    }

    external fun processFrame(yuvData: ByteArray, width: Int, height: Int): ByteArray
}
