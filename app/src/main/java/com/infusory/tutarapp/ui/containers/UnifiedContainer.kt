// UnifiedContainer.kt
package com.infusory.tutarapp.ui.containers

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import com.infusory.tutarapp.R
import kotlin.math.abs

/**
 * Universal draggable and resizable container that can hold any content.
 * Features:
 * - Smooth drag and pinch-to-resize
 * - Common close button outside border
 * - Hardware accelerated for better performance
 * - Automatic bounds checking
 * - Configurable size limits
 * - Compatible with API 24+
 */
class UnifiedContainer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    // ============================================
    // PROPERTIES
    // ============================================

    // Touch & Gesture
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var isDragging = false
    private var isResizing = false
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val scaleGestureDetector: ScaleGestureDetector

    // Position & Size
    private var posX = 0f
    private var posY = 0f
    private var baseWidth = 0
    private var baseHeight = 0
    private var currentScale = 1f
    private var targetScale = 1f

    // Size Limits
    private var minSize = dpToPx(150)
    private var maxSize = dpToPx(800)

    // UI Elements
    private val closeButton: ImageView
    private val contentContainer: FrameLayout

    // Callbacks
    var onCloseClicked: (() -> Unit)? = null
    var onPositionChanged: ((x: Float, y: Float) -> Unit)? = null
    var onSizeChanged: ((width: Int, height: Int) -> Unit)? = null

    // Configuration
    var isDraggingEnabled = true
    var isResizingEnabled = true
    var showBorder = true
        set(value) {
            field = value
            updateBackground()
        }

    // ============================================
    // INITIALIZATION
    // ============================================

    init {
        // Hardware acceleration for smooth performance
        setLayerType(LAYER_TYPE_HARDWARE, null)
        clipChildren = false
        clipToPadding = false

        // Default size
        baseWidth = dpToPx(300)
        baseHeight = dpToPx(300)

        // Create content container (holds actual content)
        contentContainer = FrameLayout(context).apply {
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            )
        }
        addView(contentContainer)

        // Create close button (outside container, top-left)
        closeButton = createCloseButton()
        addView(closeButton)

        // Setup scale gesture detector for resizing
        scaleGestureDetector = ScaleGestureDetector(context, ScaleListener())

        // Set default background
        updateBackground()

        // Set initial layout params
        layoutParams = ViewGroup.LayoutParams(baseWidth, baseHeight)

        // Set initial position (will be adjusted by parent)
        posX = dpToPx(50).toFloat()
        posY = dpToPx(50).toFloat()
        applyPosition()
    }

    private fun createCloseButton(): ImageView {
        return ImageView(context).apply {
            val buttonSize = dpToPx(28)
            layoutParams = LayoutParams(buttonSize, buttonSize).apply {
                // Position outside container, top-left
                leftMargin = -buttonSize - dpToPx(4)
                topMargin = -buttonSize - dpToPx(4)
            }

            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)

            // Circular background
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#F44336")) // Red
                setStroke(dpToPx(2), Color.WHITE)
            }

            scaleType = ImageView.ScaleType.CENTER
            elevation = dpToPx(8).toFloat()

            // Click listener
            setOnClickListener {
                onCloseClicked?.invoke()
            }

            // Keep button on top
            bringToFront()
        }
    }

    private fun updateBackground() {
        if (showBorder) {
            setBackgroundResource(R.drawable.dotted_border_background)
        } else {
            background = null
        }
    }

    // ============================================
    // CONTENT MANAGEMENT
    // ============================================

    /**
     * Set the content view to display in this container
     */
    fun setContent(view: View) {
        contentContainer.removeAllViews()

        val params = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT
        ).apply {
            // Add padding to keep content inside border
            setMargins(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
        }

        view.layoutParams = params
        contentContainer.addView(view)

        // Ensure close button stays on top
        closeButton.bringToFront()
    }

    /**
     * Get the current content view
     */
    fun getContent(): View? {
        return if (contentContainer.childCount > 0) {
            contentContainer.getChildAt(0)
        } else null
    }

    /**
     * Remove current content
     */
    fun clearContent() {
        contentContainer.removeAllViews()
    }

    // ============================================
    // TOUCH HANDLING
    // ============================================

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isDraggingEnabled && !isResizingEnabled) {
            return super.onTouchEvent(event)
        }

        // Handle resize gestures first
        var handled = false
        if (isResizingEnabled) {
            handled = scaleGestureDetector.onTouchEvent(event)
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(0)
                lastTouchX = event.rawX
                lastTouchY = event.rawY
                isDragging = false
                parent?.requestDisallowInterceptTouchEvent(true)
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
                // Skip move if resizing with two fingers
                if (isResizing && event.pointerCount >= 2) {
                    return handled
                }

                // Single finger drag
                if (isDraggingEnabled && event.pointerCount == 1 && !isResizing) {
                    val pointerIndex = event.findPointerIndex(activePointerId)
                    if (pointerIndex < 0) return handled

                    val currentX = getRawX(event, pointerIndex)
                    val currentY = getRawY(event, pointerIndex)

                    // Check if moved beyond touch slop
                    if (!isDragging) {
                        val dx = abs(currentX - lastTouchX)
                        val dy = abs(currentY - lastTouchY)
                        if (dx > touchSlop || dy > touchSlop) {
                            isDragging = true
                        }
                    }

                    // Perform drag
                    if (isDragging) {
                        val dx = currentX - lastTouchX
                        val dy = currentY - lastTouchY
                        posX += dx
                        posY += dy
                        applyPosition()
                        onPositionChanged?.invoke(posX, posY)
                        lastTouchX = currentX
                        lastTouchY = currentY
                    }
                }
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val actionIndex = event.actionIndex
                val pointerId = event.getPointerId(actionIndex)

                if (pointerId == activePointerId) {
                    // Pick new active pointer
                    val newPointerIndex = if (actionIndex == 0) 1 else 0
                    lastTouchX = getRawX(event, newPointerIndex)
                    lastTouchY = getRawY(event, newPointerIndex)
                    activePointerId = event.getPointerId(newPointerIndex)
                }

                if (event.pointerCount == 2) {
                    isResizing = false
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                isResizing = false
                activePointerId = MotionEvent.INVALID_POINTER_ID
                parent?.requestDisallowInterceptTouchEvent(false)

                // Apply bounds and snap to edges if needed
                applyBounds()
                applyPosition()
                return true
            }
        }

        return handled || super.onTouchEvent(event)
    }
    /**
     * Get raw X coordinate for a pointer index (API 24+ compatible)
     */
    private fun getRawX(event: MotionEvent, pointerIndex: Int): Float {
        val location = IntArray(2)
        (parent as? View)?.getLocationOnScreen(location)
        return event.getX(pointerIndex) + location[0].toFloat()
    }

    /**
     * Get raw Y coordinate for a pointer index (API 24+ compatible)
     */
    private fun getRawY(event: MotionEvent, pointerIndex: Int): Float {
        val location = IntArray(2)
        (parent as? View)?.getLocationOnScreen(location)
        return event.getY(pointerIndex) + location[1].toFloat()
    }
    /**
     * Get action index from action (replaces shr operator)
     */
    private val MotionEvent.actionIndex: Int
        get() = (action and MotionEvent.ACTION_POINTER_INDEX_MASK) / MotionEvent.ACTION_POINTER_INDEX_SHIFT

    private fun applyPosition() {
        translationX = posX
        translationY = posY
    }

    // ============================================
    // SCALE/RESIZE HANDLING
    // ============================================

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        private var initialScale = 1f

        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            initialScale = currentScale
            isResizing = true
            parent?.requestDisallowInterceptTouchEvent(true)
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            if (!isResizingEnabled) return false

            val scaleFactor = detector.scaleFactor
            if (scaleFactor <= 0f || scaleFactor.isNaN() || scaleFactor.isInfinite()) {
                return false
            }

            targetScale = (initialScale * detector.scaleFactor).coerceIn(
                minSize.toFloat() / baseWidth,
                maxSize.toFloat() / baseWidth
            )

            applyScale(targetScale)
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            isResizing = false
            parent?.requestDisallowInterceptTouchEvent(false)

            currentScale = targetScale
            applyBounds()

            val newWidth = (baseWidth * currentScale).toInt()
            val newHeight = (baseHeight * currentScale).toInt()
            onSizeChanged?.invoke(newWidth, newHeight)
        }
    }

    private fun applyScale(scale: Float) {
        scaleX = scale
        scaleY = scale
        currentScale = scale
    }

    private fun applyBounds() {
        val parent = parent as? ViewGroup ?: return

        val parentWidth = parent.width.toFloat()
        val parentHeight = parent.height.toFloat()

        val scaledWidth = baseWidth * currentScale
        val scaledHeight = baseHeight * currentScale

        // Keep at least 20% of container visible
        val visibleThreshold = 0.2f
        val minX = -scaledWidth * (1f - visibleThreshold)
        val maxX = parentWidth - scaledWidth * visibleThreshold
        val minY = -scaledHeight * (1f - visibleThreshold)
        val maxY = parentHeight - scaledHeight * visibleThreshold

        posX = posX.coerceIn(minX, maxX)
        posY = posY.coerceIn(minY, maxY)
    }

    // ============================================
    // PUBLIC API - SIZE & POSITION
    // ============================================

    /**
     * Set container size (base size, before scaling)
     */
    fun setContainerSize(width: Int, height: Int) {
        baseWidth = width.coerceIn(minSize, maxSize)
        baseHeight = height.coerceIn(minSize, maxSize)

        layoutParams = layoutParams?.apply {
            this.width = baseWidth
            this.height = baseHeight
        } ?: ViewGroup.LayoutParams(baseWidth, baseHeight)

        requestLayout()
    }

    /**
     * Get current size (including scale)
     */
    fun getCurrentSize(): Pair<Int, Int> {
        val width = (baseWidth * currentScale).toInt()
        val height = (baseHeight * currentScale).toInt()
        return Pair(width, height)
    }

    /**
     * Get base size (without scale)
     */
    fun getBaseSize(): Pair<Int, Int> {
        return Pair(baseWidth, baseHeight)
    }

    /**
     * Move container to position
     */
    fun moveTo(x: Float, y: Float, animate: Boolean = false) {
        if (animate) {
            animateToPosition(x, y)
        } else {
            posX = x
            posY = y
            applyPosition()
            onPositionChanged?.invoke(posX, posY)
        }
    }

    /**
     * Get current position
     */
    fun getPosition(): Pair<Float, Float> {
        return Pair(posX, posY)
    }

    /**
     * Scale container to specific scale
     */
    fun scaleTo(scale: Float, animate: Boolean = false) {
        val targetScale = scale.coerceIn(
            minSize.toFloat() / baseWidth,
            maxSize.toFloat() / baseWidth
        )

        if (animate) {
            animateScale(currentScale, targetScale)
        } else {
            currentScale = targetScale
            applyScale(targetScale)
            onSizeChanged?.invoke(
                (baseWidth * currentScale).toInt(),
                (baseHeight * currentScale).toInt()
            )
        }
    }

    /**
     * Get current scale
     */
    fun getCurrentScale(): Float = currentScale

    /**
     * Set size limits
     */
    fun setSizeLimits(minSize: Int, maxSize: Int) {
        this.minSize = minSize.coerceAtLeast(dpToPx(100))
        this.maxSize = maxSize.coerceAtMost(getScreenSize().first)
    }

    /**
     * Reset to original size and position
     */
    fun reset() {
        currentScale = 1f
        targetScale = 1f
        posX = dpToPx(50).toFloat()
        posY = dpToPx(50).toFloat()
        applyScale(1f)
        applyPosition()
    }

    // ============================================
    // ANIMATIONS
    // ============================================

    private fun animateToPosition(targetX: Float, targetY: Float) {
        val startX = posX
        val startY = posY

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 300
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                val fraction = animator.animatedValue as Float
                posX = startX + (targetX - startX) * fraction
                posY = startY + (targetY - startY) * fraction
                applyPosition()
            }
            start()
        }
    }

    private fun animateScale(fromScale: Float, toScale: Float) {
        ValueAnimator.ofFloat(fromScale, toScale).apply {
            duration = 300
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                val scale = animator.animatedValue as Float
                applyScale(scale)
            }
            start()
        }
    }

    // ============================================
    // UTILITIES
    // ============================================

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    private fun getScreenSize(): Pair<Int, Int> {
        return Pair(
            context.resources.displayMetrics.widthPixels,
            context.resources.displayMetrics.heightPixels
        )
    }

    // ============================================
    // SAVE/RESTORE STATE
    // ============================================

    /**
     * Save container state
     */
    fun saveState(): ContainerState {
        return ContainerState(
            posX = posX,
            posY = posY,
            baseWidth = baseWidth,
            baseHeight = baseHeight,
            scale = currentScale
        )
    }

    /**
     * Restore container state
     */
    fun restoreState(state: ContainerState) {
        posX = state.posX
        posY = state.posY
        baseWidth = state.baseWidth
        baseHeight = state.baseHeight
        currentScale = state.scale
        targetScale = state.scale

        layoutParams = layoutParams?.apply {
            width = baseWidth
            height = baseHeight
        } ?: ViewGroup.LayoutParams(baseWidth, baseHeight)

        applyScale(currentScale)
        applyPosition()
    }

    data class ContainerState(
        val posX: Float,
        val posY: Float,
        val baseWidth: Int,
        val baseHeight: Int,
        val scale: Float
    )
}