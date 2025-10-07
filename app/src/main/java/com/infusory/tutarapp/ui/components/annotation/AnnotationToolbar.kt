//// AnnotationToolbar.kt
//package com.infusory.tutarapp.ui.annotation
//
//import android.content.Context
//import android.util.AttributeSet
//import android.view.LayoutInflater
//import android.view.View
//import android.widget.ImageButton
//import android.widget.LinearLayout
//import androidx.core.content.ContextCompat
//import com.infusory.tutarapp.R
//
//class AnnotationToolbar @JvmOverloads constructor(
//    context: Context,
//    attrs: AttributeSet? = null,
//    defStyleAttr: Int = 0
//) : LinearLayout(context, attrs, defStyleAttr) {
//
//    enum class AnnotationTool {
//        FREE_DRAW,
//        LINE,
//        RECTANGLE,
//        CIRCLE,
//        ARROW,
//        SELECTION
//    }
//
//    private lateinit var btnFreeDraw: ImageButton
//    private lateinit var btnLine: ImageButton
//    private lateinit var btnRectangle: ImageButton
//    private lateinit var btnCircle: ImageButton
//    private lateinit var btnArrow: ImageButton
//    private lateinit var btnSelection: ImageButton
//    private lateinit var btnClear: ImageButton
//    private lateinit var btnUndo: ImageButton
//    private lateinit var btnRedo: ImageButton
//
//    private var currentTool: AnnotationTool = AnnotationTool.FREE_DRAW
//    private var onToolSelectedListener: ((AnnotationTool) -> Unit)? = null
//    private var onClearListener: (() -> Unit)? = null
//    private var onUndoListener: (() -> Unit)? = null
//    private var onRedoListener: (() -> Unit)? = null
//
//    init {
//        initView()
//    }
//
//    private fun initView() {
//        orientation = HORIZONTAL
//        LayoutInflater.from(context).inflate(R.layout.annotation_toolbar, this, true)
//
//        // Initialize buttons
//        btnFreeDraw = findViewById(R.id.btn_free_draw)
//        btnLine = findViewById(R.id.btn_line)
//        btnRectangle = findViewById(R.id.btn_rectangle)
//        btnCircle = findViewById(R.id.btn_circle)
//        btnArrow = findViewById(R.id.btn_arrow)
//        btnSelection = findViewById(R.id.btn_selection)
//        btnClear = findViewById(R.id.btn_clear)
//        btnUndo = findViewById(R.id.btn_undo)
//        btnRedo = findViewById(R.id.btn_redo)
//
//        setupButtonListeners()
//        updateButtonStates()
//    }
//
//    private fun setupButtonListeners() {
//        btnFreeDraw.setOnClickListener { selectTool(AnnotationTool.FREE_DRAW) }
//        btnLine.setOnClickListener { selectTool(AnnotationTool.LINE) }
//        btnRectangle.setOnClickListener { selectTool(AnnotationTool.RECTANGLE) }
//        btnCircle.setOnClickListener { selectTool(AnnotationTool.CIRCLE) }
//        btnArrow.setOnClickListener { selectTool(AnnotationTool.ARROW) }
//        btnSelection.setOnClickListener { selectTool(AnnotationTool.SELECTION) }
//
//        btnClear.setOnClickListener { onClearListener?.invoke() }
//        btnUndo.setOnClickListener { onUndoListener?.invoke() }
//        btnRedo.setOnClickListener { onRedoListener?.invoke() }
//    }
//
//    private fun selectTool(tool: AnnotationTool) {
//        currentTool = tool
//        updateButtonStates()
//        onToolSelectedListener?.invoke(tool)
//    }
//
//    private fun updateButtonStates() {
//        // Reset all buttons
//        resetButtonState(btnFreeDraw)
//        resetButtonState(btnLine)
//        resetButtonState(btnRectangle)
//        resetButtonState(btnCircle)
//        resetButtonState(btnArrow)
//        resetButtonState(btnSelection)
//
//        // Highlight selected button
//        when (currentTool) {
//            AnnotationTool.FREE_DRAW -> setButtonSelected(btnFreeDraw)
//            AnnotationTool.LINE -> setButtonSelected(btnLine)
//            AnnotationTool.RECTANGLE -> setButtonSelected(btnRectangle)
//            AnnotationTool.CIRCLE -> setButtonSelected(btnCircle)
//            AnnotationTool.ARROW -> setButtonSelected(btnArrow)
//            AnnotationTool.SELECTION -> setButtonSelected(btnSelection)
//        }
//    }
//
//    private fun resetButtonState(button: ImageButton) {
//        button.background = ContextCompat.getDrawable(context, R.drawable.annotation_button_background)
//        button.setColorFilter(ContextCompat.getColor(context, android.R.color.white))
//    }
//
//    private fun setButtonSelected(button: ImageButton) {
//        button.background = ContextCompat.getDrawable(context, R.drawable.annotation_button_selected)
//        button.setColorFilter(ContextCompat.getColor(context, R.color.annotation_selected_color))
//    }
//
//    fun getCurrentTool(): AnnotationTool = currentTool
//
//    fun setOnToolSelectedListener(listener: (AnnotationTool) -> Unit) {
//        onToolSelectedListener = listener
//    }
//
//    fun setOnClearListener(listener: () -> Unit) {
//        onClearListener = listener
//    }
//
//    fun setOnUndoListener(listener: () -> Unit) {
//        onUndoListener = listener
//    }
//
//    fun setOnRedoListener(listener: () -> Unit) {
//        onRedoListener = listener
//    }
//
//    fun enableUndo(enabled: Boolean) {
//        btnUndo.isEnabled = enabled
//        btnUndo.alpha = if (enabled) 1.0f else 0.5f
//    }
//
//    fun enableRedo(enabled: Boolean) {
//        btnRedo.isEnabled = enabled
//        btnRedo.alpha = if (enabled) 1.0f else 0.5f
//    }
//}