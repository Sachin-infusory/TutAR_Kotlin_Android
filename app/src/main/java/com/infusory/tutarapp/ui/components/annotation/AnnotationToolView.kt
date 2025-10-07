// AnnotationToolView.kt - Fixed resize handle and selection
package com.infusory.tutarapp.ui.annotation

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
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

// Drawing path data class
data class DrawingPath(
    val path: Path,
    val paint: Paint,
    var bounds: RectF = RectF()
) {
    fun updateBounds() {
        path.computeBounds(bounds, true)
    }
}

// Action types for undo/redo
sealed class DrawingAction {
    data class AddPath(val path: DrawingPath) : DrawingAction()
    data class RemovePath(val path: DrawingPath, val index: Int) : DrawingAction()
}

// AnnotationToolbar class
class AnnotationToolbar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    // Tool buttons
    private lateinit var freeDrawButton: ImageButton
    private lateinit var lineButton: ImageButton
    private lateinit var rectangleButton: ImageButton
    private lateinit var circleButton: ImageButton
    private lateinit var arrowButton: ImageButton
    private lateinit var eraserButton: ImageButton
    private lateinit var selectionButton: ImageButton
    private lateinit var undoButton: ImageButton
    private lateinit var redoButton: ImageButton
    private lateinit var clearButton: ImageButton

    // Currently selected tool
    private var selectedTool = AnnotationTool.FREE_DRAW

    // Callbacks
    var onToolSelected: ((AnnotationTool) -> Unit)? = null
    var onUndoPressed: (() -> Unit)? = null
    var onredoPressed: (() -> Unit)? = null
    var onClearPressed: (() -> Unit)? = null
    var onCloseAnnotation: (() -> Unit)? = null

    init {
        setupToolbar()
    }

    private fun setupToolbar() {
        orientation = HORIZONTAL

        // Set toolbar background
        background = createToolbarBackground()

        // Add padding
        val padding = dpToPx(10)
        setPadding(padding, padding, padding, padding)

        // Create tool buttons
        createToolButtons()

        // Set initial selection
        selectTool(AnnotationTool.FREE_DRAW)
    }

    private fun createToolbarBackground(): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = dpToPx(15).toFloat()
            setColor(ContextCompat.getColor(context, android.R.color.darker_gray))
            alpha = 200 // Semi-transparent
        }
    }

    private fun createToolButtons() {
        // Free Draw Button
        freeDrawButton = createToolButton(R.drawable.ic_write_tool, "Free Draw") {
            selectTool(AnnotationTool.FREE_DRAW)
        }
        addView(freeDrawButton)

        addSeparator()

        // Line Button
        lineButton = createToolButton(R.drawable.ic_line_tool, "Line") {
            selectTool(AnnotationTool.LINE)
        }
        addView(lineButton)

        // Rectangle Button
        rectangleButton = createToolButton(R.drawable.ic_square_tool, "Rectangle") {
            selectTool(AnnotationTool.RECTANGLE)
        }
        addView(rectangleButton)

        // Circle Button
        circleButton = createToolButton(R.drawable.ic_circle_tool, "Circle") {
            selectTool(AnnotationTool.CIRCLE)
        }
        addView(circleButton)

        // Arrow Button
        arrowButton = createToolButton(R.drawable.ic_arrow_tool, "Arrow") {
            selectTool(AnnotationTool.ARROW)
        }
        addView(arrowButton)

        addSeparator()

        // Eraser Button
        eraserButton = createToolButton(R.drawable.ic_eraser_tool, "Eraser") {
            selectTool(AnnotationTool.ERASER)
        }
        addView(eraserButton)

        // Selection Button
        selectionButton = createToolButton(R.drawable.ic_select_tool, "Selection") {
            selectTool(AnnotationTool.SELECTION)
        }
        addView(selectionButton)

        addSeparator()

        // Undo Button
        undoButton = createToolButton(R.drawable.ic_undo_tool, "Undo") {
            onUndoPressed?.invoke()
        }
        addView(undoButton)

        // Redo Button
        redoButton = createToolButton(R.drawable.ic_redo_tool, "Redo") {
            onredoPressed?.invoke()
        }
        addView(redoButton)
        addSeparator()

        // Clear Button
        clearButton = createToolButton(R.drawable.ic_clear_tool, "Clear") {
            onClearPressed?.invoke()
        }
        addView(clearButton)
    }

    private fun createToolButton(
        iconRes: Int,
        contentDescription: String,
        onClick: () -> Unit
    ): ImageButton {
        return ImageButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(30), dpToPx(30)).apply {
                marginStart = dpToPx(4)
                marginEnd = dpToPx(4)
            }

            background = createButtonBackground(false)
            setImageResource(iconRes)
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            this.contentDescription = contentDescription

            setOnClickListener { onClick() }
        }
    }

    private fun addSeparator() {
        val separator = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(1), dpToPx(30)).apply {
                marginStart = dpToPx(8)
                marginEnd = dpToPx(8)
            }
            setBackgroundColor(ContextCompat.getColor(context, android.R.color.darker_gray))
        }
        addView(separator)
    }

    private fun createButtonBackground(selected: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = dpToPx(8).toFloat()
            if (selected) {
                setColor(ContextCompat.getColor(context, android.R.color.holo_blue_light))
            } else {
                setColor(ContextCompat.getColor(context, android.R.color.background_light))
            }
        }
    }

    private fun selectTool(tool: AnnotationTool) {
        // Clear previous selection
        clearAllSelections()

        // Set new selection
        selectedTool = tool
        val selectedButton = when (tool) {
            AnnotationTool.FREE_DRAW -> freeDrawButton
            AnnotationTool.LINE -> lineButton
            AnnotationTool.RECTANGLE -> rectangleButton
            AnnotationTool.CIRCLE -> circleButton
            AnnotationTool.ARROW -> arrowButton
            AnnotationTool.ERASER -> eraserButton
            AnnotationTool.SELECTION -> selectionButton
        }

        selectedButton.background = createButtonBackground(true)
        onToolSelected?.invoke(tool)
    }

    private fun clearAllSelections() {
        val buttons = listOf(
            freeDrawButton, lineButton, rectangleButton,
            circleButton, arrowButton, eraserButton, selectionButton
        )

        buttons.forEach { button ->
            button.background = createButtonBackground(false)
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    fun getSelectedTool(): AnnotationTool = selectedTool

    fun setToolbarEnabled(enabled: Boolean) {
        val buttons = listOf(
            freeDrawButton, lineButton, rectangleButton,
            circleButton, arrowButton, eraserButton, selectionButton,
            undoButton, redoButton, clearButton
        )

        buttons.forEach { button ->
            button.isEnabled = enabled
            button.alpha = if (enabled) 1.0f else 0.5f
        }
    }
}

// Main AnnotationToolView class
class AnnotationToolView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : RelativeLayout(context, attrs, defStyleAttr) {

    // Drawing surface - ALWAYS VISIBLE to preserve drawings
    private var drawingView: DrawingSurfaceView? = null

    // Toolbar
    private var annotationToolbar: AnnotationToolbar? = null

    // State
    private var isAnnotationMode = false

    // Callbacks
    var onAnnotationToggle: ((Boolean) -> Unit)? = null

    // Callback for 3D rendering control
    var onDrawingStateChanged: ((isDrawing: Boolean) -> Unit)? = null

    init {
        setupAnnotationTool()
    }

    private fun setupAnnotationTool() {
        // Create drawing view - ALWAYS VISIBLE
        drawingView = DrawingSurfaceView(context)
        val drawingParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        drawingView?.layoutParams = drawingParams
        drawingView?.visibility = View.VISIBLE
        drawingView?.setTouchEnabled(false)
        addView(drawingView)

        // Create toolbar
        annotationToolbar = AnnotationToolbar(context)
        val toolbarParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        toolbarParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
        toolbarParams.addRule(RelativeLayout.CENTER_HORIZONTAL)
        toolbarParams.bottomMargin = 120
        annotationToolbar?.layoutParams = toolbarParams
        annotationToolbar?.visibility = View.GONE
        addView(annotationToolbar)

        // Set up toolbar callbacks
        annotationToolbar?.onToolSelected = { tool: AnnotationTool ->
            drawingView?.clearSelection()
            drawingView?.setDrawingTool(tool)
        }

        annotationToolbar?.onUndoPressed = {
            drawingView?.undoLastAction()
        }
        annotationToolbar?.onredoPressed = {
            drawingView?.redoLastAction()
        }

        annotationToolbar?.onClearPressed = {
            drawingView?.clearSelection()
            drawingView?.clearAllDrawings()
        }

        annotationToolbar?.onCloseAnnotation = {
            toggleAnnotationMode(false)
        }

        // Set up drawing state callback
        drawingView?.onDrawingStateChanged = { isDrawing ->
            onDrawingStateChanged?.invoke(isDrawing)
        }
    }

    fun toggleAnnotationMode(enable: Boolean? = null) {
        isAnnotationMode = enable ?: !isAnnotationMode

        if (isAnnotationMode) {
            annotationToolbar?.visibility = View.VISIBLE
            drawingView?.setTouchEnabled(true)
            drawingView?.clearSelection()
        } else {
            annotationToolbar?.visibility = View.GONE
            drawingView?.setTouchEnabled(false)
            drawingView?.clearSelection()
        }

        onAnnotationToggle?.invoke(isAnnotationMode)
    }

    fun isInAnnotationMode(): Boolean = isAnnotationMode

    fun clearAllAnnotations() {
        drawingView?.clearSelection()
        drawingView?.clearAllDrawings()
    }

    fun undoLastAnnotation() {
        drawingView?.undoLastAction()
    }

    // Custom drawing view using SurfaceView for better performance
    inner class DrawingSurfaceView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
    ) : SurfaceView(context, attrs, defStyleAttr), SurfaceHolder.Callback {

        private var currentTool = AnnotationTool.FREE_DRAW
        private var paint = Paint().apply {
            color = Color.RED
            strokeWidth = 5f
            style = Paint.Style.STROKE
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        // Selection state
        private var selectedPath: DrawingPath? = null
        private var isResizing = false
        private var lastTouchX = 0f
        private var lastTouchY = 0f
        private var eraserCursorX = 0f
        private var eraserCursorY = 0f

        private var currentPath = Path()
        private var paths = mutableListOf<DrawingPath>()
        private var startX = 0f
        private var startY = 0f
        private var prevX = 0f
        private var prevY = 0f
        private var isDrawing = false
        private var touchEnabled = false

        // Undo/Redo stacks
        private var actionHistory = mutableListOf<DrawingAction>()
        private var redoHistory = mutableListOf<DrawingAction>()

        // Bitmap for persistent drawing
        private var drawingBitmap: Bitmap? = null
        private var drawingCanvas: Canvas? = null

        // Drawing thread
        private var drawingThread: DrawingThread? = null
        private var surfaceReady = false

        // Resize handle size
        private val handleSize = 30f

        var onDrawingStateChanged: ((Boolean) -> Unit)? = null

        init {
            holder.addCallback(this)
            setZOrderOnTop(true)
            holder.setFormat(PixelFormat.TRANSPARENT)
        }

        override fun surfaceCreated(holder: SurfaceHolder) {
            surfaceReady = true
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            if (drawingBitmap == null || drawingBitmap?.width != width || drawingBitmap?.height != height) {
                drawingBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                drawingCanvas = Canvas(drawingBitmap!!)
                redrawAllPaths()
            }
            redraw()
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            surfaceReady = false
            drawingThread?.stopDrawing()
            drawingThread = null
        }

        private fun redrawAllPaths() {
            drawingCanvas?.let { canvas ->
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                paths.forEach { drawingPath ->
                    canvas.drawPath(drawingPath.path, drawingPath.paint)
                }
            }
        }

        private fun redraw() {
            if (!surfaceReady) return

            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                canvas?.let { c ->
                    // Clear canvas with transparent background
                    c.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

                    // Draw the persistent bitmap
                    drawingBitmap?.let { bitmap ->
                        c.drawBitmap(bitmap, 0f, 0f, null)
                    }

                    // Draw current path being drawn
                    if (isDrawing && touchEnabled && currentTool != AnnotationTool.SELECTION && currentTool != AnnotationTool.ERASER) {
                        c.drawPath(currentPath, paint)
                    }

                    // Draw eraser cursor when eraser is active
                    if (touchEnabled && currentTool == AnnotationTool.ERASER) {
                        val cursorPaint = Paint().apply {
                            color = Color.RED
                            alpha = 100
                            style = Paint.Style.FILL
                            isAntiAlias = true
                        }
                        val strokePaint = Paint().apply {
                            color = Color.RED
                            alpha = 200
                            style = Paint.Style.STROKE
                            strokeWidth = 2f
                            isAntiAlias = true
                        }
                        val radius = 20f
                        c.drawCircle(eraserCursorX, eraserCursorY, radius, cursorPaint)
                        c.drawCircle(eraserCursorX, eraserCursorY, radius, strokePaint)

                        // Draw crosshair
                        val crosshairSize = 10f
                        c.drawLine(
                            eraserCursorX - crosshairSize, eraserCursorY,
                            eraserCursorX + crosshairSize, eraserCursorY,
                            strokePaint
                        )
                        c.drawLine(
                            eraserCursorX, eraserCursorY - crosshairSize,
                            eraserCursorX, eraserCursorY + crosshairSize,
                            strokePaint
                        )
                    }

                    // Highlight selection
                    selectedPath?.let {
                        val highlightPaint = Paint().apply {
                            color = Color.BLUE
                            style = Paint.Style.STROKE
                            strokeWidth = 3f
                            pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
                        }
                        it.updateBounds()
                        c.drawRect(it.bounds, highlightPaint)

                        // Draw resize handle - centered on the corner
                        val handlePaint = Paint().apply {
                            color = Color.BLUE
                            style = Paint.Style.FILL
                            alpha = 150
                        }
                        val handleStrokePaint = Paint().apply {
                            color = Color.WHITE
                            style = Paint.Style.STROKE
                            strokeWidth = 2f
                        }

                        // Draw handle as a circle centered on the bottom-right corner
                        val handleCenterX = it.bounds.right
                        val handleCenterY = it.bounds.bottom
                        val handleRadius = handleSize / 2

                        c.drawCircle(handleCenterX, handleCenterY, handleRadius, handlePaint)
                        c.drawCircle(handleCenterX, handleCenterY, handleRadius, handleStrokePaint)

                        // Draw a small cross in the center for better visibility
                        val crossSize = 6f
                        c.drawLine(
                            handleCenterX - crossSize, handleCenterY,
                            handleCenterX + crossSize, handleCenterY,
                            handleStrokePaint
                        )
                        c.drawLine(
                            handleCenterX, handleCenterY - crossSize,
                            handleCenterX, handleCenterY + crossSize,
                            handleStrokePaint
                        )
                    }
                }
            } finally {
                canvas?.let { holder.unlockCanvasAndPost(it) }
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (!touchEnabled) return false

            val x = event.x
            val y = event.y

            // Update eraser cursor position
            if (currentTool == AnnotationTool.ERASER) {
                eraserCursorX = x
                eraserCursorY = y
                redraw()
            }

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (currentTool == AnnotationTool.SELECTION) {
                        handleSelectionDown(x, y)
                        return true
                    } else if (currentTool == AnnotationTool.ERASER) {
                        handleEraserTouch(x, y)
                        return true
                    } else {
                        startDrawing(x, y)
                        return true
                    }
                }

                MotionEvent.ACTION_MOVE -> {
                    if (currentTool == AnnotationTool.SELECTION) {
                        handleSelectionMove(x, y)
                        return true
                    } else if (currentTool == AnnotationTool.ERASER) {
                        handleEraserTouch(x, y)
                        return true
                    } else {
                        continueDrawing(x, y)
                        return true
                    }
                }

                MotionEvent.ACTION_UP -> {
                    if (currentTool == AnnotationTool.SELECTION) {
                        isResizing = false
                        return true
                    } else if (currentTool == AnnotationTool.ERASER) {
                        // Reset the last erased path when touch is released
                        lastErasedPath = null
                        return true
                    } else {
                        finishDrawing(x, y)
                        return true
                    }
                }
            }
            return false
        }

        private var lastErasedPath: DrawingPath? = null

        private fun handleEraserTouch(x: Float, y: Float) {
            // Find the first path that contains the touch point
            var pathToRemove: DrawingPath? = null
            var pathIndex = -1

            // Check paths in reverse order (top to bottom) for more intuitive erasing
            for (i in paths.indices.reversed()) {
                val path = paths[i]
                path.updateBounds()
                // Expand bounds slightly for easier touch detection
                val expandedBounds = RectF(path.bounds).apply {
                    inset(-20f, -20f)
                }

                if (expandedBounds.contains(x, y)) {
                    pathToRemove = path
                    pathIndex = i
                    break // Only take the first (topmost) matching path
                }
            }

            // Remove only if it's a different path than the last erased one
            // This prevents erasing multiple elements in one continuous touch
            if (pathToRemove != null && pathToRemove != lastErasedPath && pathIndex != -1) {
                lastErasedPath = pathToRemove
                paths.removeAt(pathIndex)

                // Add to action history for undo/redo
                val action = DrawingAction.RemovePath(pathToRemove, pathIndex)
                actionHistory.add(action)
                redoHistory.clear() // Clear redo history when a new action is performed

                // Redraw all remaining paths
                drawingCanvas?.let { canvas ->
                    canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                    paths.forEach { drawingPath ->
                        canvas.drawPath(drawingPath.path, drawingPath.paint)
                    }
                }

                redraw()
            }
        }

        private fun handleSelectionDown(x: Float, y: Float) {
            selectedPath = null
            isResizing = false

            // First check if touching a resize handle
            for (path in paths.reversed()) {
                path.updateBounds()
                val handleRect = RectF(
                    path.bounds.right - handleSize,
                    path.bounds.bottom - handleSize,
                    path.bounds.right + handleSize,
                    path.bounds.bottom + handleSize
                )

                if (handleRect.contains(x, y)) {
                    selectedPath = path
                    lastTouchX = x
                    lastTouchY = y
                    isResizing = true
                    redraw()
                    return
                }
            }

            // If not touching a handle, check if touching a path for moving
            for (path in paths.reversed()) {
                path.updateBounds()
                // Expand bounds slightly for easier touch detection
                val expandedBounds = RectF(path.bounds).apply {
                    inset(-10f, -10f)
                }

                if (expandedBounds.contains(x, y)) {
                    selectedPath = path
                    lastTouchX = x
                    lastTouchY = y
                    isResizing = false
                    redraw()
                    return
                }
            }

            redraw()
        }

        private fun handleSelectionMove(x: Float, y: Float) {
            selectedPath?.let { path ->
                val dx = x - lastTouchX
                val dy = y - lastTouchY

                if (isResizing) {
                    // Calculate scale based on movement from the opposite corner
                    val newWidth = path.bounds.width() + dx
                    val newHeight = path.bounds.height() + dy

                    // Prevent negative or too small sizes
                    if (newWidth > 20f && newHeight > 20f) {
                        val scaleX = newWidth / path.bounds.width()
                        val scaleY = newHeight / path.bounds.height()

                        val matrix = Matrix().apply {
                            setScale(scaleX, scaleY, path.bounds.left, path.bounds.top)
                        }
                        path.path.transform(matrix)
                        path.updateBounds()
                    }
                } else {
                    // Move the path
                    val matrix = Matrix().apply { setTranslate(dx, dy) }
                    path.path.transform(matrix)
                    path.updateBounds()
                }

                lastTouchX = x
                lastTouchY = y

                // Redraw all paths with the moved/resized path
                drawingCanvas?.let { canvas ->
                    canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                    paths.forEach { drawingPath ->
                        canvas.drawPath(drawingPath.path, drawingPath.paint)
                    }
                }

                redraw()
            }
        }

        fun setTouchEnabled(enabled: Boolean) {
            touchEnabled = enabled
        }

        private fun startDrawing(x: Float, y: Float) {
            startX = x
            startY = y
            prevX = x
            prevY = y
            isDrawing = true
            currentPath.reset()

            onDrawingStateChanged?.invoke(true)

            when (currentTool) {
                AnnotationTool.FREE_DRAW -> {
                    currentPath.moveTo(x, y)
                }
                else -> {}
            }
            redraw()
        }

        private fun continueDrawing(x: Float, y: Float) {
            when (currentTool) {
                AnnotationTool.FREE_DRAW -> {
                    val midX = (prevX + x) / 2f
                    val midY = (prevY + y) / 2f
                    currentPath.quadTo(prevX, prevY, midX, midY)
                    prevX = x
                    prevY = y
                    redraw()
                }

                AnnotationTool.LINE -> {
                    currentPath.reset()
                    currentPath.moveTo(startX, startY)
                    currentPath.lineTo(x, y)
                    redraw()
                }

                AnnotationTool.RECTANGLE -> {
                    currentPath.reset()
                    currentPath.addRect(startX, startY, x, y, Path.Direction.CW)
                    redraw()
                }

                AnnotationTool.CIRCLE -> {
                    currentPath.reset()
                    val radius = kotlin.math.sqrt(
                        (x - startX) * (x - startX) + (y - startY) * (y - startY)
                    )
                    currentPath.addCircle(startX, startY, radius, Path.Direction.CW)
                    redraw()
                }

                AnnotationTool.ARROW -> {
                    currentPath.reset()
                    drawArrow(currentPath, startX, startY, x, y)
                    redraw()
                }

                AnnotationTool.SELECTION, AnnotationTool.ERASER -> {}
            }
        }

        private fun finishDrawing(x: Float, y: Float) {
            if (isDrawing && currentTool != AnnotationTool.SELECTION && currentTool != AnnotationTool.ERASER) {
                if (currentTool == AnnotationTool.FREE_DRAW) {
                    currentPath.lineTo(x, y)
                }

                val newPaint = Paint(paint)
                val newPath = DrawingPath(Path(currentPath), newPaint)
                paths.add(newPath)
                drawingCanvas?.drawPath(currentPath, paint)

                // Add to action history
                val action = DrawingAction.AddPath(newPath)
                actionHistory.add(action)
                redoHistory.clear() // Clear redo history when a new action is performed

                currentPath.reset()
            }
            isDrawing = false
            onDrawingStateChanged?.invoke(false)
            redraw()
        }

        private fun drawArrow(path: Path, startX: Float, startY: Float, endX: Float, endY: Float) {
            path.moveTo(startX, startY)
            path.lineTo(endX, endY)

            val arrowLength = 30f
            val arrowAngle = Math.PI / 6

            val angle = kotlin.math.atan2((endY - startY).toDouble(), (endX - startX).toDouble())

            val arrowX1 = endX - arrowLength * kotlin.math.cos(angle - arrowAngle).toFloat()
            val arrowY1 = endY - arrowLength * kotlin.math.sin(angle - arrowAngle).toFloat()

            val arrowX2 = endX - arrowLength * kotlin.math.cos(angle + arrowAngle).toFloat()
            val arrowY2 = endY - arrowLength * kotlin.math.sin(angle + arrowAngle).toFloat()

            path.moveTo(endX, endY)
            path.lineTo(arrowX1, arrowY1)
            path.moveTo(endX, endY)
            path.lineTo(arrowX2, arrowY2)
        }

        fun setDrawingTool(tool: AnnotationTool) {
            currentTool = tool

            when (tool) {
                AnnotationTool.FREE_DRAW, AnnotationTool.LINE, AnnotationTool.ARROW -> {
                    paint.style = Paint.Style.STROKE
                }

                AnnotationTool.RECTANGLE, AnnotationTool.CIRCLE -> {
                    paint.style = Paint.Style.STROKE
                }

                AnnotationTool.ERASER, AnnotationTool.SELECTION -> {}
            }

            redraw()
        }

        fun clearAllDrawings() {
            paths.clear()
            currentPath.reset()
            actionHistory.clear()
            redoHistory.clear()
            selectedPath = null
            drawingCanvas?.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            redraw()
        }

        fun undoLastAction() {
            if (actionHistory.isNotEmpty()) {
                val action = actionHistory.removeAt(actionHistory.size - 1)
                redoHistory.add(action)

                when (action) {
                    is DrawingAction.AddPath -> {
                        // Undo adding a path by removing it
                        paths.remove(action.path)
                        if (selectedPath == action.path) {
                            selectedPath = null
                        }
                    }
                    is DrawingAction.RemovePath -> {
                        // Undo removing a path by adding it back at its original position
                        paths.add(action.index, action.path)
                    }
                }

                // Redraw all paths
                drawingCanvas?.let { canvas ->
                    canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                    paths.forEach { drawingPath ->
                        canvas.drawPath(drawingPath.path, drawingPath.paint)
                    }
                }

                redraw()
            }
        }

        fun redoLastAction() {
            if (redoHistory.isNotEmpty()) {
                val action = redoHistory.removeAt(redoHistory.size - 1)
                actionHistory.add(action)

                when (action) {
                    is DrawingAction.AddPath -> {
                        // Redo adding a path
                        paths.add(action.path)
                        drawingCanvas?.drawPath(action.path.path, action.path.paint)
                    }
                    is DrawingAction.RemovePath -> {
                        // Redo removing a path
                        paths.remove(action.path)
                    }
                }

                // Redraw all paths
                drawingCanvas?.let { canvas ->
                    canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                    paths.forEach { drawingPath ->
                        canvas.drawPath(drawingPath.path, drawingPath.paint)
                    }
                }

                redraw()
            }
        }

        fun clearSelection() {
            selectedPath = null
            isResizing = false
            redraw()
        }

        inner class DrawingThread : Thread() {
            private var running = false

            fun startDrawing() {
                running = true
                start()
            }

            fun stopDrawing() {
                running = false
            }

            override fun run() {
                while (running) {
                    if (isDrawing) {
                        redraw()
                    }
                    try {
                        sleep(16) // ~60 FPS
                    } catch (e: InterruptedException) {
                        break
                    }
                }
            }
        }
    }
}