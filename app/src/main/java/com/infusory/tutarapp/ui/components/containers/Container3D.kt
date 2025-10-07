package com.infusory.tutarapp.ui.components.containers

import com.infusory.tutarapp.R
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Choreographer
import android.view.SurfaceView
import android.widget.TextView
import android.widget.LinearLayout
import android.widget.ImageView
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import androidx.appcompat.app.AlertDialog
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
            // Always handle touch for auto-hide, but only interact with model if enabled
            surface.setOnTouchListener { _, event ->
                // Show controls on any interaction
                if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                    onInteractionStart()
                }

                // Let model handle touch if enabled
                val handled = if (touchEnabled && !passThroughTouches) {
                    modelViewer?.onTouchEvent(event)
                    true
                } else {
                    false
                }

                // Schedule auto-hide when interaction ends
                if (event.action == android.view.MotionEvent.ACTION_UP ||
                    event.action == android.view.MotionEvent.ACTION_CANCEL) {
                    onInteractionEnd()
                }

                handled
            }

            // Set clickable state based on touch enabled
            surface.isClickable = touchEnabled && !passThroughTouches
            surface.isFocusable = touchEnabled && !passThroughTouches
            surface.isFocusableInTouchMode = touchEnabled && !passThroughTouches
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
                dpToPx(48),
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
            gravity = Gravity.TOP
        }

        contentContainer = android.widget.FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f
            )
            setBackgroundResource(R.drawable.dotted_border_background)
            setPadding(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2))
        }

        addControlButtonsToSide()

        mainContainer.addView(controlsContainer)
        mainContainer.addView(contentContainer)

        setContent(mainContainer)
    }

    private fun addControlButtonsToSide() {
        controlsContainer?.let { container ->

            val interactionButton = createSideControlButton(
                android.R.drawable.ic_menu_rotate,
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
                android.R.drawable.ic_media_play,
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
                android.R.drawable.ic_menu_close_clear_cancel,
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
                setMargins(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
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
                dpToPx(2)
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
                setZOrderOnTop(false)
                setZOrderMediaOverlay(true)
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
            val assetPath = "models/$filename"
            context.assets.open(assetPath).use { input ->
                createBufferFromStream(input)
            }
        } catch (e: Exception) {
            try {
                context.assets.open(filename).use { input ->
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
            setTouchEnabled(false)
            setPassThroughTouches(true)
            android.widget.Toast.makeText(context, "Interaction disabled", android.widget.Toast.LENGTH_SHORT).show()
        } else {
            setTouchEnabled(true)
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
            animationToggleButton?.setImageResource(android.R.drawable.ic_media_play)
            android.widget.Toast.makeText(context, "Animation paused", android.widget.Toast.LENGTH_SHORT).show()
        } else {
            animationStartTime = System.nanoTime() - (pausedAnimationTime * 1000000000).toLong()
            animationToggleButton?.setImageResource(android.R.drawable.ic_media_pause)
            android.widget.Toast.makeText(context, "Animation playing", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun show3DMenu() {
        toggleRendering()
    }

    private fun resetView() {
        modelViewer?.transformToUnitCube()
        android.widget.Toast.makeText(context, "View reset", android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun showPerformanceStats() {
        val fps = if (isRenderingActive) "~60 FPS (optimized)" else "Paused"
        android.widget.Toast.makeText(context, "Performance: $fps", android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun toggleRendering() {
        if (isRenderingActive) {
            stopRendering()
            android.widget.Toast.makeText(context, "Rendering paused", android.widget.Toast.LENGTH_SHORT).show()
        } else {
            startRendering()
            android.widget.Toast.makeText(context, "Rendering resumed", android.widget.Toast.LENGTH_SHORT).show()
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
                animationToggleButton?.setImageResource(android.R.drawable.ic_media_pause)
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
        animationToggleButton?.setImageResource(
            if (isAnimationPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause
        )

        // Restart auto-hide timer when reattached
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
                    // Apply hidden state immediately
                    sideControlButtons.forEach { button ->
                        button.alpha = 0f
                    }
                    contentContainer?.background = null
                }
            }
        }

        animationToggleButton?.setImageResource(
            if (isAnimationPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause
        )
    }
}