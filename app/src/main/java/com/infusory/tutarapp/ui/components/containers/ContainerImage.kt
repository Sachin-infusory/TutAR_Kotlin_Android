// ContainerImage.kt
package com.infusory.tutarapp.ui.components.containers

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import com.infusory.tutarapp.R
import com.infusory.tutarapp.ui.components.containers.ControlButton
import com.infusory.tutarapp.ui.components.containers.ButtonPosition
import com.infusory.tutarapp.ui.components.containers.ContainerBase

class ContainerImage @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ContainerBase(context, ContainerType.IMAGE, attrs, defStyleAttr) {

    private var currentImageResource: Int? = null
    private var currentImagePath: String? = null
    private var currentBitmap: Bitmap? = null
    private var imageRotation = 0f
    private var imageAlpha = 1.0f
    private var imageScaleType = ImageView.ScaleType.CENTER_CROP
    private var applyFilter = FilterType.NONE
    private var imageTint: Int? = null
    private var currentImageView: ImageView? = null

    // Store the original aspect ratio to maintain it during resizing
    private var imageAspectRatio: Float = 1f

    enum class FilterType {
        NONE, GRAYSCALE, SEPIA, BLUR, BRIGHTNESS, CONTRAST, VINTAGE
    }

    init {
        setupImageContainer()
        // Remove any default padding from the container
        setPadding(0, 0, 0, 0)

        // Enable aspect ratio maintenance for image containers
        maintainAspectRatio = true
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)

