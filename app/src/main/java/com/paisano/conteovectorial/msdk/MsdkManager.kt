package com.paisano.conteovectorial.msdk

import android.content.Context
import android.util.Log
import dji.v5.common.error.IDJIError
import dji.v5.common.register.DJISDKInitEvent
import dji.v5.manager.SDKManager
import dji.v5.manager.interfaces.SDKManagerCallback
import dji.v5.network.DJINetworkManager

/**
 * Facade de inicialización/registro para DJI Mobile SDK V5.
 *
 * Responsabilidades de arquitectura:
 * 1) Inicializar MSDK una sola vez y desacoplar la UI de callbacks de bajo nivel.
 * 2) Reportar explícitamente éxito/fallo de registro contra servidores DJI.
 * 3) Reintentar registro si vuelve la conectividad y el SDK aún no está registrado.
 */
object MsdkManager {

    private const val TAG = "MsdkManager"
    @Volatile
    private var sdkInitialized = false

    fun initialize(context: Context) {
        if (sdkInitialized) return

        SDKManager.getInstance().init(context.applicationContext, object : SDKManagerCallback {
            override fun onRegisterSuccess() {
                Log.i(TAG, "Registro DJI exitoso")
            }

            override fun onRegisterFailure(error: IDJIError) {
                Log.e(TAG, "Registro DJI fallido: $error")
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
