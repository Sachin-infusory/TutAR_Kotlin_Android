package com.infusory.tutarapp.managers

import android.content.Context
import android.widget.Toast
import com.infusory.tutarapp.ui.components.containers.ContainerManager

class WhiteboardStateManager(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "whiteboard_state"
        private const val KEY_CONTAINER_COUNT = "container_count"
        private const val KEY_CAMERA_ACTIVE = "camera_active"
    }

    private val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveState(containerManager: ContainerManager, isCameraActive: Boolean) {
        val stateData = containerManager.saveState()
        val editor = sharedPrefs.edit()

        editor.putInt(KEY_CONTAINER_COUNT, stateData.containers.size)
        editor.putBoolean(KEY_CAMERA_ACTIVE, isCameraActive)

        stateData.containers.forEachIndexed { index, containerState ->
            editor.putString("container_${index}_type", containerState.type.name)
            editor.putFloat("container_${index}_x", containerState.position.first)
            editor.putFloat("container_${index}_y", containerState.position.second)
            editor.putFloat("container_${index}_scale", containerState.scale)
            editor.putInt("container_${index}_width", containerState.size.first)
            editor.putInt("container_${index}_height", containerState.size.second)

            // Save custom data
            containerState.customData.forEach { (key, value) ->
                when (value) {
                    is String -> editor.putString("container_${index}_$key", value)
                    is Int -> editor.putInt("container_${index}_$key", value)
                    is Float -> editor.putFloat("container_${index}_$key", value)
                    is Boolean -> editor.putBoolean("container_${index}_$key", value)
                }
            }
        }

        editor.apply()
    }

    fun loadState(onRestore: () -> Unit, onStartFresh: () -> Unit) {
        val containerCount = sharedPrefs.getInt(KEY_CONTAINER_COUNT, 0)

        if (containerCount > 0) {
            android.app.AlertDialog.Builder(context)
                .setTitle("Restore Previous Session?")
                .setMessage("Found $containerCount saved containers. Would you like to restore them?")
                .setPositiveButton("Restore") { _, _ ->
                    onRestore()
                    // Note: Full restoration logic would need to be implemented
                    Toast.makeText(
                        context,
                        "State restoration not fully implemented",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                .setNegativeButton("Start Fresh") { _, _ ->
                    clearState()
                    onStartFresh()
                }
                .show()
        }
    }

    fun hasStoredState(): Boolean {
        return sharedPrefs.getInt(KEY_CONTAINER_COUNT, 0) > 0
    }

    fun clearState() {
        sharedPrefs.edit().clear().apply()
    }

    fun getContainerCount(): Int {
        return sharedPrefs.getInt(KEY_CONTAINER_COUNT, 0)
    }

    fun wasCameraActive(): Boolean {
        return sharedPrefs.getBoolean(KEY_CAMERA_ACTIVE, false)
    }
}