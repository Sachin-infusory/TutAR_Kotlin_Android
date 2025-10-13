package com.infusory.tutarapp.ui.ai

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.Toast
import androidx.camera.view.PreviewView
import androidx.core.view.children

class DrawView(
    context: Context,
    private val captureView: View,
    private val onSelectionComplete: (Bitmap?) -> Unit
) : View(context) {

    // Paint for the static gradient overlay (no animation during drawing)
    private val overlayPaint = Paint().apply {
        style = Paint.Style.FILL
    }

    private var overlayGradient: LinearGradient? = null

    // Paint for the circle stroke (simplified - no shadow during drawing)
    private val strokePaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
    }

    // Paint for clearing the overlay inside the circle
    private val clearPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        isAntiAlias = true
    }

    private val path = Path()
    private val pathPoints = mutableListOf<PointF>()
    private var startX = 0f
    private var startY = 0f
    private var isDrawing = false
    private var isShowingLoadingAnimation = false

    // Loading animation properties
    private var loadingProgress = 0f
    private var sparklePhase = 0f
    private val loadingAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 3000 // 3 seconds
        interpolator = LinearInterpolator()
        addUpdateListener { animator ->
            loadingProgress = animator.animatedValue as Float
            sparklePhase = (loadingProgress * 360f) % 360f
            invalidate()
        }
        addListener(object : android.animation.Animator.AnimatorListener {
            override fun onAnimationStart(animation: android.animation.Animator) {
                isShowingLoadingAnimation = true
            }
            override fun onAnimationEnd(animation: android.animation.Animator) {
                isShowingLoadingAnimation = false
                // Start capture after animation
                performCapture()
            }
            override fun onAnimationCancel(animation: android.animation.Animator) {
                isShowingLoadingAnimation = false
            }
            override fun onAnimationRepeat(animation: android.animation.Animator) {}
        })
    }

    init {
        // Use hardware acceleration when possible
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            setLayerType(LAYER_TYPE_HARDWARE, null)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Create static gradient once when view size is determined
        if (w > 0 && h > 0) {
            overlayGradient = LinearGradient(
                0f, 0f,
                w.toFloat(), h.toFloat(),
                intArrayOf(
                    Color.argb(60, 138, 43, 226),   // Purple
                    Color.argb(60, 75, 0, 130),     // Indigo
                    Color.argb(60, 0, 191, 255),    // Deep Sky Blue
                    Color.argb(60, 138, 43, 226)    // Purple
                ),
                floatArrayOf(0f, 0.33f, 0.66f, 1f),
                Shader.TileMode.CLAMP
            )
            overlayPaint.shader = overlayGradient
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isDrawing = true
                startX = event.x
                startY = event.y
                path.moveTo(startX, startY)
                pathPoints.clear()
                pathPoints.add(PointF(startX, startY))
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDrawing) {
                    path.lineTo(event.x, event.y)
                    // Throttle point additions to reduce memory
                    if (pathPoints.size < 500) {
                        pathPoints.add(PointF(event.x, event.y))
                    }
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                if (isDrawing) {
                    isDrawing = false
                    path.lineTo(startX, startY)
                    path.close()
                    pathPoints.add(PointF(startX, startY))

                    // Start 3-second loading animation
                    loadingAnimator.start()
                }
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (isShowingLoadingAnimation) {
            // Show loading animation
            drawLoadingAnimation(canvas)
        } else {
            // Normal drawing mode
            drawNormalMode(canvas)
        }
    }

    private fun drawNormalMode(canvas: Canvas) {
        // Draw simple gradient overlay - fill entire canvas
        canvas.drawPaint(overlayPaint)

        // If user has drawn a path, clear the overlay inside it
        if (pathPoints.size > 2) {
            // Use a single layer for efficiency
            val layerId = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
            canvas.drawPaint(overlayPaint)
            canvas.drawPath(path, clearPaint)
            canvas.restoreToCount(layerId)

            // Draw simple stroke
            canvas.drawPath(path, strokePaint)
        }
    }

    private fun drawLoadingAnimation(canvas: Canvas) {
        // Draw gradient overlay - fill entire canvas
        canvas.drawPaint(overlayPaint)

        if (pathPoints.size > 2) {
            // Clear inside path
            val layerId = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
            canvas.drawPaint(overlayPaint)
            canvas.drawPath(path, clearPaint)
            canvas.restoreToCount(layerId)

            // Pulsing glow effect
            val pulseAlpha = (128 + 127 * Math.sin(loadingProgress * Math.PI * 6)).toInt()

            // Draw outer glow
            val glowPaint = Paint().apply {
                color = Color.argb(pulseAlpha / 2, 100, 200, 255)
                style = Paint.Style.STROKE
                strokeWidth = 16f
                isAntiAlias = true
            }
            canvas.drawPath(path, glowPaint)

            // Draw main stroke with pulse
            val animatedStrokePaint = Paint(strokePaint).apply {
                alpha = pulseAlpha
                strokeWidth = 6f + 2f * Math.sin(loadingProgress * Math.PI * 4).toFloat()
            }
            canvas.drawPath(path, animatedStrokePaint)

            // Draw twinkling sparkles
            drawTwinklingSparkles(canvas)

            // Draw traveling light effect
            drawTravelingLight(canvas)
        }
    }

    private fun drawTwinklingSparkles(canvas: Canvas) {
        val sparkleCount = 12
        val sparklePaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        for (i in 0 until sparkleCount) {
            // Each sparkle has its own phase offset
            val sparkleOffset = (i * 30f)
            val sparklePhaseLocal = (sparklePhase + sparkleOffset) % 360f

            // Twinkle intensity (fade in/out)
            val twinkleIntensity = Math.abs(Math.sin(Math.toRadians(sparklePhaseLocal.toDouble()))).toFloat()

            val index = (pathPoints.size * i / sparkleCount).coerceIn(0, pathPoints.size - 1)
            val point = pathPoints[index]

            // Size varies with twinkle
            val sparkleSize = 3f + twinkleIntensity * 5f
            val alpha = (128 + 127 * twinkleIntensity).toInt()

            sparklePaint.alpha = alpha

            // Draw star-shaped sparkle
            val halfSize = sparkleSize / 2

            // Horizontal line
            canvas.drawLine(
                point.x - sparkleSize * 1.5f, point.y,
                point.x + sparkleSize * 1.5f, point.y,
                sparklePaint.apply { strokeWidth = 2f }
            )

            // Vertical line
            canvas.drawLine(
                point.x, point.y - sparkleSize * 1.5f,
                point.x, point.y + sparkleSize * 1.5f,
                sparklePaint.apply { strokeWidth = 2f }
            )

            // Center dot
            canvas.drawCircle(point.x, point.y, halfSize, sparklePaint)
        }
    }

    private fun drawTravelingLight(canvas: Canvas) {
        if (pathPoints.size < 2) return

        // Calculate position along path based on progress
        val position = loadingProgress * pathPoints.size * 3 // Travel 3 times around
        val currentIndex = (position.toInt() % pathPoints.size)
        val point = pathPoints[currentIndex]

        // Draw traveling light orb
        val orbPaint = Paint().apply {
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        // Outer glow
        val outerGradient = RadialGradient(
            point.x, point.y, 30f,
            intArrayOf(
                Color.argb(80, 100, 200, 255),
                Color.argb(0, 100, 200, 255)
            ),
            null,
            Shader.TileMode.CLAMP
        )
        orbPaint.shader = outerGradient
        canvas.drawCircle(point.x, point.y, 30f, orbPaint)

        // Inner bright core
        val innerGradient = RadialGradient(
            point.x, point.y, 12f,
            intArrayOf(
                Color.WHITE,
                Color.argb(200, 150, 220, 255),
                Color.argb(0, 100, 200, 255)
            ),
            null,
            Shader.TileMode.CLAMP
        )
        orbPaint.shader = innerGradient
        canvas.drawCircle(point.x, point.y, 12f, orbPaint)
    }

    private fun performCapture() {
        android.util.Log.d("DrawView", "Starting capture after animation")
        captureFullScreenshot { bitmap ->
            android.util.Log.d("DrawView", "Capture callback - bitmap: ${bitmap != null}")
            if (bitmap != null) {
                val croppedBitmap = cropToPath(bitmap)
                bitmap.recycle()
                onSelectionComplete(croppedBitmap)
            } else {
                Toast.makeText(
                    context,
                    "Failed to capture screenshot",
                    Toast.LENGTH_SHORT
                ).show()
                onSelectionComplete(null)
            }
        }
    }

    private fun cropToPath(fullScreenshot: Bitmap): Bitmap? {
        try {
            val bounds = RectF()
            path.computeBounds(bounds, true)

            val padding = 10f
            val left = (bounds.left - padding).toInt().coerceAtLeast(0)
            val top = (bounds.top - padding).toInt().coerceAtLeast(0)
            val right = (bounds.right + padding).toInt().coerceAtMost(fullScreenshot.width)
            val bottom = (bounds.bottom + padding).toInt().coerceAtMost(fullScreenshot.height)

            val width = right - left
            val height = bottom - top

            if (width <= 0 || height <= 0) {
                android.util.Log.e("DrawView", "Invalid crop dimensions: ${width}x${height}")
                return null
            }

            val croppedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(croppedBitmap)

            val translatedPath = Path(path)
            translatedPath.offset(-left.toFloat(), -top.toFloat())

            canvas.clipPath(translatedPath)
            canvas.drawBitmap(
                fullScreenshot,
                Rect(left, top, right, bottom),
                Rect(0, 0, width, height),
                null
            )

            return croppedBitmap
        } catch (e: Exception) {
            android.util.Log.e("DrawView", "Error cropping bitmap", e)
            return null
        }
    }

    private fun captureFullScreenshot(callback: (Bitmap?) -> Unit) {
        if (captureView.width <= 0 || captureView.height <= 0) {
            callback(null)
            return
        }

        try {
            val screenshot = Bitmap.createBitmap(
                captureView.width,
                captureView.height,
                Bitmap.Config.ARGB_8888
            )

            val wasVisible = visibility
            visibility = View.GONE

            postDelayed({
                captureViewHierarchy(screenshot, callback, wasVisible)
            }, 50)

        } catch (e: Exception) {
            android.util.Log.e("DrawView", "Error creating bitmap", e)
            callback(null)
        }
    }

    private fun captureViewHierarchy(
        screenshot: Bitmap,
        callback: (Bitmap?) -> Unit,
        originalVisibility: Int
    ) {
        try {
            val canvas = Canvas(screenshot)

            // Find camera preview and all surface views
            val cameraPreview = findCameraPreview(captureView)
            val allSurfaceViews = findAllSurfaceViews(captureView)

            android.util.Log.d("DrawView", "Found camera preview: ${cameraPreview != null}")
            android.util.Log.d("DrawView", "Found ${allSurfaceViews.size} surface views")

            // Draw the regular view hierarchy (this will skip camera preview rendering)
            captureView.draw(canvas)

            // Filter out camera preview surface views from capture
            val nonCameraSurfaceViews = if (cameraPreview != null) {
                val previewSurfaceViews = findAllSurfaceViews(cameraPreview)
                allSurfaceViews.filter { sv -> !previewSurfaceViews.contains(sv) }
            } else {
                allSurfaceViews
            }

            android.util.Log.d("DrawView", "Capturing ${nonCameraSurfaceViews.size} non-camera surface views")

            if (nonCameraSurfaceViews.isEmpty()) {
                visibility = originalVisibility
                callback(screenshot)
                return
            }

            captureSurfaceViews(nonCameraSurfaceViews, canvas) { success ->
                visibility = originalVisibility
                callback(screenshot)
            }
        } catch (e: Exception) {
            android.util.Log.e("DrawView", "Error in captureViewHierarchy", e)
            visibility = originalVisibility
            callback(null)
        }
    }

    private fun findCameraPreview(view: View): PreviewView? {
        if (view is PreviewView) {
            return view
        }

        if (view is ViewGroup) {
            for (child in view.children) {
                val found = findCameraPreview(child)
                if (found != null) return found
            }
        }

        return null
    }

    private fun findAllSurfaceViews(view: View): List<SurfaceView> {
        val surfaceViews = mutableListOf<SurfaceView>()

        if (view is SurfaceView) {
            surfaceViews.add(view)
        }

        if (view is ViewGroup) {
            for (child in view.children) {
                surfaceViews.addAll(findAllSurfaceViews(child))
            }
        }

        return surfaceViews
    }

    private fun captureSurfaceViews(
        surfaceViews: List<SurfaceView>,
        targetCanvas: Canvas,
        onComplete: (Boolean) -> Unit
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            onComplete(true)
            return
        }

        if (surfaceViews.isEmpty()) {
            onComplete(true)
            return
        }

        var completedCount = 0
        var successCount = 0

        surfaceViews.forEach { surfaceView ->
            val surface = surfaceView.holder.surface

            if (!surface.isValid) {
                completedCount++
                if (completedCount == surfaceViews.size) {
                    onComplete(true)
                }
                return@forEach
            }

            try {
                val surfaceBitmap = Bitmap.createBitmap(
                    surfaceView.width,
                    surfaceView.height,
                    Bitmap.Config.ARGB_8888
                )

                val parentLocation = IntArray(2)
                captureView.getLocationInWindow(parentLocation)

                val location = IntArray(2)
                surfaceView.getLocationInWindow(location)

                location[0] -= parentLocation[0]
                location[1] -= parentLocation[1]

                PixelCopy.request(
                    surface,
                    surfaceBitmap,
                    { result ->
                        when (result) {
                            PixelCopy.SUCCESS -> {
                                targetCanvas.drawBitmap(
                                    surfaceBitmap,
                                    location[0].toFloat(),
                                    location[1].toFloat(),
                                    null
                                )
                                successCount++
                            }
                        }

                        surfaceBitmap.recycle()
                        completedCount++

                        if (completedCount == surfaceViews.size) {
                            onComplete(true)
                        }
                    },
                    Handler(Looper.getMainLooper())
                )
            } catch (e: Exception) {
                android.util.Log.e("DrawView", "Error capturing SurfaceView", e)
                completedCount++

                if (completedCount == surfaceViews.size) {
                    onComplete(true)
                }
            }
        }
    }

    fun reset() {
        path.reset()
        pathPoints.clear()
        loadingAnimator.cancel()
        isShowingLoadingAnimation = false
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        loadingAnimator.cancel()
    }
}