package com.infusory.tutarapp.ui.assets

import android.Manifest
import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.infusory.tutarapp.R
import com.infusory.tutarapp.databinding.ActivityAssetLoaderBinding
import com.infusory.tutarapp.ui.whiteboard.WhiteboardActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class AssetLoaderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAssetLoaderBinding
    private lateinit var sharedPreferences: SharedPreferences
    private var selectedZipUri: Uri? = null

    companion object {
        private const val PREF_NAME = "TutarAppPreferences"
        private const val KEY_ASSETS_LOADED = "assets_loaded"
        private const val KEY_ASSET_PATH = "asset_path"
        private const val MODELS_FOLDER_NAME = "encrypted_models"
        private const val TAG = "AssetLoaderActivity"
    }

    // Permission launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openFilePicker()
        } else {
            showError("Storage permission is required to load 3D assets")
        }
    }

    // File picker launcher
    private val pickZipFile = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            handleSelectedZipFile(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPreferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        // Check if assets are already loaded
        if (areAssetsLoaded()) {
            navigateToWhiteboard()
            return
        }

        binding = ActivityAssetLoaderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        setupUI()
        startEnterAnimations()
    }

    private fun areAssetsLoaded(): Boolean {
        return sharedPreferences.getBoolean(KEY_ASSETS_LOADED, false)
    }

    private fun saveAssetLoadedState(assetPath: String) {
        val editor = sharedPreferences.edit()
        editor.putBoolean(KEY_ASSETS_LOADED, true)
        editor.putString(KEY_ASSET_PATH, assetPath)
        editor.apply()
        Log.d(TAG, "Assets loaded state saved. Path: $assetPath")
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setupUI() {
        // Request permission button
        binding.btnRequestPermission.setOnClickListener {
            requestStoragePermission()
        }

        // Pick file button
        binding.btnPickFile.setOnClickListener {
            if (hasStoragePermission()) {
                openFilePicker()
            } else {
                requestStoragePermission()
            }
        }

        // Load assets button
        binding.btnLoadAssets.setOnClickListener {
            if (selectedZipUri != null) {
                loadAssets()
            } else {
                showError("Please select a ZIP file first")
            }
        }

        // Skip button
        binding.tvSkip.setOnClickListener {
            navigateToWhiteboard()
        }

        // Update UI based on permission status
        updateUIBasedOnPermission()
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ doesn't need READ_EXTERNAL_STORAGE for file picker
            true
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ doesn't need permission for file picker
            openFilePicker()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun openFilePicker() {
        pickZipFile.launch("application/zip")
    }

    private fun handleSelectedZipFile(uri: Uri) {
        selectedZipUri = uri

        // Get file name
        val fileName = getFileName(uri)

        // Update UI to show selected file
        binding.tvSelectedFile.text = fileName
        binding.tvSelectedFile.visibility = View.VISIBLE
        binding.ivCheckmark.visibility = View.VISIBLE

        // Enable load button
        binding.btnLoadAssets.isEnabled = true
        binding.btnLoadAssets.alpha = 1.0f

        // Animate the selected file info
        animateFileSelected()

        Toast.makeText(this, "ZIP file selected: $fileName", Toast.LENGTH_SHORT).show()
    }

    private fun getFileName(uri: Uri): String {
        var fileName = "Unknown"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex != -1) {
                fileName = cursor.getString(nameIndex)
            }
        }
        return fileName
    }

    private fun loadAssets() {
        selectedZipUri?.let { uri ->
            showLoading()

            // Use coroutine to extract ZIP in background
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val extractionResult = extractZipFile(uri) { progress, currentFile ->
                        // Update UI on main thread
                        CoroutineScope(Dispatchers.Main).launch {
                            updateProgress(progress, currentFile)
                        }
                    }

                    withContext(Dispatchers.Main) {
                        hideLoading()

                        if (extractionResult.success) {
                            Toast.makeText(
                                this@AssetLoaderActivity,
                                "Assets loaded successfully!",
                                Toast.LENGTH_SHORT
                            ).show()

                            // Save state and navigate
                            saveAssetLoadedState(extractionResult.path)
                            navigateToWhiteboard()
                        } else {
                            showError(extractionResult.errorMessage ?: "Failed to load assets")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading assets", e)
                    withContext(Dispatchers.Main) {
                        hideLoading()
                        showError("Error: ${e.message}")
                    }
                }
            }
        }
    }

    private fun extractZipFile(
        uri: Uri,
        onProgress: (progress: Int, currentFile: String) -> Unit
    ): ExtractionResult {
        var inputStream: InputStream? = null
        var zipInputStream: ZipInputStream? = null

        try {
            // Get the app's internal storage directory
            val appStorageDir = filesDir
            val modelsDir = File(appStorageDir, MODELS_FOLDER_NAME)

            // Delete existing folder if it exists
            if (modelsDir.exists()) {
                Log.d(TAG, "Deleting existing models directory")
                modelsDir.deleteRecursively()
            }

            // Create the models directory
            if (!modelsDir.mkdirs() && !modelsDir.exists()) {
                return ExtractionResult(false, "", "Failed to create models directory")
            }

            Log.d(TAG, "Created models directory: ${modelsDir.absolutePath}")

            // First pass: Count total entries in encrypted_models folder
            inputStream = contentResolver.openInputStream(uri)
                ?: return ExtractionResult(false, "", "Failed to open ZIP file")

            zipInputStream = ZipInputStream(inputStream)
            var totalEntries = 0
            var zipEntry: ZipEntry? = zipInputStream.nextEntry

            while (zipEntry != null) {
                if (zipEntry.name.contains(MODELS_FOLDER_NAME) && !zipEntry.isDirectory) {
                    val relativePath = if (zipEntry.name.contains("$MODELS_FOLDER_NAME/")) {
                        zipEntry.name.substringAfter("$MODELS_FOLDER_NAME/")
                    } else {
                        zipEntry.name.substringAfter(MODELS_FOLDER_NAME)
                    }
                    if (relativePath.isNotEmpty()) {
                        totalEntries++
                    }
                }
                zipInputStream.closeEntry()
                zipEntry = zipInputStream.nextEntry
            }

            zipInputStream.close()
            inputStream.close()

            Log.d(TAG, "Total files to extract: $totalEntries")

            if (totalEntries == 0) {
                return ExtractionResult(
                    false,
                    "",
                    "No files found in '$MODELS_FOLDER_NAME' folder"
                )
            }

            // Second pass: Extract files with progress
            inputStream = contentResolver.openInputStream(uri)
                ?: return ExtractionResult(false, "", "Failed to open ZIP file")

            zipInputStream = ZipInputStream(inputStream)
            var filesExtracted = 0
            zipEntry = zipInputStream.nextEntry

            while (zipEntry != null) {
                val entryName = zipEntry.name
                Log.d(TAG, "Processing ZIP entry: $entryName")

                // Check if the entry is inside the encrypted_models folder
                if (entryName.contains(MODELS_FOLDER_NAME)) {
                    // Extract the relative path after encrypted_models/
                    val relativePath = if (entryName.contains("$MODELS_FOLDER_NAME/")) {
                        entryName.substringAfter("$MODELS_FOLDER_NAME/")
                    } else {
                        entryName.substringAfter(MODELS_FOLDER_NAME)
                    }

                    if (relativePath.isNotEmpty()) {
                        val outputFile = File(modelsDir, relativePath)

                        if (zipEntry.isDirectory) {
                            // Create directory
                            outputFile.mkdirs()
                            Log.d(TAG, "Created directory: ${outputFile.absolutePath}")
                        } else {
                            // Create parent directories if needed
                            outputFile.parentFile?.mkdirs()

                            // Extract file
                            FileOutputStream(outputFile).use { outputStream ->
                                val buffer = ByteArray(8192)
                                var bytesRead: Int
                                while (zipInputStream.read(buffer).also { bytesRead = it } != -1) {
                                    outputStream.write(buffer, 0, bytesRead)
                                }
                            }
                            filesExtracted++

                            // Calculate and report progress
                            val progress = ((filesExtracted.toFloat() / totalEntries) * 100).toInt()
                            val fileName = outputFile.name
                            onProgress(progress, fileName)

                            Log.d(TAG, "Extracted file ($filesExtracted/$totalEntries): ${outputFile.absolutePath}")
                        }
                    }
                }

                zipInputStream.closeEntry()
                zipEntry = zipInputStream.nextEntry
            }

            Log.d(TAG, "Extraction completed. Files extracted: $filesExtracted")

            return if (filesExtracted > 0) {
                ExtractionResult(true, modelsDir.absolutePath, null)
            } else {
                ExtractionResult(
                    false,
                    "",
                    "No files found in '$MODELS_FOLDER_NAME' folder"
                )
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error extracting ZIP file", e)
            return ExtractionResult(false, "", "Extraction error: ${e.message}")
        } finally {
            try {
                zipInputStream?.close()
                inputStream?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing streams", e)
            }
        }
    }

    private fun updateProgress(progress: Int, currentFile: String) {
        binding.progressBar.progress = progress
        binding.tvLoadingStatus.text = "Extracting: $currentFile\n$progress%"
    }

    private data class ExtractionResult(
        val success: Boolean,
        val path: String,
        val errorMessage: String?
    )

    private fun updateUIBasedOnPermission() {
        val hasPermission = hasStoragePermission()

        if (hasPermission) {
            binding.btnRequestPermission.visibility = View.GONE
            binding.btnPickFile.visibility = View.VISIBLE
            binding.ivPermissionIcon.setImageResource(R.drawable.rounded_background)
            binding.tvPermissionStatus.text = "Storage Access Granted"
            binding.tvPermissionStatus.setTextColor(ContextCompat.getColor(this, R.color.success_green))
        } else {
            binding.btnRequestPermission.visibility = View.VISIBLE
            binding.btnPickFile.visibility = View.GONE
            binding.ivPermissionIcon.setImageResource(R.drawable.ic_lock)
            binding.tvPermissionStatus.text = "Storage Access Required"
            binding.tvPermissionStatus.setTextColor(ContextCompat.getColor(this, R.color.splash_primary))
        }
    }

    private fun animateFileSelected() {
        binding.tvSelectedFile.alpha = 0f
        binding.tvSelectedFile.translationY = 20f
        binding.ivCheckmark.alpha = 0f
        binding.ivCheckmark.scaleX = 0.5f
        binding.ivCheckmark.scaleY = 0.5f

        binding.tvSelectedFile.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(400)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        binding.ivCheckmark.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(400)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }

    private fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
        binding.progressBar.progress = 0
        binding.tvLoadingStatus.visibility = View.VISIBLE
        binding.tvLoadingStatus.text = "Preparing extraction..."
        binding.btnLoadAssets.text = ""
        binding.btnLoadAssets.isEnabled = false
        binding.btnPickFile.isEnabled = false
        binding.tvSkip.isEnabled = false
        binding.tvSkip.alpha = 0.5f
    }

    private fun hideLoading() {
        binding.progressBar.visibility = View.GONE
        binding.tvLoadingStatus.visibility = View.GONE
        binding.btnLoadAssets.text = "LOAD ASSETS"
        binding.btnLoadAssets.isEnabled = true
        binding.btnPickFile.isEnabled = true
        binding.tvSkip.isEnabled = true
        binding.tvSkip.alpha = 1f
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()

        // Shake animation for error
        val shakeAnimator = ObjectAnimator.ofFloat(
            binding.cardAssetLoader,
            "translationX",
            0f, 25f, -25f, 25f, -25f, 15f, -15f, 6f, -6f, 0f
        )
        shakeAnimator.duration = 600
        shakeAnimator.start()
    }

    private fun navigateToWhiteboard() {
        startActivity(Intent(this, WhiteboardActivity::class.java))
        overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
        finish()
    }

    private fun startEnterAnimations() {
        // Initially hide views
        binding.ivLogo.alpha = 0f
        binding.ivLogo.translationY = -50f

        binding.tvTitle.alpha = 0f
        binding.tvTitle.translationY = -30f

        binding.tvSubtitle.alpha = 0f
        binding.tvSubtitle.translationY = -20f

        binding.cardAssetLoader.alpha = 0f
        binding.cardAssetLoader.translationY = 100f

        binding.tvSkip.alpha = 0f

        // Animate logo
        binding.ivLogo.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(600)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        // Animate title
        binding.tvTitle.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(600)
            .setStartDelay(200)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        // Animate subtitle
        binding.tvSubtitle.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(600)
            .setStartDelay(300)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        // Animate asset loader card
        binding.cardAssetLoader.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(800)
            .setStartDelay(600)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        // Animate skip button
        binding.tvSkip.animate()
            .alpha(1f)
            .setDuration(600)
            .setStartDelay(1000)
            .start()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(android.R.anim.slide_out_right, android.R.anim.slide_in_left)
    }
}