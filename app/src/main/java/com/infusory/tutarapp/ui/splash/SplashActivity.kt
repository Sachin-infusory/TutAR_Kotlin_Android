package com.infusory.tutarapp.ui.splash

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.BounceInterpolator
import android.view.animation.OvershootInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.doOnEnd
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.infusory.tutarapp.R
import com.infusory.tutarapp.databinding.ActivitySplashBinding
import com.infusory.tutarapp.ui.auth.LoginActivity
import com.infusory.tutarapp.ui.whiteboard.WhiteboardActivity

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private val splashDuration = 3000L // 3 seconds total

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Make it fullscreen
        window.statusBarColor = getColor(R.color.splash_background_end)

        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        startAnimations()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun startAnimations() {
        // Initially hide all views
        binding.ivLogo.alpha = 0f
        binding.ivLogo.scaleX = 0f
        binding.ivLogo.scaleY = 0f

        binding.tvAppName.alpha = 0f
        binding.tvAppName.translationY = 100f

        binding.tvTagline.alpha = 0f
        binding.tvTagline.translationY = 50f

        // Start logo animation after a short delay
        Handler(Looper.getMainLooper()).postDelayed({
            animateLogo()
        }, 200)

        // Start text animations
        Handler(Looper.getMainLooper()).postDelayed({
            animateAppName()
        }, 800)

        Handler(Looper.getMainLooper()).postDelayed({
            animateTagline()
        }, 1200)

        // Navigate to login after splash duration
        Handler(Looper.getMainLooper()).postDelayed({
            navigateToLogin()
        }, splashDuration)
    }

    private fun animateLogo() {
        // Pop-up animation for logo
        val scaleXAnimator = ObjectAnimator.ofFloat(binding.ivLogo, "scaleX", 0f, 1.2f, 1f)
        val scaleYAnimator = ObjectAnimator.ofFloat(binding.ivLogo, "scaleY", 0f, 1.2f, 1f)
        val alphaAnimator = ObjectAnimator.ofFloat(binding.ivLogo, "alpha", 0f, 1f)

        val logoAnimatorSet = AnimatorSet().apply {
            playTogether(scaleXAnimator, scaleYAnimator, alphaAnimator)
            duration = 800
            interpolator = OvershootInterpolator(1.2f)
        }

        logoAnimatorSet.start()

        // Add a subtle rotation effect
        Handler(Looper.getMainLooper()).postDelayed({
            val rotationAnimator = ObjectAnimator.ofFloat(binding.ivLogo, "rotation", 0f, 5f, -5f, 0f)
            rotationAnimator.apply {
                duration = 1000
                interpolator = AccelerateDecelerateInterpolator()
                start()
            }
        }, 800)
    }

    private fun animateAppName() {
        // Slide up with fade in animation
        val translateAnimator = ObjectAnimator.ofFloat(binding.tvAppName, "translationY", 100f, 0f)
        val alphaAnimator = ObjectAnimator.ofFloat(binding.tvAppName, "alpha", 0f, 1f)

        val appNameAnimatorSet = AnimatorSet().apply {
            playTogether(translateAnimator, alphaAnimator)
            duration = 600
            interpolator = OvershootInterpolator(0.8f)
        }

        appNameAnimatorSet.start()

        // Add letter-by-letter reveal effect
        animateTextLetters()
    }

    private fun animateTextLetters() {
        val text = "TutAR"
        binding.tvAppName.text = ""

        Handler(Looper.getMainLooper()).postDelayed({
            text.forEachIndexed { index, char ->
                Handler(Looper.getMainLooper()).postDelayed({
                    binding.tvAppName.text = binding.tvAppName.text.toString() + char
                }, (index * 100).toLong())
            }
        }, 200)
    }

    private fun animateTagline() {
        // Elegant fade in with slight bounce
        val translateAnimator = ObjectAnimator.ofFloat(binding.tvTagline, "translationY", 50f, 0f)
        val alphaAnimator = ObjectAnimator.ofFloat(binding.tvTagline, "alpha", 0f, 1f)
        val scaleAnimator = ObjectAnimator.ofFloat(binding.tvTagline, "scaleX", 0.8f, 1f)
        val scaleYAnimator = ObjectAnimator.ofFloat(binding.tvTagline, "scaleY", 0.8f, 1f)

        val taglineAnimatorSet = AnimatorSet().apply {
            playTogether(translateAnimator, alphaAnimator, scaleAnimator, scaleYAnimator)
            duration = 700
            interpolator = BounceInterpolator()
        }

        taglineAnimatorSet.start()
    }

    private fun navigateToLogin() {
        // Exit animation before navigation
        val logoExitAnimator = ObjectAnimator.ofFloat(binding.ivLogo, "alpha", 1f, 0f)
        val textExitAnimator = ObjectAnimator.ofFloat(binding.tvAppName, "alpha", 1f, 0f)
        val taglineExitAnimator = ObjectAnimator.ofFloat(binding.tvTagline, "alpha", 1f, 0f)

        val exitAnimatorSet = AnimatorSet().apply {
            playTogether(logoExitAnimator, textExitAnimator, taglineExitAnimator)
            duration = 300
        }

        exitAnimatorSet.doOnEnd {
            startActivity(Intent(this@SplashActivity, WhiteboardActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }

        exitAnimatorSet.start()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        // Disable back button on splash screen
    }
}