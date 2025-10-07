package com.infusory.tutarapp.managers

import android.content.Context
import android.graphics.Color
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.SurfaceView
import android.view.View
import android.widget.*
import androidx.core.content.ContextCompat
import com.infusory.tutarapp.R
import com.infusory.tutarapp.ui.whiteboard.ActionType
import com.infusory.tutarapp.ui.components.containers.ContainerManager

class PopupHandler(private val context: Context) {

    private val inflater: LayoutInflater = LayoutInflater.from(context)
    private var currentPopup: PopupWindow? = null

    fun showColorPopup(
        anchor: ImageButton,
        surfaceView: SurfaceView,
        onImagePickRequested: () -> Unit,
        onDismiss: (() -> Unit)? = null
    ) {
        Log.i("colorPopup", "colorPopupAnchor: ${anchor.tag}")

        dismissCurrentPopup()

        val colorView = inflater.inflate(R.layout.dialog_color_picker, null)

        currentPopup = PopupWindow(
            colorView,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            elevation = 20f
            isOutsideTouchable = true
        }

        // Bind buttons
        val white = colorView.findViewById<RadioButton>(R.id.colorWhite)
        val green = colorView.findViewById<RadioButton>(R.id.colorGreen)
        val black = colorView.findViewById<RadioButton>(R.id.colorBlack)
        val colorPicker = colorView.findViewById<ImageButton>(R.id.btnColorPicker)
        val pickImage = colorView.findViewById<ImageButton>(R.id.btnPickImage)

        // Set background color actions
        white.setOnClickListener {
            surfaceView.setBackgroundColor(
                ContextCompat.getColor(context, android.R.color.white)
            )
            currentPopup?.dismiss()
        }

        green.setOnClickListener {
            surfaceView.setBackgroundColor(
                ContextCompat.getColor(context, android.R.color.holo_green_light)
            )
            currentPopup?.dismiss()
        }

        black.setOnClickListener {
            surfaceView.setBackgroundColor(
                ContextCompat.getColor(context, android.R.color.black)
            )
            currentPopup?.dismiss()
        }

        // Color picker button
        colorPicker.setOnClickListener {
            currentPopup?.dismiss()
            showCustomColorPickerDialog(surfaceView, onDismiss)
        }

        // Image picker
        pickImage.setOnClickListener {
            currentPopup?.dismiss()
            onImagePickRequested()
        }

        // Position popup near the anchor
        positionPopup(currentPopup!!, colorView, anchor)

        currentPopup?.setOnDismissListener {
            anchor.tag = false
            anchor.background = ContextCompat.getDrawable(
                context,
                R.drawable.circular_button_background
            )
            onDismiss?.invoke()
        }
    }

    private fun showCustomColorPickerDialog(
        surfaceView: SurfaceView,
        onDismiss: (() -> Unit)? = null
    ) {
        val dialogView = inflater.inflate(R.layout.dialog_custom_color_picker, null)

        val colorPalette = dialogView.findViewById<com.infusory.tutarapp.ui.components.ColorPaletteView>(R.id.colorPalette)
        val hueSlider = dialogView.findViewById<com.infusory.tutarapp.ui.components.HueSliderView>(R.id.hueSlider)
        val selectedColorPreview = dialogView.findViewById<View>(R.id.selectedColorPreview)
        val hexColorText = dialogView.findViewById<TextView>(R.id.hexColorText)

        var currentSelectedColor = Color.WHITE

        // Set up hue slider listener
        hueSlider.onHueSelected = { hue ->
            colorPalette.setHue(hue)
            currentSelectedColor = colorPalette.getCurrentColor()
            updateColorDisplay(currentSelectedColor, selectedColorPreview, hexColorText)
        }

        // Set up color palette listener
        colorPalette.onColorSelected = { color ->
            currentSelectedColor = color
            updateColorDisplay(currentSelectedColor, selectedColorPreview, hexColorText)
        }

        // Initialize with white color
        updateColorDisplay(currentSelectedColor, selectedColorPreview, hexColorText)

        // Show dialog
        val dialog = android.app.AlertDialog.Builder(context)
            .setView(dialogView)
            .setPositiveButton("Apply") { _, _ ->
                surfaceView.setBackgroundColor(currentSelectedColor)
                onDismiss?.invoke()
            }
            .setNegativeButton("Cancel") { _, _ ->
                onDismiss?.invoke()
            }
            .create()

        dialog.setOnDismissListener {
            onDismiss?.invoke()
        }

        dialog.show()
    }

