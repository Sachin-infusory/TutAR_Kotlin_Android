package com.infusory.tutarapp.ui.splash

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import com.infusory.tutarapp.ui.auth.SessionExpiredActivity
import com.infusory.tutarapp.ui.whiteboard.WhiteboardActivity
import java.text.SimpleDateFormat
import java.util.*

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private val splashDuration = 3000L // 3 seconds total

    companion object {
        private const val PREF_NAME = "TutarAppPreferences"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_LOGIN_TIME = "login_time"
        private const val KEY_LOGIN_DATE = "login_date"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_SESSION_EXPIRED = "session_expired"
        private const val SESSION_TIMEOUT_MINUTES = 30 * 24 * 60 // 30 days = 43,200 minutes
    }

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

        // Check session and navigate after splash duration
        Handler(Looper.getMainLooper()).postDelayed({
            checkSessionAndNavigate()
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

    private fun checkSessionAndNavigate() {
        val sharedPreferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        // First check if session has been permanently expired
        val isSessionPermanentlyExpired = sharedPreferences.getBoolean(KEY_SESSION_EXPIRED, false)

        if (isSessionPermanentlyExpired) {
            android.util.Log.d("SplashActivity", "Session permanently expired - navigating to SessionExpired")
            navigateToSessionExpired()
            return
        }

        val isLoggedIn = sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false)
        val loginDate = sharedPreferences.getString(KEY_LOGIN_DATE, null)
        val loginTime = sharedPreferences.getString(KEY_LOGIN_TIME, null)
        val userEmail = sharedPreferences.getString(KEY_USER_EMAIL, null)

        android.util.Log.d("SplashActivity", "Login Status: $isLoggedIn")
        android.util.Log.d("SplashActivity", "Login Date: $loginDate")
        android.util.Log.d("SplashActivity", "Login Time: $loginTime")
        android.util.Log.d("SplashActivity", "User Email: $userEmail")

        when {
            // Login status is false - go to login
            !isLoggedIn -> {
                android.util.Log.d("SplashActivity", "Not logged in - navigating to Login")
                navigateToLogin()
            }
            // Login status is true but date/time is missing - go to login
            loginDate == null || loginTime == null -> {
                android.util.Log.d("SplashActivity", "Missing login date/time - navigating to Login")
                navigateToLogin()
            }
            // Check if session has expired
            isSessionExpired(loginDate, loginTime) -> {
                android.util.Log.d("SplashActivity", "Session expired - marking as permanently expired")
                markSessionAsPermanentlyExpired()
                navigateToSessionExpired()
            }
            // Session is valid - go to whiteboard
            else -> {
                android.util.Log.d("SplashActivity", "Valid session - navigating to Whiteboard")
                navigateToWhiteboard()
            }
        }
    }

    private fun isSessionExpired(loginDate: String, loginTime: String): Boolean {
        try {
            // Combine date and time into a single timestamp
            val dateTimeString = "$loginDate $loginTime"
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val loginDateTime = dateFormat.parse(dateTimeString)

            if (loginDateTime != null) {
                val loginTimestamp = loginDateTime.time
                val currentTimestamp = System.currentTimeMillis()
                val timeDifferenceMillis = currentTimestamp - loginTimestamp

                // Convert to minutes
                val minutesPassed = timeDifferenceMillis / (1000 * 60)

                android.util.Log.d("SplashActivity", "Minutes since login: $minutesPassed")
                android.util.Log.d("SplashActivity", "Session timeout threshold: $SESSION_TIMEOUT_MINUTES minutes")

                return minutesPassed >= SESSION_TIMEOUT_MINUTES
            }
        } catch (e: Exception) {
            android.util.Log.e("SplashActivity", "Error parsing login date/time", e)
            return true // Consider expired if we can't parse
        }

        return true
    }

    private fun markSessionAsPermanentlyExpired() {
        val editor = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
        editor.putBoolean(KEY_SESSION_EXPIRED, true)
        editor.apply()
        android.util.Log.d("SplashActivity", "Session marked as permanently expired")
    }

    private fun navigateToLogin() {
        exitWithAnimation {
            startActivity(Intent(this@SplashActivity, LoginActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }
    }

    private fun navigateToSessionExpired() {
        exitWithAnimation {
            val intent = Intent(this@SplashActivity, SessionExpiredActivity::class.java)
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }
    }

    private fun navigateToWhiteboard() {
        exitWithAnimation {
            startActivity(Intent(this@SplashActivity, WhiteboardActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }
    }

    private fun exitWithAnimation(onComplete: () -> Unit) {
        // Exit animation before navigation
        val logoExitAnimator = ObjectAnimator.ofFloat(binding.ivLogo, "alpha", 1f, 0f)
        val textExitAnimator = ObjectAnimator.ofFloat(binding.tvAppName, "alpha", 1f, 0f)
        val taglineExitAnimator = ObjectAnimator.ofFloat(binding.tvTagline, "alpha", 1f, 0f)

        val exitAnimatorSet = AnimatorSet().apply {
            playTogether(logoExitAnimator, textExitAnimator, taglineExitAnimator)
            duration = 300
        }

        exitAnimatorSet.doOnEnd {
            onComplete()
        }

        exitAnimatorSet.start()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        // Disable back button on splash screen
    }
}