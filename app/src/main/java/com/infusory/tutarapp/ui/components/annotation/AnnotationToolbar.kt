// AnnotationToolbar.kt - Modern Polished Toolbar UI
package com.infusory.tutarapp.ui.components.annotation

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.core.content.ContextCompat
import com.infusory.tutarapp.R

// Enum for annotation tools
enum class AnnotationTool {
    FREE_DRAW,
    LINE,
    RECTANGLE,
    CIRCLE,
    ARROW,
    ERASER,
    SELECTION
}

// Modern AnnotationToolbar with polished design
class AnnotationToolbar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    // Tool buttons - now LinearLayout containers with labels
    private lateinit var freeDrawButton: LinearLayout
    private lateinit var lineButton: LinearLayout
    private lateinit var rectangleButton: LinearLayout
    private lateinit var circleButton: LinearLayout
    private lateinit var arrowButton: LinearLayout
    private lateinit var eraserButton: LinearLayout
    private lateinit var selectionButton: LinearLayout
    private lateinit var undoButton: LinearLayout
    private lateinit var redoButton: LinearLayout
    private lateinit var clearButton: LinearLayout
    private lateinit var expandButton: LinearLayout

    // Expanded section
    private lateinit var expandedSection: LinearLayout
    private var isExpanded = false

    // Color picker dialog
    private var colorPickerDialog: android.app.AlertDialog? = null

    // Stroke width components
    private lateinit var strokeWidthSeekBar: SeekBar
    private lateinit var strokeWidthText: TextView
    private lateinit var selectedColorPreview: View

    // Currently selected tool and settings
    private var selectedTool = AnnotationTool.FREE_DRAW
    private var currentColor = Color.RED
    private var currentStrokeWidth = 5f

    // Callbacks
    var onToolSelected: ((AnnotationTool) -> Unit)? = null
    var onUndoPressed: (() -> Unit)? = null
    var onRedoPressed: (() -> Unit)? = null
    var onClearPressed: (() -> Unit)? = null
    var onCloseAnnotation: (() -> Unit)? = null
    var onColorChanged: ((Int) -> Unit)? = null
    var onStrokeWidthChanged: ((Float) -> Unit)? = null

    init {
        setupToolbar()
    }

    private fun setupToolbar() {
        orientation = HORIZONTAL
        elevation = dpToPx(6).toFloat()

        // Modern toolbar background with subtle gradient
        background = createModernToolbarBackground()

        // Refined padding - smaller
        val padding = dpToPx(8)
        setPadding(padding, padding, padding, padding)

        // Create tool buttons
        createToolButtons()

        // Create expanded section
        createExpandedSection()

        // Set initial selection
        selectTool(AnnotationTool.FREE_DRAW)
    }

    private fun createModernToolbarBackground(): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = dpToPx(16).toFloat()
            // Modern dark background with slight transparency
            colors = intArrayOf(
                Color.parseColor("#E62C2C2C"),
                Color.parseColor("#E6242424")
            )
            gradientType = GradientDrawable.LINEAR_GRADIENT
            orientation = GradientDrawable.Orientation.TOP_BOTTOM
            // Subtle border
            setStroke(dpToPx(1), Color.parseColor("#40FFFFFF"))
        }
    }

    private fun createToolButtons() {
        // Drawing Tools Group
        freeDrawButton = createLabeledToolButton(R.drawable.ic_write_tool, "Draw", "Draw") {
            selectTool(AnnotationTool.FREE_DRAW)
        }
        addView(freeDrawButton)
        addView(createMiniSpacer())

        lineButton = createLabeledToolButton(R.drawable.ic_line_tool, "Line", "Line") {
            selectTool(AnnotationTool.LINE)
        }
        addView(lineButton)
        addView(createMiniSpacer())

        rectangleButton = createLabeledToolButton(R.drawable.ic_square_tool, "Rectangle", "Rect") {
            selectTool(AnnotationTool.RECTANGLE)
        }
        addView(rectangleButton)
        addView(createMiniSpacer())

        circleButton = createLabeledToolButton(R.drawable.ic_circle_tool, "Circle", "Circle") {
            selectTool(AnnotationTool.CIRCLE)
        }
        addView(circleButton)
        addView(createMiniSpacer())

        arrowButton = createLabeledToolButton(R.drawable.ic_arrow_tool, "Arrow", "Arrow") {
            selectTool(AnnotationTool.ARROW)
        }
        addView(arrowButton)

        addView(createModernSeparator())

        // Edit Tools Group
        eraserButton = createLabeledToolButton(R.drawable.ic_eraser_tool, "Eraser", "Erase") {
            selectTool(AnnotationTool.ERASER)
        }
        addView(eraserButton)
        addView(createMiniSpacer())

        selectionButton = createLabeledToolButton(R.drawable.ic_select_tool, "Select", "Select") {
            selectTool(AnnotationTool.SELECTION)
        }
        addView(selectionButton)

        addView(createModernSeparator())

        // Action Tools Group
        undoButton = createLabeledToolButton(R.drawable.ic_undo_tool, "Undo", "Undo") {
            onUndoPressed?.invoke()
        }
        addView(undoButton)
        addView(createMiniSpacer())

        redoButton = createLabeledToolButton(R.drawable.ic_redo_tool, "Redo", "Redo") {
            onRedoPressed?.invoke()
        }
        addView(redoButton)
        addView(createMiniSpacer())

        clearButton = createLabeledToolButton(R.drawable.ic_clear_tool, "Clear", "Clear") {
            onClearPressed?.invoke()
        }
        addView(clearButton)

        addView(createModernSeparator())

        // Expand Button
        expandButton = createLabeledToolButton(android.R.drawable.ic_media_ff, "Options", "More") {
            toggleExpandedSection()
        }
        addView(expandButton)
    }

    private fun createExpandedSection() {
        expandedSection = LinearLayout(context).apply {
            orientation = HORIZONTAL
            visibility = View.GONE
            setPadding(dpToPx(8), 0, 0, 0)
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        // Elegant separator
        expandedSection.addView(createModernSeparator())

        // Stroke Width Section
        val strokeSection = createStrokeWidthSection()
        expandedSection.addView(strokeSection)

        // Spacer
        expandedSection.addView(View(context).apply {
            layoutParams = LayoutParams(dpToPx(20), LayoutParams.MATCH_PARENT)
        })

        // Color Section
        val colorSection = createColorSection()
        expandedSection.addView(colorSection)

        addView(expandedSection)
    }

    private fun createStrokeWidthSection(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL

            // Icon/Label
            addView(TextView(context).apply {
                text = "●"
                textSize = 16f
                setTextColor(Color.parseColor("#CCFFFFFF"))
                layoutParams = LayoutParams(
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT
                ).apply {
                    rightMargin = dpToPx(8)
                }
            })

            // Value display with modern styling
            strokeWidthText = TextView(context).apply {
                text = "5"
                textSize = 13f
                setTextColor(Color.WHITE)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                layoutParams = LayoutParams(dpToPx(28), LayoutParams.WRAP_CONTENT).apply {
                    rightMargin = dpToPx(8)
                }
                gravity = android.view.Gravity.CENTER
                background = GradientDrawable().apply {
                    cornerRadius = dpToPx(6).toFloat()
                    setColor(Color.parseColor("#33FFFFFF"))
                }
                setPadding(dpToPx(6), dpToPx(4), dpToPx(6), dpToPx(4))
            }
            addView(strokeWidthText)

            // Modern seekbar
            strokeWidthSeekBar = SeekBar(context).apply {
                max = 50
                progress = 5
                layoutParams = LayoutParams(dpToPx(140), LayoutParams.WRAP_CONTENT)
                progressDrawable = createModernSeekBarDrawable()
                thumb = createModernThumb()
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        val width = progress.coerceAtLeast(1).toFloat()
                        currentStrokeWidth = width
                        strokeWidthText.text = width.toInt().toString()
                        onStrokeWidthChanged?.invoke(width)
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
            }
            addView(strokeWidthSeekBar)
        }
    }

    private fun createColorSection(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL

            // Label
            addView(TextView(context).apply {
                text = "🎨"
                textSize = 16f
                layoutParams = LayoutParams(
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT
                ).apply {
                    rightMargin = dpToPx(8)
                }
            })

            // Modern color preview that is also clickable
            selectedColorPreview = View(context).apply {
                layoutParams = LayoutParams(dpToPx(36), dpToPx(36))
                background = createColorPreviewBackground(currentColor)
                elevation = dpToPx(2).toFloat()
                isClickable = true
                isFocusable = true

                setOnClickListener {
                    // Haptic feedback
                    performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)

                    // Button press animation
                    animate()
                        .scaleX(0.9f)
                        .scaleY(0.9f)
                        .setDuration(100)
                        .withEndAction {
                            animate()
                                .scaleX(1.0f)
                                .scaleY(1.0f)
                                .setDuration(100)
                                .start()
                        }
                        .start()

                    showColorPickerDialog()
                }
            }
            addView(selectedColorPreview)
        }
    }

    private fun createColorPreviewBackground(color: Int): LayerDrawable {
        val colorCircle = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
        val border = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setStroke(dpToPx(3), Color.WHITE)
        }
        val shadow = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#33000000"))
        }

        return LayerDrawable(arrayOf(shadow, colorCircle, border)).apply {
            setLayerInset(0, 0, dpToPx(2), 0, 0)
            setLayerInset(1, dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2))
            setLayerInset(2, dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2))
        }
    }

    private fun createModernSeekBarDrawable(): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = dpToPx(4).toFloat()
            setColor(Color.parseColor("#4DFFFFFF"))
            setSize(0, dpToPx(6))
        }
    }

    private fun createModernThumb(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.WHITE)
            setSize(dpToPx(20), dpToPx(20))
            setStroke(dpToPx(2), Color.parseColor("#4D000000"))
        }
    }

    private fun showColorPickerDialog() {
        val dialogView = LayoutInflater.from(context).inflate(
            R.layout.dialog_custom_color_picker,
            null
        )

        val colorPalette = dialogView.findViewById<com.infusory.tutarapp.ui.components.ColorPaletteView>(R.id.colorPalette)
        val hueSlider = dialogView.findViewById<com.infusory.tutarapp.ui.components.HueSliderView>(R.id.hueSlider)
        val colorPreview = dialogView.findViewById<View>(R.id.selectedColorPreview)
        val hexColorText = dialogView.findViewById<TextView>(R.id.hexColorText)

        var currentSelectedColor = currentColor

        hueSlider.onHueSelected = { hue ->
            colorPalette.setHue(hue)
            currentSelectedColor = colorPalette.getCurrentColor()
            updateColorDisplayInDialog(currentSelectedColor, colorPreview, hexColorText)
        }

        colorPalette.onColorSelected = { color ->
            currentSelectedColor = color
            updateColorDisplayInDialog(currentSelectedColor, colorPreview, hexColorText)
        }

        updateColorDisplayInDialog(currentSelectedColor, colorPreview, hexColorText)

        colorPickerDialog = android.app.AlertDialog.Builder(context, android.R.style.Theme_Material_Dialog)
            .setView(dialogView)
            .setPositiveButton("Apply") { _, _ ->
                updateColorDisplay(currentSelectedColor)
            }
            .setNegativeButton("Cancel", null)
            .create()

        colorPickerDialog?.show()
    }

    private fun updateColorDisplayInDialog(color: Int, colorPreview: View, hexText: TextView) {
        colorPreview.setBackgroundColor(color)
        hexText.text = String.format("#%02X%02X%02X",
            Color.red(color),
            Color.green(color),
            Color.blue(color)
        )
    }

    private fun updateColorDisplay(color: Int) {
        currentColor = color
        selectedColorPreview.background = createColorPreviewBackground(color)
        onColorChanged?.invoke(color)
    }

    private fun toggleExpandedSection() {
        isExpanded = !isExpanded
        expandedSection.visibility = if (isExpanded) View.VISIBLE else View.GONE

//        expandButton.setImageResource(
//            if (isExpanded) android.R.drawable.ic_media_rew
//            else android.R.drawable.ic_media_ff
//        )

        // Animate the button
        expandButton.animate()
            .scaleX(if (isExpanded) 0.9f else 1.0f)
            .scaleY(if (isExpanded) 0.9f else 1.0f)
            .setDuration(150)
            .start()
    }

    private fun createLabeledToolButton(
        iconRes: Int,
        contentDescription: String,
        label: String,
        onClick: () -> Unit
    ): LinearLayout {
        return LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = android.view.Gravity.CENTER
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginStart = dpToPx(2)
                marginEnd = dpToPx(2)
            }

            // Button with icon
            val button = ImageButton(context).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(32), dpToPx(32))
                background = createModernButtonBackground(false)
                setImageResource(iconRes)
                setColorFilter(Color.parseColor("#666666"))
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6))
                this.contentDescription = contentDescription
                elevation = dpToPx(2).toFloat()

                setOnClickListener {
                    performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)

                    animate()
                        .scaleX(0.9f)
                        .scaleY(0.9f)
                        .setDuration(100)
                        .withEndAction {
                            animate()
                                .scaleX(1.0f)
                                .scaleY(1.0f)
                                .setDuration(100)
                                .start()
                        }
                        .start()

                    onClick()
                }
            }
            addView(button)

            // Label text
            val labelText = TextView(context).apply {
                text = label
                textSize = 9f
                setTextColor(Color.parseColor("#AAFFFFFF"))
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dpToPx(2)
                }
            }
            addView(labelText)

            // Store button reference for selection updates
            tag = button
        }
    }

    private fun createModernToolButton(
        iconRes: Int,
        contentDescription: String,
        onClick: () -> Unit
    ): ImageButton {
        return ImageButton(context).apply {
            layoutParams = LayoutParams(dpToPx(32), dpToPx(32)).apply {
                marginStart = dpToPx(2)
                marginEnd = dpToPx(2)
            }

            background = createModernButtonBackground(false)
            setImageResource(iconRes)
            setColorFilter(Color.parseColor("#666666"))
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6))
            this.contentDescription = contentDescription
            elevation = dpToPx(2).toFloat()

            setOnClickListener {
                performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)

                animate()
                    .scaleX(0.9f)
                    .scaleY(0.9f)
                    .setDuration(100)
                    .withEndAction {
                        animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(100)
                            .start()
                    }
                    .start()

                onClick()
            }
        }
    }

    private fun createMiniSpacer(): View {
        return View(context).apply {
            layoutParams = LayoutParams(dpToPx(4), LayoutParams.MATCH_PARENT)
        }
    }

    private fun createModernSeparator(): View {
        return View(context).apply {
            layoutParams = LayoutParams(dpToPx(2), dpToPx(28)).apply {
                marginStart = dpToPx(8)
                marginEnd = dpToPx(8)
            }
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(1).toFloat()
                setColor(Color.parseColor("#33FFFFFF"))
            }
        }
    }

    private fun createModernButtonBackground(selected: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = dpToPx(10).toFloat()
            if (selected) {
                // Modern blue gradient for selected state
                colors = intArrayOf(
                    Color.parseColor("#FF4A90E2"),
                    Color.parseColor("#FF357ABD")
                )
                gradientType = GradientDrawable.LINEAR_GRADIENT
                orientation = GradientDrawable.Orientation.TOP_BOTTOM
                setStroke(dpToPx(2), Color.parseColor("#80FFFFFF"))
            } else {
                // White background for unselected
                setColor(Color.WHITE)
            }
        }
    }

    private fun selectTool(tool: AnnotationTool) {
        clearAllSelections()

        selectedTool = tool
        val selectedButtonContainer = when (tool) {
            AnnotationTool.FREE_DRAW -> freeDrawButton
            AnnotationTool.LINE -> lineButton
            AnnotationTool.RECTANGLE -> rectangleButton
            AnnotationTool.CIRCLE -> circleButton
            AnnotationTool.ARROW -> arrowButton
            AnnotationTool.ERASER -> eraserButton
            AnnotationTool.SELECTION -> selectionButton
        }

        // Get the actual ImageButton from the container
        val button = selectedButtonContainer.tag as? ImageButton ?: selectedButtonContainer as? ImageButton
        button?.let {
            it.background = createModernButtonBackground(true)
            it.setColorFilter(Color.WHITE)

            // Pulse animation for selection
            it.animate()
                .scaleX(1.1f)
                .scaleY(1.1f)
                .setDuration(150)
                .withEndAction {
                    it.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(150)
                        .start()
                }
                .start()
        }

        onToolSelected?.invoke(tool)
    }

    private fun clearAllSelections() {
        val buttonContainers = listOf(
            freeDrawButton, lineButton, rectangleButton,
            circleButton, arrowButton, eraserButton, selectionButton
        )

        buttonContainers.forEach { container ->
            val button = container.tag as? ImageButton ?: container as? ImageButton
            button?.let {
                it.background = createModernButtonBackground(false)
                it.setColorFilter(Color.parseColor("#666666"))
            }
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    fun getSelectedTool(): AnnotationTool = selectedTool
    fun getCurrentColor(): Int = currentColor
    fun getCurrentStrokeWidth(): Float = currentStrokeWidth

    fun setToolbarEnabled(enabled: Boolean) {
        val buttonContainers = listOf(
            freeDrawButton, lineButton, rectangleButton,
            circleButton, arrowButton, eraserButton, selectionButton,
            undoButton, redoButton, clearButton, expandButton
        )

        buttonContainers.forEach { container ->
            val button = container.tag as? ImageButton ?: container as? ImageButton
            button?.let {
                it.isEnabled = enabled
                it.alpha = if (enabled) 1.0f else 0.4f
            }
            container.isEnabled = enabled
            container.alpha = if (enabled) 1.0f else 0.4f
        }

        strokeWidthSeekBar.isEnabled = enabled
        strokeWidthSeekBar.alpha = if (enabled) 1.0f else 0.4f

        selectedColorPreview.isEnabled = enabled
        selectedColorPreview.alpha = if (enabled) 1.0f else 0.4f
    }

    fun dismissColorPicker() {
        colorPickerDialog?.dismiss()
        colorPickerDialog = null
    }
}