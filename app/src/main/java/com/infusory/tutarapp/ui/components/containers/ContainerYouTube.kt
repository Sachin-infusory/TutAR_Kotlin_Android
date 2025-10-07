// ContainerYouTube.kt
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
import java.util.regex.Pattern

class ContainerYouTube @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ContainerBase(context, ContainerType.YOUTUBE, attrs, defStyleAttr) {

    private var currentVideoUrl: String? = null
    private var currentVideoId: String? = null

    private var webView: WebView? = null
    private var urlInputField: EditText? = null
    private var isPlaying = false

    init {
        setupYouTubeContainer()
        setPadding(4, 4, 4, 4)
    }

    private fun setupYouTubeContainer() {
        val buttons = listOf(
            ControlButton(
                iconRes = android.R.drawable.ic_menu_close_clear_cancel,
                onClick = { onRemoveRequest?.invoke() },
                position = ButtonPosition.TOP_START
            ),
            ControlButton(
                iconRes = android.R.drawable.ic_menu_info_details,
                onClick = { showVideoInfo() },
                position = ButtonPosition.TOP_CENTER
            ),
            ControlButton(
                iconRes = android.R.drawable.ic_menu_revert,
                onClick = { reloadVideo() },
                position = ButtonPosition.TOP_END
            )
        )

        addControlButtons(buttons)
    }

    override fun initializeContent() {
        createYouTubeView()
    }

    private fun createYouTubeView() {
        // Create a container for input and webview
        val contentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.BLACK)

            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        // Create URL input field
        urlInputField = EditText(context).apply {
            hint = "Paste YouTube URL here..."
            textSize = 14f
            setTextColor(android.graphics.Color.WHITE)
            setHintTextColor(android.graphics.Color.GRAY)
            setBackgroundColor(android.graphics.Color.parseColor("#333333"))
            setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
            setSingleLine(true)

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(4))
            }

            // Listen for text changes
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    val url = s?.toString() ?: ""
                    if (url.isNotEmpty()) {
                        loadYouTubeVideo(url)
                    }
                }
            })
        }

        // Create WebView for YouTube video
        webView = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true

            setBackgroundColor(android.graphics.Color.BLACK)
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f // Take remaining space
            ).apply {
                setMargins(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(8))
            }
        }

        // Add views to content layout
        contentLayout.addView(urlInputField)
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

    private fun loadYouTubeVideo(url: String) {
        val videoId = extractVideoId(url)

        if (videoId != null) {
            currentVideoUrl = url
            currentVideoId = videoId

            // Create embedded YouTube player HTML
            val html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <style>
                        body {
                            margin: 0;
                            padding: 0;
                            background-color: #000;
                            display: flex;
                            justify-content: center;
                            align-items: center;
                            height: 100vh;
                        }
                        .video-container {
                            position: relative;
                            width: 100%;
                            padding-bottom: 56.25%; /* 16:9 aspect ratio */
                        }
                        iframe {
                            position: absolute;
                            top: 0;
                            left: 0;
                            width: 100%;
                            height: 100%;
                            border: none;
                        }
                    </style>
                </head>
                <body>
                    <div class="video-container">
                        <iframe 
                            src="https://www.youtube.com/embed/$videoId?autoplay=1&playsinline=1&rel=0"
                            allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
                            allowfullscreen>
                        </iframe>
                    </div>
                </body>
                </html>
            """.trimIndent()

            webView?.loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "UTF-8", null)
            isPlaying = true

            Toast.makeText(context, "Loading video...", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Invalid YouTube URL", Toast.LENGTH_SHORT).show()
            currentVideoUrl = null
            currentVideoId = null
        }
    }

    private fun extractVideoId(url: String): String? {
        // Pattern for various YouTube URL formats
        val patterns = listOf(
            "(?<=watch\\?v=|/videos/|embed\\/|youtu.be\\/|\\/v\\/|\\/e\\/|watch\\?v%3D|watch\\?feature=player_embedded&v=|%2Fvideos%2F|embed%\u200C\u200B2F|youtu.be%2F|%2Fv%2F)[^#\\&\\?\\n]*",
            "(?:youtube\\.com\\/(?:[^\\/]+\\/.+\\/|(?:v|e(?:mbed)?)\\/|.*[?&]v=)|youtu\\.be\\/)([^\"&?\\/ ]{11})"
        )

        for (pattern in patterns) {
            val compiledPattern = Pattern.compile(pattern)
            val matcher = compiledPattern.matcher(url)
            if (matcher.find()) {
                val videoId = matcher.group()
                if (videoId?.length == 11) {
                    return videoId
                }
            }
        }

        // Try simple extraction for youtu.be links
        if (url.contains("youtu.be/")) {
            val parts = url.split("youtu.be/")
            if (parts.size > 1) {
                val id = parts[1].split("?")[0].split("&")[0]
                if (id.length == 11) {
                    return id
                }
            }
        }

        // Try extraction for youtube.com/watch?v= links
        if (url.contains("youtube.com/watch?v=")) {
            val parts = url.split("v=")
            if (parts.size > 1) {
                val id = parts[1].split("&")[0]
                if (id.length == 11) {
                    return id
                }
            }
        }

        return null
    }

    private fun reloadVideo() {
        if (currentVideoUrl != null) {
            loadYouTubeVideo(currentVideoUrl!!)
            Toast.makeText(context, "Video reloaded", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "No video loaded", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showVideoInfo() {
        val info = buildString {
            append("YouTube Video Information:\n\n")
            if (currentVideoId != null) {
                append("Video ID: $currentVideoId\n")
                append("URL: $currentVideoUrl\n")
                append("Status: ${if (isPlaying) "Playing" else "Stopped"}\n")
            } else {
                append("No video loaded\n")
            }
            append("Container Size: ${width} x ${height}")
        }

        AlertDialog.Builder(context)
            .setTitle("Video Information")
            .setMessage(info)
            .setPositiveButton("OK", null)
            .show()
    }

    fun setYouTubeUrl(url: String) {
        urlInputField?.setText(url)
        loadYouTubeVideo(url)
    }

    override fun getDefaultWidth(): Int = dpToPx(400)
    override fun getDefaultHeight(): Int = dpToPx(350)

    override fun getCustomSaveData(): Map<String, Any> {
        val baseData = super.getCustomSaveData().toMutableMap()
        baseData.putAll(mapOf(
            "videoUrl" to (currentVideoUrl ?: ""),
            "videoId" to (currentVideoId ?: ""),
            "isPlaying" to isPlaying
        ))
        return baseData
    }

    override fun loadCustomSaveData(data: Map<String, Any>) {
        super.loadCustomSaveData(data)

        data["videoUrl"]?.let { url ->
            if (url is String && url.isNotEmpty()) {
                setYouTubeUrl(url)
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        webView?.destroy()
    }
}