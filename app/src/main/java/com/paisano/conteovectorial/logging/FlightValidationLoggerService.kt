package com.desdelaire.vectorcount.logging

import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import java.io.File
import java.io.FileOutputStream

/**
 * Servicio de Data-Logger HITL (stub de arquitectura).
 *
 * Flujo objetivo de etapa 1:
 * 1) Gatillo por botón o timer.
 * 2) Persistencia atómica de imagen JPG + TXT homónimo con keypoints YOLO.
 * 3) Integración posterior con flujo de revisión en HitlValidationActivity.
 */
class FlightValidationLoggerService(private val context: Context) {
    companion object {
        private const val EXPECTED_KEYPOINT_VECTOR_SIZE = 4
    }

    fun persistCapture(
        nv21Frame: ByteArray,
        width: Int,
        height: Int,
        keypointsNormalized: FloatArray
    ): Pair<File, File> {
        val timestamp = System.currentTimeMillis()
        val baseName = "captura_$timestamp"
        val outDir = File(context.filesDir, "captures").apply { mkdirs() }

        val jpgFile = File(outDir, "$baseName.jpg")
        val txtFile = File(outDir, "$baseName.txt")

        YuvImage(nv21Frame, ImageFormat.NV21, width, height, null).compressToJpeg(
            Rect(0, 0, width, height),
            92,
            FileOutputStream(jpgFile)
        )

        // Formato simplificado YOLO-pose para la etapa de validación:
        // ax ay bx by (coordenadas normalizadas)
        txtFile.writeText(
            buildString {
                append(keypointsNormalized.getOrElse(0) { 0f }).append(' ')
                append(keypointsNormalized.getOrElse(1) { 0f }).append(' ')
                append(keypointsNormalized.getOrElse(2) { 0f }).append(' ')
                append(keypointsNormalized.getOrElse(EXPECTED_KEYPOINT_VECTOR_SIZE - 1) { 0f }).append('\n')
            }
        )

        return jpgFile to txtFile
    }

    fun approve(txtFile: File) {
        // Etapa 1: aprobación lógica = bloqueo por convención de nombre.
        // Etapa siguiente: mover a estado inmutable o directorio de aprobados.
        val locked = File(txtFile.parentFile, txtFile.nameWithoutExtension + ".locked.txt")
        txtFile.renameTo(locked)
    }

    fun reject(txtFile: File) {
        txtFile.delete()
    }

    fun modify(txtFile: File, ax: Float, ay: Float, bx: Float, by: Float) {
        txtFile.writeText("$ax $ay $bx $by\n")
    }
}
