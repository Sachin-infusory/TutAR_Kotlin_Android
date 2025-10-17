package com.infusory.tutarapp.ui.components.containers

import com.infusory.tutarapp.R
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Choreographer
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.SurfaceView
import android.widget.TextView
import android.widget.LinearLayout
import android.widget.ImageView
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import com.google.android.filament.View
import com.google.android.filament.android.UiHelper
import com.google.android.filament.utils.AutomationEngine
import com.google.android.filament.utils.KTX1Loader
import com.google.android.filament.utils.ModelViewer
import com.google.android.filament.utils.Utils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import android.graphics.PixelFormat
import com.infusory.tutarapp.ui.data.ModelData
import com.infusory.tutarapp.utils.ModelDecryptionUtil
import java.io.File
import kotlin.math.pow
import kotlin.math.sqrt

class Container3D @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ContainerBase(context, ContainerType.MODEL_3D, attrs, defStyleAttr) {

    companion object {
        init {
            Utils.init()
        }
    }

    // Filament components
    private var surfaceView: SurfaceView? = null
    private var uiHelper: UiHelper? = null
    private var modelViewer: ModelViewer? = null
    private val viewerContent = AutomationEngine.ViewerContent()
    private var frameCallback: FrameCallback? = null
    private var choreographer: Choreographer? = null

    private var touchEnabled = false
    private var passThroughTouches = true

    // Layout components
    private var controlsContainer: LinearLayout? = null
    private var contentContainer: android.widget.FrameLayout? = null
    private var animationToggleButton: ImageView? = null

    // Store all side control buttons for auto-hide
    private val sideControlButtons = mutableListOf<ImageView>()

    // Animation state
    private var currentAnimationIndex = 0
    private var animationStartTime = System.nanoTime()
    private var currentAnimationDuration = 0f
    private var isAnimationPaused = false
    private var pausedAnimationTime = 0f
    private var animationClips = mutableListOf<String>()
    private var isPlayingAllAnimations = true

    // Container state
    private var isInitialized = false
    private var isRenderingActive = false

    // Model data
    private var modelData: ModelData? = null
    private var modelPath: String? = null

    // Auto-hide functionality
    private var autoHideEnabled = true
    private val AUTO_HIDE_DELAY = 4000L
    private var hideRunnable: Runnable? = null
    private var controlsVisible = true
    private val FADE_DURATION = 300L

    // Enhanced drag and resize from second code
    private var lastX = 0f
    private var lastY = 0f
    private var isDragging = false
    private var isResizing = false
    private var isPinching = false
    private var activeCorner = Corner.NONE
    private val resizeHandleSize = 24f
    private val resizeHitArea = 50f
    private var initialWidth = 0
    private var initialHeight = 0
    private var scaleFactor = 1f
    private var initialDistance = 0f
    private var currentDistance = 0f
    private var isHandling3DTouch = false

    enum class Corner {
        NONE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
    }

    // ScaleGestureDetector for pinch-to-zoom
    private val scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            isPinching = true
            initialWidth = width
            initialHeight = height
            scaleFactor = 1f
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleFactor *= detector.scaleFactor
            scaleFactor = scaleFactor.coerceIn(0.5f, 3.0f)

            val params = layoutParams
            val newWidth = (initialWidth * scaleFactor).toInt()
            val newHeight = (initialHeight * scaleFactor).toInt()

