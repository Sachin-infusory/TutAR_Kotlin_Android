package com.infusory.tutarapp.ui.auth

import android.os.Bundle
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.infusory.tutarapp.databinding.ActivitySessionExpiredBinding

class SessionExpiredActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySessionExpiredBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySessionExpiredBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        startEnterAnimations()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun startEnterAnimations() {
        // Initially hide views
        binding.ivExpiredIcon.alpha = 0f
        binding.ivExpiredIcon.scaleX = 0f
        binding.ivExpiredIcon.scaleY = 0f

        binding.tvTitle.alpha = 0f
        binding.tvTitle.translationY = -50f

        binding.tvMessage.alpha = 0f
        binding.tvMessage.translationY = 50f

        // Animate icon
        binding.ivExpiredIcon.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
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

        // Animate message
        binding.tvMessage.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(600)
            .setStartDelay(400)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        // Close the application when back is pressed
        finishAffinity() // This closes all activities and exits the app
    }
}