package com.infusory.tutarapp.ui.ai

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.Toast
import androidx.camera.view.PreviewView
import androidx.core.view.children

class ScreenAnalyzerView(
    context: Context,
    private val captureView: View,
    private val onAnalysisComplete: (Bitmap?) -> Unit
) : View(context) {

    private val borderPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f  // Reduced from 12f
        isAntiAlias = true
    }

    // Glow effect paints - multiple layers for enhanced glow
    private val glowPaint1 = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 35f  // Increased from 20f
        isAntiAlias = true
        alpha = 140  // Increased from 100
        maskFilter = BlurMaskFilter(25f, BlurMaskFilter.Blur.NORMAL)  // Increased from 15f
    }

    private val glowPaint2 = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 55f  // Increased from 30f
        isAntiAlias = true
        alpha = 90  // Increased from 60
        maskFilter = BlurMaskFilter(40f, BlurMaskFilter.Blur.NORMAL)  // Increased from 25f
    }

    private val glowPaint3 = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 75f  // Increased from 40f
        isAntiAlias = true
        alpha = 50  // Increased from 30
        maskFilter = BlurMaskFilter(55f, BlurMaskFilter.Blur.NORMAL)  // Increased from 35f
    }

    private var gradientOffset = 0f
    private var isAnimating = false

    // Gradient colors similar to Google Gemini
    private val gradientColors = intArrayOf(
        Color.argb(255, 138, 43, 226),   // Purple
        Color.argb(255, 255, 105, 180),  // Hot Pink
        Color.argb(255, 255, 165, 0),    // Orange
        Color.argb(255, 255, 215, 0),    // Gold
        Color.argb(255, 0, 191, 255),    // Deep Sky Blue
        Color.argb(255, 138, 43, 226)    // Purple (loop)
    )

    private val borderAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2000 // 2 seconds animation
        interpolator = LinearInterpolator()
        addUpdateListener { animator ->
            gradientOffset = animator.animatedValue as Float
            invalidate()
        }
        addListener(object : android.animation.Animator.AnimatorListener {
            override fun onAnimationStart(animation: android.animation.Animator) {
                isAnimating = true
            }
            override fun onAnimationEnd(animation: android.animation.Animator) {
                isAnimating = false
                // Start capture after animation
                performCapture()
            }
            override fun onAnimationCancel(animation: android.animation.Animator) {
                isAnimating = false
            }
            override fun onAnimationRepeat(animation: android.animation.Animator) {}
        })
    }

    init {
        // Use software layer for blur effects
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            setLayerType(LAYER_TYPE_SOFTWARE, null)
        }
    }

    fun startAnalysis() {
        borderAnimator.start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (isAnimating) {
            drawAnimatedBorder(canvas)
        }
    }

    private fun drawAnimatedBorder(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val strokeHalf = borderPaint.strokeWidth / 2

        // Create animated gradient that moves around the border
        val path = Path().apply {
            // Start from top-left, go clockwise
            moveTo(strokeHalf, strokeHalf)
            lineTo(w - strokeHalf, strokeHalf) // Top edge
            lineTo(w - strokeHalf, h - strokeHalf) // Right edge
            lineTo(strokeHalf, h - strokeHalf) // Bottom edge
            lineTo(strokeHalf, strokeHalf) // Left edge
            close()
        }

        // Create sweeping gradient effect
        val gradient = LinearGradient(
            0f,
            0f,
            w,
            h,
            gradientColors,
            null,
            Shader.TileMode.MIRROR
        )

        // Apply rotation matrix to make gradient sweep
        val matrix = Matrix()
        matrix.setRotate(gradientOffset * 360f, w / 2, h / 2)
        gradient.setLocalMatrix(matrix)

        // Apply gradient to all paint layers
        borderPaint.shader = gradient
        glowPaint1.shader = gradient
        glowPaint2.shader = gradient
        glowPaint3.shader = gradient

        // Draw glow layers from outermost to innermost for depth
        canvas.drawPath(path, glowPaint3)
        canvas.drawPath(path, glowPaint2)
        canvas.drawPath(path, glowPaint1)

        // Draw the main border on top
        canvas.drawPath(path, borderPaint)

        // Add corner highlights for extra effect
        drawCornerHighlights(canvas)
    }

    private fun drawCornerHighlights(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cornerSize = 100f // Increased from 60f for bigger glow

        val highlightPaint = Paint().apply {
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val corners = arrayOf(
            PointF(0f, 0f),           // Top-left
            PointF(w, 0f),            // Top-right
            PointF(w, h),             // Bottom-right
            PointF(0f, h)             // Bottom-left
        )

        corners.forEachIndexed { index, corner ->
            // Offset each corner's pulse phase
            val phaseOffset = index * 0.25f
            val localPulse = (200 + 55 * Math.sin((gradientOffset + phaseOffset) * Math.PI * 4)).toInt()

            val gradient = RadialGradient(
                corner.x, corner.y, cornerSize,
                intArrayOf(
                    Color.argb(localPulse, 255, 255, 255),
                    Color.argb((localPulse * 0.7).toInt(), 255, 255, 255),
                    Color.argb((localPulse * 0.4).toInt(), 255, 255, 255),
                    Color.argb(0, 255, 255, 255)
                ),
                floatArrayOf(0f, 0.3f, 0.6f, 1f),
                Shader.TileMode.CLAMP
            )
            highlightPaint.shader = gradient
            highlightPaint.maskFilter = BlurMaskFilter(35f, BlurMaskFilter.Blur.NORMAL)  // Increased from 20f
            canvas.drawCircle(corner.x, corner.y, cornerSize, highlightPaint)
        }
    }

    private fun performCapture() {
        android.util.Log.d("ScreenAnalyzerView", "Starting full screen capture")
        captureFullScreenshot { bitmap ->
            android.util.Log.d("ScreenAnalyzerView", "Capture callback - bitmap: ${bitmap != null}")
            if (bitmap != null) {
                onAnalysisComplete(bitmap)
            } else {
                Toast.makeText(
                    context,
                    "Failed to capture screenshot",
                    Toast.LENGTH_SHORT
                ).show()
                onAnalysisComplete(null)
            }
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
            android.util.Log.e("ScreenAnalyzerView", "Error creating bitmap", e)
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

            android.util.Log.d("ScreenAnalyzerView", "Found camera preview: ${cameraPreview != null}")
            android.util.Log.d("ScreenAnalyzerView", "Found ${allSurfaceViews.size} surface views")

            // If camera preview exists and is visible, capture it first at full screen
            if (cameraPreview != null && cameraPreview.visibility == View.VISIBLE) {
                android.util.Log.d("ScreenAnalyzerView", "Camera is active, capturing camera preview first")

                // Find the PreviewView's internal SurfaceView
                val previewSurfaceViews = findAllSurfaceViews(cameraPreview)

                if (previewSurfaceViews.isNotEmpty()) {
                    // Capture camera preview surface views at full screen size
                    captureCameraPreview(previewSurfaceViews, canvas) { cameraSuccess ->
                        android.util.Log.d("ScreenAnalyzerView", "Camera capture complete: $cameraSuccess")

                        // Then draw the regular view hierarchy on top (without the camera preview views)
                        captureView.draw(canvas)

                        // Capture remaining surface views (excluding camera preview)
                        val otherSurfaceViews = allSurfaceViews.filter { sv ->
                            !previewSurfaceViews.contains(sv)
                        }

                        if (otherSurfaceViews.isNotEmpty()) {
                            captureSurfaceViews(otherSurfaceViews, canvas) { success ->
                                visibility = originalVisibility
                                callback(screenshot)
                            }
                        } else {
                            visibility = originalVisibility
                            callback(screenshot)
                        }
                    }
                } else {
                    // No surface views in camera preview, just draw normally
                    captureView.draw(canvas)
                    captureSurfaceViews(allSurfaceViews, canvas) { success ->
                        visibility = originalVisibility
                        callback(screenshot)
                    }
                }
            } else {
                // No camera active, normal capture
                android.util.Log.d("ScreenAnalyzerView", "No camera active, normal capture")
                captureView.draw(canvas)

                if (allSurfaceViews.isEmpty()) {
                    visibility = originalVisibility
                    callback(screenshot)
                    return
                }

                captureSurfaceViews(allSurfaceViews, canvas) { success ->
                    visibility = originalVisibility
                    callback(screenshot)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ScreenAnalyzerView", "Error in captureViewHierarchy", e)
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

    private fun captureCameraPreview(
        surfaceViews: List<SurfaceView>,
        targetCanvas: Canvas,
        onComplete: (Boolean) -> Unit
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            onComplete(false)
            return
        }

        if (surfaceViews.isEmpty()) {
            onComplete(false)
            return
        }

        var completedCount = 0
        var successCount = 0

        surfaceViews.forEach { surfaceView ->
            val surface = surfaceView.holder.surface

            android.util.Log.d("ScreenAnalyzerView", "Camera SurfaceView size: ${surfaceView.width}x${surfaceView.height}")
            android.util.Log.d("ScreenAnalyzerView", "Target canvas size: ${targetCanvas.width}x${targetCanvas.height}")

            if (!surface.isValid) {
                android.util.Log.w("ScreenAnalyzerView", "Camera surface not valid")
                completedCount++
                if (completedCount == surfaceViews.size) {
                    onComplete(successCount > 0)
                }
                return@forEach
            }

            try {
                // Capture at the surface's actual size - use ARGB_8888 to avoid hardware bitmap
                val surfaceBitmap = Bitmap.createBitmap(
                    surfaceView.width,
                    surfaceView.height,
                    Bitmap.Config.ARGB_8888
                )

                android.util.Log.d("ScreenAnalyzerView", "Capturing camera at surface size: ${surfaceView.width}x${surfaceView.height}")

                PixelCopy.request(
                    surface,
                    surfaceBitmap,
                    { result ->
                        when (result) {
                            PixelCopy.SUCCESS -> {
                                android.util.Log.d("ScreenAnalyzerView", "Camera capture successful, scaling to full screen")

                                try {
                                    // Convert to software bitmap if it's a hardware bitmap
                                    val softwareBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                                        surfaceBitmap.config == Bitmap.Config.HARDWARE) {
                                        surfaceBitmap.copy(Bitmap.Config.ARGB_8888, false)
                                    } else {
                                        surfaceBitmap
                                    }

                                    // Scale and draw the camera preview to fill the entire screen
                                    val destRect = RectF(0f, 0f, targetCanvas.width.toFloat(), targetCanvas.height.toFloat())
                                    val srcRect = Rect(0, 0, softwareBitmap.width, softwareBitmap.height)

                                    val paint = Paint().apply {
                                        isAntiAlias = true
                                        isFilterBitmap = true
                                    }

                                    targetCanvas.drawBitmap(softwareBitmap, srcRect, destRect, paint)

                                    // Clean up
                                    if (softwareBitmap != surfaceBitmap) {
                                        softwareBitmap.recycle()
                                    }

                                    successCount++
                                } catch (e: Exception) {
                                    android.util.Log.e("ScreenAnalyzerView", "Error drawing camera bitmap", e)
                                }
                            }
                            else -> {
                                android.util.Log.e("ScreenAnalyzerView", "Camera PixelCopy failed with result: $result")
                            }
                        }

                        surfaceBitmap.recycle()
                        completedCount++

                        if (completedCount == surfaceViews.size) {
                            android.util.Log.d("ScreenAnalyzerView", "Camera capture complete: $successCount successful")
                            onComplete(successCount > 0)
                        }
                    },
                    Handler(Looper.getMainLooper())
                )
            } catch (e: Exception) {
                android.util.Log.e("ScreenAnalyzerView", "Error capturing camera SurfaceView", e)
                completedCount++

                if (completedCount == surfaceViews.size) {
                    onComplete(successCount > 0)
                }
            }
        }
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
                android.util.Log.e("ScreenAnalyzerView", "Error capturing SurfaceView", e)
                completedCount++

                if (completedCount == surfaceViews.size) {
                    onComplete(true)
                }
            }
        }
    }

    fun reset() {
        borderAnimator.cancel()
        isAnimating = false
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        borderAnimator.cancel()
    }
}