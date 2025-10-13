package com.infusory.tutarapp.ui.components.containers

import android.content.Context
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.infusory.tutarapp.R

class ContainerText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ContainerBase(context, ContainerType.TEXT, attrs, defStyleAttr) {

    private var currentText = "Sample Text Container\n\nClick edit to modify this text."
    private var currentTitle = "Topic"
    private var currentTextSize = 15f
    private var currentTextColor = Color.WHITE
    private var currentBackgroundColor = Color.TRANSPARENT

    private var titleTextView: TextView? = null
    private var currentTextView: TextView? = null
    private var editTextView: EditText? = null
    private var mainContainer: LinearLayout? = null
    private var contentContainer: FrameLayout? = null
    private var contentWrapper: LinearLayout? = null
    private var headerContainer: LinearLayout? = null
    private var topControlsContainer: LinearLayout? = null

    // Internal states
    private var isEditing = false

    // Undo/redo
    private val undoStack = mutableListOf<String>()
    private val redoStack = mutableListOf<String>()

    // Store button references
    private var editButton: ImageView? = null
    private var undoButton: ImageView? = null
    private var redoButton: ImageView? = null
    private var removeButton: ImageView? = null
    private var moveButton: ImageView? = null

    init {
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
        // Inflate the layout
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.container_text_layout, this, false)

        // Get references to views
        mainContainer = view.findViewById(R.id.main_container)
        contentWrapper = view.findViewById(R.id.content_wrapper)
        headerContainer = view.findViewById(R.id.header_container)
        topControlsContainer = view.findViewById(R.id.top_controls_container)
        contentContainer = view.findViewById(R.id.content_container)
        titleTextView = view.findViewById(R.id.text_title)
        currentTextView = view.findViewById(R.id.text_view)
        editTextView = view.findViewById(R.id.edit_text)

        // Button references
        editButton = view.findViewById(R.id.btn_edit)
        undoButton = view.findViewById(R.id.btn_undo)
        redoButton = view.findViewById(R.id.btn_redo)
        removeButton = view.findViewById(R.id.btn_remove)

        // Setup button click listeners
        setupControlButtons()

        // Setup EditText listener for dynamic resizing
        setupEditTextListener()

        // Add the inflated view
        addView(view)

