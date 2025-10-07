package com.infusory.tutarapp.managers

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.view.SurfaceView
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.infusory.tutarapp.ui.components.containers.ContainerImage
import com.infusory.tutarapp.ui.components.containers.ContainerPdf
import java.io.InputStream

class ImagePickerHandler(
    private val activity: AppCompatActivity,
    private val mainLayout: RelativeLayout,
    private val surfaceView: SurfaceView
) {
    companion object {
        const val BACKGROUND_PICK_CODE = 101
        const val CONTAINER_IMAGE_PICK_CODE = 102
        const val CONTAINER_PDF_PICK_CODE = 103
        const val PERMISSION_REQUEST_CODE = 104

        // Maximum dimensions for the container (to prevent extremely large containers)
        private const val MAX_CONTAINER_WIDTH = 800
        private const val MAX_CONTAINER_HEIGHT = 800
    }

    private var pendingAction: (() -> Unit)? = null

    fun pickBackgroundImage() {
        if (checkAndRequestPermissions()) {
            launchImagePicker(BACKGROUND_PICK_CODE)
        } else {
            pendingAction = { launchImagePicker(BACKGROUND_PICK_CODE) }
        }
    }

    fun pickContainerImage() {
        if (checkAndRequestPermissions()) {
            launchImagePicker(CONTAINER_IMAGE_PICK_CODE)
        } else {
            pendingAction = { launchImagePicker(CONTAINER_IMAGE_PICK_CODE) }
        }
    }

    fun pickContainerPdf() {
        if (checkAndRequestPermissions()) {
            launchPdfPicker()
        } else {
            pendingAction = { launchPdfPicker() }
        }
    }

    private fun launchImagePicker(requestCode: Int) {
        val intent = Intent(Intent.ACTION_PICK).apply {
            type = "image/*"
        }
        activity.startActivityForResult(intent, requestCode)
    }

    private fun launchPdfPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        activity.startActivityForResult(intent, CONTAINER_PDF_PICK_CODE)
    }

    private fun checkAndRequestPermissions(): Boolean {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+
            if (ContextCompat.checkSelfPermission(
                    activity,
                    Manifest.permission.READ_MEDIA_IMAGES
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else {
            // Android 12 and below
            if (ContextCompat.checkSelfPermission(
                    activity,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        return if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                activity,
                permissions.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
            false
        } else {
            true
        }
    }

    fun onPermissionResult(requestCode: Int, grantResults: IntArray) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, execute pending action
                pendingAction?.invoke()
                pendingAction = null
            } else {
                Toast.makeText(
                    activity,
                    "Permission denied. Cannot access images.",
                    Toast.LENGTH_LONG
                ).show()
                pendingAction = null
            }
        }
    }

    fun handleImagePickResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK || data?.data == null) return

        val uri = data.data!!

        when (requestCode) {
            BACKGROUND_PICK_CODE -> setBackgroundImage(uri)
            CONTAINER_IMAGE_PICK_CODE -> setContainerImage(uri)
            CONTAINER_PDF_PICK_CODE -> {
                // Take persistent permission for PDF
                try {
                    activity.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    // Some URIs don't support persistable permissions, that's okay
                }
                setContainerPdf(uri)
            }
        }
    }

    private fun setBackgroundImage(imageUri: Uri) {
        try {
            val bitmap = loadBitmapFromUri(imageUri)
            val drawable = BitmapDrawable(activity.resources, bitmap)
            surfaceView.background = drawable
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(
                activity,
                "Failed to set background: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun setContainerImage(imageUri: Uri) {
        try {
            val bitmap = loadBitmapFromUri(imageUri)

            // Calculate appropriate container dimensions based on image size
            val (containerWidth, containerHeight) = calculateContainerDimensions(
                bitmap.width,
                bitmap.height
            )

            // Create a new ContainerImage with the selected image
            val imageContainer = ContainerImage(activity).apply {
                tag = "lesson_image_${System.currentTimeMillis()}" // Unique tag for each image
                layoutParams = RelativeLayout.LayoutParams(
                    containerWidth,
                    containerHeight
                )

                // Set up the removal callback
                onRemoveRequest = {
                    // Remove this container from the parent layout
                    mainLayout.removeView(this)
                    Toast.makeText(
                        activity,
                        "Image container removed",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            // Add container to layout
            mainLayout.addView(imageContainer)
            imageContainer.initializeContent()

            // Set the selected image
            imageContainer.setImage(bitmap, imageUri.toString())

            Toast.makeText(
                activity,
                "Image loaded: ${bitmap.width}x${bitmap.height}",
                Toast.LENGTH_SHORT
            ).show()

        } catch (e: Exception) {
            e.printStackTrace() // Log the full stack trace for debugging
            Toast.makeText(
                activity,
                "Failed to load image into container: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun setContainerPdf(pdfUri: Uri) {
        try {
            // Create a new ContainerPdf with the selected PDF
            val pdfContainer = ContainerPdf(activity).apply {
                tag = "lesson_pdf_${System.currentTimeMillis()}" // Unique tag for each PDF
                layoutParams = RelativeLayout.LayoutParams(
                    getDefaultWidth(),
                    getDefaultHeight()
                )

                // Set up the removal callback
                onRemoveRequest = {
                    // Remove this container from the parent layout
                    mainLayout.removeView(this)
                    Toast.makeText(
                        activity,
                        "PDF container removed",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            // Add container to layout
            mainLayout.addView(pdfContainer)
            pdfContainer.initializeContent()

            // Set the selected PDF
            pdfContainer.setPdfFromUri(pdfUri)

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(
                activity,
                "Failed to load PDF into container: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * Load bitmap from URI using the appropriate method based on Android version
     * This handles scoped storage properly
     */
    private fun loadBitmapFromUri(uri: Uri): Bitmap {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Android 9 (API 28) and above
            val source = ImageDecoder.createSource(activity.contentResolver, uri)
            ImageDecoder.decodeBitmap(source)
        } else {
            // Below Android 9
            val inputStream: InputStream? = activity.contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream).also {
                inputStream?.close()
            }
        }
    }

    /**
     * Calculate container dimensions that fit the image while respecting maximum size constraints
     * and maintaining aspect ratio
     */
    private fun calculateContainerDimensions(imageWidth: Int, imageHeight: Int): Pair<Int, Int> {
        // Convert max dimensions from dp to pixels
        val density = activity.resources.displayMetrics.density
        val maxWidth = (MAX_CONTAINER_WIDTH * density).toInt()
        val maxHeight = (MAX_CONTAINER_HEIGHT * density).toInt()

        var finalWidth = imageWidth
        var finalHeight = imageHeight

        // Scale down if image is too large, maintaining aspect ratio
        if (imageWidth > maxWidth || imageHeight > maxHeight) {
            val widthRatio = maxWidth.toFloat() / imageWidth
            val heightRatio = maxHeight.toFloat() / imageHeight
            val scaleFactor = minOf(widthRatio, heightRatio)

            finalWidth = (imageWidth * scaleFactor).toInt()
            finalHeight = (imageHeight * scaleFactor).toInt()
        }

        // Ensure minimum size for usability
        val minSize = (100 * density).toInt()
        finalWidth = maxOf(finalWidth, minSize)
        finalHeight = maxOf(finalHeight, minSize)

        return Pair(finalWidth, finalHeight)
    }
}