    private fun updateColorDisplay(
        color: Int,
        colorPreview: View,
        hexText: TextView
    ) {
        colorPreview.setBackgroundColor(color)
        hexText.text = String.format("#%02X%02X%02X",
            Color.red(color),
            Color.green(color),
            Color.blue(color)
        )
    }

    fun showActionOptionsPopup(
        anchor: View,
        type: ActionType,
        onSaveLesson: () -> Unit = {},
        onSavePdf: () -> Unit = {},
        onInsertImage: () -> Unit = {},
        onInsertPdf: () -> Unit = {},
        onInsertYoutube: () -> Unit = {},
        onInsertWebsite: () -> Unit = {},
        onDismiss: (() -> Unit)? = null
    ) {
        Log.i("ActionPopup", "Anchor: ${anchor.tag}, Type: $type")

        dismissCurrentPopup()

        val popupView = when (type) {
            ActionType.SAVE -> inflater.inflate(R.layout.dialog_save_options, null)
            ActionType.INSERT -> inflater.inflate(R.layout.dialog_insert_options, null)
        }

        // Bind buttons dynamically and set text color to black
        when (type) {
            ActionType.SAVE -> {
                popupView.findViewById<Button>(R.id.btnSaveLesson).apply {
                    setOnClickListener {
                        currentPopup?.dismiss()
                        onSaveLesson()
                        Toast.makeText(context, "Save Lesson clicked", Toast.LENGTH_SHORT).show()
                    }
                    setTextColor(Color.BLACK)
                }
                popupView.findViewById<Button>(R.id.btnSavePdf).apply {
                    setOnClickListener {
                        currentPopup?.dismiss()
                        onSavePdf()
                        Toast.makeText(context, "Save PDF clicked", Toast.LENGTH_SHORT).show()
                    }
                    setTextColor(Color.BLACK)
                }
            }
            ActionType.INSERT -> {
                popupView.findViewById<Button>(R.id.btnInsertImage).apply {
                    setOnClickListener {
                        currentPopup?.dismiss()
                        onInsertImage()
                    }
                    setTextColor(Color.BLACK)
                }
                popupView.findViewById<Button>(R.id.btnInsertPdf).apply {
                    setOnClickListener {
                        currentPopup?.dismiss()
                        onInsertPdf()
                        Toast.makeText(context, "Insert PDF clicked", Toast.LENGTH_SHORT).show()
                    }
                    setTextColor(Color.BLACK)
                }
                popupView.findViewById<Button>(R.id.btnInsertYoutube).apply {
                    setOnClickListener {
                        currentPopup?.dismiss()
                        onInsertYoutube()
                        Toast.makeText(context, "Insert YouTube clicked", Toast.LENGTH_SHORT).show()
                    }
                    setTextColor(Color.BLACK)
                }
                popupView.findViewById<Button>(R.id.btnInsertWebsite).apply {
                    setOnClickListener {
                        currentPopup?.dismiss()
                        onInsertWebsite()
                        Toast.makeText(context, "Insert Website clicked", Toast.LENGTH_SHORT).show()
                    }
                    setTextColor(Color.BLACK)
                }
            }
        }

        currentPopup = PopupWindow(
            popupView,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            elevation = 20f
            isOutsideTouchable = true
        }

        positionPopup(currentPopup!!, popupView, anchor)

        currentPopup?.setOnDismissListener {
            anchor.background = ContextCompat.getDrawable(
                context,
                R.drawable.circular_button_background
            )
            onDismiss?.invoke()
        }
    }

