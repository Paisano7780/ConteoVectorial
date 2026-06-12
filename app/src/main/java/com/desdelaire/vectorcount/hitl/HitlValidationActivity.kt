package com.desdelaire.vectorcount.hitl

import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Typeface
import android.os.Bundle
import android.os.Environment
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.desdelaire.vectorcount.R
import com.desdelaire.vectorcount.logging.FlightValidationLoggerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File

/**
 * Galería de auditoría HITL. Muestra todas las capturas .jpg de
 * VectorCount_Dataset en una cuadrícula; cada miniatura indica si ya fue
 * aprobada (existe .locked.txt → check verde) o está pendiente (cruz roja).
 * Al tocar una miniatura se abre un editor a pantalla completa con
 * KeypointCanvasView para ajustar los keypoints y aprobar (renombrar a
 * .locked.txt). Sin swipe ni movimiento a carpetas valid/discarded.
 */
class HitlValidationActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var statusText: TextView
    private val items = mutableListOf<GalleryItem>()
    private val adapter = GalleryAdapter()
    private val loggerService by lazy { FlightValidationLoggerService(this) }
    private var columnWidthPx = 0

    private data class GalleryItem(val jpg: File, val txt: File?, val locked: Boolean)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hitl_validation)
        recyclerView = findViewById(R.id.galleryRecyclerView)
        statusText = findViewById(R.id.statusText)
        val columns = 3
        columnWidthPx = (resources.displayMetrics.widthPixels - dp(8)) / columns
        recyclerView.layoutManager = GridLayoutManager(this, columns)
        recyclerView.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        loadDatasetAsync()
    }

    private fun datasetDir(): File {
        val baseDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: filesDir
        return File(baseDir, "VectorCount_Dataset")
    }

    private fun loadDatasetAsync() {
        GlobalScope.launch(Dispatchers.IO) {
            val dir = datasetDir()
            val jpgFiles = dir.listFiles { f -> f.name.endsWith(".jpg") }
                ?.sortedBy { it.name } ?: emptyList()
            val loaded = jpgFiles.map { jpg ->
                val base = jpg.nameWithoutExtension
                val lockedTxt = File(dir, "$base.locked.txt")
                val plainTxt = File(dir, "$base.txt")
                val txt = when {
                    lockedTxt.exists() -> lockedTxt
                    plainTxt.exists() -> plainTxt
                    else -> null
                }
                GalleryItem(jpg, txt, lockedTxt.exists())
            }
            runOnUiThread {
                items.clear()
                items.addAll(loaded)
                adapter.notifyDataSetChanged()
                if (items.isEmpty()) {
                    statusText.text = "No hay imágenes en VectorCount_Dataset"
                    statusText.visibility = View.VISIBLE
                } else {
                    statusText.visibility = View.GONE
                }
            }
        }
    }

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
    ).toInt()

    // ----------------------- Galería -----------------------

    private inner class GalleryAdapter : RecyclerView.Adapter<GalleryViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GalleryViewHolder {
            val root = FrameLayout(this@HitlValidationActivity).apply {
                layoutParams = RecyclerView.LayoutParams(columnWidthPx, columnWidthPx)
            }
            val image = ImageView(this@HitlValidationActivity).apply {
                layoutParams = FrameLayout.LayoutParams(MATCH, MATCH).apply {
                    setMargins(dp(2), dp(2), dp(2), dp(2))
                }
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(Color.DKGRAY)
            }
            val badge = TextView(this@HitlValidationActivity).apply {
                layoutParams = FrameLayout.LayoutParams(WRAP, WRAP).apply {
                    gravity = Gravity.TOP or Gravity.END
                    setMargins(0, dp(6), dp(6), 0)
                }
                textSize = 22f
                setTypeface(typeface, Typeface.BOLD)
                setShadowLayer(6f, 0f, 0f, Color.BLACK)
            }
            root.addView(image)
            root.addView(badge)
            return GalleryViewHolder(root, image, badge)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: GalleryViewHolder, position: Int) {
            val item = items[position]
            holder.badge.text = if (item.locked) "✓" else "✗"
            holder.badge.setTextColor(
                if (item.locked) Color.parseColor("#4CAF50") else Color.parseColor("#E53935")
            )
            holder.image.setImageBitmap(decodeThumbnail(item.jpg, columnWidthPx))
            holder.itemView.setOnClickListener { openDetail(item) }
        }
    }

    private inner class GalleryViewHolder(
        root: View,
        val image: ImageView,
        val badge: TextView
    ) : RecyclerView.ViewHolder(root)

    private fun decodeThumbnail(file: File, targetPx: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            var sample = 1
            val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
            while (targetPx > 0 && maxDim / sample > targetPx * 2) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeFile(file.absolutePath, opts)
        } catch (e: Exception) {
            null
        }
    }

    // ----------------------- Editor de detalle -----------------------

    private fun openDetail(item: GalleryItem) {
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val imageView = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        val canvas = KeypointCanvasView(this).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
        }
        val saveButton = Button(this).apply {
            text = "Guardar"
            layoutParams = FrameLayout.LayoutParams(WRAP, WRAP).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                setMargins(0, 0, dp(16), dp(16))
            }
        }
        val closeButton = Button(this).apply {
            text = "Cerrar"
            layoutParams = FrameLayout.LayoutParams(WRAP, WRAP).apply {
                gravity = Gravity.BOTTOM or Gravity.START
                setMargins(dp(16), 0, 0, dp(16))
            }
        }
        root.addView(imageView)
        root.addView(canvas)
        root.addView(saveButton)
        root.addView(closeButton)
        dialog.setContentView(root)

        // Coordenadas normalizadas vigentes. Si el .txt no existe o llega en 0f,
        // se usan posiciones por defecto visibles (25% y 75% del ancho).
        val normalized = floatArrayOf(0.25f, 0.5f, 0.75f, 0.5f)
        val bitmap = BitmapFactory.decodeFile(item.jpg.absolutePath)
        imageView.setImageBitmap(bitmap)
        item.txt?.let { readKeypointsOrNull(it) }?.copyInto(normalized)

        canvas.onPointsUpdated = { ax, ay, bx, by ->
            normalized[0] = ax
            normalized[1] = ay
            normalized[2] = bx
            normalized[3] = by
        }

        if (bitmap != null) {
            imageView.post {
                val vw = imageView.width
                val vh = imageView.height
                if (vw > 0 && vh > 0) {
                    val scale = minOf(vw.toFloat() / bitmap.width, vh.toFloat() / bitmap.height)
                    val offsetX = (vw - bitmap.width * scale) / 2
                    val offsetY = (vh - bitmap.height * scale) / 2
                    val ax = normalized[0] * bitmap.width * scale + offsetX
                    val ay = normalized[1] * bitmap.height * scale + offsetY
                    val bx = normalized[2] * bitmap.width * scale + offsetX
                    val by = normalized[3] * bitmap.height * scale + offsetY
                    val matrix = Matrix().apply {
                        setScale(scale, scale)
                        postTranslate(offsetX, offsetY)
                    }
                    canvas.setImageMatrix(matrix)
                    canvas.setKeypoints(ax, ay, bx, by, bitmap.width, bitmap.height)
                }
            }
        }

        saveButton.setOnClickListener {
            GlobalScope.launch(Dispatchers.IO) {
                val dir = datasetDir()
                val base = item.jpg.nameWithoutExtension
                val targetTxt = item.txt ?: File(dir, "$base.txt")
                loggerService.modify(targetTxt, normalized[0], normalized[1], normalized[2], normalized[3])
                if (!targetTxt.name.endsWith(".locked.txt")) {
                    loggerService.approve(targetTxt)
                }
                runOnUiThread {
                    bitmap?.recycle()
                    dialog.dismiss()
                    loadDatasetAsync()
                }
            }
        }
        closeButton.setOnClickListener {
            bitmap?.recycle()
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun readKeypointsOrNull(txtFile: File): FloatArray? {
        return try {
            val parts = txtFile.readText().trim().split(" ")
            if (parts.size >= 4) {
                val arr = floatArrayOf(
                    parts[0].toFloat(), parts[1].toFloat(),
                    parts[2].toFloat(), parts[3].toFloat()
                )
                if (arr.all { it == 0f }) null else arr
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
