package com.infusory.tutarapp.ui.components.containers

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.Log
import android.view.*
import android.widget.FrameLayout
import android.widget.ImageView
import com.infusory.tutarapp.R
import kotlin.math.*

data class ControlButton(
    val iconRes: Int,
    val onClick: () -> Unit,
    val position: ButtonPosition = ButtonPosition.TOP_START
)

enum class ButtonPosition {
    TOP_START, TOP_CENTER, TOP_END,
    BOTTOM_START, BOTTOM_CENTER, BOTTOM_END,
    CENTER_START, CENTER_END
}

open class UnifiedDraggableZoomableContainer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    // Touch state
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var isDragging = false
    private var isResizing = false
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var scaledTouchSlop = 10f

    // Container properties
    private var containerTranslationX = 0f
    private var containerTranslationY = 0f
    protected var baseWidth = 300
    protected var baseHeight = 300
    protected var currentWidth = 300
    protected var currentHeight = 300
    private var minSize = 150
    private var maxSize = 1200

    // Scale tracking
    private var currentScaleValue = 1f
    private var smoothedScale = 1f
    private val SMOOTHING_FACTOR = 0.3f // Adjust for desired smoothness
    private var lastScaleUpdateTime = 0L
    private val SCALE_UPDATE_INTERVAL = 16L // ~60fps

    // Aspect ratio management
    protected var maintainAspectRatio: Boolean = false
    protected var aspectRatio: Float = 1f

    // Multi-touch support
    private val scaleGestureDetector: ScaleGestureDetector

    // Performance optimization
    private var isActivelyTransforming = false
    private var lastBoundsCheck = 0L
    private val BOUNDS_CHECK_INTERVAL = 16L // ~60fps

    // Button management
    private val controlButtons = mutableListOf<ImageView>()
    private var buttonExclusionAreas = mutableListOf<RectF>()
    private var needsButtonUpdate = false
    private val buttonUpdateRunnable = Runnable {
        if (needsButtonUpdate) {
            updateButtonExclusionAreas()
            needsButtonUpdate = false
        }
    }

    // Cached dimensions
    private var halfWidth = 0f
    private var halfHeight = 0f

    // Callbacks
    var onContainerMoved: ((x: Float, y: Float) -> Unit)? = null
    var onContainerResized: ((width: Int, height: Int) -> Unit)? = null

    // Configuration
    open var isDraggingEnabled = true
    open var isResizingEnabled = true
    open var showBackground = true
        set(value) {
            field = value
            updateBackground()
        }

    init {
        // Set initial size
        baseWidth = dpToPx(300)
        baseHeight = dpToPx(300)
        currentWidth = baseWidth
        currentHeight = baseHeight

        // Set dynamic max size based on screen dimensions
        setDynamicSizeLimits()

        // Initialize touch slop
        scaledTouchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

        clipChildren = false
        clipToPadding = false

        // Set default background
        updateBackground()

        // Initialize scale gesture detector for resizing
        scaleGestureDetector = ScaleGestureDetector(context, ResizeListener())

        // Enable hardware acceleration for smooth transformations
        setLayerType(LAYER_TYPE_HARDWARE, null)

        // Set initial position
        containerTranslationX = 100f
        containerTranslationY = 100f
        applyPosition()

        // Update cached dimensions
        updateCachedDimensions()

        // Set initial layout params
        layoutParams = ViewGroup.LayoutParams(currentWidth, currentHeight)
    }

    private fun updateBackground() {
        if (showBackground) {
            setBackgroundResource(R.drawable.dotted_border_background)
        } else {
            background = null
        }
    }

    private fun updateCachedDimensions() {
        halfWidth = currentWidth * 0.5f
        halfHeight = currentHeight * 0.5f
    }

    private fun setTransformationMode(active: Boolean) {
        if (active != isActivelyTransforming) {
            isActivelyTransforming = active
            setDrawingCacheQuality(if (active) View.DRAWING_CACHE_QUALITY_LOW else View.DRAWING_CACHE_QUALITY_HIGH)
        }
    }

    private fun scheduleButtonUpdate() {
        if (!needsButtonUpdate) {
            needsButtonUpdate = true
            postOnAnimation(buttonUpdateRunnable)
        }
    }

    open fun addControlButtons(buttons: List<ControlButton>) {
        buttons.forEach { buttonConfig ->
            val button = createControlButton(buttonConfig.iconRes, buttonConfig.onClick)
            controlButtons.add(button)
            addView(button)
            positionButton(button, buttonConfig.position)
        }
        scheduleButtonUpdate()
    }

    open fun addControlButton(button: ControlButton) {
        val imageView = createControlButton(button.iconRes, button.onClick)
        controlButtons.add(imageView)
        addView(imageView)
        positionButton(imageView, button.position)
        scheduleButtonUpdate()
    }

    private fun createControlButton(iconRes: Int, onClick: () -> Unit): ImageView {
        return ImageView(context).apply {
            layoutParams = LayoutParams(dpToPx(24), dpToPx(24))
            setImageResource(iconRes)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#E0FFFFFF"))
                setStroke(2, Color.parseColor("#CCCCCC"))
            }
            scaleType = ImageView.ScaleType.CENTER
            elevation = 6f
            alpha = 0.9f
            setOnClickListener { onClick() }
        }
    }

    private fun bringButtonsToFront() {
        controlButtons.forEach { button ->
            button.bringToFront()
            button.elevation = 100f
        }
    }

    private fun positionButton(button: ImageView, position: ButtonPosition) {
        val buttonSize = dpToPx(24)
        val layoutParams = button.layoutParams as LayoutParams

        val existingButtonsAtPosition = controlButtons.count { existingButton ->
            if (existingButton == button) return@count false
            val existingLayoutParams = existingButton.layoutParams as LayoutParams
            when (position) {
                ButtonPosition.TOP_START ->
                    existingLayoutParams.gravity == (Gravity.TOP or Gravity.START)
                ButtonPosition.TOP_CENTER ->
                    existingLayoutParams.gravity == (Gravity.TOP or Gravity.CENTER_HORIZONTAL)
                ButtonPosition.TOP_END ->
                    existingLayoutParams.gravity == (Gravity.TOP or Gravity.END)
                ButtonPosition.BOTTOM_START ->
                    existingLayoutParams.gravity == (Gravity.BOTTOM or Gravity.START)
                ButtonPosition.BOTTOM_CENTER ->
                    existingLayoutParams.gravity == (Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
                ButtonPosition.BOTTOM_END ->
                    existingLayoutParams.gravity == (Gravity.BOTTOM or Gravity.END)
                ButtonPosition.CENTER_START ->
                    existingLayoutParams.gravity == (Gravity.CENTER_VERTICAL or Gravity.START)
                ButtonPosition.CENTER_END ->
                    existingLayoutParams.gravity == (Gravity.CENTER_VERTICAL or Gravity.END)
            }
        }

        val verticalOffset = existingButtonsAtPosition * (buttonSize + dpToPx(4))

        when (position) {
            ButtonPosition.TOP_START -> {
                val horizontalOffset = dpToPx(2)
                val verticalStartOffset = dpToPx(2)
                layoutParams.setMargins(
                    horizontalOffset,
                    verticalStartOffset + verticalOffset,
                    0,
                    0
                )
                layoutParams.gravity = Gravity.TOP or Gravity.START
            }
            ButtonPosition.TOP_CENTER -> {
                layoutParams.setMargins(0, -(buttonSize + dpToPx(8)) + verticalOffset, 0, 0)
                layoutParams.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            }
            ButtonPosition.TOP_END -> {
                layoutParams.setMargins(0, -(buttonSize + dpToPx(8)) + verticalOffset, -(buttonSize + dpToPx(8)), 0)
                layoutParams.gravity = Gravity.TOP or Gravity.END
            }
            ButtonPosition.BOTTOM_START -> {
                layoutParams.setMargins(-(buttonSize + dpToPx(8)), 0, 0, -(buttonSize + dpToPx(8)) + verticalOffset)
                layoutParams.gravity = Gravity.BOTTOM or Gravity.START
            }
            ButtonPosition.BOTTOM_CENTER -> {
                layoutParams.setMargins(0, 0, 0, -(buttonSize + dpToPx(8)) + verticalOffset)
                layoutParams.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            }
            ButtonPosition.BOTTOM_END -> {
                layoutParams.setMargins(0, 0, -(buttonSize + dpToPx(8)), -(buttonSize + dpToPx(8)) + verticalOffset)
                layoutParams.gravity = Gravity.BOTTOM or Gravity.END
            }
            ButtonPosition.CENTER_START -> {
                layoutParams.setMargins(-(buttonSize + dpToPx(8)), verticalOffset, 0, 0)
                layoutParams.gravity = Gravity.CENTER_VERTICAL or Gravity.START
            }
            ButtonPosition.CENTER_END -> {
                layoutParams.setMargins(0, verticalOffset, -(buttonSize + dpToPx(8)), 0)
                layoutParams.gravity = Gravity.CENTER_VERTICAL or Gravity.END
            }
        }
        button.layoutParams = layoutParams
    }

    private fun updateButtonExclusionAreas() {
        buttonExclusionAreas.clear()
        val buttonSize = dpToPx(24)
        val touchPadding = dpToPx(16)

        controlButtons.forEach { button ->
            val layoutParams = button.layoutParams as LayoutParams
            val rect = when {
                layoutParams.gravity and Gravity.TOP != 0 && layoutParams.gravity and Gravity.START != 0 -> {
                    RectF(0f, 0f, (buttonSize + touchPadding).toFloat(), (buttonSize + touchPadding).toFloat())
                }
                layoutParams.gravity and Gravity.TOP != 0 && layoutParams.gravity and Gravity.END != 0 -> {
                    RectF((currentWidth - buttonSize - touchPadding).toFloat(), 0f, currentWidth.toFloat(), (buttonSize + touchPadding).toFloat())
                }
                layoutParams.gravity and Gravity.BOTTOM != 0 && layoutParams.gravity and Gravity.START != 0 -> {
                    RectF(0f, (currentHeight - buttonSize - touchPadding).toFloat(), (buttonSize + touchPadding).toFloat(), currentHeight.toFloat())
                }
                layoutParams.gravity and Gravity.BOTTOM != 0 && layoutParams.gravity and Gravity.END != 0 -> {
                    RectF((currentWidth - buttonSize - touchPadding).toFloat(), (currentHeight - buttonSize - touchPadding).toFloat(), currentWidth.toFloat(), currentHeight.toFloat())
                }
                else -> RectF(0f, 0f, (buttonSize + touchPadding).toFloat(), (buttonSize + touchPadding).toFloat())
            }
            buttonExclusionAreas.add(rect)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isDraggingEnabled && !isResizingEnabled) {
            return super.onTouchEvent(event)
        }

        var handledByScale = false
        if (isResizingEnabled) {
            handledByScale = scaleGestureDetector.onTouchEvent(event)
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(0)
                lastTouchX = event.rawX
                lastTouchY = event.rawY
                isDragging = false
                setTransformationMode(true)
                if (isDraggingEnabled && !isTouchInButtonArea(event.x, event.y)) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (isResizingEnabled && event.pointerCount == 2) {
                    isResizing = true
                    isDragging = false
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isResizing && event.pointerCount >= 2) {
                    return handledByScale || true
                }
                if (!isResizing && isDraggingEnabled && event.pointerCount == 1) {
                    val pointerIndex = event.findPointerIndex(activePointerId)
                    if (pointerIndex < 0) return handledByScale || true

                    if (!isDragging) {
                        val deltaX = abs(event.getRawX(pointerIndex) - lastTouchX)
                        val deltaY = abs(event.getRawY(pointerIndex) - lastTouchY)
                        if (deltaX > scaledTouchSlop || deltaY > scaledTouchSlop) {
                            isDragging = true
                        }
                    }

                    if (isDragging && !isTouchInButtonArea(event.x, event.y)) {
                        val currentX = event.getRawX(pointerIndex)
                        val currentY = event.getRawY(pointerIndex)
                        containerTranslationX += (currentX - lastTouchX)
                        containerTranslationY += (currentY - lastTouchY)
                        translationX = containerTranslationX
                        translationY = containerTranslationY
                        onContainerMoved?.invoke(containerTranslationX, containerTranslationY)
                        lastTouchX = currentX
                        lastTouchY = currentY
                    }
                }
                return handledByScale || true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.pointerCount == 2) {
                    isResizing = false
                    val upIndex = (event.action and MotionEvent.ACTION_POINTER_INDEX_MASK) ushr MotionEvent.ACTION_POINTER_INDEX_SHIFT
                    val remainingIndex = if (upIndex == 0) 1 else 0
                    lastTouchX = event.getRawX(remainingIndex)
                    lastTouchY = event.getRawY(remainingIndex)
                    activePointerId = event.getPointerId(remainingIndex)
                    isDragging = false
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activePointerId = MotionEvent.INVALID_POINTER_ID
                isDragging = false
                isResizing = false
                parent?.requestDisallowInterceptTouchEvent(false)
                applyScreenBounds()
                applyPosition()
                setTransformationMode(false)
                return true
            }
        }
        return handledByScale || super.onTouchEvent(event)
    }

    private fun isTouchInButtonArea(x: Float, y: Float): Boolean {
        return buttonExclusionAreas.any { rect -> rect.contains(x, y) }
    }

    private fun applyScreenBounds() {
        val now = System.currentTimeMillis()
        if (now - lastBoundsCheck < BOUNDS_CHECK_INTERVAL && (isDragging || isResizing)) {
            return
        }
        lastBoundsCheck = now

        val parent = parent as? ViewGroup ?: return
        val parentWidth = parent.width.toFloat()
        val parentHeight = parent.height.toFloat()
        val visibleThreshold = 0.2f
        val minX = -currentWidth * (1f - visibleThreshold)
        val maxX = parentWidth - currentWidth * visibleThreshold
        val minY = -currentHeight * (1f - visibleThreshold)
        val maxY = parentHeight - currentHeight * visibleThreshold
        containerTranslationX = containerTranslationX.coerceIn(minX, maxX)
        containerTranslationY = containerTranslationY.coerceIn(minY, maxY)
    }

    private fun applyPosition() {
        translationX = containerTranslationX
        translationY = containerTranslationY
    }

    private fun applyScaleTransform(scale: Float) {
        val newScale = scale.coerceIn(
            minSize.toFloat() / baseWidth,
            maxSize.toFloat() / baseWidth
        )
        smoothedScale = (SMOOTHING_FACTOR * newScale) + ((1f - SMOOTHING_FACTOR) * smoothedScale)
        currentScaleValue = smoothedScale

        if (abs(currentScaleValue - scaleX) > 0.001f) {
            scaleX = currentScaleValue
            scaleY = currentScaleValue
            currentWidth = (baseWidth * currentScaleValue).toInt()
            currentHeight = (baseHeight * currentScaleValue).toInt()
            updateCachedDimensions()
            scheduleButtonUpdate()
            onContainerResized?.invoke(currentWidth, currentHeight)
        }

        applyScreenBounds()
        applyPosition()
    }

    private inner class ResizeListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        private var initialScale = 1f
        private var accumulatedScale = 1f

        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            isResizing = true
            initialScale = currentScaleValue
            accumulatedScale = 1f
            smoothedScale = currentScaleValue
            parent?.requestDisallowInterceptTouchEvent(true)
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            if (!isResizingEnabled) return false

            val currentTime = System.currentTimeMillis()
            if (currentTime - lastScaleUpdateTime < SCALE_UPDATE_INTERVAL) {
                return true
            }
            lastScaleUpdateTime = currentTime

            try {
                val scaleFactor = detector.scaleFactor
                if (scaleFactor <= 0f || scaleFactor.isNaN() || scaleFactor.isInfinite()) {
                    return false
                }

                accumulatedScale *= scaleFactor
                val newScale = initialScale * accumulatedScale
                applyScaleTransform(newScale)
                return true
            } catch (e: Exception) {
                Log.e("Container", "Error during scaling", e)
                return false
            }
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            isResizing = false
            parent?.requestDisallowInterceptTouchEvent(false)
            applyScreenBounds()
            applyPosition()
            onContainerResized?.invoke(currentWidth, currentHeight)
        }
    }

    open fun setContent(view: View) {
        val viewsToRemove = mutableListOf<View>()
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (!controlButtons.contains(child)) {
                viewsToRemove.add(child)
            }
        }
        viewsToRemove.forEach { removeView(it) }

        val contentLayoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT
        ).apply {
            val margin = dpToPx(8)
            val topMargin = if (hasButtonsInTopArea()) dpToPx(40) else margin
            val bottomMargin = if (hasButtonsInBottomArea()) dpToPx(40) else margin
            setMargins(margin, topMargin, margin, bottomMargin)
        }
        view.layoutParams = contentLayoutParams
        addView(view, 0)
        bringButtonsToFront()
    }

    open fun removeContent() {
        val viewsToRemove = mutableListOf<View>()
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (!controlButtons.contains(child)) {
                viewsToRemove.add(child)
            }
        }
        viewsToRemove.forEach { removeView(it) }
    }

    private fun hasButtonsInTopArea(): Boolean {
        return controlButtons.any { button ->
            val layoutParams = button.layoutParams as LayoutParams
            layoutParams.gravity and Gravity.TOP != 0
        }
    }

    private fun hasButtonsInBottomArea(): Boolean {
        return controlButtons.any { button ->
            val layoutParams = button.layoutParams as LayoutParams
            layoutParams.gravity and Gravity.BOTTOM != 0
        }
    }

    open fun setContainerSize(width: Int, height: Int, animate: Boolean = false) {
        val targetWidth = width.coerceIn(minSize, maxSize)
        val targetHeight = height.coerceIn(minSize, maxSize)

        if (animate) {
            val startScale = currentScaleValue
            val targetScaleX = targetWidth.toFloat() / baseWidth
            val targetScaleY = targetHeight.toFloat() / baseHeight
            val targetScale = (targetScaleX + targetScaleY) / 2f
            val animator = ValueAnimator.ofFloat(startScale, targetScale).apply {
                duration = 300
                addUpdateListener { animation ->
                    val scale = animation.animatedValue as Float
                    applyScaleTransform(scale)
                }
            }
            animator.start()
        } else {
            val targetScale = targetWidth.toFloat() / baseWidth
            applyScaleTransform(targetScale)
        }
    }

    open fun resetTransform() {
        currentScaleValue = 1f
        smoothedScale = 1f
        scaleX = 1f
        scaleY = 1f
        currentWidth = baseWidth
        currentHeight = baseHeight
        containerTranslationX = 100f
        containerTranslationY = 100f
        updateCachedDimensions()
        scheduleButtonUpdate()
        applyPosition()
    }

    open fun getCurrentSize(): Pair<Int, Int> = Pair(currentWidth, currentHeight)

    open fun getCurrentPosition(): Pair<Float, Float> = Pair(containerTranslationX, containerTranslationY)

    open fun moveContainerTo(x: Float, y: Float, animate: Boolean = false) {
        if (animate) {
            animate()
                .translationX(x)
                .translationY(y)
                .setDuration(300)
                .withEndAction {
                    containerTranslationX = x
                    containerTranslationY = y
                }
                .start()
        } else {
            containerTranslationX = x
            containerTranslationY = y
            applyPosition()
        }
    }

    open fun setSizeLimits(minSize: Int, maxSize: Int) {
        this.minSize = minSize.coerceAtLeast(100)
        this.maxSize = maxSize.coerceAtMost(getScreenHeight())
        val currentScale = getCurrentScale()
        val minScale = this.minSize.toFloat() / baseWidth
        val maxScale = this.maxSize.toFloat() / baseWidth
        if (currentScale < minScale || currentScale > maxScale) {
            val newScale = currentScale.coerceIn(minScale, maxScale)
            applyScaleTransform(newScale)
        }
    }

    private fun setDynamicSizeLimits() {
        val screenWidth = getScreenWidth()
        val screenHeight = getScreenHeight()
        minSize = dpToPx(150)
        maxSize = (min(screenWidth, screenHeight) * 0.9f).toInt()
    }

    private fun getScreenWidth(): Int = context.resources.displayMetrics.widthPixels
    private fun getScreenHeight(): Int = context.resources.displayMetrics.heightPixels

    open fun resizeTo(size: Int, animate: Boolean = false) {
        setContainerSize(size, size, animate)
    }

    open fun zoomTo(scale: Float, animate: Boolean = false) {
        if (animate) {
            val startScale = currentScaleValue
            val animator = ValueAnimator.ofFloat(startScale, scale).apply {
                duration = 300
                addUpdateListener { animation ->
                    val animScale = animation.animatedValue as Float
                    applyScaleTransform(animScale)
                }
            }
            animator.start()
        } else {
            applyScaleTransform(scale)
        }
    }

    open fun getCurrentScale(): Float = currentScaleValue

    protected fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}