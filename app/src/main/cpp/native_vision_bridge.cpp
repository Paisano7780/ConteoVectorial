package com.desdelaire.vectorcount.nativebridge

import android.content.res.AssetManager

class NativeVisionBridge {
    
    // Este bloque carga la librería de C++ en la RAM apenas se instancia la clase
    companion object {
        init {
            System.loadLibrary("native-lib")
        }
    }

    // Funciones "external" que le dicen a Kotlin: 
    // "No busques el código acá, la lógica de esto vive en C++"
    external fun initModel(assetManager: AssetManager, paramPath: String, binPath: String): Boolean
    
    external fun processFrame(yuvData: ByteArray, width: Int, height: Int): FloatArray
}
