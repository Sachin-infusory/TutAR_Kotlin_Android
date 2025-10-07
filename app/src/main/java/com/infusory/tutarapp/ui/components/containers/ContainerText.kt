// ContainerText.kt
package com.infusory.tutarapp.ui.components.containers

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.widget.TextView
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import com.infusory.tutarapp.ui.components.containers.ControlButton
import com.infusory.tutarapp.ui.components.containers.ButtonPosition

class ContainerText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ContainerBase(context, ContainerType.TEXT, attrs, defStyleAttr) {

    private var currentText = "Sample Text Container\n\nClick edit to modify this text."
    private var currentTextSize = 14f
    private var currentTextColor = Color.BLACK
    private var currentBackgroundColor = Color.parseColor("#F0F0F0")
    private var currentTextView: TextView? = null

    init {
        setupTextContainer()
    }

    private fun setupTextContainer() {
//        clearControlButtons() // Clear any existing buttons from base class

        val buttons = listOf(
            ControlButton(
                iconRes = android.R.drawable.ic_menu_close_clear_cancel,
                onClick = { onRemoveRequest?.invoke() },
                position = ButtonPosition.TOP_END
            ),
            ControlButton(
                iconRes = android.R.drawable.ic_menu_edit,
                onClick = { showEditDialog() },
                position = ButtonPosition.BOTTOM_START
            ),
            ControlButton(
                iconRes = android.R.drawable.ic_menu_more,
                onClick = { showTextMenu() },
                position = ButtonPosition.BOTTOM_END
            )
        )

        addControlButtons(buttons)
    }

    override fun initializeContent() {
        createTextView()
    }

    private fun createTextView() {
        currentTextView = TextView(context).apply {
            text = currentText
            textSize = currentTextSize
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
            setTextColor(currentTextColor)
            setBackgroundColor(currentBackgroundColor)
            gravity = android.view.Gravity.START or android.view.Gravity.TOP
        }
        setContent(currentTextView!!)
    }

    private fun showEditDialog() {
        val editText = EditText(context).apply {
            setText(currentText)
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
        }

        AlertDialog.Builder(context)
            .setTitle("Edit Text")
            .setView(editText)
            .setPositiveButton("Save") { _, _ ->
                currentText = editText.text.toString()
                updateTextContent()
                android.widget.Toast.makeText(context, "Text updated", android.widget.Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showTextMenu() {
        val options = arrayOf(
            "Change Font Size",
            "Change Text Color",
            "Change Background",
            "Text Alignment",
            "Reset to Default"
        )

        AlertDialog.Builder(context)
            .setTitle("Text Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showFontSizeDialog()
                    1 -> showTextColorDialog()
                    2 -> showBackgroundColorDialog()
                    3 -> showTextAlignmentDialog()
                    4 -> resetToDefault()
                }
            }
            .show()
    }

    private fun showFontSizeDialog() {
        val sizes = arrayOf("Small (12sp)", "Medium (14sp)", "Large (18sp)", "Extra Large (24sp)", "Huge (32sp)")
        val sizeValues = arrayOf(12f, 14f, 18f, 24f, 32f)

        val currentIndex = when (currentTextSize) {
            12f -> 0
            14f -> 1
            18f -> 2
            24f -> 3
            32f -> 4
            else -> 1
        }

        AlertDialog.Builder(context)
            .setTitle("Select Font Size")
            .setSingleChoiceItems(sizes, currentIndex) { dialog, which ->
                currentTextSize = sizeValues[which]
                updateTextContent()
                android.widget.Toast.makeText(context, "Font size: ${sizes[which]}", android.widget.Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showTextColorDialog() {
        val colors = arrayOf("Black", "Dark Gray", "Red", "Blue", "Green", "Purple", "Orange")
        val colorValues = arrayOf(
            Color.BLACK,
            Color.DKGRAY,
            Color.RED,
            Color.BLUE,
            Color.parseColor("#008000"), // Green
            Color.parseColor("#800080"), // Purple
            Color.parseColor("#FFA500")  // Orange
        )

        AlertDialog.Builder(context)
            .setTitle("Select Text Color")
            .setItems(colors) { _, which ->
                currentTextColor = colorValues[which]
                updateTextContent()
                android.widget.Toast.makeText(context, "Text color: ${colors[which]}", android.widget.Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showBackgroundColorDialog() {
        val backgrounds = arrayOf("White", "Light Gray", "Light Blue", "Light Green", "Light Yellow", "Transparent")
        val backgroundValues = arrayOf(
            Color.WHITE,
            Color.parseColor("#F0F0F0"),
            Color.parseColor("#E3F2FD"),
            Color.parseColor("#E8F5E8"),
            Color.parseColor("#FFFDE7"),
            Color.TRANSPARENT
        )

        AlertDialog.Builder(context)
            .setTitle("Select Background")
            .setItems(backgrounds) { _, which ->
                currentBackgroundColor = backgroundValues[which]
                updateTextContent()
                android.widget.Toast.makeText(context, "Background: ${backgrounds[which]}", android.widget.Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showTextAlignmentDialog() {
        val alignments = arrayOf("Left", "Center", "Right")
        val alignmentValues = arrayOf(
            android.view.Gravity.START or android.view.Gravity.TOP,
            android.view.Gravity.CENTER,
            android.view.Gravity.END or android.view.Gravity.TOP
        )

        AlertDialog.Builder(context)
            .setTitle("Select Text Alignment")
            .setItems(alignments) { _, which ->
                currentTextView?.gravity = alignmentValues[which]
                android.widget.Toast.makeText(context, "Alignment: ${alignments[which]}", android.widget.Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun resetToDefault() {
        currentText = "Sample Text Container\n\nClick edit to modify this text."
        currentTextSize = 14f
        currentTextColor = Color.BLACK
        currentBackgroundColor = Color.parseColor("#F0F0F0")
        updateTextContent()
        android.widget.Toast.makeText(context, "Reset to default", android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun updateTextContent() {
        currentTextView?.apply {
            text = currentText
            textSize = currentTextSize
            setTextColor(currentTextColor)
            setBackgroundColor(currentBackgroundColor)
        }
    }

    // Text-specific methods
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

    override fun getDefaultText(): String {
        return currentText
    }

    override fun getCustomSaveData(): Map<String, Any> {
        val baseData = super.getCustomSaveData().toMutableMap()
        baseData.putAll(mapOf(
            "text" to currentText,
            "textSize" to currentTextSize,
            "textColor" to currentTextColor,
            "backgroundColor" to currentBackgroundColor
        ))
        return baseData
    }

    override fun loadCustomSaveData(data: Map<String, Any>) {
        super.loadCustomSaveData(data)

        data["text"]?.let {
            if (it is String) currentText = it
        }
        data["textSize"]?.let {
            if (it is Float) currentTextSize = it
            else if (it is Double) currentTextSize = it.toFloat()
        }
        data["textColor"]?.let {
            if (it is Int) currentTextColor = it
        }
        data["backgroundColor"]?.let {
            if (it is Int) currentBackgroundColor = it
        }

        // Update the text view if it exists
        if (currentTextView != null) {
            updateTextContent()
        }
    }
}