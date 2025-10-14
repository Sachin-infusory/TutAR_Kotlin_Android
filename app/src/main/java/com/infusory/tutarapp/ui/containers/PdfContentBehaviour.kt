// PdfContentBehavior.kt
package com.infusory.tutarapp.ui.containers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import java.io.File
import java.io.FileOutputStream

/**
 * Content behavior for displaying PDFs in UnifiedContainer
 * Handles PDF rendering, navigation, and page display
 */
class PdfContentBehavior(
    private val context: Context
) {

    private var container: UnifiedContainer? = null
    private var pdfImageView: ImageView? = null
    private var pageIndicatorText: TextView? = null
    private var prevButton: ImageView? = null
    private var nextButton: ImageView? = null

    // PDF properties
    private var currentPdfUri: Uri? = null
    private var pdfRenderer: PdfRenderer? = null
    private var currentPage: PdfRenderer.Page? = null
    private var currentPageIndex = 0
    private var totalPages = 0
    private var pdfFile: File? = null
    private var fileDescriptor: ParcelFileDescriptor? = null

    // Rendering settings
    private var renderScale = 2f // Higher quality rendering

    /**
     * Attach this behavior to a container
     */
    fun onAttached(container: UnifiedContainer) {
        this.container = container
        createPdfViews()
        createNavigationButtons()
    }

    /**
     * Detach and cleanup resources
     */
    fun onDetached() {
        closePdfRenderer()
        container = null
        pdfImageView = null
        pageIndicatorText = null
        prevButton = null
        nextButton = null
    }

    /**
     * Create the views for displaying PDF
     */
    private fun createPdfViews() {
        val contentLayout = FrameLayout(context)

        // Create ImageView for displaying PDF pages
        pdfImageView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.WHITE)

            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )

            // Long click for info
            setOnLongClickListener {
                showPdfInfo()
                true
            }
        }
        contentLayout.addView(pdfImageView)

        // Create page indicator
        pageIndicatorText = TextView(context).apply {
            textSize = 14f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#80000000"))
            setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4))
            gravity = Gravity.CENTER
            text = "No PDF loaded"

            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = dpToPx(8)
            }
        }
        contentLayout.addView(pageIndicatorText)

        container?.setContent(contentLayout)
    }

    /**
     * Create navigation buttons for PDF pages
     */
    private fun createNavigationButtons() {
        val buttonSize = dpToPx(32)

        // Previous page button
        prevButton = ImageView(context).apply {
            setImageResource(android.R.drawable.ic_media_previous)
            setBackgroundResource(android.R.drawable.btn_default)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            alpha = 0.5f // Start disabled
            setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
            elevation = dpToPx(6).toFloat()

            layoutParams = FrameLayout.LayoutParams(buttonSize, buttonSize).apply {
                gravity = Gravity.BOTTOM or Gravity.START
                setMargins(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            }

            setOnClickListener { previousPage() }
        }

        // Next page button
        nextButton = ImageView(context).apply {
            setImageResource(android.R.drawable.ic_media_next)
            setBackgroundResource(android.R.drawable.btn_default)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            alpha = 0.5f // Start disabled
            setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
            elevation = dpToPx(6).toFloat()

            layoutParams = FrameLayout.LayoutParams(buttonSize, buttonSize).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                setMargins(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            }

            setOnClickListener { nextPage() }
        }

        // Add buttons to container
        container?.addView(prevButton)
        container?.addView(nextButton)
    }

    /**
     * Load PDF from URI
     */
    fun loadPdf(uri: Uri) {
        try {
            closePdfRenderer()

            currentPdfUri = uri

            // Copy URI content to a temporary file (required for PdfRenderer)
            pdfFile = File(context.cacheDir, "temp_pdf_${System.currentTimeMillis()}.pdf")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(pdfFile).use { output ->
                    input.copyTo(output)
                }
            }

            // Open PDF with PdfRenderer
            fileDescriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            pdfRenderer = PdfRenderer(fileDescriptor!!)
            totalPages = pdfRenderer?.pageCount ?: 0
            currentPageIndex = 0

            // Display first page
            renderPage(currentPageIndex)
            updatePageIndicator()
            updateNavigationButtons()

            android.util.Log.d("PdfContentBehavior", "PDF loaded: $totalPages pages")

            android.widget.Toast.makeText(
                context,
                "PDF loaded: $totalPages pages",
                android.widget.Toast.LENGTH_SHORT
            ).show()

        } catch (e: Exception) {
            android.util.Log.e("PdfContentBehavior", "Error loading PDF", e)
            android.widget.Toast.makeText(
                context,
                "Failed to load PDF: ${e.message}",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * Render a specific page of the PDF
     */
    private fun renderPage(pageIndex: Int) {
        if (pdfRenderer == null || pageIndex < 0 || pageIndex >= totalPages) return

        try {
            // Close previous page if open
            currentPage?.close()

            // Open the page
            currentPage = pdfRenderer?.openPage(pageIndex)
            currentPage?.let { page ->
                // Create bitmap for the page with higher resolution
                val width = (page.width * renderScale).toInt()
                val height = (page.height * renderScale).toInt()

                val bitmap = Bitmap.createBitmap(
                    width,
                    height,
                    Bitmap.Config.ARGB_8888
                )

                // Fill with white background
                bitmap.eraseColor(Color.WHITE)

                // Render page to bitmap
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                // Display bitmap
                pdfImageView?.setImageBitmap(bitmap)

                android.util.Log.d("PdfContentBehavior", "Rendered page $pageIndex: ${width}x${height}")
            }

        } catch (e: Exception) {
            android.util.Log.e("PdfContentBehavior", "Error rendering page", e)
            android.widget.Toast.makeText(
                context,
                "Error rendering page: ${e.message}",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * Go to next page
     */
    private fun nextPage() {
        if (currentPageIndex < totalPages - 1) {
            currentPageIndex++
            renderPage(currentPageIndex)
            updatePageIndicator()
            updateNavigationButtons()
        } else {
            android.widget.Toast.makeText(
                context,
                "Already at last page",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * Go to previous page
     */
    private fun previousPage() {
        if (currentPageIndex > 0) {
            currentPageIndex--
            renderPage(currentPageIndex)
            updatePageIndicator()
            updateNavigationButtons()
        } else {
            android.widget.Toast.makeText(
                context,
                "Already at first page",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * Go to specific page
     */
    fun goToPage(pageIndex: Int) {
        if (pageIndex in 0 until totalPages) {
            currentPageIndex = pageIndex
            renderPage(currentPageIndex)
            updatePageIndicator()
            updateNavigationButtons()
        }
    }

    /**
     * Update page indicator text
     */
    private fun updatePageIndicator() {
        pageIndicatorText?.text = if (totalPages > 0) {
            "Page ${currentPageIndex + 1} of $totalPages"
        } else {
            "No PDF loaded"
        }
    }

    /**
     * Update navigation button states
     */
    private fun updateNavigationButtons() {
        // Enable/disable previous button
        prevButton?.apply {
            alpha = if (currentPageIndex > 0) 1.0f else 0.5f
            isEnabled = currentPageIndex > 0
        }

        // Enable/disable next button
        nextButton?.apply {
            alpha = if (currentPageIndex < totalPages - 1) 1.0f else 0.5f
            isEnabled = currentPageIndex < totalPages - 1
        }
    }

    /**
     * Show PDF information dialog
     */
    private fun showPdfInfo() {
        val info = buildString {
            append("PDF Information:\n\n")
            append("Total Pages: $totalPages\n")
            append("Current Page: ${currentPageIndex + 1}\n")
            currentPage?.let {
                append("Page Size: ${it.width} x ${it.height}\n")
            }
            container?.let {
                val (width, height) = it.getCurrentSize()
                append("Container Size: $width x $height\n")
            }
            currentPdfUri?.let {
                append("\nFile: ${it.lastPathSegment ?: "Unknown"}")
            }
        }

        AlertDialog.Builder(context)
            .setTitle("PDF Information")
            .setMessage(info)
            .setPositiveButton("OK", null)
            .setNeutralButton("Go to Page...") { _, _ ->
                showGoToPageDialog()
            }
            .show()
    }

    /**
     * Show dialog to jump to specific page
     */
    private fun showGoToPageDialog() {
        val input = android.widget.EditText(context).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = "Enter page number (1-$totalPages)"
            setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
        }

        AlertDialog.Builder(context)
            .setTitle("Go to Page")
            .setView(input)
            .setPositiveButton("Go") { _, _ ->
                try {
                    val pageNum = input.text.toString().toInt()
                    if (pageNum in 1..totalPages) {
                        goToPage(pageNum - 1) // Convert to 0-based index
                    } else {
                        android.widget.Toast.makeText(
                            context,
                            "Invalid page number. Enter 1-$totalPages",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: NumberFormatException) {
                    android.widget.Toast.makeText(
                        context,
                        "Please enter a valid number",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Close PDF renderer and cleanup resources
     */
    private fun closePdfRenderer() {
        try {
            currentPage?.close()
            pdfRenderer?.close()
            fileDescriptor?.close()
            pdfFile?.delete()
        } catch (e: Exception) {
            android.util.Log.e("PdfContentBehavior", "Error closing PDF renderer", e)
        } finally {
            currentPage = null
            pdfRenderer = null
            fileDescriptor = null
            pdfFile = null
        }
    }

    /**
     * Save state for persistence
     */
    fun saveState(): Map<String, Any> {
        return mapOf(
            "pdfUri" to (currentPdfUri?.toString() ?: ""),
            "currentPage" to currentPageIndex,
            "totalPages" to totalPages,
            "renderScale" to renderScale
        )
    }

    /**
     * Restore state from saved data
     */
    fun restoreState(data: Map<String, Any>) {
        data["pdfUri"]?.let { uriString ->
            if (uriString is String && uriString.isNotEmpty()) {
                try {
                    val uri = Uri.parse(uriString)
                    loadPdf(uri)

                    // Restore page after PDF is loaded
                    data["currentPage"]?.let { page ->
                        if (page is Int && page >= 0 && page < totalPages) {
                            goToPage(page)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("PdfContentBehavior", "Error restoring PDF", e)
                }
            }
        }

        data["renderScale"]?.let {
            renderScale = when (it) {
                is Float -> it
                is Double -> it.toFloat()
                else -> 2f
            }
        }
    }

    // Getters
    fun getCurrentPdfUri(): Uri? = currentPdfUri
    fun getCurrentPageIndex(): Int = currentPageIndex
    fun getTotalPages(): Int = totalPages
    fun isLoaded(): Boolean = pdfRenderer != null

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}