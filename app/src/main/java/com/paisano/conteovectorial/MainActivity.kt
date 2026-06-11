package com.desdelaire.vectorcount

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.desdelaire.vectorcount.msdk.MsdkManager
import com.desdelaire.vectorcount.video.DjiVideoStreamRepository
import com.desdelaire.vectorcount.vision.VisionProcessor
import dji.sdk.keyvalue.value.common.ComponentIndexType
import dji.v5.common.error.IDJIError

class MainActivity : AppCompatActivity(), SurfaceHolder.Callback {

    private lateinit var sdkStatusText: TextView

    private val MSG_PROCESS_FRAME = 1

    private lateinit var backgroundThread: HandlerThread
    private lateinit var backgroundHandler: Handler

    @Volatile
    private var latestFrameData: ByteArray? = null
    @Volatile
    private var latestOffset: Int = 0
    @Volatile
    private var latestLength: Int = 0
    @Volatile
    private var latestWidth: Int = 0
    @Volatile
    private var latestHeight: Int = 0

    private var preallocatedBitmap: Bitmap? = null
    private val visionProcessor = VisionProcessor()
    private val destRect = Rect()
    private var surfaceHolder: SurfaceHolder? = null
    private val videoStreamRepository = DjiVideoStreamRepository()

    @Volatile
    private var isSdkRegistered = false

    private val runtimePermissions: Array<String>
        get() {
            val permissions = mutableListOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions.remove(Manifest.permission.READ_EXTERNAL_STORAGE)
                permissions.remove(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            return permissions.toTypedArray()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        sdkStatusText = findViewById(R.id.sdkStatusText)

        val surfaceView = findViewById<SurfaceView>(R.id.cameraSurfaceView)
        surfaceView.holder.addCallback(this)

        backgroundThread = HandlerThread("VideoProcessingThread")
        backgroundThread.start()
        backgroundHandler = object : Handler(backgroundThread.looper) {
            override fun handleMessage(msg: Message) {
                if (msg.what == MSG_PROCESS_FRAME) {
                    val data = latestFrameData ?: return
                    val offset = latestOffset
                    val length = latestLength
                    val width = latestWidth
                    val height = latestHeight
                    processAndDrawFrame(data, offset, length, width, height)
                }
            }
        }

        if (hasAllRuntimePermissions()) {
            registerSdk()
        } else {
            ActivityCompat.requestPermissions(this, runtimePermissions, REQUEST_PERMISSIONS_CODE)
        }
    }

    override fun onResume() {
        super.onResume()
        if (isSdkRegistered) {
            subscribeToVideo()
        }
    }

    override fun onPause() {
        super.onPause()
        unsubscribeFromVideo()
    }

    override fun onDestroy() {
        super.onDestroy()
        unsubscribeFromVideo()
        backgroundThread.quitSafely()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceHolder = holder
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        surfaceHolder = holder
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceHolder = null
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (
            requestCode == REQUEST_PERMISSIONS_CODE &&
            grantResults.isNotEmpty() &&
            grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        ) {
            registerSdk()
        } else {
            sdkStatusText.text =
                "❌ Error de Registro: Permisos no concedidos. Habilítalos en Configuración de la app."
        }
    }

    private fun hasAllRuntimePermissions(): Boolean {
        return runtimePermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun registerSdk() {
        sdkStatusText.text = "Registrando SDK..."
        MsdkManager.initSDK(this, object : MsdkManager.SDKRegistrationCallback {
            override fun onRegisterSuccess() {
                runOnUiThread {
                    sdkStatusText.text = "✅ SDK Registrado Exitosamente"
                    isSdkRegistered = true
                    subscribeToVideo()
                }
            }

            override fun onRegisterFailure(error: IDJIError) {
                runOnUiThread {
                    sdkStatusText.text = "❌ Error de Registro: ${error}"
                }
            }
        })
    }

    private fun subscribeToVideo() {
        videoStreamRepository.subscribeToNv21Frames(ComponentIndexType.LEFT_OR_MAIN) { frameData, offset, length, width, height ->
            latestFrameData = frameData
            latestOffset = offset
            latestLength = length
            latestWidth = width
            latestHeight = height

            backgroundHandler.removeMessages(MSG_PROCESS_FRAME)
            backgroundHandler.sendEmptyMessage(MSG_PROCESS_FRAME)
        }
    }

    private fun unsubscribeFromVideo() {
        videoStreamRepository.unsubscribe()
    }

    private fun processAndDrawFrame(
        frameData: ByteArray,
        offset: Int,
        length: Int,
        width: Int,
        height: Int
    ) {
        var bitmap = preallocatedBitmap
        if (bitmap == null || bitmap.width != width || bitmap.height != height) {
            bitmap?.recycle()
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            preallocatedBitmap = bitmap
        }

        val success = visionProcessor.processFrameToBitmap(
            frameData,
            offset,
            length,
            width,
            height,
            bitmap
        )

        if (success) {
            val holder = surfaceHolder ?: return
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) {
                    destRect.set(0, 0, canvas.width, canvas.height)
                    canvas.drawBitmap(bitmap, null, destRect, null)
                }
            } catch (e: Exception) {
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas)
                    } catch (e: Exception) {
                    }
                }
            }
        }
    }

    companion object {
        private const val REQUEST_PERMISSIONS_CODE = 1001
    }
}
