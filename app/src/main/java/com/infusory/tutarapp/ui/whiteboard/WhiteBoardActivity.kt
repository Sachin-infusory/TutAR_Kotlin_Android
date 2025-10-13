package com.infusory.tutarapp.ui.whiteboard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import com.infusory.tutarapp.R
import com.infusory.tutarapp.ui.ai.AiMasterDrawer
import com.infusory.tutarapp.ui.components.annotation.AnnotationToolView
import com.infusory.tutarapp.ui.data.ModelData
import com.infusory.tutarapp.ui.models.ModelBrowserDrawer
import com.infusory.tutarapp.ui.components.containers.ContainerManager
import com.infusory.tutarapp.managers.ImagePickerHandler
import com.infusory.tutarapp.managers.PopupHandler
import com.infusory.tutarapp.managers.WhiteboardButtonStateManager
import com.infusory.tutarapp.managers.CameraManager
import com.infusory.tutarapp.managers.WhiteboardStateManager
import com.infusory.tutarapp.ui.components.containers.Container3D

enum class ActionType {
    SAVE, INSERT
}

class WhiteboardActivity : AppCompatActivity() {

    // Views
    private lateinit var surfaceView: SurfaceView
    private lateinit var mainLayout: RelativeLayout

    // Managers
    private lateinit var containerManager: ContainerManager
    private lateinit var cameraManager: CameraManager
    private lateinit var buttonStateManager: WhiteboardButtonStateManager
    private lateinit var stateManager: WhiteboardStateManager

    private lateinit var popupHandler: PopupHandler
    private lateinit var imagePickerHandler: ImagePickerHandler

    // Drawers
    private var modelBrowserDrawer: ModelBrowserDrawer? = null
    private var aiMasterDrawer: AiMasterDrawer? = null

    // Annotation
    private var annotationTool: AnnotationToolView? = null

    // Track button references for auto-deactivation
    private val buttonReferences = mutableMapOf<String, MutableList<ImageButton>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        makeFullscreen()
        setContentView(R.layout.activity_whiteboard)

        initViews()
        initManagers()
        initHandlers()
        setupUI()

