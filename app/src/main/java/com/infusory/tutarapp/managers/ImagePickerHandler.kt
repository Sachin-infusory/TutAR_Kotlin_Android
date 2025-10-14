// ImagePickerHandler.kt
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
import com.infusory.tutarapp.ui.containers.UnifiedContainer
import com.infusory.tutarapp.ui.containers.ImageContentBehavior
import com.infusory.tutarapp.ui.containers.PdfContentBehavior
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

    // Store references to containers and their behaviors for cleanup
    private val containerBehaviors = mutableMapOf<UnifiedContainer, Any>()

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

            Toast.makeText(
                activity,
                "Background image set",
                Toast.LENGTH_SHORT
            ).show()
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
            val path = imageUri.toString()

            // Create UnifiedContainer
            val container = UnifiedContainer(activity).apply {
                tag = "lesson_image_${System.currentTimeMillis()}"
            }

            // Create ImageContentBehavior
            val imageBehavior = ImageContentBehavior(activity)

            // Attach behavior to container
            imageBehavior.onAttached(container)

            // Set the image (this will automatically size the container)
            imageBehavior.setImage(bitmap, path)

            // Store behavior reference for cleanup
            containerBehaviors[container] = imageBehavior

            // Set close callback
            container.onCloseClicked = {
                removeContainer(container, imageBehavior)
            }

            // IMPORTANT: Get the size AFTER setting the image
            val (width, height) = container.getCurrentSize()

            // Set proper layout params for RelativeLayout
            val layoutParams = RelativeLayout.LayoutParams(width, height)
            container.layoutParams = layoutParams

            // Add container to layout
            mainLayout.addView(container)

            // Position container AFTER adding to layout
            container.post {
                container.moveTo(100f, 100f)
            }

            Toast.makeText(
                activity,
                "Image loaded: ${bitmap.width}x${bitmap.height}",
                Toast.LENGTH_SHORT
            ).show()

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(
                activity,
                "Failed to load image into container: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun setContainerPdf(pdfUri: Uri) {
        try {
            // Create UnifiedContainer
            val container = UnifiedContainer(activity).apply {
                tag = "lesson_pdf_${System.currentTimeMillis()}"
            }

            // Create PdfContentBehavior (you'll need to create this similar to ImageContentBehavior)
            val pdfBehavior = PdfContentBehavior(activity)

            // Attach behavior to container
            pdfBehavior.onAttached(container)

            // Load the PDF
            pdfBehavior.loadPdf(pdfUri)

            // Store behavior reference for cleanup
            containerBehaviors[container] = pdfBehavior

            // Set close callback
            container.onCloseClicked = {
                removeContainer(container, pdfBehavior)
            }

            // Set default PDF container size
            val density = activity.resources.displayMetrics.density
            val defaultWidth = (400 * density).toInt()
            val defaultHeight = (500 * density).toInt()
            container.setContainerSize(defaultWidth, defaultHeight)

            // Add container to layout
            val layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            )
            container.layoutParams = layoutParams
            mainLayout.addView(container)

            // Position container
            container.moveTo(150f, 150f)

            Toast.makeText(
                activity,
                "PDF loaded successfully",
                Toast.LENGTH_SHORT
            ).show()

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
     * Remove a container and cleanup its behavior
     */
    private fun removeContainer(container: UnifiedContainer, behavior: Any) {
        // Detach behavior to cleanup resources
        when (behavior) {
            is ImageContentBehavior -> behavior.onDetached()
            is PdfContentBehavior -> behavior.onDetached()
        }

        // Remove from tracking
        containerBehaviors.remove(container)

        // Remove from layout
        mainLayout.removeView(container)

        Toast.makeText(
            activity,
            "Container removed",
            Toast.LENGTH_SHORT
        ).show()
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
     * Cleanup all containers when activity is destroyed
     */
    fun cleanup() {
        containerBehaviors.forEach { (container, behavior) ->
            when (behavior) {
                is ImageContentBehavior -> behavior.onDetached()
                is PdfContentBehavior -> behavior.onDetached()
            }
            mainLayout.removeView(container)
        }
        containerBehaviors.clear()
    }

    /**
     * Save state of all containers
     */
    fun saveContainersState(): List<ContainerStateData> {
        val states = mutableListOf<ContainerStateData>()

        containerBehaviors.forEach { (container, behavior) ->
            val containerState = container.saveState()
            val behaviorState = when (behavior) {
                is ImageContentBehavior -> behavior.saveState()
                is PdfContentBehavior -> behavior.saveState()
                else -> emptyMap()
            }

            states.add(
                ContainerStateData(
                    type = when (behavior) {
                        is ImageContentBehavior -> "image"
                        is PdfContentBehavior -> "pdf"
                        else -> "unknown"
                    },
                    containerState = containerState,
                    behaviorState = behaviorState
                )
            )
        }

        return states
    }

    /**
     * Restore containers from saved state
     */
    fun restoreContainersState(states: List<ContainerStateData>) {
        states.forEach { stateData ->
            try {
                when (stateData.type) {
                    "image" -> restoreImageContainer(stateData)
//                    "pdf" -> restorePdfContainer(stateData)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                android.util.Log.e("ImagePickerHandler", "Failed to restore container", e)
            }
        }
    }

    private fun restoreImageContainer(stateData: ContainerStateData) {
        val container = UnifiedContainer(activity)
        val imageBehavior = ImageContentBehavior(activity)

        imageBehavior.onAttached(container)
        container.restoreState(stateData.containerState)
        imageBehavior.restoreState(stateData.behaviorState)

        containerBehaviors[container] = imageBehavior

        container.onCloseClicked = {
            removeContainer(container, imageBehavior)
        }

        val layoutParams = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.WRAP_CONTENT,
            RelativeLayout.LayoutParams.WRAP_CONTENT
        )
        container.layoutParams = layoutParams
        mainLayout.addView(container)
    }

//    private fun restorePdfContainer(stateData: ContainerStateData) {
//        val container = UnifiedContainer(activity)
////        val pdfBehavior = PdfContentBehavior(activity)
//
//        pdfBehavior.onAttached(container)
//        container.restoreState(stateData.containerState)
//        pdfBehavior.restoreState(stateData.behaviorState)
//
//        containerBehaviors[container] = pdfBehavior
//
//        container.onCloseClicked = {
//            removeContainer(container, pdfBehavior)
//        }
//
//        val layoutParams = RelativeLayout.LayoutParams(
//            RelativeLayout.LayoutParams.WRAP_CONTENT,
//            RelativeLayout.LayoutParams.WRAP_CONTENT
//        )
//        container.layoutParams = layoutParams
//        mainLayout.addView(container)
//    }

    /**
     * Data class for saving container state
     */
    data class ContainerStateData(
        val type: String,
        val containerState: UnifiedContainer.ContainerState,
        val behaviorState: Map<String, Any>
    )
}