        // Initialize text content
        updateTextContent()
    }

    private fun setupEditTextListener() {
        editTextView?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Preserve cursor position
                val cursorPosition = editTextView?.selectionStart ?: 0
                // Trigger layout update when text changes
                editTextView?.post {
                    // Measure the EditText with wrap_content to get its desired height
                    editTextView?.measure(
                        View.MeasureSpec.makeMeasureSpec(editTextView!!.width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                    )
                    val newHeight = editTextView?.measuredHeight ?: 0
                    if (newHeight > 0) {
                        editTextView?.layoutParams?.height = newHeight
                    }
                    // Force layout update for all containers
                    contentContainer?.invalidate()
                    contentContainer?.requestLayout()
                    contentWrapper?.invalidate()
                    contentWrapper?.requestLayout()
                    mainContainer?.invalidate()
                    mainContainer?.requestLayout()
                    this@ContainerText.invalidate()
                    this@ContainerText.requestLayout()
                    // Restore cursor position
                    if (cursorPosition >= 0 && cursorPosition <= editTextView?.text?.length ?: 0) {
                        editTextView?.setSelection(cursorPosition)
                    }
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        // Handle focus changes to ensure proper keyboard interaction
        editTextView?.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                editTextView?.post {
                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                    imm?.showSoftInput(editTextView, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                }
            }
        }
    }

    private fun setupControlButtons() {
        // Edit button - toggles between edit and view mode
        editButton?.apply {
            setOnClickListener { toggleEditMode() }
            setOnTouchListener { _, event ->
                handleButtonTouch(this, event)
                false
            }
        }

        // Undo button
        undoButton?.apply {
            setOnClickListener { undo() }
            setOnTouchListener { _, event ->
                handleButtonTouch(this, event)
                false
            }
        }

        // Redo button
        redoButton?.apply {
            setOnClickListener { redo() }
            setOnTouchListener { _, event ->
                handleButtonTouch(this, event)
                false
            }
        }

        // Remove button
        removeButton?.apply {
            setOnClickListener { showRemoveConfirmationDialog() }
            setOnTouchListener { _, event ->
                handleButtonTouch(this, event)
                false
            }
        }

        // Move button - for dragging the container
        moveButton?.apply {
            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        handleButtonTouch(this, event)
                        performLongClick()
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        handleButtonTouch(this, event)
                        true
                    }
                    else -> false
                }
            }
        }
    }

    private fun handleButtonTouch(button: ImageView, event: MotionEvent) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                button.alpha = 0.5f
                button.scaleX = 0.9f
                button.scaleY = 0.9f
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                button.alpha = 1.0f
                button.scaleX = 1.0f
                button.scaleY = 1.0f
            }
        }
    }

    private fun toggleEditMode() {
        // Clear any pending layout requests to prevent overlap
        removeCallbacks(null)
        if (isEditing) {
            saveEdit()
        } else {
            enterEditMode()
        }
    }

    private fun showRemoveConfirmationDialog() {
        removeContainer()
    }

    private fun removeContainer() {
        (parent as? ViewGroup)?.removeView(this)
        Toast.makeText(context, "Container removed", Toast.LENGTH_SHORT).show()
    }

    private fun enterEditMode() {
        if (isEditing) return
        isEditing = true

        // Ensure TextView is fully hidden
        currentTextView?.visibility = View.GONE
        currentTextView?.invalidate()

        editTextView?.setText(currentText)

        // Make sure EditText wraps content properly and matches TextView styling
        editTextView?.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(8, 8, 8, 8)
        }
        editTextView?.setPadding(8, 8, 8, 8)
        editTextView?.setLineSpacing(0f, 1.3f)
        editTextView?.textSize = currentTextSize
        editTextView?.setTextColor(currentTextColor)
        editTextView?.visibility = View.VISIBLE

        // Request focus and show keyboard
        editTextView?.post {
            editTextView?.requestFocus()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
            imm?.showSoftInput(editTextView, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            // Measure EditText to ensure it has the correct height
            editTextView?.measure(
                View.MeasureSpec.makeMeasureSpec(editTextView!!.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            val newHeight = editTextView?.measuredHeight ?: 0
            if (newHeight > 0) {
                editTextView?.layoutParams?.height = newHeight
            }
            // Force full layout update
            contentContainer?.invalidate()
            contentContainer?.requestLayout()
            contentWrapper?.invalidate()
            contentWrapper?.requestLayout()
            mainContainer?.invalidate()
            mainContainer?.requestLayout()
            this@ContainerText.invalidate()
            this@ContainerText.requestLayout()
        }

        // Change edit button icon to save icon
        editButton?.setImageResource(android.R.drawable.ic_menu_save)

        Toast.makeText(context, "Edit mode", Toast.LENGTH_SHORT).show()
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

    private fun undo() {
        if (undoStack.isNotEmpty()) {
            redoStack.add(currentText)
            currentText = undoStack.removeLast()
            updateTextContent()

            // If in edit mode, update the edit text too
            if (isEditing) {
                editTextView?.setText(currentText)
                editTextView?.setSelection(currentText.length) // Move cursor to end
            }

            Toast.makeText(context, "Undo applied", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Nothing to undo", Toast.LENGTH_SHORT).show()
        }
    }

    private fun redo() {
        if (redoStack.isNotEmpty()) {
            undoStack.add(currentText)
            currentText = redoStack.removeLast()
            updateTextContent()

            // If in edit mode, update the edit text too
            if (isEditing) {
                editTextView?.setText(currentText)
                editTextView?.setSelection(currentText.length) // Move cursor to end
            }

            Toast.makeText(context, "Redo applied", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Nothing to redo", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exitEditMode() {
        isEditing = false
        editTextView?.visibility = View.GONE
        editTextView?.invalidate()
        currentTextView?.visibility = View.VISIBLE

        // Hide keyboard
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
        imm?.hideSoftInputFromWindow(editTextView?.windowToken, 0)

        // Change save button icon back to edit icon
        editButton?.setImageResource(android.R.drawable.ic_menu_edit)

        // Force full layout update
        post {
            contentContainer?.invalidate()
            contentContainer?.requestLayout()
            contentWrapper?.invalidate()
            contentWrapper?.requestLayout()
            mainContainer?.invalidate()
            mainContainer?.requestLayout()
            this@ContainerText.invalidate()
            this@ContainerText.requestLayout()
        }
    }

    private fun updateTextContent() {
        currentTextView?.apply {
            text = currentText
            textSize = currentTextSize
            setTextColor(currentTextColor)
            setBackgroundColor(currentBackgroundColor)

            // Ensure consistent styling with EditText
            setPadding(8, 8, 8, 8)
            setLineSpacing(0f, 1.3f)

            // Make sure TextView wraps content properly
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(8, 8, 8, 8)
            }
        }

        titleTextView?.text = currentTitle

        // Ensure container maintains wrap_content height
        ensureContainerDynamicHeight()

        // Force remeasurement and layout
        post {
            currentTextView?.measure(
                View.MeasureSpec.makeMeasureSpec(currentTextView!!.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            val newHeight = currentTextView?.measuredHeight ?: 0
            if (newHeight > 0) {
                currentTextView?.layoutParams?.height = newHeight
            }
            contentContainer?.invalidate()
            contentContainer?.requestLayout()
            contentWrapper?.invalidate()
            contentWrapper?.requestLayout()
            mainContainer?.invalidate()
            mainContainer?.requestLayout()
            this@ContainerText.invalidate()
            this@ContainerText.requestLayout()
        }
    }

    private fun ensureContainerDynamicHeight() {
        // Ensure all container layouts use WRAP_CONTENT height
        contentContainer?.layoutParams = contentContainer?.layoutParams?.apply {
            height = FrameLayout.LayoutParams.WRAP_CONTENT
            if (this is FrameLayout.LayoutParams) {
                setMargins(0, 0, 0, 0)
            }
        } ?: FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)

        contentWrapper?.layoutParams = contentWrapper?.layoutParams?.apply {
            height = LinearLayout.LayoutParams.WRAP_CONTENT
            if (this is LinearLayout.LayoutParams) {
                setMargins(0, 0, 0, 0)
            }
        } ?: LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

        mainContainer?.layoutParams = mainContainer?.layoutParams?.apply {
            height = LinearLayout.LayoutParams.WRAP_CONTENT
            if (this is LinearLayout.LayoutParams) {
                setMargins(0, 0, 0, 0)
            }
        } ?: LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)

        // Ensure the root container maintains WRAP_CONTENT
        val rootParams = layoutParams ?: FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        if (rootParams.height != ViewGroup.LayoutParams.WRAP_CONTENT) {
            rootParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
            layoutParams = rootParams
        }

        // Force remeasurement of the entire view hierarchy
        post {
            measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            requestLayout()
        }
    }

    override fun getCustomSaveData(): Map<String, Any> {
        val baseData = super.getCustomSaveData().toMutableMap()
        baseData.putAll(
            mapOf(
                "text" to currentText,
                "title" to currentTitle,
                "textSize" to currentTextSize,
                "textColor" to currentTextColor,
                "backgroundColor" to currentBackgroundColor,
                "undoStack" to undoStack.toList(),
                "redoStack" to redoStack.toList()
            )
        )
        return baseData
    }

    override fun loadCustomSaveData(data: Map<String, Any>) {
        super.loadCustomSaveData(data)
        (data["text"] as? String)?.let { currentText = it }
        (data["title"] as? String)?.let { currentTitle = it }
        (data["textSize"] as? Double)?.let { currentTextSize = it.toFloat() }
        (data["textColor"] as? Int)?.let { currentTextColor = it }
        (data["backgroundColor"] as? Int)?.let { currentBackgroundColor = it }

        @Suppress("UNCHECKED_CAST")
        (data["undoStack"] as? List<String>)?.let {
            undoStack.clear()
            undoStack.addAll(it)
        }

        @Suppress("UNCHECKED_CAST")
        (data["redoStack"] as? List<String>)?.let {
            redoStack.clear()
            redoStack.addAll(it)
        }

        updateTextContent()
    }

    // Public API methods
    fun setText(text: String) {
        currentText = text
        updateTextContent()
    }

    fun getText(): String = currentText

    fun setTitle(title: String) {
        currentTitle = title
        updateTextContent()
    }

    fun getTitle(): String = currentTitle

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
}