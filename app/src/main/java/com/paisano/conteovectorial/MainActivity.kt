package com.paisano.conteovectorial

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Actividad principal mínima.
 *
 * En esta etapa sólo ofrece un contenedor de UI base para conectar con los módulos de
 * captura de stream, inferencia JNI y disparadores de guardado del data-logger.
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}
