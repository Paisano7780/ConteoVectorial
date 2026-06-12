package com.desdelaire.vectorcount

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.desdelaire.vectorcount.hitl.HitlValidationActivity
import com.desdelaire.vectorcount.msdk.MsdkManager
import com.google.android.material.button.MaterialButton
import dji.v5.common.error.IDJIError

class HomeActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var openFlightButton: MaterialButton
    private lateinit var openHitlButton: MaterialButton

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
        setContentView(R.layout.activity_home)

        statusText = findViewById(R.id.homeStatusText)
        openFlightButton = findViewById(R.id.btnOpenFlight)
        openHitlButton = findViewById(R.id.btnOpenHitl)

        openFlightButton.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
        openHitlButton.setOnClickListener {
            startActivity(Intent(this, HitlValidationActivity::class.java))
        }

        if (hasAllRuntimePermissions()) {
            registerSdk()
        } else {
            ActivityCompat.requestPermissions(this, runtimePermissions, REQUEST_PERMISSIONS_CODE)
        }
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
            statusText.text =
                "❌ Permisos no concedidos. Habilítalos en Configuración de la app."
        }
    }

    private fun hasAllRuntimePermissions(): Boolean {
        return runtimePermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun registerSdk() {
        statusText.text = "Registrando SDK..."
        MsdkManager.initSDK(this, object : MsdkManager.SDKRegistrationCallback {
            override fun onRegisterSuccess() {
                runOnUiThread {
                    statusText.text = "✅ SDK Registrado"
                    setNavigationEnabled(true)
                }
            }

            override fun onRegisterFailure(error: IDJIError) {
                runOnUiThread {
                    statusText.text = "❌ Error de Registro: $error"
                    setNavigationEnabled(false)
                }
            }
        })
    }

    private fun setNavigationEnabled(enabled: Boolean) {
        openFlightButton.isEnabled = enabled
        openHitlButton.isEnabled = enabled
    }

    companion object {
        private const val REQUEST_PERMISSIONS_CODE = 1001
    }
}