            if (newWidth >= 200 && newHeight >= 150) {
                params.width = newWidth
                params.height = newHeight
                layoutParams = params
            }
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            isPinching = false
            scaleFactor = 1f
            initialDistance = 0f
        }
    })

    init {
        // Disable background for main container since content container will have it
        showBackground = false

        // Initialize hide runnable
        hideRunnable = Runnable {
            hideControls()
        }

        setup3DContainer()
    }

    fun setTouchEnabled(enabled: Boolean) {
        touchEnabled = enabled
        updateTouchHandling()
    }

    fun setModelData(modelData: ModelData, fullPath: String) {
        this.modelData = modelData
        this.modelPath = fullPath

        if (isInitialized) {
            loadModel()
        }
    }

    fun setPassThroughTouches(enabled: Boolean) {
        passThroughTouches = enabled
        updateTouchHandling()
    }

    private fun updateTouchHandling() {
        surfaceView?.let { surface ->
            if (touchEnabled && !passThroughTouches) {
                // When interaction is enabled, intercept touches for 3D manipulation
                surface.setOnTouchListener { _, event ->
                    modelViewer?.onTouchEvent(event)
                    // Mark that we're handling 3D touch to prevent container manipulation
                    isHandling3DTouch = event.action != MotionEvent.ACTION_UP &&
                            event.action != MotionEvent.ACTION_CANCEL

                    // Show controls on interaction
                    if (event.action == MotionEvent.ACTION_DOWN) {
                        onInteractionStart()
                    } else if (event.action == MotionEvent.ACTION_UP ||
                        event.action == MotionEvent.ACTION_CANCEL) {
                        onInteractionEnd()
                    }
                    true
                }
                surface.isClickable = true
                surface.isFocusable = true
                surface.isFocusableInTouchMode = true
            } else {
                // When interaction is disabled, allow touches to pass through to container
                surface.setOnTouchListener { _, event ->
                    // Show controls on any interaction
                    if (event.action == MotionEvent.ACTION_DOWN) {
                        onInteractionStart()
                    } else if (event.action == MotionEvent.ACTION_UP ||
                        event.action == MotionEvent.ACTION_CANCEL) {
                        onInteractionEnd()
                    }
                    false
                }
                surface.isClickable = false
                surface.isFocusable = false
                surface.isFocusableInTouchMode = false
                isHandling3DTouch = false
            }
        }
    }

    private fun setup3DContainer() {
        // Setup will be done in initializeContent
    }

    override fun initializeContent() {
        if (!isInitialized) {
            if (modelData == null) {
                android.util.Log.e("Container3D", "Model data not set before initialization")
                contentContainer?.addView(createErrorView("No model data provided"))
                return
            }

            createSideControlsLayout()
            create3DView()
            isInitialized = true

            // Start initial auto-hide timer
            scheduleAutoHide()
        }
    }

    // Auto-hide functionality
    private fun scheduleAutoHide() {
        if (!autoHideEnabled) return

        cancelAutoHide()
        hideRunnable?.let {
            postDelayed(it, AUTO_HIDE_DELAY)
        }
    }

    private fun cancelAutoHide() {
        hideRunnable?.let {
            removeCallbacks(it)
        }
    }

    private fun onInteractionStart() {
        cancelAutoHide()
        showControls()
    }

    private fun onInteractionEnd() {
        scheduleAutoHide()
    }

    private fun showControls() {
        if (controlsVisible) return

        controlsVisible = true

        // Fade in side control buttons
        sideControlButtons.forEach { button ->
            button.animate()
                .alpha(1.0f)
                .setDuration(FADE_DURATION)
                .start()
        }

        // Show dotted border on content container
        contentContainer?.animate()
            ?.alpha(1f)
            ?.setDuration(FADE_DURATION)
            ?.withStartAction {
                contentContainer?.setBackgroundResource(R.drawable.dotted_border_background)
            }
            ?.start()
    }

    private fun hideControls() {
        if (!controlsVisible) return

        controlsVisible = false

        // Fade out side control buttons
        sideControlButtons.forEach { button ->
            button.animate()
                .alpha(0f)
                .setDuration(FADE_DURATION)
                .start()
        }

        // Hide dotted border on content container
        contentContainer?.animate()
            ?.alpha(1f)
            ?.setDuration(FADE_DURATION)
            ?.withEndAction {
                contentContainer?.background = null
            }
            ?.start()
    }

    fun setAutoHideEnabled(enabled: Boolean) {
        autoHideEnabled = enabled
        if (enabled) {
            scheduleAutoHide()
        } else {
            cancelAutoHide()
            showControls()
        }
    }

    private fun createSideControlsLayout() {
        val mainContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        controlsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            setPadding(0, 0, 0, 0)
            gravity = Gravity.TOP or Gravity.END
        }

        contentContainer = android.widget.FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f
            )
            setBackgroundResource(R.drawable.dotted_border_background)
            setPadding(0, dpToPx(2), dpToPx(2), dpToPx(2))
        }

        addControlButtonsToSide()

        mainContainer.addView(controlsContainer)
        mainContainer.addView(contentContainer)

        setContent(mainContainer)
    }

    private fun addControlButtonsToSide() {
        controlsContainer?.let { container ->

            val interactionButton = createSideControlButton(
                R.drawable.ic_interact,
                "Toggle Interaction"
            ) {
                onInteractionStart()
                toggleInteraction()
                onInteractionEnd()
            }
            container.addView(interactionButton)
            sideControlButtons.add(interactionButton)

            container.addView(createSpacer())

            animationToggleButton = createSideControlButton(
                R.drawable.ic_play_animation,
                "Toggle Animation"
            ) {
                onInteractionStart()
                toggleAnimation()
                onInteractionEnd()
            }
            container.addView(animationToggleButton!!)
            sideControlButtons.add(animationToggleButton!!)

            container.addView(createSpacer())

            val closeButton = createSideControlButton(
                R.drawable.ic_close_white,
                "Close"
            ) {
                onInteractionStart()
                onRemoveRequest?.invoke()
            }
            container.addView(closeButton)
            sideControlButtons.add(closeButton)
        }
    }

    private fun createSideControlButton(iconRes: Int, tooltip: String, onClick: () -> Unit): ImageView {
        return ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(32), dpToPx(32)).apply {
                setMargins(0, dpToPx(2), 0, dpToPx(2))
            }
            setImageResource(iconRes)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke(0, Color.TRANSPARENT)
            }
            scaleType = ImageView.ScaleType.CENTER
            elevation = 4f
            alpha = 1.0f
            contentDescription = tooltip

            setOnClickListener { onClick() }

            setOnTouchListener { view, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        onInteractionStart()
                        alpha = 0.6f
                        scaleX = 0.95f
                        scaleY = 0.95f
                    }
                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                        alpha = 1.0f
                        scaleX = 1.0f
                        scaleY = 1.0f
                        onInteractionEnd()
                    }
                }
                false
            }
        }
    }

    private fun createSpacer(): android.view.View {
        return android.view.View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(0)
            )
        }
    }

    private fun create3DView() {
        try {
            surfaceView = SurfaceView(context).apply {
                layoutParams = android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                )

                holder.setFormat(PixelFormat.TRANSLUCENT)
                setZOrderOnTop(true)
            }

            uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK).apply {
                isOpaque = false
            }

            modelViewer = ModelViewer(surfaceView = surfaceView!!, uiHelper = uiHelper!!)

            updateTouchHandling()

            configureViewerForTransparency()
            loadModel()
            createIndirectLight()

            contentContainer?.addView(surfaceView!!)
            startRendering()

        } catch (e: Exception) {
            contentContainer?.addView(createFallbackView())
            android.util.Log.e("Container3D", "Failed to initialize 3D view", e)
        }
    }

    private fun configureViewerForTransparency() {
        modelViewer?.view?.apply {
            blendMode = View.BlendMode.TRANSLUCENT

            renderQuality = renderQuality.apply {
                hdrColorBuffer = View.QualityLevel.LOW
            }

            dynamicResolutionOptions = dynamicResolutionOptions.apply {
                enabled = false
                quality = View.QualityLevel.LOW
            }

            multiSampleAntiAliasingOptions = multiSampleAntiAliasingOptions.apply {
                enabled = false
            }

            antiAliasing = View.AntiAliasing.FXAA

            ambientOcclusionOptions = ambientOcclusionOptions.apply {
                enabled = false
            }

            bloomOptions = bloomOptions.apply {
                enabled = false
            }

            screenSpaceReflectionsOptions = screenSpaceReflectionsOptions.apply {
                enabled = false
            }

            temporalAntiAliasingOptions = temporalAntiAliasingOptions.apply {
                enabled = false
            }

            fogOptions = fogOptions.apply {
                enabled = false
            }

            depthOfFieldOptions = depthOfFieldOptions.apply {
                enabled = false
            }

            vignetteOptions = vignetteOptions.apply {
                enabled = false
            }
        }

        modelViewer?.renderer?.let { renderer ->
            renderer.clearOptions?.let { clearOptions ->
                clearOptions.clearColor = floatArrayOf(0.0f, 0.0f, 0.0f, 0.0f)
                clearOptions.clear = true
                renderer.setClearOptions(clearOptions)
            }
        }
    }

    private fun loadModel() {
        val currentModelData = modelData
        if (currentModelData == null) {
            android.util.Log.e("Container3D", "No model data available for loading")
            return
        }

        try {
            val buffer = loadModelFromAssets(currentModelData.filename)

            buffer?.let { modelBuffer ->
                modelViewer?.apply {
                    loadModelGlb(modelBuffer)
                    transformToUnitCube()

                    val scene = this.scene
                    val asset = this.asset

                    asset?.let {
                        it.entities.forEach { entity -> scene.removeEntity(entity) }

                        if (it.entities.isNotEmpty()) {
                            scene.addEntity(it.entities[0])
                        }
                    }

                    initializeAnimations()
                }
                android.util.Log.d("Container3D", "Successfully loaded model: ${currentModelData.name}")
            } ?: run {
                android.util.Log.e("Container3D", "Failed to load model buffer for: ${currentModelData.filename}")
                contentContainer?.removeAllViews()
                contentContainer?.addView(createErrorView("Model file not found: ${currentModelData.filename}"))
            }
        } catch (e: Exception) {
            android.util.Log.e("Container3D", "Failed to load model: ${currentModelData.name}", e)
            contentContainer?.removeAllViews()
            contentContainer?.addView(createErrorView("Error loading model: ${e.message}"))
        }
    }

    private fun loadModelFromAssets(filename: String): ByteBuffer? {
        return try {
            val actualFilename = filename.substringAfterLast('/')
            android.util.Log.d("Container3D", "Loading model - Original: $filename, Extracted: $actualFilename")

            val encryptedModelsDir = File(context.filesDir, "encrypted_models")
            val modelFile = File(encryptedModelsDir, actualFilename)

            if (modelFile.exists() && modelFile.canRead()) {
                android.util.Log.d("Container3D", "Found encrypted model: ${modelFile.absolutePath}")

                val decryptedBuffer = ModelDecryptionUtil.decryptModelFile(modelFile)
                if (decryptedBuffer != null) {
                    return decryptedBuffer
                } else {
                    android.util.Log.e("Container3D", "Failed to decrypt model: $actualFilename")
                }
            }

            val externalModelsDir = File(context.getExternalFilesDir(null), "encrypted_models")
            val externalModelFile = File(externalModelsDir, actualFilename)

            if (externalModelFile.exists() && externalModelFile.canRead()) {
                android.util.Log.d("Container3D", "Found encrypted model in external storage: ${externalModelFile.absolutePath}")

                val decryptedBuffer = ModelDecryptionUtil.decryptModelFile(externalModelFile)
                if (decryptedBuffer != null) {
                    return decryptedBuffer
                }
            }

            android.util.Log.w("Container3D", "Model not found in storage, trying assets: $actualFilename")
            tryLoadFromAssets(actualFilename)

        } catch (e: Exception) {
            android.util.Log.e("Container3D", "Failed to load model: $filename", e)
            val actualFilename = filename.substringAfterLast('/')
            tryLoadFromAssets(actualFilename)
        }
    }

    private fun tryLoadFromAssets(filename: String): ByteBuffer? {
        return try {
            val assetPath = "models/$filename"
            context.assets.open(assetPath).use { input ->
                android.util.Log.d("Container3D", "Loaded model from assets: $assetPath")
                createBufferFromStream(input)
            }
        } catch (e: Exception) {
            try {
                context.assets.open(filename).use { input ->
                    android.util.Log.d("Container3D", "Loaded model from assets: $filename")
                    createBufferFromStream(input)
                }
            } catch (e2: Exception) {
                android.util.Log.e("Container3D", "Model file not found in assets: $filename", e2)
                null
            }
        }
    }

    private fun createBufferFromStream(inputStream: java.io.InputStream): ByteBuffer {
        val bytes = ByteArray(inputStream.available())
        inputStream.read(bytes)
        return ByteBuffer.allocateDirect(bytes.size).apply {
            order(ByteOrder.nativeOrder())
            put(bytes)
            rewind()
        }
    }

    private fun createIndirectLight() {
        try {
            val ibl = "default_env_ibl.ktx"
            val buffer = readCompressedAsset(ibl)

            modelViewer?.let { viewer ->
                val indirectLight = KTX1Loader.createIndirectLight(viewer.engine, buffer)
                indirectLight.intensity = 30_000.0f

                viewer.scene.indirectLight = indirectLight
                viewerContent.indirectLight = indirectLight
            }
        } catch (e: Exception) {
            android.util.Log.e("Container3D", "Failed to create indirect light", e)
        }
    }

    private fun readCompressedAsset(assetName: String): ByteBuffer {
        val input = context.assets.open(assetName)
        val bytes = ByteArray(input.available())
        input.read(bytes)
        return ByteBuffer.wrap(bytes)
    }

    private fun initializeAnimations() {
        modelViewer?.animator?.let { animator ->
            animationClips.clear()

            for (i in 0 until animator.animationCount) {
                val animName = animator.getAnimationName(i)
                animationClips.add(animName)
                android.util.Log.d("Container3D", "Found animation: $animName (duration: ${animator.getAnimationDuration(i)}s)")
            }

            if (animator.animationCount > 1) {
                isPlayingAllAnimations = true
                android.util.Log.d("Container3D", "Model has ${animator.animationCount} animations - using combined playback mode")
            } else {
                isPlayingAllAnimations = false
                android.util.Log.d("Container3D", "Model has single animation - using individual playback mode")
            }

            animationStartTime = System.nanoTime()
            currentAnimationDuration = 0f
            pausedAnimationTime = 0f
        }
    }

    private fun startRendering() {
        if (!isRenderingActive) {
            choreographer = Choreographer.getInstance()
            frameCallback = FrameCallback()
            choreographer?.postFrameCallback(frameCallback)
            isRenderingActive = true
        }
    }

    private fun stopRendering() {
        frameCallback?.let { callback ->
            choreographer?.removeFrameCallback(callback)
        }
        frameCallback = null
        isRenderingActive = false
    }

    private inner class FrameCallback : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (isInitialized && isRenderingActive) {
                choreographer?.postFrameCallback(this)
                modelViewer?.render(frameTimeNanos)
                if (!isAnimationPaused) {
                    handleAnimation(frameTimeNanos)
                }
            }
        }

        private fun handleAnimation(frameTimeNanos: Long) {
            modelViewer?.animator?.apply {
                if (animationCount > 0) {
                    if (isPlayingAllAnimations) {
                        playAllAnimationsTogether(frameTimeNanos)
                    } else {
                        playIndividualAnimation(frameTimeNanos)
                    }
                    updateBoneMatrices()
                }
            }
        }

        private fun playAllAnimationsTogether(frameTimeNanos: Long) {
            modelViewer?.animator?.apply {
                val elapsedTimeSeconds = (frameTimeNanos - animationStartTime).toDouble() / 1000000000

                var maxDuration = 0f
                for (i in 0 until animationCount) {
                    val duration = getAnimationDuration(i)
                    if (duration > maxDuration) {
                        maxDuration = duration
                    }
                }

                if (maxDuration > 0f && elapsedTimeSeconds >= maxDuration) {
                    animationStartTime = frameTimeNanos
                }

                for (i in 0 until animationCount) {
                    val animTime = (elapsedTimeSeconds % getAnimationDuration(i).toDouble()).toFloat()
                    applyAnimation(i, animTime)
                }
            }
        }

        private fun playIndividualAnimation(frameTimeNanos: Long) {
            modelViewer?.animator?.apply {
                val elapsedTimeSeconds = (frameTimeNanos - animationStartTime).toDouble() / 1000000000

                if (currentAnimationDuration == 0f) {
                    currentAnimationDuration = getAnimationDuration(currentAnimationIndex)
                }

                if (elapsedTimeSeconds >= currentAnimationDuration) {
                    currentAnimationIndex = (currentAnimationIndex + 1) % animationCount
                    animationStartTime = frameTimeNanos
                    currentAnimationDuration = getAnimationDuration(currentAnimationIndex)
                    applyAnimation(currentAnimationIndex, 0f)
                } else {
                    applyAnimation(currentAnimationIndex, elapsedTimeSeconds.toFloat())
                }
            }
        }
    }

    // Helper methods from second code for better drag/resize
    private fun getDistance(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        val dx = event.getX(0) - event.getX(1)
        val dy = event.getY(0) - event.getY(1)
        return sqrt(dx * dx + dy * dy)
    }

    private fun getCornerAtPosition(x: Float, y: Float): Corner {
        // Check top-left corner
        val distanceTopLeft = sqrt(
            (x - resizeHandleSize / 2).toDouble().pow(2.0) +
                    (y - resizeHandleSize / 2).toDouble().pow(2.0)
        ).toFloat()
        if (distanceTopLeft <= resizeHitArea) return Corner.TOP_LEFT

        // Check top-right corner
        val distanceTopRight = sqrt(
            (x - (width - resizeHandleSize / 2)).toDouble().pow(2.0) +
                    (y - resizeHandleSize / 2).toDouble().pow(2.0)
        ).toFloat()
        if (distanceTopRight <= resizeHitArea) return Corner.TOP_RIGHT

        // Check bottom-left corner
        val distanceBottomLeft = sqrt(
            (x - resizeHandleSize / 2).toDouble().pow(2.0) +
                    (y - (height - resizeHandleSize / 2)).toDouble().pow(2.0)
        ).toFloat()
        if (distanceBottomLeft <= resizeHitArea) return Corner.BOTTOM_LEFT

        // Check bottom-right corner
        val distanceBottomRight = sqrt(
            (x - (width - resizeHandleSize / 2)).toDouble().pow(2.0) +
                    (y - (height - resizeHandleSize / 2)).toDouble().pow(2.0)
        ).toFloat()
        if (distanceBottomRight <= resizeHitArea) return Corner.BOTTOM_RIGHT

        return Corner.NONE
    }

    // Override onTouchEvent with better logic from second code
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // If we're currently handling 3D touch, don't allow container manipulation
        if (isHandling3DTouch && touchEnabled && !passThroughTouches) {
            when (event.action) {
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isHandling3DTouch = false
                }
            }
            return true
        }

        // Handle scale gestures
        scaleGestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (event.pointerCount == 1) {
                    lastX = event.rawX
                    lastY = event.rawY

                    val touchX = event.x
                    val touchY = event.y
                    activeCorner = getCornerAtPosition(touchX, touchY)

                    isResizing = activeCorner != Corner.NONE
                    isDragging = !isResizing && !isPinching

                    bringToFront()
                    onInteractionStart()
                }
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount == 2) {
                    isPinching = true
                    isDragging = false
                    isResizing = false
                    initialWidth = width
                    initialHeight = height
                    initialDistance = getDistance(event)
                    scaleFactor = 1f
                    onInteractionStart()
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (isPinching && event.pointerCount >= 2) {
                    currentDistance = getDistance(event)
                    if (initialDistance > 0) {
                        val scale = currentDistance / initialDistance
                        scaleFactor = scale.coerceIn(0.5f, 3.0f)

                        val params = layoutParams
                        val newWidth = (initialWidth * scaleFactor).toInt()
                        val newHeight = (initialHeight * scaleFactor).toInt()

                        if (newWidth >= 200 && newHeight >= 150) {
                            params.width = newWidth
                            params.height = newHeight
                            layoutParams = params
                        }
                    }
                } else if (event.pointerCount == 1 && !isPinching) {
                    val deltaX = event.rawX - lastX
                    val deltaY = event.rawY - lastY

                    if (isDragging) {
                        val params = layoutParams as ViewGroup.MarginLayoutParams
                        params.leftMargin = (params.leftMargin + deltaX).toInt()
                        params.topMargin = (params.topMargin + deltaY).toInt()
                        layoutParams = params
                    } else if (isResizing) {
                        val params = layoutParams
                        var newWidth = params.width
                        var newHeight = params.height
                        var newLeftMargin = (params as ViewGroup.MarginLayoutParams).leftMargin
                        var newTopMargin = params.topMargin

                        when (activeCorner) {
                            Corner.TOP_LEFT -> {
                                newWidth = (params.width - deltaX).toInt()
                                newHeight = (params.height - deltaY).toInt()
                                newLeftMargin = (params.leftMargin + deltaX).toInt()
                                newTopMargin = (params.topMargin + deltaY).toInt()
                            }
                            Corner.TOP_RIGHT -> {
                                newWidth = (params.width + deltaX).toInt()
                                newHeight = (params.height - deltaY).toInt()
                                newTopMargin = (params.topMargin + deltaY).toInt()
                            }
                            Corner.BOTTOM_LEFT -> {
                                newWidth = (params.width - deltaX).toInt()
                                newHeight = (params.height + deltaY).toInt()
                                newLeftMargin = (params.leftMargin + deltaX).toInt()
                            }
                            Corner.BOTTOM_RIGHT -> {
                                newWidth = (params.width + deltaX).toInt()
                                newHeight = (params.height + deltaY).toInt()
                            }
                            Corner.NONE -> {}
                        }

                        if (newWidth >= 200 && newHeight >= 150) {
                            params.width = newWidth
                            params.height = newHeight
                            params.leftMargin = newLeftMargin
                            params.topMargin = newTopMargin
                            layoutParams = params
                        }
                    }

                    lastX = event.rawX
                    lastY = event.rawY
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                isDragging = false
                isResizing = false
                isPinching = false
                activeCorner = Corner.NONE
                initialDistance = 0f
                scaleFactor = 1f
                isHandling3DTouch = false
                onInteractionEnd()
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                if (event.pointerCount <= 2) {
                    isPinching = false
                    initialDistance = 0f
                    scaleFactor = 1f
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                isResizing = false
                isPinching = false
                activeCorner = Corner.NONE
                initialDistance = 0f
                isHandling3DTouch = false
                onInteractionEnd()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun createFallbackView(): android.view.View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
            setBackgroundColor(Color.parseColor("#263238"))

            addView(TextView(context).apply {
                text = "🎲"
                textSize = 48f
                gravity = android.view.Gravity.CENTER
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            })

            addView(TextView(context).apply {
                text = "3D Model Container\n${modelData?.name ?: "Unknown Model"}\nFilament not available"
                textSize = 12f
                gravity = android.view.Gravity.CENTER
                setTextColor(Color.LTGRAY)
                setPadding(0, dpToPx(8), 0, 0)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            })
        }
    }

    private fun createErrorView(message: String): android.view.View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
            setBackgroundColor(Color.parseColor("#FF5722"))
            gravity = Gravity.CENTER

            addView(TextView(context).apply {
                text = "❌"
                textSize = 32f
                gravity = android.view.Gravity.CENTER
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            })

            addView(TextView(context).apply {
                text = message
                textSize = 11f
                gravity = android.view.Gravity.CENTER
                setTextColor(Color.WHITE)
                setPadding(0, dpToPx(8), 0, 0)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            })
        }
    }

    private fun toggleInteraction() {
        if (touchEnabled) {
            autoHideEnabled=true;
            setTouchEnabled(false)
            setPassThroughTouches(true)
            android.widget.Toast.makeText(context, "Interaction disabled", android.widget.Toast.LENGTH_SHORT).show()
        } else {
            setTouchEnabled(true)
            autoHideEnabled=false;
            setPassThroughTouches(false)
            android.widget.Toast.makeText(context, "Interaction enabled", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleAnimation() {
        val animator = modelViewer?.animator
        if (animator == null || animator.animationCount == 0) {
            android.widget.Toast.makeText(context, "No animations available", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        isAnimationPaused = !isAnimationPaused

        if (isAnimationPaused) {
            pausedAnimationTime = ((System.nanoTime() - animationStartTime).toDouble() / 1000000000).toFloat()
            // Try to use ic_pause_animation if available, fallback to ic_play_animation
            try {
                animationToggleButton?.setImageResource(R.drawable.ic_play_animation)
            } catch (e: Exception) {
                animationToggleButton?.setImageResource(R.drawable.ic_pause_animation)
            }
            android.widget.Toast.makeText(context, "Animation paused", android.widget.Toast.LENGTH_SHORT).show()
        } else {
            animationStartTime = System.nanoTime() - (pausedAnimationTime * 1000000000).toLong()
            animationToggleButton?.setImageResource(R.drawable.ic_pause_animation)
            android.widget.Toast.makeText(context, "Animation playing", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun switchToAnimation(animationIndex: Int) {
        modelViewer?.animator?.apply {
            if (animationIndex >= 0 && animationIndex < animationCount) {
                currentAnimationIndex = animationIndex
                animationStartTime = System.nanoTime()
                currentAnimationDuration = getAnimationDuration(animationIndex)
                pausedAnimationTime = 0f
                isAnimationPaused = false
                animationToggleButton?.setImageResource(R.drawable.ic_play_animation)
            }
        }
    }

    fun getCurrentAnimationInfo(): String {
        return modelViewer?.animator?.let { animator ->
            if (animator.animationCount > 0) {
                if (isPlayingAllAnimations) {
                    "All ${animator.animationCount} animations playing together"
                } else {
                    "${animator.getAnimationName(currentAnimationIndex)} (${currentAnimationIndex + 1}/${animator.animationCount})"
                }
            } else {
                "No animations"
            }
        } ?: "No animator"
    }

    fun getModelInfo(): String {
        val currentModelData = modelData
        val animator = modelViewer?.animator

        val animationInfo = if (animator != null && animator.animationCount > 0) {
            val clipsList = animationClips.mapIndexed { index, name ->
                "  ${index + 1}. $name (${String.format("%.2f", animator.getAnimationDuration(index))}s)"
            }.joinToString("\n")

            """
            Animation Mode: ${if (isPlayingAllAnimations) "All clips together" else "Individual clips"}
            Animation Clips (${animator.animationCount}):
            $clipsList
            Current State: ${getCurrentAnimationInfo()}
            """.trimIndent()
        } else {
            "No animations available"
        }

        return if (currentModelData != null) {
            """
            Model: ${currentModelData.name}
            File: ${currentModelData.filename}
            Path: ${modelPath ?: "Unknown"}
            Rendering: ${if (isRenderingActive) "Active" else "Paused"}
            
            $animationInfo
            """.trimIndent()
        } else {
            "No model data available"
        }
    }

    fun pauseRendering() {
        stopRendering()
    }

    fun resumeRendering() {
        startRendering()
    }

    fun pauseAnimation() {
        if (!isAnimationPaused) {
            toggleAnimation()
        }
    }

    fun playAnimation() {
        if (isAnimationPaused) {
            toggleAnimation()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isInitialized && !isRenderingActive) {
            startRendering()
        }
        // Update animation button icon based on state
        try {
            animationToggleButton?.setImageResource(
                if (isAnimationPaused) R.drawable.ic_play_animation else R.drawable.ic_pause_animation
            )
        } catch (e: Exception) {
            animationToggleButton?.setImageResource(R.drawable.ic_pause_animation)
        }

        if (autoHideEnabled) {
            scheduleAutoHide()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopRendering()
        uiHelper?.detach()
        cancelAutoHide()
    }

    override fun getDefaultWidth(): Int = dpToPx(400)
    override fun getDefaultHeight(): Int = dpToPx(350)

    override fun getCustomSaveData(): Map<String, Any> {
        val baseData = super.getCustomSaveData().toMutableMap()
        baseData.putAll(mapOf(
            "modelFilename" to (modelData?.filename ?: ""),
            "modelName" to (modelData?.name ?: ""),
            "modelPath" to (modelPath ?: ""),
            "currentAnimationIndex" to currentAnimationIndex,
            "isRenderingActive" to isRenderingActive,
            "isAnimationPaused" to isAnimationPaused,
            "pausedAnimationTime" to pausedAnimationTime,
            "isPlayingAllAnimations" to isPlayingAllAnimations,
            "animationClips" to animationClips,
            "autoHideEnabled" to autoHideEnabled,
            "controlsVisible" to controlsVisible
        ))
        return baseData
    }

    override fun loadCustomSaveData(data: Map<String, Any>) {
        super.loadCustomSaveData(data)

        data["currentAnimationIndex"]?.let {
            if (it is Int) currentAnimationIndex = it
        }
        data["isRenderingActive"]?.let {
            if (it is Boolean && it && !isRenderingActive) {
                startRendering()
            }
        }
        data["isAnimationPaused"]?.let {
            if (it is Boolean) isAnimationPaused = it
        }
        data["pausedAnimationTime"]?.let {
            if (it is Float) pausedAnimationTime = it
        }
        data["isPlayingAllAnimations"]?.let {
            if (it is Boolean) isPlayingAllAnimations = it
        }
        data["animationClips"]?.let {
            if (it is List<*>) {
                animationClips.clear()
                it.filterIsInstance<String>().forEach { clip ->
                    animationClips.add(clip)
                }
            }
        }
        data["autoHideEnabled"]?.let {
            if (it is Boolean) {
                autoHideEnabled = it
                if (autoHideEnabled) {
                    scheduleAutoHide()
                }
            }
        }
        data["controlsVisible"]?.let {
            if (it is Boolean) {
                controlsVisible = it
                if (!controlsVisible) {
                    sideControlButtons.forEach { button ->
                        button.alpha = 0f
                    }
                    contentContainer?.background = null
                }
            }
        }

        try {
            animationToggleButton?.setImageResource(
                if (isAnimationPaused) R.drawable.ic_play_animation else R.drawable.ic_pause_animation
            )
        } catch (e: Exception) {
            animationToggleButton?.setImageResource(R.drawable.ic_play_animation)
        }
    }
}