        if (changed) {
            android.util.Log.d("ContainerImage", "Container laid out: ${right - left} x ${bottom - top}")
        }
    }

    fun setImage(bitmap: Bitmap, path: String) {
        currentBitmap = bitmap
        currentImagePath = path
        currentImageResource = null

        // Get actual bitmap dimensions (not rotated)
        val actualWidth = bitmap.width
        val actualHeight = bitmap.height

        // Store the aspect ratio for future resize operations
        imageAspectRatio = actualWidth.toFloat() / actualHeight.toFloat()

        // Update the parent container's aspect ratio property and enable maintenance
        maintainAspectRatio = true
        aspectRatio = imageAspectRatio

        android.util.Log.d("ContainerImage", "Setting image: ${actualWidth} x ${actualHeight}, aspect ratio: $imageAspectRatio, maintain: $maintainAspectRatio")

        // Update container size to match ACTUAL image dimensions
        updateContainerSizeToImage(actualWidth, actualHeight)

        // Set the image to the ImageView
        currentImageView?.apply {
            setImageBitmap(bitmap)
            setPadding(0, 0, 0, 0)
            scaleType = ImageView.ScaleType.CENTER_CROP  // Use CENTER_CROP to fill without distortion
            adjustViewBounds = false  // Changed to false to prevent additional margins
        }
        updateImageView()
    }

    private fun updateContainerSizeToImage(imageWidth: Int, imageHeight: Int) {
        val density = context.resources.displayMetrics.density

        // Maximum dimensions to prevent extremely large containers
        val maxWidth = (800 * density).toInt()
        val maxHeight = (800 * density).toInt()

        // Start with actual image dimensions
        var targetWidth = imageWidth
        var targetHeight = imageHeight

        // Scale down if image is too large, maintaining aspect ratio
        if (imageWidth > maxWidth || imageHeight > maxHeight) {
            val widthRatio = maxWidth.toFloat() / imageWidth
            val heightRatio = maxHeight.toFloat() / imageHeight
            val scaleFactor = minOf(widthRatio, heightRatio)

            targetWidth = (imageWidth * scaleFactor).toInt()
            targetHeight = (imageHeight * scaleFactor).toInt()
        }

        // Ensure minimum size for usability
        val minSize = (100 * density).toInt()
        targetWidth = maxOf(targetWidth, minSize)
        targetHeight = maxOf(targetHeight, minSize)

        android.util.Log.d("ContainerImage", "Setting container size: $targetWidth x $targetHeight")

        // Update base dimensions to match the image's initial size
        baseWidth = targetWidth
        baseHeight = targetHeight
        currentWidth = targetWidth
        currentHeight = targetHeight

        // Update the container's layout params with correct width and height
        val newLayoutParams = when (val params = layoutParams) {
            is android.widget.RelativeLayout.LayoutParams -> {
                params.width = targetWidth
                params.height = targetHeight
                params
            }
            is android.view.ViewGroup.MarginLayoutParams -> {
                params.width = targetWidth
                params.height = targetHeight
                params
            }
            else -> {
                android.view.ViewGroup.LayoutParams(targetWidth, targetHeight)
            }
        }

        layoutParams = newLayoutParams
        requestLayout()
    }

    private fun setupImageContainer() {
        // Remove any padding from container
        setPadding(4, 4, 4, 4)

        val buttons = listOf(
            ControlButton(
                iconRes = android.R.drawable.ic_menu_close_clear_cancel,
                onClick = { onRemoveRequest?.invoke() },
                position = ButtonPosition.TOP_START
            ),
            ControlButton(
                iconRes = android.R.drawable.ic_menu_camera,
                onClick = { showImageSelectionDialog() },
                position = ButtonPosition.TOP_CENTER
            ),
            ControlButton(
                iconRes = android.R.drawable.ic_menu_rotate,
                onClick = { rotateImage() },
                position = ButtonPosition.TOP_END
            ),
            ControlButton(
                iconRes = android.R.drawable.ic_menu_edit,
                onClick = { showImageEditDialog() },
                position = ButtonPosition.BOTTOM_START
            ),
            ControlButton(
                iconRes = android.R.drawable.ic_menu_more,
                onClick = { showImageMenu() },
                position = ButtonPosition.BOTTOM_END
            )
        )

        addControlButtons(buttons)
    }

    override fun initializeContent() {
        createImageView()
    }

    private fun createImageView() {
        currentImageView = ImageView(context).apply {
            // Only set image if one exists
            currentBitmap?.let {
                setImageBitmap(it)
            } ?: currentImageResource?.let {
                setImageResource(it)
            }

            // Remove any padding from ImageView
            setPadding(0, 0, 0, 0)

            scaleType = imageScaleType
            adjustViewBounds = false  // Set to false to prevent unwanted margins
            rotation = imageRotation
            imageAlpha = (this@ContainerImage.imageAlpha * 255).toInt()

            // Apply tint if set
            imageTint?.let { setColorFilter(it, PorterDuff.Mode.SRC_ATOP) }

            // Match parent to fill the container completely with NO margins
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            ).apply {
                setMargins(0, 0, 0, 0)  // Zero margins
            }
        }

        // Remove existing content (except buttons)
        val viewsToRemove = mutableListOf<View>()
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            // Check if it's a button by seeing if it's an ImageView with small size
            val isButton = child is ImageView &&
                    child.layoutParams.width == dpToPx(24) &&
                    child.layoutParams.height == dpToPx(24)
            if (!isButton) {
                viewsToRemove.add(child)
            }
        }
        viewsToRemove.forEach { view -> removeView(view) }

        // Add the image view at index 0 so buttons stay on top
        addView(currentImageView!!, 0)

        applyCurrentFilter()
    }

    private fun showImageSelectionDialog() {
        val options = arrayOf(
            "Sample Images",
            "Camera (Placeholder)",
            "Gallery (Placeholder)",
            "Remove Image"
        )

        AlertDialog.Builder(context)
            .setTitle("Select Image Source")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showSampleImagesDialog()
                    1 -> {
                        android.widget.Toast.makeText(context, "Camera integration coming soon", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    2 -> {
                        android.widget.Toast.makeText(context, "Gallery integration coming soon", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    3 -> removeImage()
                }
            }
            .show()
    }

    private fun showSampleImagesDialog() {
        val images = arrayOf("App Logo", "Android Robot", "Star Icon", "Info Icon")
        val imageResources = arrayOf(
            R.drawable.tutar_logo,
            android.R.drawable.sym_def_app_icon,
            android.R.drawable.btn_star_big_on,
            android.R.drawable.ic_dialog_info
        )

        AlertDialog.Builder(context)
            .setTitle("Select Sample Image")
            .setItems(images) { _, which ->
                currentImageResource = imageResources[which]
                currentImagePath = null
                currentBitmap = null
                updateImageView()
                android.widget.Toast.makeText(context, "Image: ${images[which]}", android.widget.Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun removeImage() {
        currentImageResource = null
        currentImagePath = null
        currentBitmap = null
        currentImageView?.setImageDrawable(null)
        currentImageView?.setBackgroundColor(Color.LTGRAY)
        android.widget.Toast.makeText(context, "Image removed", android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun rotateImage() {
        imageRotation = (imageRotation + 90f) % 360f
        updateImageView()
        android.widget.Toast.makeText(context, "Rotated to ${imageRotation.toInt()}°", android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun showImageEditDialog() {
        val editView = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(dpToPx(20), dpToPx(16), dpToPx(20), dpToPx(16))
        }

        // Rotation control
        editView.addView(android.widget.TextView(context).apply {
            text = "Rotation: ${imageRotation.toInt()}°"
            textSize = 16f
        })

        val rotationSeekBar = android.widget.SeekBar(context).apply {
            max = 360
            progress = imageRotation.toInt()
            setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        imageRotation = progress.toFloat()
                        (editView.getChildAt(0) as android.widget.TextView).text = "Rotation: ${progress}°"
                    }
                }
                override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
            })
        }
        editView.addView(rotationSeekBar)

        // Alpha control
        editView.addView(android.widget.TextView(context).apply {
            text = "Opacity: ${(imageAlpha * 100).toInt()}%"
            textSize = 16f
            setPadding(0, dpToPx(16), 0, 0)
        })

        val alphaSeekBar = android.widget.SeekBar(context).apply {
            max = 100
            progress = (imageAlpha * 100).toInt()
            setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        imageAlpha = progress / 100f
                        (editView.getChildAt(2) as android.widget.TextView).text = "Opacity: ${progress}%"
                    }
                }
                override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
            })
        }
        editView.addView(alphaSeekBar)

        AlertDialog.Builder(context)
            .setTitle("Edit Image")
            .setView(editView)
            .setPositiveButton("Apply") { _, _ ->
                updateImageView()
                android.widget.Toast.makeText(context, "Changes applied", android.widget.Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Reset") { _, _ ->
                imageRotation = 0f
                imageAlpha = 1.0f
                updateImageView()
                android.widget.Toast.makeText(context, "Image reset", android.widget.Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showImageMenu() {
        val options = arrayOf(
            "Scale Type",
            "Apply Filter",
            "Tint Color",
            "Reset All Effects",
            "Image Info"
        )

        AlertDialog.Builder(context)
            .setTitle("Image Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showScaleTypeDialog()
                    1 -> showFilterDialog()
                    2 -> showTintDialog()
                    3 -> resetAllEffects()
                    4 -> showImageInfoDialog()
                }
            }
            .show()
    }

    private fun showScaleTypeDialog() {
        val scaleTypes = arrayOf(
            "Fit XY (Fill)", "Center Crop", "Fit Center", "Center Inside", "Fit Start", "Fit End", "Center", "Matrix"
        )
        val scaleTypeValues = arrayOf(
            ImageView.ScaleType.FIT_XY,
            ImageView.ScaleType.CENTER_CROP,
            ImageView.ScaleType.FIT_CENTER,
            ImageView.ScaleType.CENTER_INSIDE,
            ImageView.ScaleType.FIT_START,
            ImageView.ScaleType.FIT_END,
            ImageView.ScaleType.CENTER,
            ImageView.ScaleType.MATRIX
        )

        val currentIndex = scaleTypeValues.indexOf(imageScaleType)

        AlertDialog.Builder(context)
            .setTitle("Select Scale Type")
            .setSingleChoiceItems(scaleTypes, currentIndex) { dialog, which ->
                imageScaleType = scaleTypeValues[which]
                updateImageView()
                android.widget.Toast.makeText(context, "Scale: ${scaleTypes[which]}", android.widget.Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showFilterDialog() {
        val filters = FilterType.values().map { it.name.lowercase().replaceFirstChar { char -> char.uppercase() } }.toTypedArray()
        val currentIndex = FilterType.values().indexOf(applyFilter)

        AlertDialog.Builder(context)
            .setTitle("Apply Filter")
            .setSingleChoiceItems(filters, currentIndex) { dialog, which ->
                applyFilter = FilterType.values()[which]
                updateImageView()
                android.widget.Toast.makeText(context, "Filter: ${filters[which]}", android.widget.Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showTintDialog() {
        val colors = arrayOf("None", "Red", "Green", "Blue", "Yellow", "Purple", "Orange", "Gray")
        val colorValues = arrayOf(
            null, Color.RED, Color.GREEN, Color.BLUE,
            Color.YELLOW, Color.parseColor("#800080"), Color.parseColor("#FFA500"), Color.GRAY
        )

        AlertDialog.Builder(context)
            .setTitle("Select Tint Color")
            .setItems(colors) { _, which ->
                imageTint = colorValues[which]
                updateImageView()
                android.widget.Toast.makeText(context, "Tint: ${colors[which]}", android.widget.Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun resetAllEffects() {
        imageRotation = 0f
        imageAlpha = 1.0f
        imageScaleType = ImageView.ScaleType.CENTER_CROP  // Reset to CENTER_CROP
        applyFilter = FilterType.NONE
        imageTint = null
        updateImageView()
        android.widget.Toast.makeText(context, "All effects reset", android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun showImageInfoDialog() {
        val info = buildString {
            append("Image Information:\n\n")
            append("Source: ${if (currentImageResource != null) "Resource" else if (currentImagePath != null) "File" else "None"}\n")
            currentBitmap?.let {
                append("Bitmap Size: ${it.width} x ${it.height} (W×H)\n")
            }
            append("Container Size: ${width} x ${height} (W×H)\n")
            append("Rotation: ${imageRotation.toInt()}°\n")
            append("Opacity: ${(imageAlpha * 100).toInt()}%\n")
            append("Scale Type: ${imageScaleType.name}\n")
            append("Filter: ${applyFilter.name}\n")
            append("Tint: ${if (imageTint != null) "Applied" else "None"}")
        }

        AlertDialog.Builder(context)
            .setTitle("Image Information")
            .setMessage(info)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun updateImageView() {
        currentImageView?.apply {
            rotation = imageRotation
            imageAlpha = (this@ContainerImage.imageAlpha * 255).toInt()
            scaleType = imageScaleType
            adjustViewBounds = false  // Keep false to prevent margins

            // Clear previous tint
            clearColorFilter()

            // Apply tint if set
            imageTint?.let { setColorFilter(it, PorterDuff.Mode.SRC_ATOP) }

            // Apply filter
            applyCurrentFilter()
        }
    }

    private fun applyCurrentFilter() {
        currentImageView?.let { imageView ->
            when (applyFilter) {
                FilterType.NONE -> {
                    imageView.colorFilter = null
                }
                FilterType.GRAYSCALE -> {
                    val matrix = ColorMatrix().apply { setSaturation(0f) }
                    imageView.colorFilter = ColorMatrixColorFilter(matrix)
                }
                FilterType.SEPIA -> {
                    val matrix = ColorMatrix().apply {
                        setSaturation(0f)
                        val sepiaMatrix = ColorMatrix(floatArrayOf(
                            0.393f, 0.769f, 0.189f, 0f, 0f,
                            0.349f, 0.686f, 0.168f, 0f, 0f,
                            0.272f, 0.534f, 0.131f, 0f, 0f,
                            0f, 0f, 0f, 1f, 0f
                        ))
                        postConcat(sepiaMatrix)
                    }
                    imageView.colorFilter = ColorMatrixColorFilter(matrix)
                }
                FilterType.BRIGHTNESS -> {
                    val matrix = ColorMatrix().apply {
                        set(floatArrayOf(
                            1.2f, 0f, 0f, 0f, 50f,
                            0f, 1.2f, 0f, 0f, 50f,
                            0f, 0f, 1.2f, 0f, 50f,
                            0f, 0f, 0f, 1f, 0f
                        ))
                    }
                    imageView.colorFilter = ColorMatrixColorFilter(matrix)
                }
                FilterType.CONTRAST -> {
                    val matrix = ColorMatrix().apply {
                        set(floatArrayOf(
                            1.5f, 0f, 0f, 0f, -64f,
                            0f, 1.5f, 0f, 0f, -64f,
                            0f, 0f, 1.5f, 0f, -64f,
                            0f, 0f, 0f, 1f, 0f
                        ))
                    }
                    imageView.colorFilter = ColorMatrixColorFilter(matrix)
                }
                FilterType.VINTAGE -> {
                    val matrix = ColorMatrix().apply {
                        set(floatArrayOf(
                            0.9f, 0.5f, 0.1f, 0f, 0f,
                            0.3f, 0.8f, 0.1f, 0f, 0f,
                            0.2f, 0.3f, 0.5f, 0f, 0f,
                            0f, 0f, 0f, 1f, 0f
                        ))
                    }
                    imageView.colorFilter = ColorMatrixColorFilter(matrix)
                }
                FilterType.BLUR -> {
                    val matrix = ColorMatrix().apply {
                        set(floatArrayOf(
                            0.8f, 0f, 0f, 0f, 0f,
                            0f, 0.8f, 0f, 0f, 0f,
                            0f, 0f, 0.8f, 0f, 0f,
                            0f, 0f, 0f, 1f, 0f
                        ))
                    }
                    imageView.colorFilter = ColorMatrixColorFilter(matrix)
                }
            }
        }
    }

    // Image-specific methods
    fun setImageResource(resourceId: Int) {
        currentImageResource = resourceId
        currentImagePath = null
        currentBitmap = null
        currentImageView?.setImageResource(resourceId)
        updateImageView()
    }

    fun setImagePath(path: String) {
        currentImagePath = path
        currentImageResource = null
    }

    fun setImageRotation(rotation: Float) {
        imageRotation = rotation % 360f
        updateImageView()
    }

    fun setImageAlpha(alpha: Float) {
        imageAlpha = alpha.coerceIn(0f, 1f)
        updateImageView()
    }

    fun setImageScaleType(scaleType: ImageView.ScaleType) {
        imageScaleType = scaleType
        updateImageView()
    }

    fun setImageFilter(filter: FilterType) {
        applyFilter = filter
        updateImageView()
    }

    fun setImageTint(color: Int?) {
        imageTint = color
        updateImageView()
    }

    fun getCurrentImageResource(): Int? = currentImageResource
    fun getCurrentImagePath(): String? = currentImagePath
    fun getCurrentBitmap(): Bitmap? = currentBitmap
    fun getImageRotation(): Float = imageRotation
    fun getImageAlpha(): Float = imageAlpha
    fun getImageScaleType(): ImageView.ScaleType = imageScaleType
    fun getImageFilter(): FilterType = applyFilter
    fun getImageTint(): Int? = imageTint

    override fun getDefaultWidth(): Int = dpToPx(320)
    override fun getDefaultHeight(): Int = dpToPx(320)

    override fun getCustomSaveData(): Map<String, Any> {
        val baseData = super.getCustomSaveData().toMutableMap()
        baseData.putAll(mapOf(
            "imageResource" to (currentImageResource ?: -1),
            "imagePath" to (currentImagePath ?: ""),
            "imageRotation" to imageRotation,
            "imageAlpha" to imageAlpha,
            "imageScaleType" to imageScaleType.name,
            "imageFilter" to applyFilter.name,
            "imageTint" to (imageTint ?: -1),
            "imageAspectRatio" to imageAspectRatio
        ))
        return baseData
    }

    override fun loadCustomSaveData(data: Map<String, Any>) {
        super.loadCustomSaveData(data)

        data["imageResource"]?.let {
            if (it is Int && it != -1) currentImageResource = it
        }
        data["imagePath"]?.let {
            if (it is String && it.isNotEmpty()) currentImagePath = it
        }
        data["imageRotation"]?.let {
            if (it is Float) imageRotation = it
            else if (it is Double) imageRotation = it.toFloat()
        }
        data["imageAlpha"]?.let {
            if (it is Float) imageAlpha = it
            else if (it is Double) imageAlpha = it.toFloat()
        }
        data["imageScaleType"]?.let {
            if (it is String) {
                try {
                    imageScaleType = ImageView.ScaleType.valueOf(it)
                } catch (e: IllegalArgumentException) {
                    imageScaleType = ImageView.ScaleType.CENTER_CROP
                }
            }
        }
        data["imageFilter"]?.let {
            if (it is String) {
                try {
                    applyFilter = FilterType.valueOf(it)
                } catch (e: IllegalArgumentException) {
                    applyFilter = FilterType.NONE
                }
            }
        }
        data["imageTint"]?.let {
            if (it is Int && it != -1) imageTint = it
        }
        data["imageAspectRatio"]?.let {
            if (it is Float) imageAspectRatio = it
            else if (it is Double) imageAspectRatio = it.toFloat()
        }

        if (currentImageView != null) {
            updateImageView()
        }
    }

    // Override the parent's setContainerSize to maintain aspect ratio
    override fun setContainerSize(width: Int, height: Int, animate: Boolean) {
        // If we have an image with an aspect ratio, maintain it during resize
        if (currentBitmap != null && imageAspectRatio > 0) {
            // Calculate the new dimensions maintaining aspect ratio
            val newWidth: Int
            val newHeight: Int

            if (imageAspectRatio >= 1f) {
                // Landscape or square - use width as reference
                newWidth = width
                newHeight = (width / imageAspectRatio).toInt()
            } else {
                // Portrait - use height as reference
                newHeight = height
                newWidth = (height * imageAspectRatio).toInt()
            }

            android.util.Log.d("ContainerImage", "Resizing with aspect ratio: $imageAspectRatio, new size: ${newWidth}x${newHeight}")
            super.setContainerSize(newWidth, newHeight, animate)
        } else {
            // No image loaded, use default behavior
            super.setContainerSize(width, height, animate)
        }
    }
}