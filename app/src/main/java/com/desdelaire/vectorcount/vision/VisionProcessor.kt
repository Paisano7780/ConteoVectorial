package com.desdelaire.vectorcount.vision

import android.graphics.Bitmap

class VisionProcessor {
    init {
        System.loadLibrary("vectorcount_vision")
    }

    external fun processFrame(yuvData: ByteArray, width: Int, height: Int): ByteArray

    external fun processFrameToBitmap(
        yuvData: ByteArray,
        offset: Int,
        length: Int,
        width: Int,
        height: Int,
        outBitmap: Bitmap
    ): Boolean

    external fun detectKeypoints(assetManager: android.content.res.AssetManager, bitmap: android.graphics.Bitmap): FloatArray
}
