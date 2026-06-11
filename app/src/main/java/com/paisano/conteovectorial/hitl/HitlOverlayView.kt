package com.desdelaire.vectorcount.hitl

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * Vista táctil para editar keypoints/vector de validación.
 *
 * Stub de arquitectura:
 * - Dibuja dos puntos (A y B) y su vector.
 * - Expone callback cuando el usuario arrastra puntos para reescribir coordenadas en TXT.
 */
class HitlOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        style = Paint.Style.FILL
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GREEN
        strokeWidth = 4f
    }

    var pointAX = 120f
    var pointAY = 120f
    var pointBX = 280f
    var pointBY = 220f

    var onPointsChanged: ((ax: Float, ay: Float, bx: Float, by: Float) -> Unit)? = null

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawLine(pointAX, pointAY, pointBX, pointBY, linePaint)
        canvas.drawCircle(pointAX, pointAY, 18f, pointPaint)
        canvas.drawCircle(pointBX, pointBY, 18f, pointPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_MOVE) {
            // Etapa 1 (simple): mover punto B con arrastre para habilitar edición del vector.
            pointBX = event.x
            pointBY = event.y
            onPointsChanged?.invoke(pointAX, pointAY, pointBX, pointBY)
            invalidate()
            return true
        }
        return super.onTouchEvent(event)
    }
}
