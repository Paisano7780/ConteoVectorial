package com.desdelaire.vectorcount

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.desdelaire.vectorcount.msdk.MsdkManager
import com.desdelaire.vectorcount.logging.FlightValidationLoggerService
import com.desdelaire.vectorcount.telemetry.TelemetryManager
import com.desdelaire.vectorcount.video.DjiVideoStreamRepository
import com.desdelaire.vectorcount.vision.VisionProcessor
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import dji.sdk.keyvalue.value.common.ComponentIndexType
import dji.sdk.keyvalue.value.common.Attitude
import dji.sdk.keyvalue.value.common.LocationCoordinate3D
import dji.sdk.keyvalue.value.flightcontroller.FlightMode
import dji.v5.common.error.IDJIError

class MainActivity : AppCompatActivity(), SurfaceHolder.Callback {

    private lateinit var sdkStatusText: TextView
    private lateinit var flightModeText: TextView
    private lateinit var batteryText: TextView
    private lateinit var rcSignalText: TextView
    private lateinit var satellitesText: TextView
    private lateinit var altDistText: TextView
    private lateinit var compassImage: ImageView
    private val telemetryManager = TelemetryManager()
    @Volatile
    private var homeLocation: LocationCoordinate3D? = null

    private val MSG_PROCESS_FRAME = 1

    private lateinit var backgroundThread: HandlerThread
    private lateinit var backgroundHandler: Handler

    private val frameLock = Any()
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
    @Volatile
    private var latestKeypoints: FloatArray? = null

