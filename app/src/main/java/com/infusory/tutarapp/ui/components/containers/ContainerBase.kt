// ContainerBase.kt
package com.infusory.tutarapp.ui.components.containers

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import com.infusory.tutarapp.ui.components.containers.UnifiedDraggableZoomableContainer
import com.infusory.tutarapp.ui.components.containers.ControlButton
import com.infusory.tutarapp.ui.components.containers.ButtonPosition

open class ContainerBase @JvmOverloads constructor(
    context: Context,
    val containerType: ContainerType,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : UnifiedDraggableZoomableContainer(context, attrs, defStyleAttr) {

    enum class ContainerType {
        TEXT,
        IMAGE,
        MODEL_3D,
        PDF,
        YOUTUBE,
        WEBSITE
    }

    // Callback for when container requests to be removed
    var onRemoveRequest: (() -> Unit)? = null

    init {
        setupContainerByType()
    }

    private fun setupContainerByType() {
        when (containerType) {
            else -> {
                setupStandardContainer()
            }
        }
    }

    private fun setupStandardContainer() {
        val buttons = listOf(
            ControlButton(
                iconRes = android.R.drawable.ic_menu_close_clear_cancel,
                onClick = { onRemoveRequest?.invoke() },
                position = ButtonPosition.TOP_START
            ),
            ControlButton(
                iconRes = android.R.drawable.ic_menu_more,
                onClick = { showStandardMenu() },
                position = ButtonPosition.TOP_CENTER
            ),
            ControlButton(
                iconRes = android.R.drawable.ic_menu_zoom,
                onClick = { toggleSize() },
                position = ButtonPosition.TOP_END
            )
        )
//        addControlButtons(buttons)
    }

    private fun setupMinimalContainer() {
        showBackground = false
        // No buttons for minimal container
    }

    private fun setupReadOnlyContainer() {
        isResizingEnabled = false

        val closeButton = ControlButton(
            iconRes = android.R.drawable.ic_menu_close_clear_cancel,
            onClick = { onRemoveRequest?.invoke() },
            position = ButtonPosition.TOP_END
        )
        addControlButton(closeButton)
    }

    protected open fun showStandardMenu() {
        // Override in subclasses for custom menus
        android.widget.Toast.makeText(context, "Standard container menu", android.widget.Toast.LENGTH_SHORT).show()
    }

    protected fun toggleSize() {
        val currentScale = getCurrentScale()
        val newScale = if (currentScale > 1.5f) 1.0f else 2.0f
        zoomTo(newScale, animate = true)

        android.widget.Toast.makeText(
            context,
            "Container ${if (newScale > 1.5f) "expanded" else "normal"}",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    open fun initializeContent() {
        val content = createDefaultContent()
        setContent(content)
    }

    protected open fun createDefaultContent(): android.view.View {
        return TextView(context).apply {
            text = getDefaultText()
            textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
            textSize = 14f
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
            setTextColor(android.graphics.Color.GRAY)
            gravity = android.view.Gravity.CENTER
        }
    }

    protected open fun getDefaultText(): String {
        return when (containerType) {
            else -> "Container\nInteractive content"
        }
    }

    open fun getDefaultWidth(): Int = dpToPx(300)
    open fun getDefaultHeight(): Int = dpToPx(300)

    // Save/Load methods for container-specific data
    open fun getCustomSaveData(): Map<String, Any> {
        return mapOf(
            "containerType" to containerType.name,
            "isDraggingEnabled" to isDraggingEnabled,
            "isResizingEnabled" to isResizingEnabled,
            "showBackground" to showBackground
        )
    }

    open fun loadCustomSaveData(data: Map<String, Any>) {
        data["isDraggingEnabled"]?.let {
            if (it is Boolean) isDraggingEnabled = it
        }
        data["isResizingEnabled"]?.let {
            if (it is Boolean) isResizingEnabled = it
        }
        data["showBackground"]?.let {
            if (it is Boolean) showBackground = it
        }
    }

//    protected fun dpToPx(dp: Int): Int {
//        return (dp * context.resources.displayMetrics.density).toInt()
//    }
}