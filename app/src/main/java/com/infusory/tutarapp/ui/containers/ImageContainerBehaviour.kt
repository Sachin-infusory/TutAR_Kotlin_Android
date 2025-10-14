// ImageContentBehavior.kt
package com.infusory.tutarapp.ui.containers

import android.content.Context
import android.graphics.*
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import com.infusory.tutarapp.R

/**
 * Content behavior for displaying images in UnifiedContainer
 * Handles image display, rotation, filters, and effects
 */
class ImageContentBehavior(
    private val context: Context
) {

    private var container: UnifiedContainer? = null
    private var imageView: ImageView? = null
    private var rotateButton: ImageView? = null

    // Image properties
    private var currentBitmap: Bitmap? = null
    private var currentImagePath: String? = null
    private var currentImageResource: Int? = null
    private var imageRotation = 0f
    private var imageAlpha = 1.0f
    private var imageScaleType = ImageView.ScaleType.CENTER_CROP
    private var applyFilter = FilterType.NONE
    private var imageTint: Int? = null
    private var imageAspectRatio: Float = 1f

    enum class FilterType {
        NONE, GRAYSCALE, SEPIA, BLUR, BRIGHTNESS, CONTRAST, VINTAGE
    }

    /**
     * Attach this behavior to a container
     */
    fun onAttached(container: UnifiedContainer) {
        this.container = container
        createImageView()
        createRotateButton()
    }

    /**
     * Detach and cleanup resources
     */
    fun onDetached() {
        currentBitmap?.recycle()
        currentBitmap = null
        container = null
        imageView = null
        rotateButton = null
    }

    /**
     * Create the ImageView that displays the image
     */
    private fun createImageView() {
        imageView = ImageView(context).apply {
            scaleType = imageScaleType
            adjustViewBounds = false
            setPadding(0, 0, 0, 0)

            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )

            // Long click for options menu
            setOnLongClickListener {
                showImageMenu()
                true
            }
        }

        container?.setContent(imageView!!)
    }

    /**
     * Create rotate button
     */
    private fun createRotateButton() {
        rotateButton = ImageView(context).apply {
            val buttonSize = dpToPx(28)
            layoutParams = FrameLayout.LayoutParams(buttonSize, buttonSize).apply {
                gravity = Gravity.TOP or Gravity.END
                rightMargin = dpToPx(4)
                topMargin = dpToPx(4)
            }

            setImageResource(android.R.drawable.ic_menu_rotate)

            // Circular background
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(Color.parseColor("#2196F3")) // Blue
                setStroke(dpToPx(2), Color.WHITE)
            }

            scaleType = ImageView.ScaleType.CENTER
            elevation = dpToPx(8).toFloat()

            setOnClickListener {
                rotateImage()
            }
        }

        // Add button to container (it will be on top of content)
        container?.addView(rotateButton)
    }

    /**
     * Set image from bitmap
     */
    fun setImage(bitmap: Bitmap, path: String) {
        currentBitmap?.recycle() // Recycle old bitmap
        currentBitmap = bitmap
        currentImagePath = path
        currentImageResource = null

        val actualWidth = bitmap.width
        val actualHeight = bitmap.height
        imageAspectRatio = actualWidth.toFloat() / actualHeight.toFloat()

        // Calculate optimal container size for image
        val (width, height) = calculateOptimalSize(actualWidth, actualHeight)
        container?.setContainerSize(width, height)

        // Display image
        imageView?.setImageBitmap(bitmap)
        updateImageView()

        android.util.Log.d("ImageContentBehavior", "Image set: ${width}x${height}, aspect: $imageAspectRatio")
    }

    /**
     * Set image from resource
     */
    fun setImageResource(resourceId: Int) {
        currentImageResource = resourceId
        currentImagePath = null
        currentBitmap = null
        imageView?.setImageResource(resourceId)
        updateImageView()
    }

    /**
     * Calculate optimal size for image
     */
    private fun calculateOptimalSize(imageWidth: Int, imageHeight: Int): Pair<Int, Int> {
        val density = context.resources.displayMetrics.density
        val maxSize = (800 * density).toInt()
        val minSize = (150 * density).toInt()

        var targetWidth = imageWidth
        var targetHeight = imageHeight

        // Scale down if too large
        if (imageWidth > maxSize || imageHeight > maxSize) {
            val scale = minOf(
                maxSize.toFloat() / imageWidth,
                maxSize.toFloat() / imageHeight
            )
            targetWidth = (imageWidth * scale).toInt()
            targetHeight = (imageHeight * scale).toInt()
        }

        // Ensure minimum size
        targetWidth = targetWidth.coerceIn(minSize, maxSize)
        targetHeight = targetHeight.coerceIn(minSize, maxSize)

        return Pair(targetWidth, targetHeight)
    }

    /**
     * Rotate image by 90 degrees
     */
    private fun rotateImage() {
        imageRotation = (imageRotation + 90f) % 360f

        // Swap container dimensions for 90° or 270° rotation
        container?.let { c ->
            val (baseWidth, baseHeight) = c.getBaseSize()
            val is90or270 = imageRotation % 180f == 90f

            if (is90or270) {
                // Swap width and height
                c.setContainerSize(baseHeight, baseWidth)
            } else {
                // Restore original dimensions
                c.setContainerSize(baseWidth, baseHeight)
            }
        }

        updateImageView()
        android.widget.Toast.makeText(
            context,
            "Rotated to ${imageRotation.toInt()}°",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    /**
     * Update image view with current settings
     */
    private fun updateImageView() {
        imageView?.apply {
            rotation = imageRotation
//            alpha = imageAlpha
            scaleType = imageScaleType
            adjustViewBounds = false

            // Clear previous filters
            clearColorFilter()

            // Apply tint if set
            imageTint?.let { setColorFilter(it, PorterDuff.Mode.SRC_ATOP) }

            // Apply filter
            applyCurrentFilter()
        }
    }

    /**
     * Apply color filter based on selected filter type
     */
    private fun applyCurrentFilter() {
        imageView?.let { iv ->
            when (applyFilter) {
                FilterType.NONE -> iv.colorFilter = null
                FilterType.GRAYSCALE -> {
                    val matrix = ColorMatrix().apply { setSaturation(0f) }
                    iv.colorFilter = ColorMatrixColorFilter(matrix)
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
                    iv.colorFilter = ColorMatrixColorFilter(matrix)
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
                    iv.colorFilter = ColorMatrixColorFilter(matrix)
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
                    iv.colorFilter = ColorMatrixColorFilter(matrix)
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
                    iv.colorFilter = ColorMatrixColorFilter(matrix)
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
                    iv.colorFilter = ColorMatrixColorFilter(matrix)
                }
            }
        }
    }

    /**
     * Show image options menu
     */
    private fun showImageMenu() {
        val options = arrayOf(
            "Scale Type",
            "Apply Filter",
            "Edit (Rotation & Opacity)",
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
                    2 -> showImageEditDialog()
                    3 -> showTintDialog()
                    4 -> resetAllEffects()
                    5 -> showImageInfoDialog()
                }
            }
            .show()
    }

    private fun showScaleTypeDialog() {
        val scaleTypes = arrayOf(
            "Fit XY (Fill)", "Center Crop", "Fit Center", "Center Inside",
            "Fit Start", "Fit End", "Center", "Matrix"
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
                android.widget.Toast.makeText(
                    context,
                    "Scale: ${scaleTypes[which]}",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showFilterDialog() {
        val filters = FilterType.values().map {
            it.name.lowercase().replaceFirstChar { char -> char.uppercase() }
        }.toTypedArray()
        val currentIndex = FilterType.values().indexOf(applyFilter)

        AlertDialog.Builder(context)
            .setTitle("Apply Filter")
            .setSingleChoiceItems(filters, currentIndex) { dialog, which ->
                applyFilter = FilterType.values()[which]
                updateImageView()
                android.widget.Toast.makeText(
                    context,
                    "Filter: ${filters[which]}",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
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
                        (editView.getChildAt(0) as android.widget.TextView).text = "Rotation: $progress°"
                    }
                }
                override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
            })
        }
        editView.addView(rotationSeekBar)

        // Opacity control
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
                        (editView.getChildAt(2) as android.widget.TextView).text = "Opacity: $progress%"
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
                android.widget.Toast.makeText(
                    context,
                    "Changes applied",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
            .setNeutralButton("Reset") { _, _ ->
                imageRotation = 0f
                imageAlpha = 1.0f
                updateImageView()
                android.widget.Toast.makeText(
                    context,
                    "Image reset",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
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
                android.widget.Toast.makeText(
                    context,
                    "Tint: ${colors[which]}",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
            .show()
    }

    private fun resetAllEffects() {
        imageRotation = 0f
        imageAlpha = 1.0f
        imageScaleType = ImageView.ScaleType.CENTER_CROP
        applyFilter = FilterType.NONE
        imageTint = null
        updateImageView()
        android.widget.Toast.makeText(
            context,
            "All effects reset",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    private fun showImageInfoDialog() {
        val info = buildString {
            append("Image Information:\n\n")
            append("Source: ${when {
                currentImageResource != null -> "Resource"
                currentImagePath != null -> "File: $currentImagePath"
                else -> "None"
            }}\n")
            currentBitmap?.let {
                append("Bitmap Size: ${it.width} x ${it.height} (W×H)\n")
            }
            container?.let {
                val (width, height) = it.getCurrentSize()
                append("Container Size: $width x $height (W×H)\n")
            }
            append("Rotation: ${imageRotation.toInt()}°\n")
            append("Opacity: ${(imageAlpha * 100).toInt()}%\n")
            append("Scale Type: ${imageScaleType.name}\n")
            append("Filter: ${applyFilter.name}\n")
            append("Tint: ${if (imageTint != null) "Applied" else "None"}\n")
            append("Aspect Ratio: ${"%.2f".format(imageAspectRatio)}")
        }

        AlertDialog.Builder(context)
            .setTitle("Image Information")
            .setMessage(info)
            .setPositiveButton("OK", null)
            .show()
    }

    /**
     * Save state for persistence
     */
    fun saveState(): Map<String, Any> {
        return mapOf(
            "imageResource" to (currentImageResource ?: -1),
            "imagePath" to (currentImagePath ?: ""),
            "imageRotation" to imageRotation,
            "imageAlpha" to imageAlpha,
            "imageScaleType" to imageScaleType.name,
            "imageFilter" to applyFilter.name,
            "imageTint" to (imageTint ?: -1),
            "imageAspectRatio" to imageAspectRatio
        )
    }

    /**
     * Restore state from saved data
     */
    fun restoreState(data: Map<String, Any>) {
        data["imageResource"]?.let {
            if (it is Int && it != -1) currentImageResource = it
        }
        data["imagePath"]?.let {
            if (it is String && it.isNotEmpty()) currentImagePath = it
        }
        data["imageRotation"]?.let {
            imageRotation = when (it) {
                is Float -> it
                is Double -> it.toFloat()
                else -> 0f
            }
        }
        data["imageAlpha"]?.let {
            imageAlpha = when (it) {
                is Float -> it
                is Double -> it.toFloat()
                else -> 1f
            }
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
            imageAspectRatio = when (it) {
                is Float -> it
                is Double -> it.toFloat()
                else -> 1f
            }
        }

        updateImageView()
    }

    // Getters
    fun getCurrentBitmap(): Bitmap? = currentBitmap
    fun getCurrentImagePath(): String? = currentImagePath
    fun getImageRotation(): Float = imageRotation
    fun getImageAlpha(): Float = imageAlpha
    fun getImageAspectRatio(): Float = imageAspectRatio

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}