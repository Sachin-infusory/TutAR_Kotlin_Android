// AnnotationToolView.kt - Main View with Drawing Logic
package com.infusory.tutarapp.ui.components.annotation

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.RelativeLayout

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

// Main AnnotationToolView class
class AnnotationToolView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : RelativeLayout(context, attrs, defStyleAttr) {

    private var drawingView: DrawingSurfaceView? = null
    private var annotationToolbar: AnnotationToolbar? = null
    private var isAnnotationMode = false

    var onAnnotationToggle: ((Boolean) -> Unit)? = null
    var onDrawingStateChanged: ((isDrawing: Boolean) -> Unit)? = null

    init {
        setupAnnotationTool()
    }

    private fun setupAnnotationTool() {
        drawingView = DrawingSurfaceView(context)
        val drawingParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        drawingView?.layoutParams = drawingParams
        drawingView?.visibility = View.VISIBLE
        drawingView?.setTouchEnabled(false)
        addView(drawingView)

        annotationToolbar = AnnotationToolbar(context)
        val toolbarParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        toolbarParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
        toolbarParams.addRule(RelativeLayout.CENTER_HORIZONTAL)
        toolbarParams.bottomMargin = 120
        annotationToolbar?.layoutParams = toolbarParams
        annotationToolbar?.visibility = View.GONE
        addView(annotationToolbar)

        annotationToolbar?.onToolSelected = { tool: AnnotationTool ->
            drawingView?.clearSelection()
            drawingView?.setDrawingTool(tool)
        }

        annotationToolbar?.onColorChanged = { color ->
            drawingView?.setDrawingColor(color)
        }

        annotationToolbar?.onStrokeWidthChanged = { width ->
            drawingView?.setStrokeWidth(width)
        }

        annotationToolbar?.onUndoPressed = {
            drawingView?.undoLastAction()
        }

        annotationToolbar?.onRedoPressed = {
            drawingView?.redoLastAction()
        }

        annotationToolbar?.onClearPressed = {
            drawingView?.clearSelection()
            drawingView?.clearAllDrawings()
        }

        annotationToolbar?.onCloseAnnotation = {
            toggleAnnotationMode(false)
        }

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
            annotationToolbar?.dismissColorPicker()
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

    // DrawingSurfaceView - handles all drawing logic
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

        private var actionHistory = mutableListOf<DrawingAction>()
        private var redoHistory = mutableListOf<DrawingAction>()

        private var drawingBitmap: Bitmap? = null
        private var drawingCanvas: Canvas? = null
        private var drawingThread: DrawingThread? = null
        private var surfaceReady = false

        private val handleSize = 30f
        private var lastErasedPath: DrawingPath? = null

        var onDrawingStateChanged: ((Boolean) -> Unit)? = null

        init {
            holder.addCallback(this)
            setZOrderOnTop(true)
            holder.setFormat(PixelFormat.TRANSPARENT)
        }

        fun setDrawingColor(color: Int) {
            paint.color = color
        }

        fun setStrokeWidth(width: Float) {
            paint.strokeWidth = width
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
                    c.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

                    drawingBitmap?.let { bitmap ->
                        c.drawBitmap(bitmap, 0f, 0f, null)
                    }

                    if (isDrawing && touchEnabled && currentTool != AnnotationTool.SELECTION && currentTool != AnnotationTool.ERASER) {
                        c.drawPath(currentPath, paint)
                    }

                    if (touchEnabled && currentTool == AnnotationTool.ERASER) {
                        drawEraserCursor(c)
                    }

                    selectedPath?.let {
                        drawSelection(c, it)
                    }
                }
            } finally {
                canvas?.let { holder.unlockCanvasAndPost(it) }
            }
        }

        private fun drawEraserCursor(canvas: Canvas) {
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
            canvas.drawCircle(eraserCursorX, eraserCursorY, radius, cursorPaint)
            canvas.drawCircle(eraserCursorX, eraserCursorY, radius, strokePaint)

            // Draw crosshair
            val crosshairSize = 10f
            canvas.drawLine(
                eraserCursorX - crosshairSize, eraserCursorY,
                eraserCursorX + crosshairSize, eraserCursorY,
                strokePaint
            )
            canvas.drawLine(
                eraserCursorX, eraserCursorY - crosshairSize,
                eraserCursorX, eraserCursorY + crosshairSize,
                strokePaint
            )
        }

        private fun drawSelection(canvas: Canvas, path: DrawingPath) {
            val highlightPaint = Paint().apply {
                color = Color.BLUE
                style = Paint.Style.STROKE
                strokeWidth = 3f
                pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
            }
            path.updateBounds()
            canvas.drawRect(path.bounds, highlightPaint)

            // Draw resize handle
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

            val handleCenterX = path.bounds.right
            val handleCenterY = path.bounds.bottom
            val handleRadius = handleSize / 2

            canvas.drawCircle(handleCenterX, handleCenterY, handleRadius, handlePaint)
            canvas.drawCircle(handleCenterX, handleCenterY, handleRadius, handleStrokePaint)

            // Draw crosshair in handle
            val crossSize = 6f
            canvas.drawLine(
                handleCenterX - crossSize, handleCenterY,
                handleCenterX + crossSize, handleCenterY,
                handleStrokePaint
            )
            canvas.drawLine(
                handleCenterX, handleCenterY - crossSize,
                handleCenterX, handleCenterY + crossSize,
                handleStrokePaint
            )
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (!touchEnabled) return false

            val x = event.x
            val y = event.y

            if (currentTool == AnnotationTool.ERASER) {
                eraserCursorX = x
                eraserCursorY = y
                redraw()
            }

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    return when (currentTool) {
                        AnnotationTool.SELECTION -> {
                            handleSelectionDown(x, y)
                            true
                        }
                        AnnotationTool.ERASER -> {
                            handleEraserTouch(x, y)
                            true
                        }
                        else -> {
                            startDrawing(x, y)
                            true
                        }
                    }
                }

                MotionEvent.ACTION_MOVE -> {
                    return when (currentTool) {
                        AnnotationTool.SELECTION -> {
                            handleSelectionMove(x, y)
                            true
                        }
                        AnnotationTool.ERASER -> {
                            handleEraserTouch(x, y)
                            true
                        }
                        else -> {
                            continueDrawing(x, y)
                            true
                        }
                    }
                }

                MotionEvent.ACTION_UP -> {
                    return when (currentTool) {
                        AnnotationTool.SELECTION -> {
                            isResizing = false
                            true
                        }
                        AnnotationTool.ERASER -> {
                            lastErasedPath = null
                            true
                        }
                        else -> {
                            finishDrawing(x, y)
                            true
                        }
                    }
                }
            }
            return false
        }

        private fun handleEraserTouch(x: Float, y: Float) {
            var pathToRemove: DrawingPath? = null
            var pathIndex = -1

            for (i in paths.indices.reversed()) {
                val path = paths[i]
                path.updateBounds()
                val expandedBounds = RectF(path.bounds).apply {
                    inset(-20f, -20f)
                }

                if (expandedBounds.contains(x, y)) {
                    pathToRemove = path
                    pathIndex = i
                    break
                }
            }

            if (pathToRemove != null && pathToRemove != lastErasedPath && pathIndex != -1) {
                lastErasedPath = pathToRemove
                paths.removeAt(pathIndex)

                val action = DrawingAction.RemovePath(pathToRemove, pathIndex)
                actionHistory.add(action)
                redoHistory.clear()

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

            // Check if touching a resize handle
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

            // Check if touching a path for moving
            for (path in paths.reversed()) {
                path.updateBounds()
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
                    val newWidth = path.bounds.width() + dx
                    val newHeight = path.bounds.height() + dy

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
                    val matrix = Matrix().apply { setTranslate(dx, dy) }
                    path.path.transform(matrix)
                    path.updateBounds()
                }

                lastTouchX = x
                lastTouchY = y

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

                val action = DrawingAction.AddPath(newPath)
                actionHistory.add(action)
                redoHistory.clear()

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
                        paths.remove(action.path)
                        if (selectedPath == action.path) {
                            selectedPath = null
                        }
                    }
                    is DrawingAction.RemovePath -> {
                        paths.add(action.index, action.path)
                    }
                }

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
                        paths.add(action.path)
                        drawingCanvas?.drawPath(action.path.path, action.path.paint)
                    }
                    is DrawingAction.RemovePath -> {
                        paths.remove(action.path)
                    }
                }

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