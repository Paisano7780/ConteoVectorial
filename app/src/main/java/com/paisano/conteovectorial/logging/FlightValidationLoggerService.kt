package com.paisano.conteovectorial.logging

import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream

class FlightValidationLoggerService(private val context: Context) {
    companion object {
        private const val TAG = "FlightValidationLogger"
        private const val EXPECTED_KEYPOINT_COUNT = 4
    }

    fun persistCapture(bitmap: Bitmap, keypointsNormalized: FloatArray): Boolean {
        return try {
            val timestamp = System.currentTimeMillis()
            val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir
            val outDir = File(
                baseDir,
                "VectorCount_Dataset"
            ).apply { mkdirs() }

            val jpgFile = File(outDir, "frame_$timestamp.jpg")
            val txtFile = File(outDir, "frame_$timestamp.txt")

            FileOutputStream(jpgFile).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            }

            txtFile.writeText(buildString {
                for (index in 0 until EXPECTED_KEYPOINT_COUNT) {
                    if (index > 0) {
                        append(' ')
                    }
                    append(keypointsNormalized.getOrElse(index) { 0f })
                }
                append('\n')
            })

            true
        } catch (e: Exception) {
            Log.e(TAG, "Capture persistence failed", e)
            false
        }
    }

    fun approve(txtFile: File) {
        val locked = File(txtFile.parentFile, txtFile.nameWithoutExtension + ".locked.txt")
        if (!txtFile.renameTo(locked)) {
            Log.e(TAG, "Failed to rename approved file")
        }
    }

    fun reject(txtFile: File) {
        txtFile.delete()
    }

    fun modify(txtFile: File, ax: Float, ay: Float, bx: Float, by: Float) {
        txtFile.writeText("$ax $ay $bx $by\n")
    }
}
