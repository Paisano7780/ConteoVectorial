package com.paisano.conteovectorial.hitl

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.paisano.conteovectorial.R

/**
 * UI secundaria para validación Human-in-the-Loop.
 *
 * Responsabilidades previstas:
 * - Cargar capturas persistidas por el data-logger.
 * - Superponer y editar vectores/keypoints con interacción táctil.
 * - Ejecutar acciones Aprobar/Rechazar/Modificar sobre el TXT asociado.
 */
class HitlValidationActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hitl_validation)
    }
}
