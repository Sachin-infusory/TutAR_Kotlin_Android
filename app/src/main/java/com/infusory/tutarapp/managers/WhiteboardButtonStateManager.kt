package com.infusory.tutarapp.managers

import android.content.Context
import android.view.View
import android.widget.ImageButton
import androidx.core.content.ContextCompat
import com.infusory.tutarapp.R

class WhiteboardButtonStateManager(private val context: Context) {

    private val pairedButtonsState = mutableMapOf<Int, Boolean>()

    // Store references to paired buttons across toolbars
    private val buttonPairs = mutableMapOf<ImageButton, ImageButton>()

    // Individual buttons that toggle independently (no pairing needed)
    private val individualButtonIds = listOf(
        R.id.color_plate,
        R.id.btn_save,
        R.id.btn_insert
    )

    // Paired button IDs (buttons that should sync across left/right toolbars)
    private val pairedButtonIds = listOf(
        R.id.btn_draw,
        R.id.btn_ar,
        R.id.btn_menu,
        R.id.btn_setting,
        R.id.btn_load_lesson
    )

    /**
     * Register a pair of buttons from left and right toolbars
     * This allows them to sync their states
     */
    fun registerButtonPair(leftButton: ImageButton, rightButton: ImageButton) {
        buttonPairs[leftButton] = rightButton
        buttonPairs[rightButton] = leftButton
    }

    /**
     * Setup a button with its click listener
     */
    fun setupButton(button: ImageButton, onClick: (Boolean) -> Unit) {
        val id = button.id

        button.setOnClickListener {
            if (individualButtonIds.contains(id)) {
                handleIndividualButton(button, onClick)
            } else if (pairedButtonIds.contains(id)) {
                handlePairedButton(button, onClick)
            } else {
                // Default to individual behavior
                handleIndividualButton(button, onClick)
            }
        }
    }

    private fun handleIndividualButton(button: ImageButton, onClick: (Boolean) -> Unit) {
        val isActive = button.tag as? Boolean ?: false
        val newState = !isActive
        button.tag = newState

        updateButtonVisual(button, newState)
        onClick(newState)
    }

    private fun handlePairedButton(button: ImageButton, onClick: (Boolean) -> Unit) {
        val id = button.id
        val currentState = pairedButtonsState[id] ?: false
        val newState = !currentState

        // Update state for this button ID
        pairedButtonsState[id] = newState

        // Update visual for both the clicked button and its pair
        updateButtonVisual(button, newState)
        buttonPairs[button]?.let { pairedButton ->
            updateButtonVisual(pairedButton, newState)
        }

        onClick(newState)
    }

    private fun updateButtonVisual(button: ImageButton, isActive: Boolean) {
        button.tag = isActive

        if (isActive) {
            button.background = ContextCompat.getDrawable(
                context,
                R.drawable.circular_button_background
            )?.apply {
                setTint(ContextCompat.getColor(context, android.R.color.holo_blue_light))
            }
        } else {
            button.background = ContextCompat.getDrawable(
                context,
                R.drawable.circular_button_background
            )
        }
    }

    /**
     * Deactivate a button and its pair
     */
    fun deactivateButton(button: ImageButton) {
        val id = button.id
        pairedButtonsState[id] = false

        updateButtonVisual(button, false)
        buttonPairs[button]?.let { pairedButton ->
            updateButtonVisual(pairedButton, false)
        }
    }

    /**
     * Deactivate multiple buttons
     */
    fun deactivateButtons(vararg buttons: ImageButton) {
        buttons.forEach { deactivateButton(it) }
    }

    /**
     * Reset all button states
     */
    fun resetAllButtons() {
        pairedButtonsState.clear()
        buttonPairs.keys.forEach { button ->
            updateButtonVisual(button, false)
        }
    }

    /**
     * Get the current state of a button by ID
     */
    fun getButtonState(buttonId: Int): Boolean {
        return pairedButtonsState[buttonId] ?: false
    }

    /**
     * Clear all registered button pairs (useful for cleanup)
     */
    fun clearButtonPairs() {
        buttonPairs.clear()
    }
}