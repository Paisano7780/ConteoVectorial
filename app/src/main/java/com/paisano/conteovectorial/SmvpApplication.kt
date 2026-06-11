package com.paisano.conteovectorial

import android.app.Application
import com.paisano.conteovectorial.msdk.MsdkManager

/**
 * Punto de entrada de la app SMVP.
 *
 * Mantiene una inicialización temprana y explícita del MSDK V5 para que los callbacks
 * de estado de registro/conexión estén disponibles desde el arranque de la aplicación.
 */
class SmvpApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MsdkManager.initialize(this)
    }
}