    private val bitmapLock = Any()
    private var preallocatedBitmap: Bitmap? = null
    private val visionProcessor = VisionProcessor()
    private val destRect = Rect()
    private var surfaceHolder: SurfaceHolder? = null
    private val videoStreamRepository = DjiVideoStreamRepository()
    private val flightValidationLoggerService by lazy { FlightValidationLoggerService(this) }
    private val captureScope = kotlinx.coroutines.CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var isSdkRegistered = false

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_PERMISSIONS_CODE = 1001
        private const val THREAD_SHUTDOWN_TIMEOUT_MS = 500L
        private const val CAPTURE_FEEDBACK_ALPHA = 0.35f
        private const val CAPTURE_FEEDBACK_DURATION_MS = 100L
    }

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
        flightModeText = findViewById(R.id.flightModeText)
        batteryText = findViewById(R.id.batteryText)
        rcSignalText = findViewById(R.id.rcSignalText)
        satellitesText = findViewById(R.id.satellitesText)
        altDistText = findViewById(R.id.altDistText)
        compassImage = findViewById(R.id.compassImage)
        val captureFab = findViewById<FloatingActionButton>(R.id.captureFab)
        captureFab.setOnClickListener {
            captureFab.alpha = CAPTURE_FEEDBACK_ALPHA
            captureFab.postDelayed({ captureFab.alpha = 1f }, CAPTURE_FEEDBACK_DURATION_MS)
            captureScope.launch {
                val snapshot = synchronized(bitmapLock) {
                    val bitmap = preallocatedBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                    val keypoints = latestKeypoints?.copyOf()
                    if (bitmap != null && keypoints != null) {
                        bitmap to keypoints
                    } else {
                        null
                    }
                }
                if (snapshot != null) {
                    val capturedBitmap = snapshot.first
                    try {
                        val saved = flightValidationLoggerService.persistCapture(capturedBitmap, snapshot.second)
                        runOnUiThread {
                            Toast.makeText(
                                this@MainActivity,
                                if (saved) "Captura guardada" else "Error al guardar captura",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } finally {
                        capturedBitmap.recycle()
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            "Sin datos disponibles",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

        val surfaceView = findViewById<SurfaceView>(R.id.cameraSurfaceView)
        surfaceView.holder.addCallback(this)

        backgroundThread = HandlerThread("VideoProcessingThread")
        backgroundThread.start()
        backgroundHandler = object : Handler(backgroundThread.looper) {
            override fun handleMessage(msg: Message) {
                if (msg.what == MSG_PROCESS_FRAME) {
                    var data: ByteArray?
                    var offset: Int
                    var length: Int
                    var width: Int
                    var height: Int
                    synchronized(frameLock) {
                        data = latestFrameData
                        offset = latestOffset
                        length = latestLength
                        width = latestWidth
                        height = latestHeight
                    }
                    if (data != null) {
                        processAndDrawFrame(data!!, offset, length, width, height)
                    }
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
            startTelemetry()
        }
    }

    override fun onPause() {
        super.onPause()
        unsubscribeFromVideo()
        telemetryManager.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        unsubscribeFromVideo()
        captureScope.cancel()
        backgroundThread.quitSafely()
        try {
            backgroundThread.join(THREAD_SHUTDOWN_TIMEOUT_MS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        synchronized(bitmapLock) {
            preallocatedBitmap?.recycle()
            preallocatedBitmap = null
        }
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
                    sdkStatusText.text = getString(R.string.flight_system_status)
                    isSdkRegistered = true
                    subscribeToVideo()
                    startTelemetry()
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
            synchronized(frameLock) {
                latestFrameData = frameData
                latestOffset = offset
                latestLength = length
                latestWidth = width
                latestHeight = height
            }

            backgroundHandler.removeMessages(MSG_PROCESS_FRAME)
            backgroundHandler.sendEmptyMessage(MSG_PROCESS_FRAME)
        }
    }

    private fun unsubscribeFromVideo() {
        videoStreamRepository.unsubscribe()
    }

    private fun startTelemetry() {
        telemetryManager.start(object : TelemetryManager.Callbacks {
            override fun onBatteryPercent(percent: Int) {
                runOnUiThread { batteryText.text = "$percent%" }
            }

            override fun onFlightMode(flightMode: FlightMode?) {
                runOnUiThread { flightModeText.text = shortFlightMode(flightMode) }
            }

            override fun onSatelliteCount(count: Int) {
                runOnUiThread { satellitesText.text = "GPS $count" }
            }

            override fun onRcSignalQuality(quality: Int) {
                runOnUiThread { rcSignalText.text = "RC $quality" }
            }

            override fun onAircraftLocation(location: LocationCoordinate3D?) {
                if (location == null) return
                if (homeLocation == null && location.latitude != null && location.longitude != null) {
                    homeLocation = location
                }
                runOnUiThread { altDistText.text = formatAltitudeDistance(location) }
            }

            override fun onAircraftAttitude(attitude: Attitude?) {
                val yaw = attitude?.yaw ?: return
                runOnUiThread { compassImage.rotation = yaw.toFloat() }
            }
        })
    }

    private fun shortFlightMode(flightMode: FlightMode?): String {
        if (flightMode == null) return getString(R.string.flight_mode_placeholder)
        val name = flightMode.name
        return when {
            name.contains("SPORT") -> "S"
            name.contains("CINE") || name.contains("TRIPOD") -> "C"
            name.contains("NORMAL") || name.contains("NOVICE") ||
                name.contains("GPS") || name.contains("ATTI") -> "N"
            else -> name
        }
    }

    private fun formatAltitudeDistance(location: LocationCoordinate3D): String {
        val altitude = location.altitude ?: 0.0
        val home = homeLocation
        val distance = if (
            home?.latitude != null && home.longitude != null &&
            location.latitude != null && location.longitude != null
        ) {
            val results = FloatArray(1)
            Location.distanceBetween(
                home.latitude, home.longitude,
                location.latitude, location.longitude,
                results
            )
            results[0].toDouble()
        } else {
            0.0
        }
        return "ALT %.1f m\nDIST %.1f m".format(altitude, distance)
    }

    private fun processAndDrawFrame(
        frameData: ByteArray,
        offset: Int,
        length: Int,
        width: Int,
        height: Int
    ) {
        synchronized(bitmapLock) {
            val bitmap = preallocatedBitmap ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                preallocatedBitmap = it
            }
            val currentBitmap = if (bitmap.width != width || bitmap.height != height) {
                bitmap.recycle()
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                    preallocatedBitmap = it
                }
            } else {
                bitmap
            }

            val success = visionProcessor.processFrameToBitmap(
                frameData,
                offset,
                length,
                width,
                height,
                currentBitmap
            )

            if (success) {
                val detectedKeypoints = try {
                    visionProcessor.detectKeypoints(assets, currentBitmap)
                } catch (e: Exception) {
                    Log.e(TAG, "Keypoint detection failed", e)
                    null
                }
                if (detectedKeypoints != null) {
                    synchronized(bitmapLock) {
                        latestKeypoints = detectedKeypoints
                    }
                }
                val holder = surfaceHolder ?: return
                var canvas: Canvas? = null
                try {
                    canvas = holder.lockCanvas()
                    if (canvas != null) {
                        destRect.set(0, 0, canvas.width, canvas.height)
                        canvas.drawBitmap(currentBitmap, null, destRect, null)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Frame rendering failed", e)
                } finally {
                    if (canvas != null) {
                        try {
                            holder.unlockCanvasAndPost(canvas)
                        } catch (e: Exception) {
                            Log.e(TAG, "Canvas post failed", e)
                        }
                    }
                }
            }
        }
    }

}
