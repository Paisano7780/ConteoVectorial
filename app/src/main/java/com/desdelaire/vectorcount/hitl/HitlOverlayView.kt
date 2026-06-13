package com.desdelaire.vectorcount.hitl

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

class HitlOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }

    private val originCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GREEN
        style = Paint.Style.FILL
    }

    private val destinationCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        style = Paint.Style.FILL
    }

    private val arrowHeadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        style = Paint.Style.FILL
    }

    var pointAX = 120f
    var pointAY = 120f
    var pointBX = 280f
    var pointBY = 220f

    var onPointsChanged: ((ax: Float, ay: Float, bx: Float, by: Float) -> Unit)? = null

    companion object {
        private const val ARROW_HEAD_LENGTH = 30f
        private const val ARROW_WING_ANGLE_RADIANS = PI / 6.0  // 30 degrees
        private const val CIRCLE_RADIUS = 18f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw main line from A to B
        canvas.drawLine(pointAX, pointAY, pointBX, pointBY, linePaint)

        // Draw Point A (Origin) as green circle
        canvas.drawCircle(pointAX, pointAY, CIRCLE_RADIUS, originCirclePaint)

        // Draw Point B (Destination) as red circle
        canvas.drawCircle(pointBX, pointBY, CIRCLE_RADIUS, destinationCirclePaint)

        // Draw arrow head at Point B
        drawArrowHead(canvas, pointAX, pointAY, pointBX, pointBY)
    }

    private fun drawArrowHead(canvas: Canvas, startX: Float, startY: Float, endX: Float, endY: Float) {
        // Calculate angle from A to B
        val angle = atan2(endY - startY, endX - startX)

        // Calculate arrow head base point (offset from B along the line)
        val baseX = endX - ARROW_HEAD_LENGTH * cos(angle).toFloat()
        val baseY = endY - ARROW_HEAD_LENGTH * sin(angle).toFloat()

        // Calculate left and right points of arrow head
        val leftX = baseX + ARROW_HEAD_LENGTH * cos(angle + ARROW_WING_ANGLE_RADIANS).toFloat()
        val leftY = baseY + ARROW_HEAD_LENGTH * sin(angle + ARROW_WING_ANGLE_RADIANS).toFloat()

        val rightX = baseX + ARROW_HEAD_LENGTH * cos(angle - ARROW_WING_ANGLE_RADIANS).toFloat()
        val rightY = baseY + ARROW_HEAD_LENGTH * sin(angle - ARROW_WING_ANGLE_RADIANS).toFloat()

        // Create path for arrow head triangle
        val path = Path()
        path.moveTo(endX, endY)
        path.lineTo(leftX, leftY)
        path.lineTo(rightX, rightY)
        path.close()

        canvas.drawPath(path, arrowHeadPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_MOVE) {
            pointBX = event.x
            pointBY = event.y
            onPointsChanged?.invoke(pointAX, pointAY, pointBX, pointBY)
            invalidate()
            return true
        }
        return super.onTouchEvent(event)
    }
}
