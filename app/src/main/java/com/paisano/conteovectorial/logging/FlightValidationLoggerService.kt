package com.desdelaire.vectorcount.logging

import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import java.io.File
import java.io.FileOutputStream

class FlightValidationLoggerService(private val context: Context) {
    fun persistCapture(bitmap: Bitmap, keypointsNormalized: FloatArray): Pair<File, File> {
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
            append(keypointsNormalized.getOrElse(0) { 0f }.normalizedValue()).append(' ')
            append(keypointsNormalized.getOrElse(1) { 0f }.normalizedValue()).append(' ')
            append(keypointsNormalized.getOrElse(2) { 0f }.normalizedValue()).append(' ')
            append(keypointsNormalized.getOrElse(3) { 0f }.normalizedValue())
            append('\n')
        })

        return jpgFile to txtFile
    }

    fun approve(txtFile: File) {
        val locked = File(txtFile.parentFile, txtFile.nameWithoutExtension + ".locked.txt")
        txtFile.renameTo(locked)
    }

    fun reject(txtFile: File) {
        txtFile.delete()
    }

    fun modify(txtFile: File, ax: Float, ay: Float, bx: Float, by: Float) {
        txtFile.writeText("$ax $ay $bx $by\n")
    }

    private fun Float.normalizedValue(): Float {
        return if (isFinite()) {
            coerceIn(0f, 1f)
        } else {
            0f
        }
    }
}
