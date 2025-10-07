package com.infusory.tutarapp.ui.components

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class HueSliderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var selectedY = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selectorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val selectorInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    var onHueSelected: ((Float) -> Unit)? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        selectedY = 0f
        // Trigger initial hue selection
        onHueSelected?.invoke(getCurrentHue())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()

        // Create rainbow gradient for hue
        val hueColors = IntArray(7)
        hueColors[0] = Color.RED
        hueColors[1] = Color.YELLOW
        hueColors[2] = Color.GREEN
        hueColors[3] = Color.CYAN
        hueColors[4] = Color.BLUE
        hueColors[5] = Color.MAGENTA
        hueColors[6] = Color.RED

        val hueShader = LinearGradient(
            0f, 0f, 0f, height,
            hueColors,
            null,
            Shader.TileMode.CLAMP
        )

        paint.shader = hueShader
        canvas.drawRect(0f, 0f, width, height, paint)

        // Draw selector indicators
        val selectorLeft = 0f
        val selectorRight = width

        // Draw horizontal lines for selector
        canvas.drawLine(selectorLeft, selectedY, selectorRight, selectedY, selectorPaint)
        canvas.drawLine(selectorLeft, selectedY, selectorRight, selectedY, selectorInnerPaint)

        // Draw triangular indicators on the sides
        val trianglePath = Path().apply {
            // Left triangle
            moveTo(0f, selectedY - 8f)
            lineTo(8f, selectedY)
            lineTo(0f, selectedY + 8f)
            close()

            // Right triangle
            moveTo(width, selectedY - 8f)
            lineTo(width - 8f, selectedY)
            lineTo(width, selectedY + 8f)
            close()
        }

        canvas.drawPath(trianglePath, selectorPaint)
        canvas.drawPath(trianglePath, selectorInnerPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_MOVE -> {
                selectedY = event.y.coerceIn(0f, height.toFloat())
                val hue = (selectedY / height) * 360f
                onHueSelected?.invoke(hue)
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    fun setHue(hue: Float) {
        selectedY = (hue / 360f) * height
        invalidate()
        onHueSelected?.invoke(hue)
    }

    fun getCurrentHue(): Float {
        return (selectedY / height) * 360f
    }
}