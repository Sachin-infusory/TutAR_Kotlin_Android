// ContainerFactory.kt
package com.infusory.tutarapp.ui.containers

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.widget.ImageView
import android.widget.TextView

object ContainerFactory {

    /**
     * Create a container with an image
     */
    fun createImageContainer(
        context: Context,
        bitmap: Bitmap,
        onClose: () -> Unit
    ): UnifiedContainer {
        val container = UnifiedContainer(context)

        // Create ImageView
        val imageView = ImageView(context).apply {
            setImageBitmap(bitmap)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }

        // Calculate optimal size based on image
        val (width, height) = calculateOptimalImageSize(context, bitmap)
        container.setContainerSize(width, height)
        container.setContent(imageView)
        container.onCloseClicked = onClose

        return container
    }

    fun createImageContainer(
        context: Context,
        bitmap: Bitmap,
        path: String,
        onClose: () -> Unit
    ): UnifiedContainer {
        val container = UnifiedContainer(context)
        val behavior = ImageContentBehavior(context)

        // Attach behavior to container
        behavior.onAttached(container)

        // Set the image (this will also set optimal container size)
        behavior.setImage(bitmap, path)

        // Set close callback
        container.onCloseClicked = {
            behavior.onDetached()
            onClose()
        }

        return container
    }

    /**
     * Create a container with a PDF viewer
     */
    fun createPdfContainer(
        context: Context,
        uri: Uri,
        onClose: () -> Unit
    ): UnifiedContainer {
        val container = UnifiedContainer(context)

        // Create PDF view (you'll need to implement PdfView)
        val pdfView = createPdfView(context, uri)

        container.setContainerSize(dpToPx(context, 400), dpToPx(context, 500))
        container.setContent(pdfView)
        container.onCloseClicked = onClose

        return container
    }

    /**
     * Create a container with text
     */
    fun createTextContainer(
        context: Context,
        text: String,
        onClose: () -> Unit
    ): UnifiedContainer {
        val container = UnifiedContainer(context)

        val textView = TextView(context).apply {
            this.text = text
            textSize = 16f
            setPadding(16, 16, 16, 16)
        }

        container.setContainerSize(dpToPx(context, 300), dpToPx(context, 200))
        container.setContent(textView)
        container.onCloseClicked = onClose

        return container
    }

    /**
     * Create a container with custom view
     */
    fun createCustomContainer(
        context: Context,
        content: View,
        width: Int,
        height: Int,
        onClose: () -> Unit
    ): UnifiedContainer {
        val container = UnifiedContainer(context)
        container.setContainerSize(width, height)
        container.setContent(content)
        container.onCloseClicked = onClose
        return container
    }

    // Helper functions
    private fun calculateOptimalImageSize(context: Context, bitmap: Bitmap): Pair<Int, Int> {
        val maxSize = dpToPx(context, 600)
        val minSize = dpToPx(context, 150)

        var width = bitmap.width
        var height = bitmap.height

        if (width > maxSize || height > maxSize) {
            val scale = minOf(
                maxSize.toFloat() / width,
                maxSize.toFloat() / height
            )
            width = (width * scale).toInt()
            height = (height * scale).toInt()
        }

        return Pair(
            width.coerceIn(minSize, maxSize),
            height.coerceIn(minSize, maxSize)
        )
    }

    private fun createPdfView(context: Context, uri: Uri): View {
        // Placeholder - implement your PDF viewer here
        return TextView(context).apply {
            text = "PDF: ${uri.lastPathSegment}"
            textSize = 14f
        }
    }

    private fun dpToPx(context: Context, dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}