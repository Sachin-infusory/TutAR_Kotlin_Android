// ContainerPdf.kt
package com.infusory.tutarapp.ui.components.containers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.AttributeSet
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import java.io.File
import java.io.FileOutputStream

class ContainerPdf @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ContainerBase(context, ContainerBase.ContainerType.PDF, attrs, defStyleAttr) {

    private var currentPdfUri: Uri? = null
    private var pdfRenderer: PdfRenderer? = null
    private var currentPage: PdfRenderer.Page? = null
    private var currentPageIndex = 0
    private var totalPages = 0
    private var pdfFile: File? = null
    private var fileDescriptor: ParcelFileDescriptor? = null

    private var pdfImageView: ImageView? = null
    private var pageIndicatorText: TextView? = null

    // Store button references directly
    private lateinit var prevButton: ImageView
    private lateinit var nextButton: ImageView

    init {
        setupPdfContainer()
        setPadding(4, 4, 4, 4)
    }

    private fun setupPdfContainer() {
        val buttons = listOf(
            ControlButton(
                iconRes = android.R.drawable.ic_menu_close_clear_cancel,
                onClick = { onRemoveRequest?.invoke() },
                position = ButtonPosition.TOP_START
            ),
            ControlButton(
                iconRes = android.R.drawable.ic_menu_info_details,
                onClick = { showPdfInfo() },
                position = ButtonPosition.TOP_CENTER
            ),
            ControlButton(
                iconRes = android.R.drawable.ic_menu_rotate,
                onClick = { rotatePdf() },
                position = ButtonPosition.TOP_END
            )
        )

        addControlButtons(buttons)

        // Create and add navigation buttons manually with direct references
        prevButton = ImageView(context).apply {
            setImageResource(android.R.drawable.ic_media_previous)
            setBackgroundResource(android.R.drawable.btn_default)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            alpha = 0.5f // Start disabled
            setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))

            setOnClickListener { previousPage() }

            layoutParams = android.widget.FrameLayout.LayoutParams(
                dpToPx(32),
                dpToPx(32)
            ).apply {
                gravity = android.view.Gravity.BOTTOM or android.view.Gravity.START
                setMargins(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            }
        }

        nextButton = ImageView(context).apply {
            setImageResource(android.R.drawable.ic_media_next)
            setBackgroundResource(android.R.drawable.btn_default)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            alpha = 0.5f // Start disabled
            setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))

            setOnClickListener { nextPage() }

            layoutParams = android.widget.FrameLayout.LayoutParams(
                dpToPx(32),
                dpToPx(32)
            ).apply {
                gravity = android.view.Gravity.BOTTOM or android.view.Gravity.END
                setMargins(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            }
        }

        addView(prevButton)
        addView(nextButton)
    }

    override fun initializeContent() {
        createPdfView()
    }

    private fun createPdfView() {
        // Create ImageView for displaying PDF pages
        pdfImageView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(android.graphics.Color.WHITE)

            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            ).apply {
                setMargins(0, 0, 0, 0)
            }
        }

        // Create page indicator
        pageIndicatorText = TextView(context).apply {
            textSize = 14f
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(android.graphics.Color.parseColor("#80000000"))
            setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4))
            gravity = android.view.Gravity.CENTER
            text = "No PDF loaded"

            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
                bottomMargin = dpToPx(48)
            }
        }

        // Remove existing content (but keep buttons)
        val viewsToRemove = mutableListOf<android.view.View>()
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            // Don't remove navigation buttons or control buttons
            if (child != prevButton && child != nextButton &&
                !(child is ImageView && child.layoutParams.width == dpToPx(24))) {
                viewsToRemove.add(child)
            }
        }
        viewsToRemove.forEach { view -> removeView(view) }

        // Add views (at index 0 to be behind buttons)
        addView(pdfImageView, 0)
        addView(pageIndicatorText, 1)
    }

    fun setPdfFromUri(uri: Uri) {
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

            android.widget.Toast.makeText(
                context,
                "PDF loaded: $totalPages pages",
                android.widget.Toast.LENGTH_SHORT
            ).show()

        } catch (e: Exception) {
            android.util.Log.e("ContainerPdf", "Error loading PDF", e)
            android.widget.Toast.makeText(
                context,
                "Failed to load PDF: ${e.message}",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun renderPage(pageIndex: Int) {
        if (pdfRenderer == null || pageIndex < 0 || pageIndex >= totalPages) return

        try {
            // Close previous page if open
            currentPage?.close()

            // Open the page
            currentPage = pdfRenderer?.openPage(pageIndex)
            currentPage?.let { page ->
                // Create bitmap for the page
                val bitmap = Bitmap.createBitmap(
                    page.width * 2,  // Higher resolution
                    page.height * 2,
                    Bitmap.Config.ARGB_8888
                )

                // Render page to bitmap
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                // Display bitmap
                pdfImageView?.setImageBitmap(bitmap)
            }

        } catch (e: Exception) {
            android.util.Log.e("ContainerPdf", "Error rendering page", e)
        }
    }

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

    private fun updatePageIndicator() {
        pageIndicatorText?.text = "Page ${currentPageIndex + 1} of $totalPages"
    }

    private fun updateNavigationButtons() {
        // Enable/disable buttons based on current page
        prevButton.alpha = if (currentPageIndex > 0) 1.0f else 0.5f
        prevButton.isEnabled = currentPageIndex > 0

        nextButton.alpha = if (currentPageIndex < totalPages - 1) 1.0f else 0.5f
        nextButton.isEnabled = currentPageIndex < totalPages - 1
    }

    private fun rotatePdf() {
        pdfImageView?.rotation = (pdfImageView?.rotation ?: 0f) + 90f
        android.widget.Toast.makeText(
            context,
            "Rotated to ${(pdfImageView?.rotation ?: 0f).toInt()}°",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    private fun showPdfInfo() {
        val info = buildString {
            append("PDF Information:\n\n")
            append("Total Pages: $totalPages\n")
            append("Current Page: ${currentPageIndex + 1}\n")
            currentPage?.let {
                append("Page Size: ${it.width} x ${it.height}\n")
            }
            append("Container Size: ${width} x ${height}\n")
            append("Rotation: ${(pdfImageView?.rotation ?: 0f).toInt()}°")
        }

        AlertDialog.Builder(context)
            .setTitle("PDF Information")
            .setMessage(info)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun closePdfRenderer() {
        currentPage?.close()
        pdfRenderer?.close()
        fileDescriptor?.close()
        pdfFile?.delete()

        currentPage = null
        pdfRenderer = null
        fileDescriptor = null
        pdfFile = null
    }

    override fun getDefaultWidth(): Int = dpToPx(400)
    override fun getDefaultHeight(): Int = dpToPx(500)

    override fun getCustomSaveData(): Map<String, Any> {
        val baseData = super.getCustomSaveData().toMutableMap()
        baseData.putAll(mapOf(
            "pdfUri" to (currentPdfUri?.toString() ?: ""),
            "currentPage" to currentPageIndex,
            "totalPages" to totalPages
        ))
        return baseData
    }

    override fun loadCustomSaveData(data: Map<String, Any>) {
        super.loadCustomSaveData(data)

        data["pdfUri"]?.let { uriString ->
            if (uriString is String && uriString.isNotEmpty()) {
                val uri = Uri.parse(uriString)
                setPdfFromUri(uri)
            }
        }

        data["currentPage"]?.let { page ->
            if (page is Int && page >= 0) {
                currentPageIndex = page
                renderPage(currentPageIndex)
                updatePageIndicator()
                updateNavigationButtons()
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        closePdfRenderer()
    }
}