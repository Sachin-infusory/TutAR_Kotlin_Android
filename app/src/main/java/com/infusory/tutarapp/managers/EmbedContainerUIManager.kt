package com.infusory.tutarapp.managers

import android.content.Context
import android.view.ViewGroup
import android.widget.RelativeLayout
import com.infusory.tutarapp.managers.EmbeddingDraggableResizableContainer

class EmbedContainerUIManager(
    private val context: Context,
    private val parentLayout: RelativeLayout
) {
    private val embedContainers = mutableListOf<EmbeddingDraggableResizableContainer>()

    var onContainerRemoved: ((EmbeddingDraggableResizableContainer) -> Unit)? = null

    fun addYouTubeContainer(): EmbeddingDraggableResizableContainer {
        val containerState = EmbedContainerManager.createNewEmbedContainerState(context, "youtube")
        val container = EmbeddingDraggableResizableContainer(context)

        val params = RelativeLayout.LayoutParams(containerState.width, containerState.height)
        params.leftMargin = containerState.leftMargin
        params.topMargin = containerState.topMargin
        container.layoutParams = params

        container.onCloseClickListener = {
            removeEmbedContainer(container)
            EmbedContainerManager.removeEmbedContainerState(context, containerState.containerId)
        }

        embedContainers.add(container)
        parentLayout.addView(container)

        // Save the container state
        EmbedContainerManager.addEmbedContainerState(context, containerState)

        return container
    }

    fun addYouTubeContainerWithUrlAndSearch(
        youtubeUrl: String,
        searchQuery: String = ""
    ): EmbeddingDraggableResizableContainer {
        val container = addYouTubeContainer()

        // Extract video ID if it's a full URL
        val videoId = extractYouTubeVideoId(youtubeUrl)
        val finalUrl = if (videoId != null) {
            "https://www.youtube.com/watch?v=$videoId"
        } else {
            youtubeUrl // Use as-is if it's already an ID or direct URL
        }

        // Perform search with the URL
        container.performSearchForUrl(finalUrl, searchQuery.ifEmpty { "YouTube Video" })

        return container
    }

    private fun extractYouTubeVideoId(url: String): String? {
        val patterns = arrayOf(
            "youtube\\.com/watch\\?v=([^&]+)",
            "youtu\\.be/([^?]+)",
            "youtube\\.com/embed/([^?]+)",
            "youtube\\.com/shorts/([^?]+)"
        )

        for (pattern in patterns) {
            val regex = Regex(pattern)
            val match = regex.find(url)
            if (match != null) {
                return match.groupValues[1]
            }
        }
        return null
    }

    fun addWebsiteContainer(): EmbeddingDraggableResizableContainer {
        val containerState = EmbedContainerManager.createNewEmbedContainerState(context, "website")
        val container = EmbeddingDraggableResizableContainer(context)

        val params = RelativeLayout.LayoutParams(containerState.width, containerState.height)
        params.leftMargin = containerState.leftMargin
        params.topMargin = containerState.topMargin
        container.layoutParams = params

        container.onCloseClickListener = {
            removeEmbedContainer(container)
            EmbedContainerManager.removeEmbedContainerState(context, containerState.containerId)
        }

        embedContainers.add(container)
        parentLayout.addView(container)

        // Save the container state
        EmbedContainerManager.addEmbedContainerState(context, containerState)

        return container
    }

    fun addYouTubeContainerWithUrl(youtubeUrl: String): EmbeddingDraggableResizableContainer {
        val container = addYouTubeContainer()
        container.performSearchForUrl(youtubeUrl, youtubeUrl)
        return container
    }

    fun removeEmbedContainer(container: EmbeddingDraggableResizableContainer) {
        val state = container.getCurrentState()
        EmbedContainerManager.removeEmbedContainerState(context, state.containerId)

        embedContainers.remove(container)
        parentLayout.removeView(container)
        onContainerRemoved?.invoke(container)
    }

    fun getEmbedContainerCount(): Int {
        return embedContainers.size
    }

    fun getAllEmbedContainers(): List<EmbeddingDraggableResizableContainer> {
        return embedContainers.toList()
    }

    // we can use this when we need to clear a page
    fun clearAllEmbedContainers() {
        embedContainers.forEach { container ->
            val state = container.getCurrentState()
            EmbedContainerManager.removeEmbedContainerState(context, state.containerId)
            parentLayout.removeView(container)
        }
        embedContainers.clear()
    }
}