    fun showContainerManagementMenu(
        containerManager: ContainerManager,
        onCameraToggle: () -> Unit,
        onPauseAll3D: () -> Unit,
        onResumeAll3D: () -> Unit,
        isCameraActive: Boolean,
        onDismiss: (() -> Unit)? = null
    ) {
        val options = arrayOf(
            "Reset All Positions",
            "Zoom All to 1x",
            "Zoom All to 2x",
            "Arrange in Grid",
            "Toggle Dragging",
            "Toggle Resizing",
            "Pause All 3D Rendering",
            "Resume All 3D Rendering",
            "Container Statistics",
            "Clear All Containers",
            if (isCameraActive) "Stop Camera" else "Start Camera"
        )

        val dialog = android.app.AlertDialog.Builder(context)
            .setTitle("Container Management")
            .setItems(options) { dialogInterface, which ->
                when (which) {
                    0 -> containerManager.resetAllContainers()
                    1 -> containerManager.zoomAllContainers(1.0f)
                    2 -> containerManager.zoomAllContainers(2.0f)
                    3 -> containerManager.arrangeContainersInGrid()
                    4 -> containerManager.toggleDraggingForAllContainers()
                    5 -> containerManager.toggleResizingForAllContainers()
                    6 -> onPauseAll3D()
                    7 -> onResumeAll3D()
                    8 -> showContainerStatistics(containerManager, isCameraActive)
                    9 -> containerManager.clearAllContainers()
                    10 -> onCameraToggle()
                }
                dialogInterface.dismiss()
            }
            .setOnDismissListener {
                onDismiss?.invoke()
            }
            .create()

        dialog.show()
    }

    private fun showContainerStatistics(
        containerManager: ContainerManager,
        isCameraActive: Boolean
    ) {
        val allContainers = containerManager.getAllContainers()
        val container3Ds = allContainers.filterIsInstance<com.infusory.tutarapp.ui.components.containers.Container3D>()

        val stats = """
            Total Containers: ${allContainers.size}
            Regular Containers: ${allContainers.size - container3Ds.size}
            3D Containers: ${container3Ds.size}
            Camera Status: ${if (isCameraActive) "Active" else "Inactive"}
            
            3D Models:
            ${container3Ds.mapIndexed { index, container ->
            "  ${index + 1}. ${container.getCurrentAnimationInfo()}"
        }.joinToString("\n")}
        """.trimIndent()

        android.app.AlertDialog.Builder(context)
            .setTitle("Container Statistics")
            .setMessage(stats)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun positionPopup(popup: PopupWindow, popupView: View, anchor: View) {
        popupView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val popupWidth = popupView.measuredWidth
        val popupHeight = popupView.measuredHeight

        val location = IntArray(2)
        anchor.getLocationOnScreen(location)
        val anchorX = location[0]
        val anchorY = location[1]
        val anchorWidth = anchor.width
        val anchorHeight = anchor.height

        var popupX = anchorX + anchorWidth + 30
        var popupY = anchorY + (anchorHeight - popupHeight) / 2

        // Check if popup goes off screen horizontally
        val displayMetrics = context.resources.displayMetrics
        if (popupX + popupWidth > displayMetrics.widthPixels) {
            popupX = anchorX - popupWidth - 30
        }

        if (popupY + popupHeight > displayMetrics.heightPixels) {
            popupY = displayMetrics.heightPixels - popupHeight - 10
        }
        if (popupY < 0) popupY = 10

        popup.showAtLocation(anchor, Gravity.NO_GRAVITY, popupX, popupY)
    }

    private fun dismissCurrentPopup() {
        currentPopup?.dismiss()
        currentPopup = null
    }

    fun dismissAll() {
        dismissCurrentPopup()
    }
}