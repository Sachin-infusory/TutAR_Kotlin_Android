package com.infusory.tutarapp.ui.components.containers

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.infusory.tutarapp.R

class ContainerText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ContainerBase(context, ContainerType.TEXT, attrs, defStyleAttr) {

    private var currentText = "Sample Text Container\n\nClick edit to modify this text."
    private var currentTextSize = 14f
    private var currentTextColor = Color.BLACK
    private var currentBackgroundColor = Color.TRANSPARENT

    private var currentTextView: TextView? = null
    private var editTextView: EditText? = null
    private var mainContainer: LinearLayout? = null
    private var contentContainer: FrameLayout? = null
    private var controlsContainer: LinearLayout? = null

    // Internal states
    private var isEditing = false

    // Undo/redo
    private val undoStack = mutableListOf<String>()
    private val redoStack = mutableListOf<String>()

    // Store button references
    private val sideControlButtons = mutableListOf<ImageView>()
    private var editButton: ImageView? = null
    private var undoButton: ImageView? = null
    private var redoButton: ImageView? = null
    private var removeButton: ImageView? = null
    private var saveButton: ImageView? = null
    private var cancelButton: ImageView? = null

    init {
        // Disable background for main container since content container will have it
        showBackground = false

        setupTextContainer()
        setPadding(4, 4, 4, 4)
        clipChildren = false
        clipToPadding = false
        android.util.Log.d("ContainerText", "ContainerText initialized")
    }

    private fun setupTextContainer() {
        initializeContent()
    }

    override fun initializeContent() {
        createMainContainer()
        createTextView()
        setupControlButtons()
    }

    private fun createMainContainer() {
        mainContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        controlsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(48),
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
            gravity = Gravity.TOP
        }

