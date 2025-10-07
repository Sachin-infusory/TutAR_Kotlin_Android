package com.infusory.tutarapp.managers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.view.SurfaceView
import android.view.View
import android.widget.Toast
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val cameraPreviewView: PreviewView,
    private val surfaceView: SurfaceView
) {
    companion object {
        const val CAMERA_PERMISSION_REQUEST_CODE = 100
    }

    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var camera: Camera? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var isCameraActive = false

    var onCameraStateChanged: ((Boolean) -> Unit)? = null

    fun isCameraActive() = isCameraActive

    fun toggleCamera() {
        if (isCameraActive) {
            stopCamera()
        } else {
            startCamera()
        }
    }

    fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()

                preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(cameraPreviewView.surfaceProvider)
                }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    cameraProvider?.unbindAll()

                    camera = cameraProvider?.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview
                    )

                    cameraPreviewView.visibility = View.VISIBLE
                    surfaceView.visibility = View.GONE

                    isCameraActive = true
                    onCameraStateChanged?.invoke(true)

                    Toast.makeText(
                        context,
                        "Camera activated - AR mode enabled",
                        Toast.LENGTH_SHORT
                    ).show()

                } catch (exc: Exception) {
                    Toast.makeText(
                        context,
                        "Failed to start camera: ${exc.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }

            } catch (exc: Exception) {
                Toast.makeText(
                    context,
                    "Camera initialization failed: ${exc.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun stopCamera() {
        try {
            cameraProvider?.unbindAll()

            cameraPreviewView.visibility = View.GONE
            surfaceView.visibility = View.VISIBLE

            isCameraActive = false
            onCameraStateChanged?.invoke(false)

            Toast.makeText(context, "Camera deactivated - Normal mode", Toast.LENGTH_SHORT).show()

        } catch (exc: Exception) {
            Toast.makeText(
                context,
                "Failed to stop camera: ${exc.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun shutdown() {
        stopCamera()
        cameraExecutor.shutdown()
    }
}