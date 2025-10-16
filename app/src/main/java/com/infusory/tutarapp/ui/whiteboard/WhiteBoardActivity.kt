package com.infusory.tutarapp.ui.whiteboard

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
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
import com.infusory.tutarapp.ui.dialogs.SettingsPopup
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import android.widget.RelativeLayout
import com.infusory.tutarapp.ui.ai.DrawView
import com.infusory.tutarapp.ui.ai.ScreenAnalyzerView
import com.infusory.tutarapp.ui.containers.UnifiedContainer
import com.infusory.tutarapp.ui.containers.ImageContentBehavior
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.util.Base64

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

    private var settingsPopup: SettingsPopup? = null

    // Drawers
    private var modelBrowserDrawer: ModelBrowserDrawer? = null
    private var aiMasterDrawer: AiMasterDrawer? = null

    // Annotation
    private var annotationTool: AnnotationToolView? = null

    // Track button references for auto-deactivation
    private val buttonReferences = mutableMapOf<String, MutableList<ImageButton>>()

    companion object {
        const val STORAGE_PERMISSION_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        makeFullscreen()
        setContentView(R.layout.activity_whiteboard)

        initViews()
        initManagers()
        initHandlers()
        setupUI()
        SettingsPopup(this).applySavedSettings()
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
            elevation = 0f
            visibility = View.GONE
        }
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

        modelBrowserDrawer?.setOnDismissListener {
            deactivateButtonsByKey("menu")
        }
    }

    private fun setupAiMaster() {
        Log.e("WhiteboardActivity", "setupAiMaster: Starting")
        val backgroundColor = getMainLayoutBackgroundColor()
        Log.e("WhiteboardActivity", "setupAiMaster: Background color: #${String.format("%08X", backgroundColor)}")
        aiMasterDrawer = AiMasterDrawer(this, backgroundColor) { response ->
            handleAiMasterResponse(response)
        }
    }

    private fun setupButtonListeners() {
        val leftToolbar = findViewById<View>(R.id.left_toolbar)
        val rightToolbar = findViewById<View>(R.id.right_toolbar)

        registerButtonPairs(leftToolbar, rightToolbar)
        setupToolbarButtons(leftToolbar)
        setupToolbarButtons(rightToolbar)

        val aiMasterBtn = findViewById<ImageButton>(R.id.ai_master_btn)
        if (aiMasterBtn != null) {
            registerButton("ai_master", aiMasterBtn)
            buttonStateManager.setupButton(aiMasterBtn) {
                showAiMaster()
            }
        } else {
            Log.w("WhiteboardActivity", "AI Master button not found")
        }

        val circleSearchBtn = findViewById<ImageButton>(R.id.btn_circle_search)
        if (circleSearchBtn != null) {
            registerButton("circle_search", circleSearchBtn)
            buttonStateManager.setupButton(circleSearchBtn) {
                startCircleToSearch()
            }
        } else {
            Log.w("WhiteboardActivity", "Circle to Search button not found")
        }

        val btnAnalyzeScreen = findViewById<ImageButton>(R.id.btn_analyze_screen)
        if (btnAnalyzeScreen != null) {
            registerButton("analyze_screen", btnAnalyzeScreen)
            buttonStateManager.setupButton(btnAnalyzeScreen) {
                startScreenAnalysis()
            }
        }
    }

    private fun registerButton(key: String, button: ImageButton) {
        if (!buttonReferences.containsKey(key)) {
            buttonReferences[key] = mutableListOf()
        }
        buttonReferences[key]?.add(button)
    }

    private fun deactivateButtonsByKey(key: String) {
        when (key) {
            "menu" -> {
                modelBrowserDrawer?.dismiss()
            }

            "circle_search" -> {
                // No specific cleanup needed for now
            }

            "analyze_screen" -> {
                // No specific cleanup needed
            }
        }

        buttonReferences[key]?.forEach { button ->
            buttonStateManager.deactivateButtons(button)
        }
    }

    private fun registerButtonPairs(leftToolbar: View, rightToolbar: View) {
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
            } else {
                Log.w("WhiteboardActivity", "Button pair not found for ID: $buttonId")
            }
        }
    }

    private fun setupToolbarButtons(toolbar: View) {
        val btnDraw = toolbar.findViewById<ImageButton>(R.id.btn_draw)
        if (btnDraw != null) {
            registerButton("draw", btnDraw)
            buttonStateManager.setupButton(btnDraw) { isActive ->
                if (isActive) {
                    annotationTool?.toggleAnnotationMode(true)
                } else {
                    if (annotationTool?.isInAnnotationMode() == true) {
                        annotationTool?.toggleAnnotationMode(false)
                    }
                }
            }
        }

        val btnAr = toolbar.findViewById<ImageButton>(R.id.btn_ar)
        if (btnAr != null) {
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
        }

        val colorPlate = toolbar.findViewById<ImageButton>(R.id.color_plate)
        if (colorPlate != null) {
            registerButton("color", colorPlate)
            buttonStateManager.setupButton(colorPlate) { isActive ->
                if (isActive) {
                    popupHandler.showColorPopup(
                        colorPlate,
                        surfaceView,
                        onImagePickRequested = { imagePickerHandler.pickBackgroundImage() },
                        onDismiss = {
                            deactivateButtonsByKey("color")
                        }
                    )
                }
            }
        }

        val btnLoadLesson = toolbar.findViewById<ImageButton>(R.id.btn_load_lesson)
        if (btnLoadLesson != null) {
            registerButton("lesson", btnLoadLesson)
            buttonStateManager.setupButton(btnLoadLesson) { isActive ->
                if (isActive) {
                    // TODO: Implement load lesson functionality
                    // After functionality completes, call: deactivateButtonsByKey("lesson")
                }
            }
        }

        val btnMenu = toolbar.findViewById<ImageButton>(R.id.btn_menu)
        if (btnMenu != null) {
            registerButton("menu", btnMenu)
            buttonStateManager.setupButton(btnMenu) { isActive ->
                if (isActive) {
                    showModelBrowser()
                    annotationTool?.toggleAnnotationMode(false)
                } else {
                    modelBrowserDrawer?.dismiss()
                }
            }
        }

        val btnSave = toolbar.findViewById<ImageButton>(R.id.btn_save)
        if (btnSave != null) {
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
                            deactivateButtonsByKey("save")
                        }
                    )
                }
            }
        }

        val btnInsert = toolbar.findViewById<ImageButton>(R.id.btn_insert)
        if (btnInsert != null) {
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
                            deactivateButtonsByKey("insert")
                        }
                    )
                }
            }
        }

        val btnSetting = toolbar.findViewById<ImageButton>(R.id.btn_setting)
        if (btnSetting != null) {
            registerButton("setting", btnSetting)
            buttonStateManager.setupButton(btnSetting) { isActive ->
                if (isActive) {
                    showSettingsPopup()
                } else {
                    settingsPopup?.dismiss()
                }
            }
        }
    }

    private fun showSettingsPopup() {
        settingsPopup = SettingsPopup(this)
        settingsPopup?.show()
        settingsPopup?.dialog?.setOnDismissListener {
            deactivateButtonsByKey("setting")
        }
    }

    private fun startScreenAnalysis() {

        pauseAll3DRenderingForDrawing()
        mainLayout.requestLayout()
        mainLayout.invalidate()

        mainLayout.postDelayed({
            val dialog = Dialog(this, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen)

            val analyzerView = ScreenAnalyzerView(this, mainLayout) { bitmap ->

                dialog.dismiss()
                resumeAll3DRenderingAfterDrawing()
                deactivateButtonsByKey("analyze_screen")

                if (bitmap != null) {
                    performFullScreenAnalysis(bitmap)
                } else {
                    Toast.makeText(
                        this,
                        "Failed to capture screenshot",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            val layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            analyzerView.layoutParams = layoutParams

            dialog.setContentView(analyzerView)

            dialog.window?.apply {
                setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundDrawableResource(android.R.color.transparent)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setDecorFitsSystemWindows(false)
                    insetsController?.apply {
                        hide(android.view.WindowInsets.Type.systemBars())
                        systemBarsBehavior =
                            android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    }
                } else {
                    @Suppress("DEPRECATION")
                    decorView.systemUiVisibility = (
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            )
                }
            }

            dialog.setOnCancelListener {
                android.util.Log.d("WhiteboardActivity", "Screen Analysis cancelled")
                analyzerView.reset()
                resumeAll3DRenderingAfterDrawing()
                deactivateButtonsByKey("analyze_screen")
            }

            dialog.show()
            analyzerView.startAnalysis()
        }, 150)
    }

    private fun performFullScreenAnalysis(bitmap: Bitmap) {
        performSearch(bitmap)
        Toast.makeText(
            this,
            "Full screen captured and saved. Ready for AI analysis.",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun startCircleToSearch() {
        android.util.Log.d("WhiteboardActivity", "Starting Circle to Search")

        pauseAll3DRenderingForDrawing()
        mainLayout.requestLayout()
        mainLayout.invalidate()

        mainLayout.postDelayed({
            val dialog = Dialog(this, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen)

            val drawView = DrawView(this, mainLayout) { bitmap ->
                android.util.Log.d("WhiteboardActivity", "DrawView callback received")

                dialog.dismiss()
                resumeAll3DRenderingAfterDrawing()
                deactivateButtonsByKey("circle_search")

                if (bitmap != null) {
                    android.util.Log.d(
                        "WhiteboardActivity",
                        "Bitmap captured: ${bitmap.width}x${bitmap.height}"
                    )
                    performSearch(bitmap)
                } else {
                    android.util.Log.e("WhiteboardActivity", "Bitmap is null")
                    Toast.makeText(
                        this,
                        "Failed to capture screenshot",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            val layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            drawView.layoutParams = layoutParams

            dialog.setContentView(drawView)

            dialog.window?.apply {
                setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundDrawableResource(android.R.color.transparent)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setDecorFitsSystemWindows(false)
                    insetsController?.apply {
                        hide(android.view.WindowInsets.Type.systemBars())
                        systemBarsBehavior =
                            android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    }
                } else {
                    @Suppress("DEPRECATION")
                    decorView.systemUiVisibility = (
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            )
                }
            }

            dialog.setOnCancelListener {
                android.util.Log.d("WhiteboardActivity", "Circle to Search cancelled")
                drawView.reset()
                resumeAll3DRenderingAfterDrawing()
                deactivateButtonsByKey("circle_search")
            }

            dialog.show()
        }, 150)
    }


    private fun saveBitmapToGallery(bitmap: Bitmap, prefix: String = "Screenshot") {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    STORAGE_PERMISSION_REQUEST_CODE
                )
                return
            }
        }

        val contentValues = ContentValues().apply {
            put(
                MediaStore.Images.Media.DISPLAY_NAME,
                "${prefix}_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.png"
            )
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/TutarApp")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (uri != null) {
            try {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }
                Toast.makeText(this, "Image saved to gallery", Toast.LENGTH_SHORT).show()
            } catch (e: IOException) {
                Log.e("WhiteboardActivity", "Error saving image", e)
                Toast.makeText(this, "Error saving image: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Failed to create image file", Toast.LENGTH_SHORT).show()
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

            STORAGE_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(
                        this,
                        "Storage permission granted. Try saving again.",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        this,
                        "Storage permission denied. Cannot save image.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            ImagePickerHandler.PERMISSION_REQUEST_CODE -> {
                // Delegate to ImagePickerHandler
                imagePickerHandler.onPermissionResult(requestCode, grantResults)
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
            Toast.makeText(this, "Error processing AI response: ${e.message}", Toast.LENGTH_LONG)
                .show()
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
        val managedContainer3Ds =
            containerManager.getAllContainers().filterIsInstance<Container3D>()
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
        val managedContainer3Ds =
            containerManager.getAllContainers().filterIsInstance<Container3D>()
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
        // Let ImagePickerHandler handle image/PDF picking results
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
        settingsPopup?.dismiss()
        settingsPopup = null

        // Cleanup image picker handler
        imagePickerHandler.cleanup()
    }

    override fun onBackPressed() {
        if (cameraManager.isCameraActive()) {
            cameraManager.stopCamera()
            return
        }
        if (annotationTool?.isInAnnotationMode() == true) {
            annotationTool?.toggleAnnotationMode(false)
            return
        }
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

    /*
    * Add image container from base64 string (for AI responses)
    * UPDATED: Uses UnifiedContainer with ImageContentBehavior
    */
    fun addImageContainerFromBase64(imageBase64: String) {
        try {
            val decodedString = if (imageBase64.contains(",")) {
                imageBase64.split(",")[1]
            } else {
                imageBase64
            }
            val decodedByte = android.util.Base64.decode(decodedString, android.util.Base64.DEFAULT)
            val bitmap =
                android.graphics.BitmapFactory.decodeByteArray(decodedByte, 0, decodedByte.size)

            if (bitmap != null) {
                // Create UnifiedContainer
                val container = UnifiedContainer(this).apply {
                    tag = "base64_image_${System.currentTimeMillis()}"
                    elevation = 50f
                }

                // Create ImageContentBehavior
                val imageBehavior = ImageContentBehavior(this)

                // Attach behavior to container
                imageBehavior.onAttached(container)

                // Set the image
                val path = "base64_image_${System.currentTimeMillis()}"
                imageBehavior.setImage(bitmap, path)

                // Set close callback
                container.onCloseClicked = {
                    imageBehavior.onDetached()
                    mainLayout.removeView(container)
                }

                // IMPORTANT: Get the size AFTER setting the image
                val (width, height) = container.getCurrentSize()

                // Set proper layout params
                val layoutParams = RelativeLayout.LayoutParams(width, height)
                container.layoutParams = layoutParams

                // Add to layout
                mainLayout.addView(container)

                // Position with offset AFTER adding to layout
                container.post {
                    val offsetX = containerManager.getContainerCount() * 60f + 100f
                    val offsetY = containerManager.getContainerCount() * 60f + 200f
                    container.moveTo(offsetX, offsetY, animate = false)
                }

                // Bring annotation tool to front
                annotationTool?.bringToFront()

                Toast.makeText(this, "Image added to canvas", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Failed to decode image", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            android.util.Log.e("WhiteboardActivity", "Error adding image to container", e)
            Toast.makeText(this, "Error adding image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /*
    * Add YouTube container with URL
    */
    fun addYouTubeContainerWithUrl(youtubeUrl: String) {
        try {
            val youtubeContainer = containerManager.addYouTubeContainer()
            if (youtubeContainer is com.infusory.tutarapp.ui.components.containers.ContainerYouTube) {
                youtubeContainer.setYouTubeUrl(youtubeUrl)
                Toast.makeText(this, "YouTube video loaded", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            android.util.Log.e("WhiteboardActivity", "Error adding YouTube container", e)
            Toast.makeText(this, "Error adding YouTube video: ${e.message}", Toast.LENGTH_SHORT)
                .show()
        }
    }

    /*
    * Add text container with content
    */
    fun addTextContainerWithContent(text: String, title: String = "") {
        try {
            val containerText = com.infusory.tutarapp.ui.components.containers.ContainerText(this)
            val fullText = if (title.isNotEmpty()) {
                "$title\n\n$text"
            } else {
                text
            }
            containerText.setText(fullText)
            val width = (300 * resources.displayMetrics.density).toInt()
            val height = (400 * resources.displayMetrics.density).toInt()
            val layoutParams = RelativeLayout.LayoutParams(width, height)
            containerText.layoutParams = layoutParams
            containerText.elevation = 50f

            // Position the container with offset based on existing containers
            val offsetX = containerManager.getContainerCount() * 60f + 100f
            val offsetY = containerManager.getContainerCount() * 60f + 150f
            containerText.moveContainerTo(offsetX, offsetY, animate = true)
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
            Toast.makeText(this, "Error adding text: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun performSearch(bitmap: Bitmap) {
        try {
            val base64Image = convertBitmapToBase64(bitmap)

            if (base64Image.isNullOrEmpty()) {
                Toast.makeText(this, "Failed to convert image", Toast.LENGTH_SHORT).show()
                return
            }
            aiMasterDrawer?.let {
                it.performImageSearchDirectly(base64Image)
                Toast.makeText(this, "Analyzing new image...", Toast.LENGTH_SHORT).show()
            } ?: run {
                Toast.makeText(this, "AI Master not initialized", Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            Log.e("WhiteboardActivity", "Error converting bitmap to base64", e)
            Toast.makeText(this, "Error processing image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /*
    * Converts a Bitmap to Base64 (without data URI prefix).
    */
    private fun convertBitmapToBase64(bitmap: Bitmap): String? {
        return try {
            Log.d("WhiteboardActivity", "=== Starting Bitmap to Base64 Conversion ===")

            val outputStream = ByteArrayOutputStream()
            val format = Bitmap.CompressFormat.PNG
            val success = bitmap.compress(format, 100, outputStream)

            if (!success) {
                Log.e("WhiteboardActivity", "❌ Bitmap compression failed")
                return null
            }

            val byteArray = outputStream.toByteArray()
            val sizeKB = byteArray.size / 1024.0
            Log.d(
                "WhiteboardActivity",
                "✅ Compressed to ${String.format("%.2f", sizeKB)} KB (${byteArray.size} bytes)"
            )

            outputStream.close()

            // Encode to base64
            val base64 = Base64.encodeToString(byteArray, Base64.NO_WRAP)
            Log.d("WhiteboardActivity", "✅ Base64 encoded: ${base64.length} characters")

            // Verify it's a valid PNG base64
            if (base64.startsWith("iVBORw0KGgo")) {
                Log.d("WhiteboardActivity", "✅ Valid PNG base64 signature confirmed")
            } else {
                Log.w("WhiteboardActivity", "⚠️ Unexpected base64 start: ${base64.take(20)}")
            }

            base64
        } catch (e: Exception) {
            Log.e("WhiteboardActivity", "❌ Error in convertBitmapToBase64", e)
            null
        }
    }

    private fun getMainLayoutBackgroundColor(): Int {
        Log.e("WhiteboardActivity", "getMainLayoutBackgroundColor: Starting")
        return try {
            if (mainLayout == null) {
                Log.e("WhiteboardActivity", "mainLayout is null!")
                return Color.WHITE
            }
            val background = mainLayout.background
            if (background is ColorDrawable) {
                val color = background.color
                Log.e("WhiteboardActivity", "Background color (ColorDrawable): #${String.format("%08X", color)}")
                color
            } else {
                Log.e("WhiteboardActivity", "Background is null or not a ColorDrawable, falling back to theme")
                val attrs = intArrayOf(android.R.attr.background)
                val typedArray = theme.obtainStyledAttributes(attrs)
                val color = typedArray.getColor(0, Color.WHITE)
                Log.e("WhiteboardActivity", "Theme background color: #${String.format("%08X", color)}")
                typedArray.recycle()
                color
            }
        } catch (e: Exception) {
            Log.e("WhiteboardActivity", "Error getting background color", e)
            Color.WHITE
        }
    }
}