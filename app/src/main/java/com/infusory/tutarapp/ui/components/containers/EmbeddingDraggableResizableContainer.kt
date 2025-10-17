package com.infusory.tutarapp.managers

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.*
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.infusory.tutarapp.R
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import java.util.*
import java.util.regex.Pattern
import kotlin.math.pow
import kotlin.math.sqrt


class EmbeddingDraggableResizableContainer(context: Context) : ViewGroup(context) {
    private val borderPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 4f
        pathEffect = DashPathEffect(floatArrayOf(20f, 10f), 0f)
    }

    private var currentSearchQuery: String = ""

    // Load drawable icons
    private val dragIcon = ContextCompat.getDrawable(context, R.drawable.ic_more)
    private val closeIcon = ContextCompat.getDrawable(context, R.drawable.ic_close)
    private val searchIcon = ContextCompat.getDrawable(context, R.drawable.ic_search)
    private val resizeIcon = ContextCompat.getDrawable(context, R.drawable.ic_insert)
    private val fullScreenIcon = ContextCompat.getDrawable(context, R.drawable.ic_square_tool)

    private var lastX = 0f
    private var lastY = 0f
    private var isDragging = false
    private var isResizing = false
    private var isPinching = false
    private var isResizeMode = false
    private var isFullScreen = false
    private var activeCorner = Corner.NONE

    // Touch flags for buttons
    private var isCloseTouched = false
    private var isResizeButtonTouched = false
    private var isFullScreenButtonTouched = false
    private var isSearchIconTouched = false

    // Sizes and constants
    private val iconSize = 80f
    private val resizeHandleSize = 60f
    private val iconSpacing = 20f
    private val searchBarHeight = 96f
    private val searchBarMargin = 12f
    private val resizeHitArea = 80f
    private val buttonAreaHeight = 100f

    // Original state for full screen exit
    private var originalWidth = 0
    private var originalHeight = 0
    private var originalLeftMargin = 0
    private var originalTopMargin = 0
    private var initialHeight = 0

    // Dimension constraints
    private val minWidth = 400
    private val minHeight = 350
    private val maxWidth: Int get() = screenWidth  // Full screen width as max
    private val maxHeight: Int get() = screenHeight // Full screen height as max
    private val defaultWidth = 500
    private val defaultHeight = 400


    private val screenWidth: Int get() = context.resources.displayMetrics.widthPixels
    private val screenHeight: Int get() = context.resources.displayMetrics.heightPixels

    // Aspect ratios
    private val youtubeAspectRatio = 16f / 9f
    private val websiteAspectRatio = 16f / 10f
    private var aspectRatio = youtubeAspectRatio

    // Views
    private val searchBar: EditText
    private val youTubePlayerView: YouTubePlayerView
    private val webView: WebView
    private var youTubePlayer: YouTubePlayer? = null


    // State management
    private val containerId = UUID.randomUUID().toString()
    private val embedContainerManager = EmbedContainerManager(context, containerId)

    private enum class ContentType { NONE, YOUTUBE, WEBSITE }

    private var currentContentType = ContentType.NONE

    // Only 3 corners for resizing: TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
    enum class Corner { NONE, BOTTOM_LEFT, BOTTOM_RIGHT }

    var onCloseClickListener: (() -> Unit)? = null

    // Add getter for current URL
    private fun getCurrentUrl(): String {
        return embedContainerManager.getUrls().lastOrNull() ?: ""
    }

    // Add getter for current content type
    private fun getCurrentContentTypeString(): String {
        return when (currentContentType) {
            ContentType.YOUTUBE -> "YOUTUBE"
            ContentType.WEBSITE -> "WEBSITE"
            ContentType.NONE -> "NONE"
        }
    }

    // Update getCurrentState to include all properties
    fun getCurrentState(): EmbedContainerState {
        val params = layoutParams as? MarginLayoutParams
        return EmbedContainerState(
            leftMargin = params?.leftMargin ?: 0,
            topMargin = params?.topMargin ?: 0,
            width = width,
            height = height,
            searchQuery = currentSearchQuery,
            urls = embedContainerManager.getUrls(),
            isInteractMode = isResizeMode,
            containerId = containerId,
            containerType = embedContainerManager.getState().containerType,
            isFullScreen = isFullScreen,
            aspectRatio = aspectRatio,
            currentContentType = getCurrentContentTypeString(),
            currentUrl = getCurrentUrl()
        )
    }

    // Scale gesture detector for pinch-to-zoom
    private val scaleGestureDetector =
        ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            private var lastFocusX = 0f
            private var lastFocusY = 0f

            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                if (!isResizeMode || isFullScreen) return false
                isPinching = true
                initialWidth = width
                initialHeight = height
                scaleFactor = 1f

                lastFocusX = detector.focusX
                lastFocusY = detector.focusY

                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (!isResizeMode || isFullScreen) return false

                scaleFactor *= detector.scaleFactor
                scaleFactor = scaleFactor.coerceIn(0.5f, 3.0f)

                // Calculate new width maintaining aspect ratio with screen limits
                val newWidth = (initialWidth * scaleFactor).toInt().coerceIn(minWidth, maxWidth)

                // Calculate height based on aspect ratio (excluding button area)
                val contentHeight = (newWidth / aspectRatio).toInt()
                var newHeight = contentHeight + buttonAreaHeight.toInt()

                // Ensure height constraints with screen limits
                newHeight = newHeight.coerceIn(minHeight, maxHeight)

                // If height was constrained, recalculate width
                val finalWidth = if (newHeight >= maxHeight) {
                    val requiredContentHeight = maxHeight - buttonAreaHeight.toInt()
                    (requiredContentHeight * aspectRatio).toInt().coerceIn(minWidth, maxWidth)
                } else if (newHeight <= minHeight) {
                    val requiredContentHeight = minHeight - buttonAreaHeight.toInt()
                    (requiredContentHeight * aspectRatio).toInt().coerceIn(minWidth, maxWidth)
                } else {
                    newWidth
                }

                val finalHeight = if (newHeight >= maxHeight) {
                    maxHeight
                } else if (newHeight <= minHeight) {
                    minHeight
                } else {
                    newHeight
                }

                val params = layoutParams
                params.width = finalWidth
                params.height = finalHeight

                // Adjust position to scale around the focus point
                val focusDeltaX = detector.focusX - lastFocusX
                val focusDeltaY = detector.focusY - lastFocusY

                if (params is MarginLayoutParams) {
                    params.leftMargin = (params.leftMargin - focusDeltaX * 0.5f).toInt()
                        .coerceIn(0, screenWidth - finalWidth)
                    params.topMargin = (params.topMargin - focusDeltaY * 0.5f).toInt()
                        .coerceIn(0, screenHeight - finalHeight)
                }

                layoutParams = params
                requestLayout()
                updateState()

                lastFocusX = detector.focusX
                lastFocusY = detector.focusY

                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                isPinching = false
                scaleFactor = 1f
            }
        })
    private var initialWidth = 0
    private var scaleFactor = 1f

    init {
        setBackgroundColor(Color.TRANSPARENT)
        setWillNotDraw(false)

        // Initialize search bar
        searchBar = createSearchBar()
        addView(searchBar)

        // Initialize YouTube Player
        youTubePlayerView = createYouTubePlayer()
        addView(youTubePlayerView)

        // Initialize WebView
        webView = createWebView()
        addView(webView)

        // Load initial state
        loadInitialState()

        // Default close listener
        onCloseClickListener = {
            destroy()
            (parent as? ViewGroup)?.removeView(this)
        }
    }

    private fun createSearchBar(): EditText {
        return EditText(context).apply {
            hint = "Enter YouTube URL or website"
            setBackgroundResource(android.R.drawable.edit_text)
            inputType =
                android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            setCompoundDrawablesWithIntrinsicBounds(null, null, searchIcon, null)
            compoundDrawablePadding = 12
            setPadding(16, 12, 16, 12)
            textSize = 16f
            isEnabled = true // Always enabled

            embedContainerManager.loadState()?.let { state ->
                setText(state.searchQuery)
                currentSearchQuery = state.searchQuery
            }

            setOnEditorActionListener { _, actionId, event ->
                // Remove the isResizeMode check - always allow search
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
                    event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
                ) {
                    performSearch()
                    true
                } else false
            }

            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    currentSearchQuery = s?.toString() ?: ""
                    updateState()
                }
            })
        }
    }

    private fun createYouTubePlayer(): YouTubePlayerView {
        return YouTubePlayerView(context).apply {
            enableAutomaticInitialization = false
            initialize(object : AbstractYouTubePlayerListener() {
                override fun onReady(player: YouTubePlayer) {
                    this@EmbeddingDraggableResizableContainer.youTubePlayer = player
                }
            }, true)
            visibility = View.GONE
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
    }

    private fun createWebView(): WebView {
        return WebView(context).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }
            }

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    if (currentContentType == ContentType.WEBSITE) updateAspectRatio()
                }
            }
            setBackgroundColor(Color.TRANSPARENT)
            visibility = View.GONE
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
    }

    private fun loadInitialState() {
        embedContainerManager.loadState()?.let { state ->
            isResizeMode = state.isInteractMode
            updateInteractionState()
            searchBar.setText(state.searchQuery)

            searchBar.visibility = View.VISIBLE

            val params = layoutParams as? MarginLayoutParams ?: return

            // Use state dimensions if available, otherwise use better default size
            if (state.width > 0 && state.height > 0) {
                params.width = state.width.coerceIn(minWidth, screenWidth)
                params.height = state.height.coerceIn(minHeight, screenHeight)
                params.leftMargin = state.leftMargin.coerceIn(0, screenWidth - params.width)
                params.topMargin = state.topMargin.coerceIn(0, screenHeight - params.height)
                aspectRatio = state.width.toFloat() / state.height.toFloat()
            } else {
                // Set better default size but don't center - use current position
                params.width = defaultWidth
                params.height = defaultHeight
                // Don't change position - keep current margins
            }

            layoutParams = params

            state.urls.lastOrNull()?.let { loadContent(it) }
        } ?: run {
            // If no saved state, set better default size but don't center
            val params = layoutParams as? MarginLayoutParams ?: return
            params.width = defaultWidth
            params.height = defaultHeight
            // Don't center - just use current position
            layoutParams = params
        }
    }

    // Update the toggleFullScreen method to properly track state
    private fun toggleFullScreen() {
        if (isFullScreen) {
            // Exit full screen - use constrained dimensions
            val params = layoutParams as? MarginLayoutParams ?: return
            params.width = originalWidth.coerceIn(minWidth, maxWidth)
            params.height = originalHeight.coerceIn(minHeight, maxHeight)
            params.leftMargin = originalLeftMargin.coerceIn(0, screenWidth - params.width)
            params.topMargin = originalTopMargin.coerceIn(0, screenHeight - params.height)
            layoutParams = params
            isFullScreen = false
        } else {
            // Enter full screen
            val params = layoutParams as? MarginLayoutParams ?: return
            originalWidth = params.width
            originalHeight = params.height
            originalLeftMargin = params.leftMargin
            originalTopMargin = params.topMargin

            val availableHeight = screenHeight - buttonAreaHeight.toInt()
            val fullScreenWidth: Int
            val fullScreenHeight: Int
            isResizeMode = false

            if (screenWidth / aspectRatio <= availableHeight) {
                fullScreenWidth = screenWidth
                fullScreenHeight = (screenWidth / aspectRatio).toInt() + buttonAreaHeight.toInt()
            } else {
                fullScreenHeight = screenHeight
                fullScreenWidth = ((screenHeight - buttonAreaHeight) * aspectRatio).toInt()
            }

            // Ensure full screen dimensions don't exceed screen bounds
            params.width = fullScreenWidth.coerceAtMost(screenWidth)
            params.height = fullScreenHeight.coerceAtMost(screenHeight)
            params.leftMargin = (screenWidth - params.width) / 2
            params.topMargin = 0
            layoutParams = params
            isFullScreen = true
        }

        updateLayout()
        updateState()
        invalidate()
        if (isFullScreen) bringToFront()
    }

    private fun performSearch() {
        val query = searchBar.text.toString().trim()
        currentSearchQuery = query

        if (query.isNotEmpty() && isValidUrl(query)) {
            loadContent(query)
            searchBar.clearFocus()
            hideKeyboard()
            updateState()
            Toast.makeText(context, "Loading content...", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(
                context,
                "Please enter a valid YouTube URL or website",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Add this method to load content from saved state
    // Add this method to load content from saved state
    fun performSearchForUrl(url: String, searchQuery: String = "") {
        // Set the search query in the search bar (for display purposes only)
        searchBar.setText(searchQuery.ifEmpty { url })
        currentSearchQuery = searchQuery.ifEmpty { url }

        // Load the content immediately without requiring search button click
        loadContent(url)

        // Update state
        updateState()

        // Clear focus and hide keyboard to ensure clean state
        searchBar.clearFocus()
        hideKeyboard()
    }

    private fun resetYouTubePlayer() {
        youTubePlayer?.pause()
        youTubePlayer?.seekTo(0f)
        youTubePlayerView.visibility = View.GONE
    }

    private fun resetWebView() {
        webView.stopLoading()
        webView.clearHistory()
        webView.visibility = View.GONE
        webView.onPause()
        webView.pauseTimers()
    }

    private fun hideKeyboard() {
        val imm =
            context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(searchBar.windowToken, 0)
    }

    private fun loadContent(url: String) {
        val normalizedUrl = normalizeUrl(url)

        // Store current position before loading content
        val params = layoutParams as? MarginLayoutParams
        val currentLeftMargin = params?.leftMargin ?: 0
        val currentTopMargin = params?.topMargin ?: 0

        // Stop and hide previous content before loading new content
        stopPreviousContent()

        if (isYouTubeUrl(normalizedUrl)) {
            showYouTubePlayer()
            extractYouTubeVideoId(normalizedUrl)?.let { videoId ->
                loadYouTubeVideo(videoId)
                currentContentType = ContentType.YOUTUBE
                updateAspectRatio()
                adjustContainerForYouTube()
            }
        } else {
            showWebView()
            loadWebsite(normalizedUrl)
            currentContentType = ContentType.WEBSITE
            updateAspectRatio()
            adjustContainerForWebsite()
        }

        embedContainerManager.addUrl(normalizedUrl)
        updateState()
        requestLayout()

        searchBar.visibility = View.VISIBLE

        // Show loading toast
        Toast.makeText(context, "Loading content...", Toast.LENGTH_SHORT).show()
    }

    private fun stopPreviousContent() {
        when (currentContentType) {
            ContentType.YOUTUBE -> {
                resetYouTubePlayer()
            }

            ContentType.WEBSITE -> {
                resetWebView()
            }

            ContentType.NONE -> {
                // Nothing to stop
            }
        }
    }

    private fun showYouTubePlayer() {
        // Ensure WebView is properly stopped and hidden
        webView.stopLoading()
        webView.visibility = View.GONE
        webView.onPause()

        youTubePlayerView.visibility = View.VISIBLE
        youTubePlayerView.bringToFront()
    }


    private fun showWebView() {
        // Ensure YouTube is properly stopped and hidden
        pauseYouTubeVideo()
        youTubePlayerView.visibility = View.GONE

        webView.visibility = View.VISIBLE
        webView.onResume()
        webView.resumeTimers()
        webView.bringToFront()
    }

    private fun loadYouTubeVideo(videoId: String) {
        youTubePlayer?.cueVideo(videoId, 0f)
    }

    private fun loadWebsite(url: String) {
        webView.loadUrl(url)
    }

    private fun adjustContainerForYouTube() {
        resizeContainer(
            700, // Reduced from 900
            (700 / youtubeAspectRatio).toInt() + buttonAreaHeight.toInt(),
            center = false // Change from true to false
        )
    }

    private fun adjustContainerForWebsite() {
        resizeContainer(
            600, // Reduced from 800
            (600 / websiteAspectRatio).toInt() + buttonAreaHeight.toInt(),
            center = false // Change from true to false
        )
    }

    private fun resizeContainer(newWidth: Int, newHeight: Int, center: Boolean = false) {
        val params = layoutParams as? MarginLayoutParams ?: return

        // Ensure the container is within min/max limits
        val constrainedWidth = newWidth.coerceIn(minWidth, maxWidth) // ADD maxWidth
        val constrainedHeight = newHeight.coerceIn(minHeight, maxHeight) // ADD maxHeight

        // Store current position before resizing
        val currentLeftMargin = params.leftMargin
        val currentTopMargin = params.topMargin

        params.width = constrainedWidth
        params.height = constrainedHeight

        if (center) {
            params.leftMargin = (screenWidth - constrainedWidth) / 2
            params.topMargin = (screenHeight - constrainedHeight) / 2
        } else {
            params.leftMargin = currentLeftMargin.coerceIn(0, screenWidth - constrainedWidth)
            params.topMargin = currentTopMargin.coerceIn(0, screenHeight - constrainedHeight)
        }

        layoutParams = params
        updateLayout()
        updateState()
    }

    private fun updateAspectRatio() {
        when (currentContentType) {
            ContentType.YOUTUBE -> aspectRatio = youtubeAspectRatio
            ContentType.WEBSITE -> aspectRatio = websiteAspectRatio
            ContentType.NONE -> aspectRatio = 16f / 10f // Default aspect ratio
        }

        // Ensure current dimensions respect the new aspect ratio
        if (currentContentType != ContentType.NONE) {
            val params = layoutParams as? MarginLayoutParams ?: return
            val contentHeight = (params.width / aspectRatio).toInt()
            val totalHeight = contentHeight + buttonAreaHeight.toInt()

            if (totalHeight != params.height && totalHeight >= minHeight) {
                params.height = totalHeight
                params.topMargin = params.topMargin.coerceIn(0, screenHeight - totalHeight)
                layoutParams = params
                requestLayout()
            }
        }
    }

    private fun updateInteractionState() {
        // Always enable the content views for interaction, regardless of resize mode
        youTubePlayerView.isEnabled = true
        webView.isEnabled = true

        // Remove any touch listeners that block interaction
        webView.setOnTouchListener(null)

        // Only pause content when actively resizing, not just in resize mode
        if (isResizing || isDragging || isPinching) {
            pauseYouTubeVideo()
            webView.onPause()
            webView.pauseTimers()
        } else {
            // Resume content when not actively manipulating
            if (currentContentType == ContentType.WEBSITE) {
                webView.onResume()
                webView.resumeTimers()
            }
        }

        invalidate()
    }

    private fun pauseYouTubeVideo() {
        youTubePlayer?.pause()
        // Also stop any ongoing playback
        youTubePlayer?.seekTo(0f)
    }

    // URL validation helpers
    private fun isValidUrl(url: String): Boolean {
        val normalizedUrl = if (!url.startsWith("http")) "https://$url" else url
        val youtubePattern = Pattern.compile(
            "^(https?://)?(www\\.)?(youtube\\.com/(watch\\?v=|shorts/|embed/|live/)|youtu\\.be/).+$",
            Pattern.CASE_INSENSITIVE
        )
        val websitePattern = Pattern.compile(
            "^(https?://)?([a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,}(/.*)?$",
            Pattern.CASE_INSENSITIVE
        )
        return youtubePattern.matcher(normalizedUrl).matches() || websitePattern.matcher(
            normalizedUrl
        ).matches()
    }

    private fun isYouTubeUrl(url: String): Boolean {
        val pattern = Pattern.compile(
            "^(https?://)?(www\\.)?(youtube\\.com/(watch\\?v=|shorts/|embed/|live/)|youtu\\.be/).+$",
            Pattern.CASE_INSENSITIVE
        )
        return pattern.matcher(url).matches()
    }

    private fun extractYouTubeVideoId(url: String): String? {
        val pattern = Pattern.compile(
            "^.*(?:(?:youtu\\.be/|v/|vi/|u/\\w/|embed/|shorts/)|(?:(?:watch)?\\?v(?:i)?=|&v(?:i)?=))([^#&?]*).*",
            Pattern.CASE_INSENSITIVE
        )
        val matcher = pattern.matcher(url)
        return if (matcher.find()) matcher.group(1) else null
    }

    private fun normalizeUrl(url: String): String {
        return if (!url.startsWith("http")) "https://$url" else url
    }

    private fun updateState() {
        val params = layoutParams as? MarginLayoutParams ?: return
        embedContainerManager.setState(
            EmbedContainerState(
                leftMargin = params.leftMargin,
                topMargin = params.topMargin,
                width = width,
                height = height,
                searchQuery = currentSearchQuery,
                urls = embedContainerManager.getUrls(),
                isInteractMode = isResizeMode,
                containerId = containerId,
                containerType = embedContainerManager.getState().containerType,
                isFullScreen = isFullScreen,
                aspectRatio = aspectRatio,
                currentContentType = getCurrentContentTypeString(),
                currentUrl = getCurrentUrl()
            )
        )
    }

    // Layout methods
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        val measuredWidth: Int
        val measuredHeight: Int

        when (widthMode) {
            MeasureSpec.EXACTLY -> {
                measuredWidth = widthSize.coerceIn(minWidth, maxWidth)
            }
            MeasureSpec.AT_MOST -> {
                measuredWidth = widthSize.coerceAtMost(maxWidth).coerceAtLeast(minWidth)
            }
            else -> {
                measuredWidth = defaultWidth.coerceIn(minWidth, maxWidth)
            }
        }

        when (heightMode) {
            MeasureSpec.EXACTLY -> {
                measuredHeight = heightSize.coerceIn(minHeight, maxHeight)
            }
            MeasureSpec.AT_MOST -> {
                measuredHeight = heightSize.coerceAtMost(maxHeight).coerceAtLeast(minHeight)
            }
            else -> {
                measuredHeight = defaultHeight.coerceIn(minHeight, maxHeight)
            }
        }

        setMeasuredDimension(measuredWidth, measuredHeight)

        val buttonAreaHeightPx = buttonAreaHeight.toInt()
        val contentHeight = measuredHeight - buttonAreaHeightPx

        // Calculate available width for search bar (accounting for icons)
        val totalIconsWidth = (iconSize * 3) + (iconSpacing * 2)
        val searchBarAvailableWidth =
            measuredWidth - (2 * searchBarMargin).toInt() - totalIconsWidth.toInt()

        // Always measure search bar (even in full screen)
        searchBar.measure(
            MeasureSpec.makeMeasureSpec(
                searchBarAvailableWidth.coerceAtLeast(100),
                MeasureSpec.EXACTLY
            ),
            MeasureSpec.makeMeasureSpec(searchBarHeight.toInt(), MeasureSpec.EXACTLY)
        )

        // Measure content views
        val contentWidthSpec =
            MeasureSpec.makeMeasureSpec(measuredWidth.coerceAtLeast(1), MeasureSpec.EXACTLY)
        val contentHeightSpec =
            MeasureSpec.makeMeasureSpec(contentHeight.coerceAtLeast(1), MeasureSpec.EXACTLY)
        youTubePlayerView.measure(contentWidthSpec, contentHeightSpec)
        webView.measure(contentWidthSpec, contentHeightSpec)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        updateLayout()
    }

    private fun updateLayout() {
        val buttonAreaHeightPx = buttonAreaHeight.toInt()

        // Calculate the right margin to avoid overlapping with icons
        val totalIconsWidth = (iconSize * 3) + (iconSpacing * 2)
        val searchBarRightMargin = totalIconsWidth.toInt() + searchBarMargin.toInt()

        // Layout search bar - always visible, even in full screen
        searchBar.layout(
            searchBarMargin.toInt(),
            searchBarMargin.toInt(),
            width - searchBarRightMargin,
            searchBarMargin.toInt() + searchBarHeight.toInt()
        )

        // Layout content views - adjust top position to account for search bar
        val contentTop = buttonAreaHeightPx
        youTubePlayerView.layout(0, contentTop, width, height)
        webView.layout(0, contentTop, width, height)
    }

    // Drawing methods
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), borderPaint)

        // Calculate positions for icons (from right to left)
        val closeIconX = width - iconSize / 2
        val fullScreenIconX = closeIconX - iconSize - iconSpacing
        val resizeIconX = fullScreenIconX - iconSize - iconSpacing

        // Draw icons in order: resize, fullscreen, close
        if (!isFullScreen) {
            drawIcon(canvas, resizeIcon, resizeIconX, iconSize / 2, Color.BLACK)
        }
        drawIcon(canvas, fullScreenIcon, fullScreenIconX, iconSize / 2, Color.BLACK)
        drawIcon(canvas, closeIcon, closeIconX, iconSize / 2, Color.BLACK)

        // Draw resize handles only in resize mode and NOT in fullscreen
        if (isResizeMode && !isFullScreen) {
            // BOTTOM_LEFT - rotation 0° (no rotation)
            drawResizeHandle(canvas, resizeHandleSize / 2, height - resizeHandleSize / 2, 0f)
            // BOTTOM_RIGHT - rotation -90°
            drawResizeHandle(
                canvas,
                width - resizeHandleSize / 2,
                height - resizeHandleSize / 2,
                -90f
            )
        }
    }

    private fun drawIcon(
        canvas: Canvas,
        icon: Drawable?,
        centerX: Float,
        centerY: Float,
        tintColor: Int
    ) {
        icon?.let {
            val size = (iconSize / 2).toInt()

            it.setTint(tintColor)
            it.setBounds(
                (centerX - size * 0.7f).toInt(),
                (centerY - size * 0.7f).toInt(),
                (centerX + size * 0.7f).toInt(),
                (centerY + size * 0.7f).toInt()
            )
            it.draw(canvas)
        }
    }

    private fun drawResizeHandle(canvas: Canvas, centerX: Float, centerY: Float, rotation: Float) {
        dragIcon?.let { icon ->
            val halfSize = (resizeHandleSize / 2).toInt()

            canvas.save()
            canvas.rotate(rotation, centerX, centerY)

            icon.setTint(Color.BLACK)
            icon.setBounds(
                (centerX - halfSize * 0.5f).toInt(),
                (centerY - halfSize * 0.5f).toInt(),
                (centerX + halfSize * 0.5f).toInt(),
                (centerY + halfSize * 0.5f).toInt()
            )
            icon.draw(canvas)

            canvas.restore()
        }
    }

    // Touch handling methods
    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        val touchX = event.x
        val touchY = event.y

        // Check if search ICON is touched FIRST (handle this separately)
        if (isSearchIconTouched(touchX, touchY)) {
            return true // Intercept and handle search icon touch
        }

        // ALWAYS allow search bar TEXT AREA touches in ANY state
        if (isInSearchBarTextArea(touchX, touchY)) {
            return false // Don't intercept - let search bar handle text input
        }

        if (isResizeMode) return true

        // Check if any other button is touched
        if (isCloseButtonTouched(touchX, touchY) ||
            isResizeButtonTouched(touchX, touchY) ||
            isFullScreenButtonTouched(touchX, touchY)
        ) {
            return true
        }

        return false
    }

    private fun isInSearchBarTextArea(x: Float, y: Float): Boolean {
        // Only consider the text input area of the search bar, not the icon area
        val iconRight = searchBar.right.toFloat()
        val iconLeft =
            iconRight - searchBar.compoundDrawablePadding - (searchIcon?.intrinsicWidth ?: 0)

        return x >= searchBar.left && x < iconLeft && // Exclude icon area
                y >= searchBar.top && y <= searchBar.bottom
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // FIRST check if search icon is being touched (in any mode)
        if (isSearchIconTouched(event.x, event.y)) {
            return handleSearchIconTouch(event)
        }

        if (!isResizeMode) {
            // Handle other button touches in content mode
            return handleButtonTouches(event)
        }

        // Handle resize mode touches with 3-corner behavior
        scaleGestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> handleActionDown(event)
            MotionEvent.ACTION_POINTER_DOWN -> handleActionPointerDown(event)
            MotionEvent.ACTION_MOVE -> handleActionMove(event)
            MotionEvent.ACTION_UP -> handleActionUp(event)
            MotionEvent.ACTION_POINTER_UP -> handleActionPointerUp(event)
            MotionEvent.ACTION_CANCEL -> resetTouchState()
        }
        return true
    }

    private fun handleSearchIconTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isSearchIconTouched = true
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (isSearchIconTouched) {
                    searchBar.requestFocus()
                    performSearch()
                    isSearchIconTouched = false
                    return true
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                isSearchIconTouched = false
            }
        }
        return true
    }

    private fun handleButtonTouches(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val touchX = event.x
                val touchY = event.y

                isCloseTouched = isCloseButtonTouched(touchX, touchY)
                isResizeButtonTouched = isResizeButtonTouched(touchX, touchY)
                isFullScreenButtonTouched = isFullScreenButtonTouched(touchX, touchY)

                // Don't handle search icon here anymore - it's handled separately
                return isCloseTouched || isResizeButtonTouched || isFullScreenButtonTouched
            }

            MotionEvent.ACTION_UP -> {
                val touchX = event.x
                val touchY = event.y

                if (isCloseTouched && isCloseButtonTouched(touchX, touchY)) {
                    onCloseClickListener?.invoke()
                } else if (isResizeButtonTouched && isResizeButtonTouched(touchX, touchY)) {
                    toggleResizeMode()
                } else if (isFullScreenButtonTouched && isFullScreenButtonTouched(touchX, touchY)) {
                    toggleFullScreen()
                }

                resetTouchState()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                resetTouchState()
            }
        }
        return true
    }

    private fun handleActionDown(event: MotionEvent) {
        if (event.pointerCount == 1) {
            val touchX = event.x
            val touchY = event.y

            // Search icon is now handled separately in onTouchEvent
            // Skip search icon check here

            if (isCloseButtonTouched(touchX, touchY)) {
                isCloseTouched = true
                isResizeButtonTouched = false
                isFullScreenButtonTouched = false
                isDragging = false
                isResizing = false
                activeCorner = Corner.NONE
                return
            }

            if (isResizeButtonTouched(touchX, touchY)) {
                isResizeButtonTouched = true
                isCloseTouched = false
                isFullScreenButtonTouched = false
                isDragging = false
                isResizing = false
                activeCorner = Corner.NONE
                return
            }

            if (isFullScreenButtonTouched(touchX, touchY)) {
                isFullScreenButtonTouched = true
                isCloseTouched = false
                isResizeButtonTouched = false
                isDragging = false
                isResizing = false
                activeCorner = Corner.NONE
                return
            }

            lastX = event.rawX
            lastY = event.rawY

            activeCorner = getCornerAtPosition(touchX, touchY)

            isResizing = activeCorner != Corner.NONE
            isDragging = !isResizing && !isPinching
            isCloseTouched = false
            isResizeButtonTouched = false
            isFullScreenButtonTouched = false

            bringToFront()
        }
    }

    private fun handleActionPointerDown(event: MotionEvent) {
        if (event.pointerCount == 2) {
            isPinching = true
            isDragging = false
            isResizing = false
            initialWidth = width
            scaleFactor = 1f
        }
    }

    private fun handleActionMove(event: MotionEvent) {
        if (isPinching && event.pointerCount >= 2) {
            // Handled by scaleGestureDetector
            return
        } else if (event.pointerCount == 1 && !isPinching) {
            val deltaX = event.rawX - lastX
            val deltaY = event.rawY - lastY

            if (isDragging) {
                moveContainer(deltaX, deltaY)
            } else if (isResizing) {
                resizeFromCorner(deltaX, deltaY)
            }

            lastX = event.rawX
            lastY = event.rawY
        }
    }

    private fun handleActionUp(event: MotionEvent) {
        // Search icon is handled separately, remove it from here

        if (isCloseTouched && event.pointerCount == 1) {
            val touchX = event.x
            val touchY = event.y
            if (isCloseButtonTouched(touchX, touchY)) {
                onCloseClickListener?.invoke()
            }
        }

        if (isResizeButtonTouched && event.pointerCount == 1) {
            val touchX = event.x
            val touchY = event.y
            if (isResizeButtonTouched(touchX, touchY)) {
                toggleResizeMode()
            }
        }

        if (isFullScreenButtonTouched && event.pointerCount == 1) {
            val touchX = event.x
            val touchY = event.y
            if (isFullScreenButtonTouched(touchX, touchY)) {
                toggleFullScreen()
            }
        }

        resetTouchState()
    }

    private fun handleActionPointerUp(event: MotionEvent) {
        if (event.pointerCount <= 2) {
            isPinching = false
            scaleFactor = 1f
        }
    }

    private fun moveContainer(deltaX: Float, deltaY: Float) {
        val params = layoutParams as MarginLayoutParams
        params.leftMargin = (params.leftMargin + deltaX).toInt().coerceIn(0, screenWidth - width)
        params.topMargin = (params.topMargin + deltaY).toInt().coerceIn(0, screenHeight - height)
        layoutParams = params
        updateState()
    }

    private fun resizeFromCorner(deltaX: Float, deltaY: Float) {
        val params = layoutParams as MarginLayoutParams
        var newWidth = params.width
        var newHeight = params.height
        var newLeftMargin = params.leftMargin
        var newTopMargin = params.topMargin

        when (activeCorner) {
            Corner.BOTTOM_LEFT -> {
                // Calculate new width based on horizontal movement
                val proposedWidth = (params.width - deltaX).toInt()
                newWidth = proposedWidth.coerceIn(minWidth, maxWidth)

                // Calculate height based on aspect ratio (excluding button area)
                val contentHeight = (newWidth / aspectRatio).toInt()
                newHeight = contentHeight + buttonAreaHeight.toInt()

                // Ensure height constraints
                newHeight = newHeight.coerceIn(minHeight, maxHeight)

                // If height was constrained, recalculate width
                if (newHeight >= maxHeight) {
                    newHeight = maxHeight
                    val requiredContentHeight = maxHeight - buttonAreaHeight.toInt()
                    newWidth =
                        (requiredContentHeight * aspectRatio).toInt().coerceIn(minWidth, maxWidth)
                } else if (newHeight <= minHeight) {
                    newHeight = minHeight
                    val requiredContentHeight = minHeight - buttonAreaHeight.toInt()
                    newWidth =
                        (requiredContentHeight * aspectRatio).toInt().coerceIn(minWidth, maxWidth)
                }

                // Adjust left margin to maintain bottom-left anchor
                newLeftMargin = params.leftMargin + (params.width - newWidth)

                // Ensure the container stays within screen bounds
                newLeftMargin = newLeftMargin.coerceIn(0, screenWidth - newWidth)
            }

            Corner.BOTTOM_RIGHT -> {
                // Calculate new width based on horizontal movement
                val proposedWidth = (params.width + deltaX).toInt()
                newWidth = proposedWidth.coerceIn(minWidth, maxWidth)

                // Calculate height based on aspect ratio (excluding button area)
                val contentHeight = (newWidth / aspectRatio).toInt()
                newHeight = contentHeight + buttonAreaHeight.toInt()

                // Ensure height constraints
                newHeight = newHeight.coerceIn(minHeight, maxHeight)

                // If height was constrained, recalculate width
                if (newHeight >= maxHeight) {
                    newHeight = maxHeight
                    val requiredContentHeight = maxHeight - buttonAreaHeight.toInt()
                    newWidth =
                        (requiredContentHeight * aspectRatio).toInt().coerceIn(minWidth, maxWidth)
                } else if (newHeight <= minHeight) {
                    newHeight = minHeight
                    val requiredContentHeight = minHeight - buttonAreaHeight.toInt()
                    newWidth =
                        (requiredContentHeight * aspectRatio).toInt().coerceIn(minWidth, maxWidth)
                }

                // Keep the same left margin for bottom-right resizing
                newLeftMargin = params.leftMargin

                // Ensure the container stays within screen bounds
                newLeftMargin = newLeftMargin.coerceIn(0, screenWidth - newWidth)
            }

            Corner.NONE -> return
        }

        // Apply the new dimensions if they meet requirements
        if (newWidth in minWidth..maxWidth && newHeight in minHeight..maxHeight) {
            params.width = newWidth
            params.height = newHeight
            params.leftMargin = newLeftMargin
            params.topMargin = newTopMargin.coerceIn(0, screenHeight - newHeight)
            layoutParams = params
            updateLayout()
            updateState()
            invalidate()
        }
    }

    private fun toggleResizeMode() {
        isResizeMode = !isResizeMode
        // Remove searchBar.isEnabled = !isResizeMode - search bar should always be enabled
        updateInteractionState()
        updateState()
        invalidate()
    }

    private fun resetTouchState() {
        isDragging = false
        isResizing = false
        isPinching = false
        isCloseTouched = false
        isResizeButtonTouched = false
        isFullScreenButtonTouched = false
        isSearchIconTouched = false
        activeCorner = Corner.NONE
        scaleFactor = 1f
    }

    // Button touch detection
    private fun isCloseButtonTouched(x: Float, y: Float): Boolean {
        val closeIconX = width - iconSize / 2
        return getDistance(x, y, closeIconX, iconSize / 2) <= resizeHitArea
    }

    private fun isResizeButtonTouched(x: Float, y: Float): Boolean {
        if (isFullScreen) return false // Resize button not available in fullscreen

        val closeIconX = width - iconSize / 2
        val fullScreenIconX = closeIconX - iconSize - iconSpacing
        val resizeIconX = fullScreenIconX - iconSize - iconSpacing
        return getDistance(x, y, resizeIconX, iconSize / 2) <= resizeHitArea
    }

    private fun isFullScreenButtonTouched(x: Float, y: Float): Boolean {
        val closeIconX = width - iconSize / 2
        val fullScreenIconX = closeIconX - iconSize - iconSpacing
        return getDistance(x, y, fullScreenIconX, iconSize / 2) <= resizeHitArea
    }

    private fun isSearchIconTouched(x: Float, y: Float): Boolean {
        if (searchBar.visibility != View.VISIBLE) return false

        val searchIconRight = searchBar.right.toFloat()
        val searchIconLeft =
            searchIconRight - searchBar.compoundDrawablePadding - (searchIcon?.intrinsicWidth ?: 0)
        val searchIconTop =
            searchBar.top.toFloat() + (searchBar.height - (searchIcon?.intrinsicHeight ?: 0)) / 2
        val searchIconBottom = searchIconTop + (searchIcon?.intrinsicHeight ?: 0)

        // Add some padding around the icon for better touch detection
        val touchPadding = 30f // Increased padding for better touch detection

        val isInIconArea = x >= (searchIconLeft - touchPadding) &&
                x <= (searchIconRight + touchPadding) &&
                y >= (searchIconTop - touchPadding) &&
                y <= (searchIconBottom + touchPadding)

        return isInIconArea
    }

    private fun getDistance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        return sqrt((x1 - x2).pow(2) + (y1 - y2).pow(2))
    }

    // Only detect 3 corners: TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
    private fun getCornerAtPosition(x: Float, y: Float): Corner {

        val distanceBottomLeft =
            getDistance(x, y, resizeHandleSize / 2, height - resizeHandleSize / 2)
        if (distanceBottomLeft <= resizeHitArea) return Corner.BOTTOM_LEFT

        // Check BOTTOM_RIGHT corner
        val distanceBottomRight =
            getDistance(x, y, width - resizeHandleSize / 2, height - resizeHandleSize / 2)
        if (distanceBottomRight <= resizeHitArea) return Corner.BOTTOM_RIGHT

        return Corner.NONE
    }

    // Fixed updateContainerState method
    fun updateContainerState(state: EmbedContainerState) {
        val params = layoutParams as? MarginLayoutParams ?: return

        // Use state dimensions but ensure they meet minimum requirements
        val newWidth =
            if (state.width > 0) state.width.coerceIn(minWidth, screenWidth) else defaultWidth
        val newHeight =
            if (state.height > 0) state.height.coerceIn(minHeight, screenHeight) else defaultHeight

        params.width = newWidth
        params.height = newHeight
        params.leftMargin = state.leftMargin.coerceIn(0, screenWidth - newWidth)
        params.topMargin = state.topMargin.coerceIn(0, screenHeight - newHeight)

        layoutParams = params

        isResizeMode = state.isInteractMode
        isFullScreen = state.isFullScreen
        currentSearchQuery = state.searchQuery
        searchBar.setText(state.searchQuery)

        // Restore aspect ratio
        aspectRatio = state.aspectRatio

        searchBar.visibility = View.VISIBLE

        // Load URLs and restore content
        state.urls.forEach { url ->
            embedContainerManager.addUrl(url)
        }

        // Load the last URL to restore content
        state.urls.lastOrNull()?.let { url ->
            loadContent(url)
        }

        // Fix full screen state restoration
        if (isFullScreen && !state.isFullScreen) {
            // We're in full screen but shouldn't be - exit full screen
            toggleFullScreen()
        } else if (!isFullScreen && state.isFullScreen) {
            // We're not in full screen but should be - enter full screen
            // Store current state before entering full screen
            originalWidth = params.width
            originalHeight = params.height
            originalLeftMargin = params.leftMargin
            originalTopMargin = params.topMargin
            toggleFullScreen()
        }

        updateInteractionState()
        invalidate()
        requestLayout()
    }

    fun destroy() {
        // Stop all media playback
        pauseYouTubeVideo()
        webView.stopLoading()
        webView.onPause()
        webView.pauseTimers()

        youTubePlayerView.release()
        webView.destroy()
        removeAllViews()
        embedContainerManager.clearState()
    }
}