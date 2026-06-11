package com.paisano.conteovectorial.hitl

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.os.Environment
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GestureDetectorCompat
import com.paisano.conteovectorial.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File

class HitlValidationActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var keypointCanvas: KeypointCanvasView
    private lateinit var statusText: TextView
    private lateinit var gestureDetector: GestureDetectorCompat
    private val dataset = mutableListOf<Pair<File, File>>()
    private var currentIndex = 0
    private var currentBitmap: Bitmap? = null
    private var currentTxtFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hitl_validation)
        imageView = findViewById(R.id.imageView)
        keypointCanvas = findViewById(R.id.keypointCanvas)
        statusText = findViewById(R.id.statusText)
        gestureDetector = GestureDetectorCompat(this, SwipeGestureListener())
        keypointCanvas.onPointsUpdated = { ax, ay, bx, by ->
            if (currentTxtFile != null) {
                GlobalScope.launch(Dispatchers.IO) {
                    updateKeypointFile(currentTxtFile!!, ax, ay, bx, by)
                }
            }
        }
        loadDatasetAsync()
    }

    private fun loadDatasetAsync() {
        GlobalScope.launch(Dispatchers.IO) {
            dataset.clear()
            currentIndex = 0
            val baseDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: filesDir
            val datasetDir = File(baseDir, "VectorCount_Dataset")
            if (!datasetDir.exists() || datasetDir.listFiles() == null) {
                runOnUiThread {
                    statusText.text = "No hay datos pendientes de auditoría"
                    statusText.visibility = android.view.View.VISIBLE
                    imageView.isEnabled = false
                    keypointCanvas.isEnabled = false
                }
                return@launch
            }
            val jpgFiles = datasetDir.listFiles { f -> f.name.endsWith(".jpg") }?.associateBy {
                it.nameWithoutExtension
            } ?: emptyMap()
            val txtFiles = datasetDir.listFiles { f -> f.name.endsWith(".txt") && !f.name.endsWith(".locked.txt") }
                ?.associateBy { it.nameWithoutExtension } ?: emptyMap()
            for ((key, txtFile) in txtFiles) {
                val jpgFile = jpgFiles[key]
                if (jpgFile != null) {
                    dataset.add(jpgFile to txtFile)
                }
            }
            dataset.sortBy { it.first.nameWithoutExtension }
            runOnUiThread {
                if (dataset.isEmpty()) {
                    statusText.text = "No hay datos pendientes de auditoría"
                    statusText.visibility = android.view.View.VISIBLE
                    imageView.isEnabled = false
                    keypointCanvas.isEnabled = false
                } else {
                    statusText.visibility = android.view.View.GONE
                    imageView.isEnabled = true
                    keypointCanvas.isEnabled = true
                    loadCurrent()
                }
            }
        }
    }

    private fun loadCurrent() {
        if (currentIndex < 0 || currentIndex >= dataset.size) {
            statusText.text = "No hay datos pendientes de auditoría"
            statusText.visibility = android.view.View.VISIBLE
            return
        }
        val (jpgFile, txtFile) = dataset[currentIndex]
        currentTxtFile = txtFile
        GlobalScope.launch(Dispatchers.IO) {
            val bitmap = BitmapFactory.decodeFile(jpgFile.absolutePath)
            val keypoints = readKeypoints(txtFile)
            runOnUiThread {
                currentBitmap?.recycle()
                currentBitmap = bitmap
                imageView.setImageBitmap(bitmap)
                if (bitmap != null && keypoints != null) {
                    imageView.post {
                        val screenWidth = imageView.width
                        val screenHeight = imageView.height
                        if (screenWidth > 0 && screenHeight > 0) {
                            val scaleX = screenWidth.toFloat() / bitmap.width
                            val scaleY = screenHeight.toFloat() / bitmap.height
                            val scale = minOf(scaleX, scaleY)
                            val offsetX = (screenWidth - bitmap.width * scale) / 2
                            val offsetY = (screenHeight - bitmap.height * scale) / 2
                            val pointAX = keypoints[0] * bitmap.width * scale + offsetX
                            val pointAY = keypoints[1] * bitmap.height * scale + offsetY
                            val pointBX = keypoints[2] * bitmap.width * scale + offsetX
                            val pointBY = keypoints[3] * bitmap.height * scale + offsetY
                            val matrix = Matrix().apply {
                                setScale(scale, scale)
                                postTranslate(offsetX, offsetY)
                            }
                            keypointCanvas.setKeypoints(pointAX, pointAY, pointBX, pointBY, bitmap.width, bitmap.height)
                            keypointCanvas.setImageMatrix(matrix)
                        }
                    }
                }
            }
        }
    }

    private fun readKeypoints(txtFile: File): FloatArray? {
        return try {
            val line = txtFile.readText().trim()
            val parts = line.split(" ")
            if (parts.size >= 4) {
                floatArrayOf(parts[0].toFloat(), parts[1].toFloat(), parts[2].toFloat(), parts[3].toFloat())
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun updateKeypointFile(txtFile: File, ax: Float, ay: Float, bx: Float, by: Float) {
        try {
            txtFile.writeText("$ax $ay $bx $by\n")
        } catch (e: Exception) {
        }
    }

    private fun approveCurrentItem() {
        if (currentIndex >= dataset.size) return
        val (jpgFile, txtFile) = dataset[currentIndex]
        GlobalScope.launch(Dispatchers.IO) {
            val validDir = File(txtFile.parentFile, "valid").apply { mkdirs() }
            val targetTxtFile = File(validDir, txtFile.name)
            val targetJpgFile = File(validDir, jpgFile.name)
            try {
                jpgFile.renameTo(targetJpgFile)
                txtFile.renameTo(targetTxtFile)
            } catch (e: Exception) {
            }
            currentIndex++
            runOnUiThread {
                if (currentIndex < dataset.size) {
                    loadCurrent()
                } else {
                    statusText.text = "No hay datos pendientes de auditoría"
                    statusText.visibility = android.view.View.VISIBLE
                }
            }
        }
    }

    private fun rejectCurrentItem() {
        if (currentIndex >= dataset.size) return
        val (jpgFile, txtFile) = dataset[currentIndex]
        GlobalScope.launch(Dispatchers.IO) {
            val discardedDir = File(txtFile.parentFile, "discarded").apply { mkdirs() }
            val targetTxtFile = File(discardedDir, txtFile.name)
            val targetJpgFile = File(discardedDir, jpgFile.name)
            try {
                jpgFile.renameTo(targetJpgFile)
                txtFile.renameTo(targetTxtFile)
            } catch (e: Exception) {
            }
            currentIndex++
            runOnUiThread {
                if (currentIndex < dataset.size) {
                    loadCurrent()
                } else {
                    statusText.text = "No hay datos pendientes de auditoría"
                    statusText.visibility = android.view.View.VISIBLE
                }
            }
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev != null) {
            gestureDetector.onTouchEvent(ev)
        }
        return super.dispatchTouchEvent(ev)
    }

    private inner class SwipeGestureListener : GestureDetector.SimpleOnGestureListener() {
        private val swipeThreshold = 100
        private val swipeVelocityThreshold = 100

        override fun onFling(e1: MotionEvent?, e2: MotionEvent?, velocityX: Float, velocityY: Float): Boolean {
            if (e1 == null || e2 == null) return false
            val diffX = e2.x - e1.x
            val diffY = e2.y - e1.y
            return if (kotlin.math.abs(diffX) > kotlin.math.abs(diffY)) {
                if (kotlin.math.abs(diffX) > swipeThreshold && kotlin.math.abs(velocityX) > swipeVelocityThreshold) {
                    if (diffX > 0) {
                        approveCurrentItem()
                        true
                    } else {
                        rejectCurrentItem()
                        true
                    }
                } else {
                    false
                }
            } else {
                false
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        currentBitmap?.recycle()
    }
}