        Toast.makeText(this, "Welcome to TutAR Whiteboard with 3D!", Toast.LENGTH_LONG).show()
    }

    private fun makeFullscreen() {
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
    }

    private fun initViews() {
        surfaceView = findViewById(R.id.surface_view)
        mainLayout = findViewById(R.id.main)
    }

    private fun initManagers() {
        setupAnnotationTool()
        setupCameraManager()
        setupContainerManager()

        buttonStateManager = WhiteboardButtonStateManager(this)
        stateManager = WhiteboardStateManager(this)
    }

    private fun initHandlers() {
        popupHandler = PopupHandler(this)
        imagePickerHandler = ImagePickerHandler(this, mainLayout, surfaceView)
    }

    private fun setupUI() {
        setupButtonListeners()
        setupModelBrowser()
        setupAiMaster()
    }

    private fun setupAnnotationTool() {
        annotationTool = AnnotationToolView(this)
        val layoutParams = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.MATCH_PARENT
        )
        annotationTool?.layoutParams = layoutParams
        annotationTool?.elevation = 200f
        mainLayout.addView(annotationTool)

        annotationTool?.onAnnotationToggle = { isEnabled ->
            val message = if (isEnabled) "Annotation mode enabled" else "Annotation mode disabled"
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

            // Auto-deactivate draw button when annotation is manually disabled
            if (!isEnabled) {
                buttonReferences["draw"]?.forEach { button ->
                    buttonStateManager.deactivateButtons(button)
                }
            }
        }

        annotationTool?.onDrawingStateChanged = { isDrawing ->
            if (isDrawing) pauseAll3DRenderingForDrawing()
            else resumeAll3DRenderingAfterDrawing()
        }
    }

    private fun setupCameraManager() {
        val cameraPreviewView = PreviewView(this).apply {
            layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT
            )
            elevation = 0f  // Lowest elevation - acts as background
            visibility = View.GONE
        }
        // Add camera preview at index 0 (bottom layer)
        mainLayout.addView(cameraPreviewView, 0)

        cameraManager = CameraManager(this, this, cameraPreviewView, surfaceView)
        cameraManager.onCameraStateChanged = { isActive ->
            // Optional: Update UI based on camera state
        }
    }

    private fun setupContainerManager() {
        containerManager = ContainerManager(this, mainLayout, maxContainers = 8)

        containerManager.onContainerRemoved = { container ->
            if (container is Container3D) {
                container.pauseRendering()
            }
        }
    }

    private fun setupModelBrowser() {
        modelBrowserDrawer = ModelBrowserDrawer(this) { modelData, fullPath ->
            createCustom3DContainer(modelData, fullPath)
        }

        // Listen for drawer dismissal
        modelBrowserDrawer?.setOnDismissListener {
            deactivateButtonsByKey("menu")
        }
    }

    private fun setupAiMaster() {
        aiMasterDrawer = AiMasterDrawer(this) { response ->
            handleAiMasterResponse(response)
        }
    }

    private fun setupButtonListeners() {
        val leftToolbar = findViewById<View>(R.id.left_toolbar)
        val rightToolbar = findViewById<View>(R.id.right_toolbar)

        // Register button pairs first (so they sync across toolbars)
        registerButtonPairs(leftToolbar, rightToolbar)

        // Setup buttons on both toolbars
        setupToolbarButtons(leftToolbar)
        setupToolbarButtons(rightToolbar)

        // AI Master button (standalone, not in toolbar)
        val aiMasterBtn = findViewById<ImageButton>(R.id.ai_master_btn)
        registerButton("ai_master", aiMasterBtn)
        buttonStateManager.setupButton(aiMasterBtn) {
            showAiMaster()
        }
    }

    private fun deactivateButtonsByKey(key: String) {
        // Close functionality based on key
        when (key) {
            "menu" -> {
                modelBrowserDrawer?.dismiss()
            }
        }

        // Deactivate buttons
        buttonReferences[key]?.forEach { button ->
            buttonStateManager.deactivateButtons(button)
        }
    }

    /**
     * Register a button with a key for tracking
     */
    private fun registerButton(key: String, button: ImageButton) {
        if (!buttonReferences.containsKey(key)) {
            buttonReferences[key] = mutableListOf()
        }
        buttonReferences[key]?.add(button)
    }

    private fun registerButtonPairs(leftToolbar: View, rightToolbar: View) {
        // Register all paired buttons
        val pairedButtonIds = listOf(
            R.id.btn_draw,
            R.id.btn_ar,
            R.id.btn_menu,
            R.id.btn_setting,
            R.id.btn_load_lesson
        )

        pairedButtonIds.forEach { buttonId ->
            val leftButton = leftToolbar.findViewById<ImageButton>(buttonId)
            val rightButton = rightToolbar.findViewById<ImageButton>(buttonId)

            if (leftButton != null && rightButton != null) {
                buttonStateManager.registerButtonPair(leftButton, rightButton)
            }
        }
    }

    private fun setupToolbarButtons(toolbar: View) {
        // Draw button
        val btnDraw = toolbar.findViewById<ImageButton>(R.id.btn_draw)
        registerButton("draw", btnDraw)
        buttonStateManager.setupButton(btnDraw) { isActive ->
            if (isActive) {
                annotationTool?.toggleAnnotationMode(true)
            } else {
                // Ensure annotation mode is turned off when button deactivates
                if (annotationTool?.isInAnnotationMode() == true) {
                    annotationTool?.toggleAnnotationMode(false)
                }
            }
        }

        // AR button
        val btnAr = toolbar.findViewById<ImageButton>(R.id.btn_ar)
        registerButton("ar", btnAr)
        buttonStateManager.setupButton(btnAr) { isActive ->
            if (isActive) {
                toggleCameraWithPermission()
            } else {
                if (cameraManager.isCameraActive()) {
                    cameraManager.stopCamera()
                }
            }
        }

        // Color picker button
        val colorPlate = toolbar.findViewById<ImageButton>(R.id.color_plate)
        registerButton("color", colorPlate)
        buttonStateManager.setupButton(colorPlate) { isActive ->
            if (isActive) {
                popupHandler.showColorPopup(
                    colorPlate,
                    surfaceView,
                    onImagePickRequested = { imagePickerHandler.pickBackgroundImage() },
                    onDismiss = {
                        // Auto-deactivate when popup closes
                        deactivateButtonsByKey("color")
                    }
                )
            }
        }

        // Load lesson button
        val btnLoadLesson = toolbar.findViewById<ImageButton>(R.id.btn_load_lesson)
        registerButton("lesson", btnLoadLesson)
        buttonStateManager.setupButton(btnLoadLesson) { isActive ->
            if (isActive) {
                // TODO: Implement load lesson functionality
                // After functionality completes, call: deactivateButtonsByKey("lesson")
            }
        }

        // Menu (3D models) button
        val btnMenu = toolbar.findViewById<ImageButton>(R.id.btn_menu)
        registerButton("menu", btnMenu)
        buttonStateManager.setupButton(btnMenu) { isActive ->
            if (isActive) {
                showModelBrowser()
                annotationTool?.toggleAnnotationMode(false)
            } else {
                modelBrowserDrawer?.dismiss()
            }
        }

        // Save button
        val btnSave = toolbar.findViewById<ImageButton>(R.id.btn_save)
        registerButton("save", btnSave)
        buttonStateManager.setupButton(btnSave) { isActive ->
            if (isActive) {
                popupHandler.showActionOptionsPopup(
                    btnSave,
                    ActionType.SAVE,
                    onSaveLesson = {
                        // TODO: Implement save
                        deactivateButtonsByKey("save")
                    },
                    onSavePdf = {
                        // TODO: Implement save PDF
                        deactivateButtonsByKey("save")
                    },
                    onDismiss = {
                        // Auto-deactivate when popup closes
                        deactivateButtonsByKey("save")
                    }
                )
            }
        }

        // Insert button
        val btnInsert = toolbar.findViewById<ImageButton>(R.id.btn_insert)
        registerButton("insert", btnInsert)
        buttonStateManager.setupButton(btnInsert) { isActive ->
            if (isActive) {
                popupHandler.showActionOptionsPopup(
                    btnInsert,
                    ActionType.INSERT,
                    onInsertImage = {
                        imagePickerHandler.pickContainerImage()
                        deactivateButtonsByKey("insert")
                    },
                    onInsertPdf = {
                        imagePickerHandler.pickContainerPdf()
                        deactivateButtonsByKey("insert")
                    },
                    onInsertYoutube = {
                        containerManager.addYouTubeContainer()
                        deactivateButtonsByKey("insert")
                    },
                    onInsertWebsite = {
                        containerManager.addWebsiteContainer()
                        deactivateButtonsByKey("insert")
                    },
                    onDismiss = {
                        // Auto-deactivate when popup closes without action
                        deactivateButtonsByKey("insert")
                    }
                )
            }
        }

        // Settings button
        val btnSetting = toolbar.findViewById<ImageButton>(R.id.btn_setting)
        registerButton("setting", btnSetting)
        buttonStateManager.setupButton(btnSetting) { isActive ->
            if (isActive) {
                popupHandler.showContainerManagementMenu(
                    containerManager,
                    onCameraToggle = { toggleCameraWithPermission() },
                    onPauseAll3D = { pauseAll3DRenderingForDrawing() },
                    onResumeAll3D = { resumeAll3DRenderingAfterDrawing() },
                    isCameraActive = cameraManager.isCameraActive(),
                    onDismiss = {
                        // Auto-deactivate when menu closes
                        deactivateButtonsByKey("setting")
                    }
                )
            }
        }
    }


    private fun toggleCameraWithPermission() {
        if (cameraManager.checkCameraPermission()) {
            cameraManager.toggleCamera()
        } else {
            requestCameraPermission()
        }
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            CameraManager.CAMERA_PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            CameraManager.CAMERA_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    cameraManager.startCamera()
                } else {
                    Toast.makeText(
                        this,
                        "Camera permission is required for AR features",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun showModelBrowser() {
        modelBrowserDrawer?.show()
    }

    private fun showAiMaster() {
        aiMasterDrawer?.show()
    }

    private fun handleAiMasterResponse(response: String) {
        android.util.Log.d("AiMasterResponse", response)

        try {
            if (response.isNotEmpty()) {
                android.app.AlertDialog.Builder(this)
                    .setTitle("AI Master Response")
                    .setMessage("Response received! Check logs for details.\n\nResponse length: ${response.length} characters")
                    .setPositiveButton("OK") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .setNeutralButton("View Raw") { dialog, _ ->
                        showRawResponseDialog(response)
                        dialog.dismiss()
                    }
                    .show()
            } else {
                Toast.makeText(this, "Empty response received", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            android.util.Log.e("WhiteboardActivity", "Error handling AI response", e)
            Toast.makeText(
                this,
                "Error processing AI response: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun showRawResponseDialog(response: String) {
        val scrollView = android.widget.ScrollView(this)
        val textView = android.widget.TextView(this).apply {
            text = response
            setPadding(16, 16, 16, 16)
            textSize = 12f
            setTextIsSelectable(true)
        }
        scrollView.addView(textView)

        android.app.AlertDialog.Builder(this)
            .setTitle("Raw API Response")
            .setView(scrollView)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun createCustom3DContainer(modelData: ModelData, fullPath: String) {
        if (containerManager.getContainerCount() >= 1) {
            Toast.makeText(this, "Maximum containers reached", Toast.LENGTH_SHORT).show()
            return
        }

        val container3D = Container3D(this)
        container3D.setModelData(modelData, fullPath)

        val layoutParams = RelativeLayout.LayoutParams(
            container3D.getDefaultWidth(),
            container3D.getDefaultHeight()
        )
        container3D.layoutParams = layoutParams

        // Set elevation to ensure containers are above camera (elevation 0) but below annotation (elevation 200)
        container3D.elevation = 50f

        val offsetX = containerManager.getContainerCount() * 60f
        val offsetY = containerManager.getContainerCount() * 60f + 100f
        container3D.moveContainerTo(offsetX, offsetY, animate = false)

        container3D.onRemoveRequest = {
            containerManager.removeContainer(container3D)
        }

        mainLayout.addView(container3D)
        container3D.initializeContent()

        annotationTool?.bringToFront()
    }

    fun pauseAll3DRenderingForDrawing() {
        val managedContainer3Ds = containerManager.getAllContainers().filterIsInstance<Container3D>()

        val directContainer3Ds = mutableListOf<Container3D>()
        for (i in 0 until mainLayout.childCount) {
            val child = mainLayout.getChildAt(i)
            if (child is Container3D) {
                directContainer3Ds.add(child)
            }
        }

        val allContainer3Ds = (managedContainer3Ds + directContainer3Ds).distinct()
        android.util.Log.d("DEBUG", "Pausing ${allContainer3Ds.size} 3D containers")

        allContainer3Ds.forEach { container ->
            container.pauseRendering()
        }
    }

    fun resumeAll3DRenderingAfterDrawing() {
        val managedContainer3Ds = containerManager.getAllContainers().filterIsInstance<Container3D>()

        val directContainer3Ds = mutableListOf<Container3D>()
        for (i in 0 until mainLayout.childCount) {
            val child = mainLayout.getChildAt(i)
            if (child is Container3D) {
                directContainer3Ds.add(child)
            }
        }

        val allContainer3Ds = (managedContainer3Ds + directContainer3Ds).distinct()
        android.util.Log.d("DEBUG", "Resuming ${allContainer3Ds.size} 3D containers")

        allContainer3Ds.forEach { container ->
            container.resumeRendering()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        imagePickerHandler.handleImagePickResult(requestCode, resultCode, data)
    }

    override fun onPause() {
        super.onPause()
        pauseAll3DRenderingForDrawing()
        if (cameraManager.isCameraActive()) {
            cameraManager.stopCamera()
        }
    }

    override fun onResume() {
        super.onResume()
        resumeAll3DRenderingAfterDrawing()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraManager.shutdown()
        modelBrowserDrawer?.dismiss()
        aiMasterDrawer?.dismiss()
    }

    override fun onBackPressed() {
        // Check if camera is active
        if (cameraManager.isCameraActive()) {
            cameraManager.stopCamera()
            return
        }

        // Check if annotation mode is active
        if (annotationTool?.isInAnnotationMode() == true) {
            annotationTool?.toggleAnnotationMode(false)
            return
        }

        // Handle normal whiteboard exit logic
        if (containerManager.getContainerCount() > 0) {
            android.app.AlertDialog.Builder(this)
                .setTitle("Save Whiteboard?")
                .setMessage("You have ${containerManager.getContainerCount()} container(s). Save before leaving?")
                .setPositiveButton("Save & Exit") { _, _ ->
                    stateManager.saveState(containerManager, cameraManager.isCameraActive())
                    Toast.makeText(this, "Whiteboard saved", Toast.LENGTH_SHORT).show()
                    super.onBackPressed()
                }
                .setNegativeButton("Exit Without Saving") { _, _ ->
                    super.onBackPressed()
                }
                .setNeutralButton("Cancel") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        } else {
            super.onBackPressed()
        }
    }

    // Add this method to WhiteboardActivity
    fun addImageContainerFromBase64(imageBase64: String) {
        try {
            // Decode the base64 string to bitmap
            val decodedString = if (imageBase64.contains(",")) {
                imageBase64.split(",")[1] // Remove data URI prefix if present
            } else {
                imageBase64
            }
            val decodedByte = android.util.Base64.decode(decodedString, android.util.Base64.DEFAULT)
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(decodedByte, 0, decodedByte.size)

            if (bitmap != null) {
                // Create a new ContainerImage
                val containerImage = com.infusory.tutarapp.ui.components.containers.ContainerImage(this)

                // Set the bitmap to the container
                containerImage.setImage(bitmap, "base64_image_${System.currentTimeMillis()}")

                // Set layout params
                val layoutParams = RelativeLayout.LayoutParams(
                    containerImage.getDefaultWidth(),
                    containerImage.getDefaultHeight()
                )
                containerImage.layoutParams = layoutParams

                // Set elevation to ensure containers are above camera but below annotation
                containerImage.elevation = 50f

                // Position the container with offset
                val offsetX = containerManager.getContainerCount() * 60f + 100f
                val offsetY = containerManager.getContainerCount() * 60f + 200f
                containerImage.moveContainerTo(offsetX, offsetY, animate = false)

                // Set remove callback
                containerImage.onRemoveRequest = {
                    containerManager.removeContainer(containerImage)
                }

                // Add to main layout
                mainLayout.addView(containerImage)

                // Initialize content
                containerImage.initializeContent()

                // Register with container manager (if your ContainerManager supports it)
                // containerManager.addContainer(containerImage) // Uncomment if this method exists

                Toast.makeText(this, "Image added to canvas", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Failed to decode image", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            android.util.Log.e("WhiteboardActivity", "Error adding image to container", e)
            Toast.makeText(this, "Error adding image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Add this method to WhiteboardActivity class
    fun addYouTubeContainerWithUrl(youtubeUrl: String) {
        try {
            // Use the existing containerManager to add YouTube container
            val youtubeContainer = containerManager.addYouTubeContainer()

            // Set the YouTube URL to the container
            if (youtubeContainer is com.infusory.tutarapp.ui.components.containers.ContainerYouTube) {
                youtubeContainer.setYouTubeUrl(youtubeUrl)
                Toast.makeText(this, "YouTube video loaded", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            android.util.Log.e("WhiteboardActivity", "Error adding YouTube container", e)
            Toast.makeText(this, "Error adding YouTube video: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Add this updated method to your WhiteboardActivity class

    // Add this updated method to your WhiteboardActivity class

    fun addTextContainerWithContent(text: String, title: String = "") {
        try {
            // Create a new ContainerText
            val containerText = com.infusory.tutarapp.ui.components.containers.ContainerText(this)

            // Set the title if provided
            if (title.isNotEmpty()) {
                containerText.setTitle(title)
            }

            // Set the text content
            containerText.setText(text)

            // Set layout params with WRAP_CONTENT for dynamic height
            val width = (350 * resources.displayMetrics.density).toInt()

            val layoutParams = RelativeLayout.LayoutParams(
                width,
                RelativeLayout.LayoutParams.WRAP_CONTENT  // Dynamic height based on content
            )
            containerText.layoutParams = layoutParams

            // Set elevation to ensure containers are above camera but below annotation
            containerText.elevation = 50f

            // Position the container with offset based on existing containers
            val offsetX = containerManager.getContainerCount() * 60f + 100f
            val offsetY = containerManager.getContainerCount() * 60f + 150f
            containerText.moveContainerTo(offsetX, offsetY, animate = true)

            // Set remove callback
            containerText.onRemoveRequest = {
                containerManager.removeContainer(containerText)
            }

            // Add to main layout
            mainLayout.addView(containerText)

            // Initialize content
            containerText.initializeContent()

            // Bring annotation tool to front
            annotationTool?.bringToFront()

            Toast.makeText(this, "Text added to canvas", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            android.util.Log.e("WhiteboardActivity", "Error adding text container", e)
            Toast.makeText(this, "Error adding text: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}