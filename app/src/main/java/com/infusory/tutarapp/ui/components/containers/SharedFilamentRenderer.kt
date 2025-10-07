//// SharedFilamentRenderer.kt - Singleton for managing shared 3D rendering
//package com.infusory.tutarapp.ui.utils.rendering
//
//import android.content.Context
//import android.util.Log
//import android.view.Choreographer
//import android.view.SurfaceView
//import com.google.android.filament.*
//import com.google.android.filament.android.UiHelper
//import com.google.android.filament.utils.AutomationEngine
//import com.google.android.filament.utils.KTX1Loader
//import com.google.android.filament.utils.ModelViewer
//import com.google.android.filament.utils.Utils
//import java.nio.ByteBuffer
//import java.nio.ByteOrder
//import java.util.concurrent.ConcurrentHashMap
//
//data class Model3DEntity(
//    val id: String,
//    val entities: List<Int>,
//    val animator: Animator?,
//    val modelViewer: ModelViewer,
//    var isVisible: Boolean = true,
//    var currentAnimationIndex: Int = 0,
//    var animationStartTime: Long = System.nanoTime()
//)
//
//object SharedFilamentRenderer {
//
//    companion object {
//        init {
//            Utils.init()
//        }
//
//        private const val TAG = "SharedFilamentRenderer"
//
//        // Model file names
//        private val modelFiles = arrayOf(
//            "Eagle.glb",
//            "skeleton.glb",
//            "skeleton.glb", // Replace with different models
//            "Eagle.glb"     // Replace with different models
//        )
//    }
//
//    // Core Filament components (shared)
//    private var engine: Engine? = null
//    private var scene: Scene? = null
//    private var renderer: Renderer? = null
//    private var swapChain: SwapChain? = null
//    private var view: View? = null
//    private var camera: Camera? = null
//    private var indirectLight: IndirectLight? = null
//
//    // Rendering management
//    private var frameCallback: FrameCallback? = null
//    private var choreographer: Choreographer? = null
//    private var isRenderingActive = false
//    private var mainSurfaceView: SurfaceView? = null
//    private var uiHelper: UiHelper? = null
//
//    // Model management
//    private val loadedModels = ConcurrentHashMap<String, Model3DEntity>()
//    private val activeContainers = mutableSetOf<String>()
//
//    // Callbacks
//    private val containerCallbacks = ConcurrentHashMap<String, (Long) -> Unit>()
//
//    fun initialize(context: Context, surfaceView: SurfaceView): Boolean {
//        try {
//            if (engine != null) {
//                Log.d(TAG, "Already initialized")
//                return true
//            }
//
//            mainSurfaceView = surfaceView
//
//            // Initialize UI helper
//            uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK).apply {
//                isOpaque = false
//                renderCallback = object : UiHelper.RendererCallback {
//                    override fun onNativeWindowChanged(surface: Surface?) {
//                        surface?.let {
//                            swapChain?.let { chain -> engine?.destroySwapChain(chain) }
//                            swapChain = engine?.createSwapChain(it)
//                        }
//                    }
//
//                    override fun onDetachedFromSurface() {
//                        swapChain?.let { chain -> engine?.destroySwapChain(chain) }
//                        swapChain = null
//                    }
//
//                    override fun onResized(width: Int, height: Int) {
//                        view?.viewport = Viewport(0, 0, width, height)
//                        camera?.setProjection(
//                            45.0,
//                            width.toDouble() / height.toDouble(),
//                            0.1,
//                            20.0,
//                            Camera.Projection.PERSPECTIVE
//                        )
//                    }
//                }
//            }
//
//            uiHelper?.attachTo(surfaceView)
//
//            // Create engine
//            engine = Engine.create()
//
//            // Create renderer
//            renderer = engine?.createRenderer()
//
//            // Create scene
//            scene = engine?.createScene()
//
//            // Create view
//            view = engine?.createView()
//
//            // Create camera
//            camera = engine?.createCamera(engine?.entityManager?.create() ?: 0)
//
//            // Configure view
//            configureView()
//
//            // Setup camera
//            setupCamera()
//
//            // Create indirect light
//            createIndirectLight(context)
//
//            // Setup choreographer
//            choreographer = Choreographer.getInstance()
//
//            Log.d(TAG, "SharedFilamentRenderer initialized successfully")
//            return true
//
//        } catch (e: Exception) {
//            Log.e(TAG, "Failed to initialize SharedFilamentRenderer", e)
//            cleanup()
//            return false
//        }
//    }
//
//    private fun configureView() {
//        view?.apply {
//            scene = this@SharedFilamentRenderer.scene
//            camera = this@SharedFilamentRenderer.camera
//
//            // Optimize for performance
//            blendMode = View.BlendMode.OPAQUE
//
//            renderQuality = renderQuality.apply {
//                hdrColorBuffer = View.QualityLevel.LOW
//            }
//
//            dynamicResolutionOptions = dynamicResolutionOptions.apply {
//                enabled = false
//                quality = View.QualityLevel.LOW
//            }
//
//            multiSampleAntiAliasingOptions = multiSampleAntiAliasingOptions.apply {
//                enabled = false
//            }
//
//            antiAliasing = View.AntiAliasing.NONE
//
//            ambientOcclusionOptions = ambientOcclusionOptions.apply {
//                enabled = false
//            }
//
//            bloomOptions = bloomOptions.apply {
//                enabled = false
//            }
//
//            screenSpaceReflectionsOptions = screenSpaceReflectionsOptions.apply {
//                enabled = false
//            }
//
//            temporalAntiAliasingOptions = temporalAntiAliasingOptions.apply {
//                enabled = false
//            }
//
//            fogOptions = fogOptions.apply {
//                enabled = false
//            }
//
//            depthOfFieldOptions = depthOfFieldOptions.apply {
//                enabled = false
//            }
//
//            vignetteOptions = vignetteOptions.apply {
//                enabled = false
//            }
//        }
//    }
//
//    private fun setupCamera() {
//        camera?.apply {
//            lookAt(
//                0.0, 0.0, 4.0,  // eye
//                0.0, 0.0, 0.0,  // center
//                0.0, 1.0, 0.0   // up
//            )
//        }
//    }
//
//    private fun createIndirectLight(context: Context) {
//        try {
//            val ibl = "default_env_ibl.ktx"
//            val buffer = readCompressedAsset(context, ibl)
//
//            engine?.let { eng ->
//                indirectLight = KTX1Loader.createIndirectLight(eng, buffer)
//                indirectLight?.intensity = 30_000.0f
//                scene?.indirectLight = indirectLight
//            }
//        } catch (e: Exception) {
//            Log.e(TAG, "Failed to create indirect light", e)
//        }
//    }
//
//    private fun readCompressedAsset(context: Context, assetName: String): ByteBuffer {
//        val input = context.assets.open(assetName)
//        val bytes = ByteArray(input.available())
//        input.read(bytes)
//        return ByteBuffer.wrap(bytes)
//    }
//
//    fun loadModel(context: Context, containerId: String, modelIndex: Int): String? {
//        val modelFile = modelFiles[modelIndex % modelFiles.size]
//        val modelId = "${containerId}_${modelFile}"
//
//        try {
//            // Check if model already loaded
//            if (loadedModels.containsKey(modelId)) {
//                Log.d(TAG, "Model $modelId already loaded")
//                return modelId
//            }
//
//            // Create a temporary surface view for the ModelViewer
//            val tempSurfaceView = SurfaceView(context)
//            val tempUiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
//
//            // Create ModelViewer for loading (we'll extract entities from it)
//            val modelViewer = ModelViewer(surfaceView = tempSurfaceView, uiHelper = tempUiHelper)
//
//            // Load model data
//            val buffer = context.assets.open(modelFile).use { input ->
//                val bytes = ByteArray(input.available())
//                input.read(bytes)
//                ByteBuffer.allocateDirect(bytes.size).apply {
//                    order(ByteOrder.nativeOrder())
//                    put(bytes)
//                    rewind()
//                }
//            }
//
//            // Load model
//            modelViewer.loadModelGlb(buffer)
//            modelViewer.transformToUnitCube()
//
//            // Extract entities and add to shared scene
//            val asset = modelViewer.asset
//            val entities = asset?.entities?.toList() ?: emptyList()
//
//            if (entities.isNotEmpty()) {
//                // Add entities to shared scene
//                entities.forEach { entity ->
//                    scene?.addEntity(entity)
//                }
//
//                // Store model info
//                val model3DEntity = Model3DEntity(
//                    id = modelId,
//                    entities = entities,
//                    animator = modelViewer.animator,
//                    modelViewer = modelViewer,
//                    isVisible = true
//                )
//
//                loadedModels[modelId] = model3DEntity
//
//                Log.d(TAG, "Model $modelId loaded successfully with ${entities.size} entities")
//                return modelId
//            } else {
//                Log.e(TAG, "No entities found in model $modelFile")
//                return null
//            }
//
//        } catch (e: Exception) {
//            Log.e(TAG, "Failed to load model $modelFile", e)
//            return null
//        }
//    }
//
//    fun registerContainer(containerId: String, callback: (Long) -> Unit) {
//        activeContainers.add(containerId)
//        containerCallbacks[containerId] = callback
//
//        if (!isRenderingActive) {
//            startRendering()
//        }
//    }
//
//    fun unregisterContainer(containerId: String) {
//        activeContainers.remove(containerId)
//        containerCallbacks.remove(containerId)
//
//        // Remove models associated with this container
//        val modelsToRemove = loadedModels.keys.filter { it.startsWith("${containerId}_") }
//        modelsToRemove.forEach { modelId ->
//            removeModel(modelId)
//        }
//
//        if (activeContainers.isEmpty()) {
//            stopRendering()
//        }
//    }
//
//    private fun removeModel(modelId: String) {
//        loadedModels[modelId]?.let { model ->
//            // Remove entities from scene
//            model.entities.forEach { entity ->
//                scene?.removeEntity(entity)
//            }
//            loadedModels.remove(modelId)
//            Log.d(TAG, "Model $modelId removed")
//        }
//    }
//
//    fun setModelVisibility(modelId: String, visible: Boolean) {
//        loadedModels[modelId]?.let { model ->
//            if (model.isVisible != visible) {
//                model.isVisible = visible
//                model.entities.forEach { entity ->
//                    if (visible) {
//                        scene?.addEntity(entity)
//                    } else {
//                        scene?.removeEntity(entity)
//                    }
//                }
//            }
//        }
//    }
//
//    fun switchAnimation(modelId: String, animationIndex: Int) {
//        loadedModels[modelId]?.let { model ->
//            model.animator?.let { animator ->
//                if (animationIndex >= 0 && animationIndex < animator.animationCount) {
//                    model.currentAnimationIndex = animationIndex
//                    model.animationStartTime = System.nanoTime()
//                }
//            }
//        }
//    }
//
//    private fun startRendering() {
//        if (!isRenderingActive && choreographer != null) {
//            frameCallback = FrameCallback()
//            choreographer?.postFrameCallback(frameCallback)
//            isRenderingActive = true
//            Log.d(TAG, "Rendering started")
//        }
//    }
//
//    private fun stopRendering() {
//        if (isRenderingActive) {
//            frameCallback?.let { callback ->
//                choreographer?.removeFrameCallback(callback)
//            }
//            frameCallback = null
//            isRenderingActive = false
//            Log.d(TAG, "Rendering stopped")
//        }
//    }
//
//    fun pauseRendering() {
//        stopRendering()
//    }
//
//    fun resumeRendering() {
//        if (activeContainers.isNotEmpty()) {
//            startRendering()
//        }
//    }
//
//    private inner class FrameCallback : Choreographer.FrameCallback {
//        override fun doFrame(frameTimeNanos: Long) {
//            if (isRenderingActive && engine != null && renderer != null && view != null && swapChain != null) {
//                try {
//                    choreographer?.postFrameCallback(this)
//
//                    // Update animations
//                    updateAnimations(frameTimeNanos)
//
//                    // Render frame
//                    if (renderer?.beginFrame(swapChain!!, frameTimeNanos) == true) {
//                        renderer?.render(view!!)
//                        renderer?.endFrame()
//                    }
//
//                    // Notify containers
//                    containerCallbacks.values.forEach { callback ->
//                        callback(frameTimeNanos)
//                    }
//
//                } catch (e: Exception) {
//                    Log.e(TAG, "Error during frame rendering", e)
//                }
//            }
//        }
//
//        private fun updateAnimations(frameTimeNanos: Long) {
//            loadedModels.values.forEach { model ->
//                if (model.isVisible) {
//                    model.animator?.let { animator ->
//                        if (animator.animationCount > 0) {
//                            val elapsedTimeSeconds = (frameTimeNanos - model.animationStartTime).toDouble() / 1000000000
//                            val duration = animator.getAnimationDuration(model.currentAnimationIndex)
//
//                            if (elapsedTimeSeconds >= duration) {
//                                // Move to next animation
//                                model.currentAnimationIndex = (model.currentAnimationIndex + 1) % animator.animationCount
//                                model.animationStartTime = frameTimeNanos
//                                animator.applyAnimation(model.currentAnimationIndex, 0f)
//                            } else {
//                                animator.applyAnimation(model.currentAnimationIndex, elapsedTimeSeconds.toFloat())
//                            }
//
//                            animator.updateBoneMatrices()
//                        }
//                    }
//                }
//            }
//        }
//    }
//
//    fun getModelInfo(modelId: String): String? {
//        return loadedModels[modelId]?.let { model ->
//            val animator = model.animator
//            if (animator != null && animator.animationCount > 0) {
//                "${animator.getAnimationName(model.currentAnimationIndex)} (${model.currentAnimationIndex + 1}/${animator.animationCount})"
//            } else {
//                "No animations"
//            }
//        }
//    }
//
//    fun getLoadedModelCount(): Int = loadedModels.size
//
//    fun getActiveContainerCount(): Int = activeContainers.size
//
//    fun cleanup() {
//        Log.d(TAG, "Cleaning up SharedFilamentRenderer")
//
//        stopRendering()
//
//        // Clear models
//        loadedModels.values.forEach { model ->
//            model.entities.forEach { entity ->
//                scene?.removeEntity(entity)
//            }
//        }
//        loadedModels.clear()
//
//        // Clear callbacks
//        containerCallbacks.clear()
//        activeContainers.clear()
//
//        // Cleanup Filament objects
//        indirectLight?.let { engine?.destroyIndirectLight(it) }
//        camera?.let { engine?.destroyEntity(it.entity) }
//        view?.let { engine?.destroyView(it) }
//        scene?.let { engine?.destroyScene(it) }
//        renderer?.let { engine?.destroyRenderer(it) }
//        swapChain?.let { engine?.destroySwapChain(it) }
//        uiHelper?.detach()
//        engine?.destroy()
//
//        // Reset references
//        indirectLight = null
//        camera = null
//        view = null
//        scene = null
//        renderer = null
//        swapChain = null
//        uiHelper = null
//        engine = null
//        mainSurfaceView = null
//        choreographer = null
//
//        Log.d(TAG, "SharedFilamentRenderer cleanup complete")
//    }
//}