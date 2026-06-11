package com.desdelaire.vectorcount.msdk

import android.content.Context
import android.util.Log
import dji.v5.common.error.IDJIError
import dji.v5.common.register.DJISDKInitEvent
import dji.v5.manager.SDKManager
import dji.v5.manager.interfaces.SDKManagerCallback
import dji.v5.network.DJINetworkManager

object MsdkManager {

    private const val TAG = "MsdkManager"
    private const val PACKAGE_MISMATCH_ERROR = "REGISTRATION_RESULT_APP_KEY_AND_PACKAGE_NAME_MISMATCH"

    interface SDKRegistrationCallback {
        fun onRegisterSuccess()
        fun onRegisterFailure(error: IDJIError)
    }

    @Volatile
    private var sdkInitialized = false

    fun initSDK(context: Context, callback: SDKRegistrationCallback) {
        if (sdkInitialized) {
            if (SDKManager.getInstance().isRegistered) {
                callback.onRegisterSuccess()
            }
            return
        }

        SDKManager.getInstance().init(context.applicationContext, object : SDKManagerCallback {
            override fun onRegisterSuccess() {
                Log.i(TAG, "Registro DJI exitoso")
                callback.onRegisterSuccess()
            }

            override fun onRegisterFailure(error: IDJIError) {
                val errorText = error.toString()
                Log.e(TAG, "Registro DJI fallido: $errorText")
                if (errorText.contains(PACKAGE_MISMATCH_ERROR)) {
                    Log.w(
                        TAG,
                        "El Package Name no coincide con el registrado en el portal de DJI. " +
                            "Verifica que sea exactamente com.desdelaire.vectorcount"
                    )
                }
                callback.onRegisterFailure(error)
            }

            override fun onProductDisconnect(productId: Int) {
                Log.w(TAG, "Producto DJI desconectado: $productId")
            }

            override fun onProductConnect(productId: Int) {
                Log.i(TAG, "Producto DJI conectado: $productId")
            }

            override fun onProductChanged(productId: Int) {
                Log.i(TAG, "Producto DJI cambiado: $productId")
            }

            override fun onInitProcess(event: DJISDKInitEvent, totalProcess: Int) {
                Log.d(TAG, "Init DJI event=$event progress=$totalProcess")
                if (event == DJISDKInitEvent.INITIALIZE_COMPLETE) {
                    SDKManager.getInstance().registerApp()
                }
            }

            override fun onDatabaseDownloadProgress(current: Long, total: Long) {
                Log.d(TAG, "Descarga DB DJI: $current/$total")
            }
        })

        DJINetworkManager.getInstance().addNetworkStatusListener { isAvailable ->
            if (isAvailable && !SDKManager.getInstance().isRegistered) {
                Log.i(TAG, "Red disponible: reintentando registro DJI")
                SDKManager.getInstance().registerApp()
            }
        }

        sdkInitialized = true
    }

    fun destroy() {
        SDKManager.getInstance().destroy()
        sdkInitialized = false
    }
}