        contentContainer = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            setBackgroundResource(R.drawable.dotted_border_background)
            setPadding(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2))
        }

        mainContainer?.let { container ->
            if (container is LinearLayout) {
                container.addView(controlsContainer)
                container.addView(contentContainer)
            }
        }

        addView(mainContainer)
    }

    private fun setupControlButtons() {
        // Edit button
//        editButton = createSideControlButton(android.R.drawable.ic_menu_edit, "Edit") { enterEditMode() }
//        controlsContainer?.addView(editButton)
//        sideControlButtons.add(editButton!!)
//        controlsContainer?.addView(createSpacer())
//
//        // Undo button
//        undoButton = createSideControlButton(android.R.drawable.ic_menu_revert, "Undo") { undo() }
//        controlsContainer?.addView(undoButton)
//        sideControlButtons.add(undoButton!!)
//        controlsContainer?.addView(createSpacer())
//
//        // Redo button
//        redoButton = createSideControlButton(android.R.drawable.ic_dialog_dialer, "Redo") { redo() }
//        controlsContainer?.addView(redoButton)
//        sideControlButtons.add(redoButton!!)
//        controlsContainer?.addView(createSpacer())

        // Remove button
        removeButton = createSideControlButton(
            android.R.drawable.ic_menu_close_clear_cancel,
            "Remove"
        ) { showRemoveConfirmationDialog() }
        controlsContainer?.addView(removeButton)
        sideControlButtons.add(removeButton!!)
        controlsContainer?.addView(createSpacer())

        // Save button
//        saveButton = createSideControlButton(android.R.drawable.ic_menu_save, "Save") { saveEdit() }
//        controlsContainer?.addView(saveButton)
//        sideControlButtons.add(saveButton!!)
//        saveButton?.visibility = View.GONE
//        controlsContainer?.addView(createSpacer())
//
//        // Cancel button
//        cancelButton = createSideControlButton(android.R.drawable.ic_menu_close_clear_cancel, "Cancel") { cancelEdit() }
//        controlsContainer?.addView(cancelButton)
//        sideControlButtons.add(cancelButton!!)
//        cancelButton?.visibility = View.GONE
//        controlsContainer?.addView(createSpacer())
    }

    private fun createSideControlButton(
        iconRes: Int,
        tooltip: String,
        onClick: () -> Unit
    ): ImageView {
        return ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(32), dpToPx(32)).apply {
                setMargins(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2))
            }
            setImageResource(iconRes)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke(0, Color.TRANSPARENT)
            }
            scaleType = ImageView.ScaleType.CENTER
            elevation = 4f
            alpha = 1.0f
            contentDescription = tooltip

            setOnClickListener { onClick() }

            setOnTouchListener { _, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        alpha = 0.6f
                        scaleX = 0.95f
                        scaleY = 0.95f
                    }

                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                        alpha = 1.0f
                        scaleX = 1.0f
                        scaleY = 1.0f
                    }
                }
                false
            }

            android.util.Log.d("ContainerText", "Button created: $tooltip")
        }
    }

    private fun createSpacer(): View {
        return View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(2)
            )
        }
    }

    private fun updateButtonVisibility() {
        if (isEditing) {
            // Hide default buttons
            editButton?.visibility = View.GONE
            undoButton?.visibility = View.GONE
            redoButton?.visibility = View.GONE
            removeButton?.visibility = View.GONE
            // Show edit mode buttons
            saveButton?.visibility = View.VISIBLE
            cancelButton?.visibility = View.VISIBLE
        } else {
            // Show default buttons
            editButton?.visibility = View.VISIBLE
            undoButton?.visibility = View.VISIBLE
            redoButton?.visibility = View.VISIBLE
            removeButton?.visibility = View.VISIBLE
            // Hide edit mode buttons
            saveButton?.visibility = View.GONE
            cancelButton?.visibility = View.GONE
        }

        // Update spacer visibility based on adjacent buttons
        controlsContainer?.let { container ->
            for (i in 0 until container.childCount) {
                val child = container.getChildAt(i)
                if (child is View && child !is ImageView) {
                    val prevButton = if (i > 0) container.getChildAt(i - 1) else null
                    child.visibility =
                        if (prevButton?.visibility == View.VISIBLE) View.VISIBLE else View.GONE
                }
            }
        }

        // Request layout updates
        controlsContainer?.requestLayout()
        mainContainer?.requestLayout()
        contentContainer?.requestLayout()
        requestLayout()
    }

    private fun showRemoveConfirmationDialog() {
        removeContainer()
    }

    private fun removeContainer() {
        (parent as? android.view.ViewGroup)?.removeView(this)
        Toast.makeText(context, "Container removed", Toast.LENGTH_SHORT).show()
    }

    private fun createTextView() {
        currentTextView = TextView(context).apply {
            text = currentText
            textSize = currentTextSize
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            setTextColor(currentTextColor)
            setBackgroundColor(currentBackgroundColor)
            gravity = Gravity.START or Gravity.TOP
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            visibility = View.VISIBLE
        }

        contentContainer?.let { container ->
            container.removeAllViews()
            container.addView(currentTextView)
        }

        post {
            requestLayout()
        }

        android.util.Log.d("ContainerText", "TextView added, child count: $childCount")
    }

    private fun enterEditMode() {
        if (isEditing) return
        isEditing = true

        editTextView = EditText(context).apply {
            setText(currentText)
            textSize = currentTextSize
            setTextColor(currentTextColor)
            setBackgroundColor(currentBackgroundColor)
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        contentContainer?.removeAllViews()
        contentContainer?.addView(editTextView)

        updateButtonVisibility()
    }

    private fun saveEdit() {
        if (!isEditing || editTextView == null) return

        val newText = editTextView!!.text.toString()
        if (newText != currentText) {
            undoStack.add(currentText)
            redoStack.clear()
            currentText = newText
        }

        updateTextContent()
        Toast.makeText(context, "Changes saved", Toast.LENGTH_SHORT).show()
        exitEditMode()
    }

    private fun cancelEdit() {
        if (!isEditing) return
        Toast.makeText(context, "Edit cancelled", Toast.LENGTH_SHORT).show()
        exitEditMode()
    }

    private fun undo() {
        if (undoStack.isNotEmpty()) {
            redoStack.add(currentText)
            currentText = undoStack.removeLast()
            updateTextContent()
            Toast.makeText(context, "Undo applied", Toast.LENGTH_SHORT).show()
        }
    }

    private fun redo() {
        if (redoStack.isNotEmpty()) {
            undoStack.add(currentText)
            currentText = redoStack.removeLast()
            updateTextContent()
            Toast.makeText(context, "Redo applied", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exitEditMode() {
        isEditing = false
        contentContainer?.removeAllViews()
        contentContainer?.addView(currentTextView)

        updateButtonVisibility()
    }

    private fun updateTextContent() {
        currentTextView?.apply {
            text = currentText
            textSize = currentTextSize
            setTextColor(currentTextColor)
            setBackgroundColor(currentBackgroundColor)
        }
        currentTextView?.requestLayout()
        contentContainer?.requestLayout()
        mainContainer?.requestLayout()
        requestLayout()
    }

    override fun getCustomSaveData(): Map<String, Any> {
        val baseData = super.getCustomSaveData().toMutableMap()
        baseData.putAll(
            mapOf(
                "text" to currentText,
                "textSize" to currentTextSize,
                "textColor" to currentTextColor,
                "backgroundColor" to currentBackgroundColor
            )
        )
        return baseData
    }

    override fun loadCustomSaveData(data: Map<String, Any>) {
        super.loadCustomSaveData(data)
        (data["text"] as? String)?.let { currentText = it }
        (data["textSize"] as? Double)?.let { currentTextSize = it.toFloat() }
        (data["textColor"] as? Int)?.let { currentTextColor = it }
        (data["backgroundColor"] as? Int)?.let { currentBackgroundColor = it }
        updateTextContent()
    }

    fun setText(text: String) {
        currentText = text
        updateTextContent()
    }

    fun getText(): String = currentText

    fun setTextSize(size: Float) {
        currentTextSize = size
        updateTextContent()
    }

    fun setTextColor(color: Int) {
        currentTextColor = color
        updateTextContent()
    }

    fun setTextBackgroundColor(color: Int) {
        currentBackgroundColor = color
        updateTextContent()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (changed) {
            post {
                requestLayout()
            }
        }
    }

    override fun addView(child: View?, index: Int) {
        super.addView(child, index)
        post {
            requestLayout()
        }
    }
}