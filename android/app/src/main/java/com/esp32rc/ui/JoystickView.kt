package com.esp32rc.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.min

class JoystickView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var onJoystickChanged: ((x: Float, y: Float) -> Unit)? = null

    init {
        isClickable = true
        isFocusable = true
        isFocusableInTouchMode = true
    }

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x1800_0000.toInt()
    }
    private val baseStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * resources.displayMetrics.density
        color = 0x60FF_FFFF.toInt()
    }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x99CC_CCCC.toInt()
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 12f * resources.displayMetrics.density
        color = 0x80CC_CCCC.toInt()
    }

    private var centerX = 0f
    private var centerY = 0f
    private var baseRadius = 0f
    private var thumbRadius = 0f
    private var thumbX = 0f
    private var thumbY = 0f
    private var normX = 0f
    private var normY = 0f
    private var activePointerId = MotionEvent.INVALID_POINTER_ID

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        centerX = w / 2f
        centerY = h / 2f
        baseRadius = min(w, h) / 2f * 0.95f
        thumbRadius = baseRadius * 0.3f
        thumbX = centerX
        thumbY = centerY
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawCircle(centerX, centerY, baseRadius, basePaint)
        canvas.drawCircle(centerX, centerY, baseRadius, baseStrokePaint)
        canvas.drawCircle(thumbX, thumbY, thumbRadius, thumbPaint)

        val l = (normY + normX).toInt().coerceIn(-255, 255)
        val r = (normY - normX).toInt().coerceIn(-255, 255)
        val dy = textPaint.textSize * 0.4f
        canvas.drawText("L $l", centerX, centerY - baseRadius - dy - 4f, textPaint)
        canvas.drawText("R $r", centerX, centerY + baseRadius + textPaint.textSize + 4f, textPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(0)
                updateThumb(event.x, event.y)
                performClick()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(activePointerId)
                if (index >= 0) updateThumb(event.getX(index), event.getY(index))
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activePointerId = MotionEvent.INVALID_POINTER_ID
                resetThumb()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun updateThumb(x: Float, y: Float) {
        val dx = x - centerX
        val dy = y - centerY
        val maxRange = baseRadius - thumbRadius
        val distance = hypot(dx, dy)
        if (distance > maxRange && distance > 0f) {
            val scale = maxRange / distance
            thumbX = centerX + dx * scale
            thumbY = centerY + dy * scale
        } else {
            thumbX = x
            thumbY = y
        }
        normX = ((thumbX - centerX) / maxRange).coerceIn(-1f, 1f)
        normY = ((thumbY - centerY) / maxRange).coerceIn(-1f, 1f)
        invalidate()
        onJoystickChanged?.invoke(normX, normY)
    }

    private fun resetThumb() {
        thumbX = centerX
        thumbY = centerY
        normX = 0f
        normY = 0f
        invalidate()
        onJoystickChanged?.invoke(0f, 0f)
    }
}
