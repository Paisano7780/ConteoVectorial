package com.desdelaire.vectorcount.video

import dji.sdk.keyvalue.value.common.ComponentIndexType
import dji.v5.manager.datacenter.MediaDataCenter
import dji.v5.manager.interfaces.ICameraStreamManager

/**
 * Stub de conexión de video MSDK V5.
 *
 * Este repositorio centraliza la suscripción al stream de cámara RGB/YUV del dron mediante
 * ICameraStreamManager para desacoplar la adquisición de frames de la capa de UI.
 */
class DjiVideoStreamRepository {

    private var listener: ICameraStreamManager.CameraFrameListener? = null

    fun subscribeToNv21Frames(
        cameraIndex: ComponentIndexType,
        onFrame: (frameData: ByteArray, offset: Int, length: Int, width: Int, height: Int) -> Unit
    ) {
        val frameListener = object : ICameraStreamManager.CameraFrameListener {
            override fun onFrame(
                frameData: ByteArray,
                offset: Int,
                length: Int,
                width: Int,
                height: Int,
                format: ICameraStreamManager.FrameFormat
            ) {
                onFrame(frameData, offset, length, width, height)
            }
        }

        listener = frameListener
        MediaDataCenter.getInstance().cameraStreamManager.addFrameListener(
            cameraIndex,
            ICameraStreamManager.FrameFormat.NV21,
            frameListener
        )
    }

    fun unsubscribe() {
        listener?.let { MediaDataCenter.getInstance().cameraStreamManager.removeFrameListener(it) }
        listener = null
    }
}
