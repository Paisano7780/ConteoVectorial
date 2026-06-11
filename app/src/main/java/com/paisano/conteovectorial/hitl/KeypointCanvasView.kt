package com.paisano.conteovectorial.hitl

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class KeypointCanvasView @JvmOverloads constructor(
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

    private var pointAX = 0f
    private var pointAY = 0f
    private var pointBX = 0f
    private var pointBY = 0f
    private var bitmapWidth = 0
    private var bitmapHeight = 0
    private var imageMatrix: Matrix? = null
    private var draggedPoint = -1
    private val toleranceRadius = 50f

    var onPointsUpdated: ((ax: Float, ay: Float, bx: Float, by: Float) -> Unit)? = null

    fun setKeypoints(ax: Float, ay: Float, bx: Float, by: Float, bitmapW: Int, bitmapH: Int) {
        this.pointAX = ax
        this.pointAY = ay
        this.pointBX = bx
        this.pointBY = by
        this.bitmapWidth = bitmapW
        this.bitmapHeight = bitmapH
        invalidate()
    }

    fun setImageMatrix(matrix: Matrix) {
        this.imageMatrix = matrix
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (pointAX > 0 && pointAY > 0 && pointBX > 0 && pointBY > 0) {
            canvas.drawLine(pointAX, pointAY, pointBX, pointBY, linePaint)
            canvas.drawCircle(pointAX, pointAY, 18f, pointPaint)
            canvas.drawCircle(pointBX, pointBY, 18f, pointPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                draggedPoint = when {
                    distanceTo(event.x, event.y, pointAX, pointAY) <= toleranceRadius -> 0
                    distanceTo(event.x, event.y, pointBX, pointBY) <= toleranceRadius -> 1
                    else -> -1
                }
                return draggedPoint != -1
            }
            MotionEvent.ACTION_MOVE -> {
                if (draggedPoint >= 0) {
                    val screenX = event.x
                    val screenY = event.y
                    when (draggedPoint) {
                        0 -> {
                            pointAX = screenX
                            pointAY = screenY
                        }
                        1 -> {
                            pointBX = screenX
                            pointBY = screenY
                        }
                    }
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                if (draggedPoint >= 0) {
                    val screenX = event.x
                    val screenY = event.y
                    val matrix = imageMatrix
                    if (matrix != null && bitmapWidth > 0 && bitmapHeight > 0) {
                        val inverseMatrix = Matrix()
                        matrix.invert(inverseMatrix)
                        val pts = floatArrayOf(screenX, screenY)
                        inverseMatrix.mapPoints(pts)
                        val pixelX = pts[0]
                        val pixelY = pts[1]
                        val normX = (pixelX / bitmapWidth).coerceIn(0f, 1f)
                        val normY = (pixelY / bitmapHeight).coerceIn(0f, 1f)
                        val newAX: Float
                        val newAY: Float
                        val newBX: Float
                        val newBY: Float
                        when (draggedPoint) {
                            0 -> {
                                newAX = normX
                                newAY = normY
                                val ptsB = floatArrayOf(pointBX, pointBY)
                                inverseMatrix.mapPoints(ptsB)
                                val normBX = (ptsB[0] / bitmapWidth).coerceIn(0f, 1f)
                                val normBY = (ptsB[1] / bitmapHeight).coerceIn(0f, 1f)
                                onPointsUpdated?.invoke(newAX, newAY, normBX, normBY)
                            }
                            1 -> {
                                val ptsA = floatArrayOf(pointAX, pointAY)
                                inverseMatrix.mapPoints(ptsA)
                                val normAX = (ptsA[0] / bitmapWidth).coerceIn(0f, 1f)
                                val normAY = (ptsA[1] / bitmapHeight).coerceIn(0f, 1f)
                                newBX = normX
                                newBY = normY
                                onPointsUpdated?.invoke(normAX, normAY, newBX, newBY)
                            }
                        }
                    }
                    draggedPoint = -1
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun distanceTo(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        return kotlin.math.sqrt(((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2)))
    }
}

