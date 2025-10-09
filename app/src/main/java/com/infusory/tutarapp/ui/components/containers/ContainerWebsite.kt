// ContainerWebsite.kt
package com.infusory.tutarapp.ui.components.containers

import android.content.Context
import android.util.AttributeSet
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.infusory.tutarapp.ui.components.containers.ControlButton
import com.infusory.tutarapp.ui.components.containers.ButtonPosition

class ContainerWebsite @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ContainerBase(context, ContainerType.WEBSITE, attrs, defStyleAttr) {

    private var currentUrl: String? = null
    private var currentTitle: String? = null

    private var webView: WebView? = null
    private var urlInputField: EditText? = null
    private var isLoading = false

    // Track interaction mode
    private var isWebInteractionMode = false

    init {
        setupWebsiteContainer()
        setPadding(4, 4, 4, 4)
    }

    private fun setupWebsiteContainer() {
        val buttons = listOf(
            // Close button - positioned outside top-left
            ControlButton(
                iconRes = android.R.drawable.ic_menu_close_clear_cancel,
                onClick = { onRemoveRequest?.invoke() },
                position = ButtonPosition.OUTSIDE_TOP_START
            ),
            // Toggle interaction button - below close button
            ControlButton(
                iconRes = android.R.drawable.ic_menu_edit,
                onClick = { toggleInteractionMode() },
                position = ButtonPosition.OUTSIDE_TOP_START_BELOW
            ),
            // Info button - top center
            ControlButton(
                iconRes = android.R.drawable.ic_menu_info_details,
                onClick = { showWebsiteInfo() },
                position = ButtonPosition.TOP_CENTER
            ),
            // Reload button - top end
            ControlButton(
                iconRes = android.R.drawable.ic_menu_revert,
                onClick = { reloadWebsite() },
                position = ButtonPosition.TOP_END
            )
        )

        addControlButtons(buttons)
    }

