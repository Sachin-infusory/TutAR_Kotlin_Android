package com.infusory.tutarapp.ui.components

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class ColorPaletteView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var currentHue = 0f
    private var selectedX = 0f
    private var selectedY = 0f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selectorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val selectorInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    var onColorSelected: ((Int) -> Unit)? = null

    init {
        selectedX = width / 2f
        selectedY = height / 2f
    }

    fun setHue(hue: Float) {
        currentHue = hue
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        selectedX = w / 2f
        selectedY = h / 2f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()

        // Draw the saturation-value gradient
        val hueColor = Color.HSVToColor(floatArrayOf(currentHue, 1f, 1f))

        // Create horizontal gradient (saturation)
        val saturationShader = LinearGradient(
            0f, 0f, width, 0f,
            Color.WHITE, hueColor,
            Shader.TileMode.CLAMP
        )

        // Create vertical gradient (value/brightness)
        val valueShader = LinearGradient(
            0f, 0f, 0f, height,
            Color.TRANSPARENT, Color.BLACK,
            Shader.TileMode.CLAMP
        )

        // Combine both gradients
        val composeShader = ComposeShader(
            saturationShader,
            valueShader,
            PorterDuff.Mode.MULTIPLY
        )

        paint.shader = composeShader
        canvas.drawRect(0f, 0f, width, height, paint)

        // Draw selector circle
        canvas.drawCircle(selectedX, selectedY, 12f, selectorPaint)
        canvas.drawCircle(selectedX, selectedY, 12f, selectorInnerPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_MOVE -> {
                selectedX = event.x.coerceIn(0f, width.toFloat())
                selectedY = event.y.coerceIn(0f, height.toFloat())

                val selectedColor = getColorAtPosition(selectedX, selectedY)
                onColorSelected?.invoke(selectedColor)

                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun getColorAtPosition(x: Float, y: Float): Int {
        val saturation = (x / width).coerceIn(0f, 1f)
        val value = 1f - (y / height).coerceIn(0f, 1f)

        return Color.HSVToColor(floatArrayOf(currentHue, saturation, value))
    }

    fun getCurrentColor(): Int {
        return getColorAtPosition(selectedX, selectedY)
    }
}