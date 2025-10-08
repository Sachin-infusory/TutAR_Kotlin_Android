package com.infusory.tutarapp.filament//import com.google.android.filament.utils.ModelViewer
//import android.view.SurfaceView
//import com.google.android.filament.utils.GestureDetector
//import android.view.MotionEvent
//import com.google.android.filament.Camera
//import com.google.android.filament.utils.Float2
//import com.google.android.filament.utils.manipulators.Manipulator
//
//class ModelViewer(surfaceView: SurfaceView, uiHelper: UiHelper) : ModelViewer(surfaceView, uiHelper) {
//
//    // Adjust this value to increase/decrease zoom sensitivity
//    private val ZOOM_SENSITIVITY = 2.0f  // Increase from default (e.g., default might be around 1.0)
//
//    override fun onTouchEvent(event: MotionEvent): Boolean {
//        // Delegate to GestureDetector but intercept pinch gestures
//        val gestureDetector = gestureDetector
//        if (event.pointerCount == 2) {
//            // Handle pinch gesture manually
//            when (event.actionMasked) {
//                MotionEvent.ACTION_MOVE -> {
//                    val pointer1 = Float2(event.getX(0), event.getY(0))
//                    val pointer2 = Float2(event.getX(1), event.getY(1))
//                    val prevPointer1 = Float2(event.getHistoricalX(0, 0), event.getHistoricalY(0, 0))
//                    val prevPointer2 = Float2(event.getHistoricalX(1, 0), event.getHistoricalY(1, 0))
//
//                    val currentDistance = calculateDistance(pointer1, pointer2)
//                    val prevDistance = calculateDistance(prevPointer1, prevPointer2)
//
//                    if (currentDistance > 0 && prevDistance > 0) {
//                        val scale = currentDistance / prevDistance
//                        applyZoom(scale)
//                    }
//                    return true
//                }
//            }
//        }
//        // Fallback to default gesture handling for other events
//        return gestureDetector.onTouchEvent(event)
//    }
//
//    private fun calculateDistance(p1: Float2, p2: Float2): Float {
//        val dx = p2.x - p1.x
//        val dy = p2.y - p1.y
//        return kotlin.math.sqrt(dx * dx + dy * dy)
//    }
//
//    private fun applyZoom(scale: Float) {
//        // Apply custom zoom sensitivity
//        val adjustedScale = if (scale > 1.0f) {
//            1.0f + (scale - 1.0f) * ZOOM_SENSITIVITY
//        } else {
//            1.0f - (1.0f - scale) * ZOOM_SENSITIVITY
//        }
//
//        // Adjust camera position
//        val camera = view.camera
//        val manipulator = manipulator
//        manipulator?.let {
//            val currentDistance = manipulator.targetPosition.z
//            val newDistance = currentDistance / adjustedScale
//            manipulator.setTargetPosition(
//                manipulator.targetPosition.x,
//                manipulator.targetPosition.y,
//                newDistance
//            )
//        }
//    }
//}