    private fun toggleInteractionMode() {
        isWebInteractionMode = !isWebInteractionMode

        // Update container dragging based on mode
        isDraggingEnabled = !isWebInteractionMode
        isResizingEnabled = !isWebInteractionMode

        // Update visual feedback
        updateInteractionModeVisuals()

        val message = if (isWebInteractionMode) {
            "Web Interaction Mode: You can now interact with the webpage"
        } else {
            "Container Mode: You can now move and resize the container"
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private fun updateInteractionModeVisuals() {
        // Change border color based on mode
        if (isWebInteractionMode) {
            // Green tint to indicate web interaction mode
            setBackgroundResource(com.infusory.tutarapp.R.drawable.dotted_border_background)
            alpha = 1.0f
        } else {
            // Normal appearance for container mode
            setBackgroundResource(com.infusory.tutarapp.R.drawable.dotted_border_background)
            alpha = 0.95f
        }
    }

    override fun initializeContent() {
        createWebsiteView()
    }

    private fun createWebsiteView() {
        // Create a container for input and webview
        val contentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.WHITE)

            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        // Create horizontal layout for URL input and GO button
        val urlBarLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(4))
            }
        }

        // Create URL input field
        urlInputField = EditText(context).apply {
            hint = "Enter website URL here..."
            textSize = 14f
            setTextColor(android.graphics.Color.BLACK)
            setHintTextColor(android.graphics.Color.GRAY)
            setBackgroundColor(android.graphics.Color.parseColor("#F5F5F5"))
            setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
            setSingleLine(true)

            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f // Take remaining space
            )
        }

        // Create GO button
        val goButton = android.widget.Button(context).apply {
            text = "GO"
            textSize = 14f
            setTextColor(android.graphics.Color.WHITE)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.parseColor("#2196F3"))
                cornerRadius = dpToPx(8).toFloat()
            }
            setPadding(dpToPx(6), 0, dpToPx(6), 0)
            minHeight = 0
            minimumHeight = 0

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT // Match the height of the parent (url bar)
            ).apply {
                setMargins(dpToPx(8), 0, 0, 0)
            }

            setOnClickListener {
                val url = urlInputField?.text?.toString()?.trim() ?: ""
                if (url.isNotEmpty()) {
                    loadWebsite(url)
                    // Hide keyboard
                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                    imm.hideSoftInputFromWindow(windowToken, 0)
                } else {
                    Toast.makeText(context, "Please enter a URL", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Add input field and button to url bar layout
        urlBarLayout.addView(urlInputField)
        urlBarLayout.addView(goButton)

        // Create WebView for website
        webView = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false

            setBackgroundColor(android.graphics.Color.WHITE)

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    isLoading = false
                    currentTitle = view?.title
                    Toast.makeText(context, "Page loaded", Toast.LENGTH_SHORT).show()
                }

                override fun onReceivedError(
                    view: WebView?,
                    errorCode: Int,
                    description: String?,
                    failingUrl: String?
                ) {
                    super.onReceivedError(view, errorCode, description, failingUrl)
                    isLoading = false
                    Toast.makeText(context, "Error loading page: $description", Toast.LENGTH_SHORT).show()
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    if (newProgress == 100) {
                        isLoading = false
                    }
                }
            }

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f // Take remaining space
            ).apply {
                setMargins(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(8))
            }
        }

        // Add views to content layout
        contentLayout.addView(urlBarLayout)
        contentLayout.addView(webView)

        // Remove existing content (but keep control buttons)
        val viewsToRemove = mutableListOf<android.view.View>()
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child !is android.widget.ImageView ||
                child.layoutParams.width != dpToPx(24)) {
                viewsToRemove.add(child)
            }
        }
        viewsToRemove.forEach { view -> removeView(view) }

        // Add content layout (at index 0 to be behind buttons)
        addView(contentLayout, 0)
    }

    private fun loadWebsite(url: String) {
        var normalizedUrl = url.trim()

        // Add https:// if no protocol is specified
        if (!normalizedUrl.startsWith("http://") && !normalizedUrl.startsWith("https://")) {
            normalizedUrl = "https://$normalizedUrl"
        }

        currentUrl = normalizedUrl
        isLoading = true

        webView?.loadUrl(normalizedUrl)
        Toast.makeText(context, "Loading website...", Toast.LENGTH_SHORT).show()
    }

    private fun reloadWebsite() {
        if (currentUrl != null) {
            webView?.reload()
            Toast.makeText(context, "Website reloaded", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "No website loaded", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showWebsiteInfo() {
        val info = buildString {
            append("Website Information:\n\n")
            if (currentUrl != null) {
                append("Title: ${currentTitle ?: "Unknown"}\n")
                append("URL: $currentUrl\n")
                append("Status: ${if (isLoading) "Loading" else "Loaded"}\n")
            } else {
                append("No website loaded\n")
            }
            append("Interaction Mode: ${if (isWebInteractionMode) "Web" else "Container"}\n")
            append("Container Size: ${width} x ${height}")
        }

        AlertDialog.Builder(context)
            .setTitle("Website Information")
            .setMessage(info)
            .setPositiveButton("OK", null)
            .show()
    }

    fun setWebsiteUrl(url: String) {
        urlInputField?.setText(url)
        loadWebsite(url)
    }

    fun goBack(): Boolean {
        return if (webView?.canGoBack() == true) {
            webView?.goBack()
            true
        } else {
            false
        }
    }

    fun goForward(): Boolean {
        return if (webView?.canGoForward() == true) {
            webView?.goForward()
            true
        } else {
            false
        }
    }

    override fun getDefaultWidth(): Int = dpToPx(400)
    override fun getDefaultHeight(): Int = dpToPx(500)

    override fun getCustomSaveData(): Map<String, Any> {
        val baseData = super.getCustomSaveData().toMutableMap()
        baseData.putAll(mapOf(
            "url" to (currentUrl ?: ""),
            "title" to (currentTitle ?: ""),
            "isLoading" to isLoading,
            "isWebInteractionMode" to isWebInteractionMode
        ))
        return baseData
    }

    override fun loadCustomSaveData(data: Map<String, Any>) {
        super.loadCustomSaveData(data)

        data["url"]?.let { url ->
            if (url is String && url.isNotEmpty()) {
                setWebsiteUrl(url)
            }
        }

        data["isWebInteractionMode"]?.let { mode ->
            if (mode is Boolean) {
                isWebInteractionMode = mode
                isDraggingEnabled = !mode
                isResizingEnabled = !mode
                updateInteractionModeVisuals()
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        webView?.destroy()
    }
}