
package com.infusory.tutarapp.ui.auth

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.infusory.tutarapp.R
import com.infusory.tutarapp.databinding.ActivityLoginBinding
import com.infusory.tutarapp.ui.whiteboard.WhiteboardActivity
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        setupUI()
        setupObservers()
        startEnterAnimations()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setupUI() {
        // Set up click listeners
        binding.btnLogin.setOnClickListener {
            performLogin()
        }

        binding.tvForgotPassword.setOnClickListener {
            // Handle forgot password
            Toast.makeText(this, "Forgot password clicked", Toast.LENGTH_SHORT).show()
        }

        // Set up text watchers for validation
        binding.etEmail.addTextChangedListener(createTextWatcher())
        binding.etPassword.addTextChangedListener(createTextWatcher())

        // Set up focus change listeners for animation
        binding.etEmail.setOnFocusChangeListener { _, hasFocus ->
            animateInputField(binding.tilEmail, hasFocus)
        }

        binding.etPassword.setOnFocusChangeListener { _, hasFocus ->
            animateInputField(binding.tilPassword, hasFocus)
        }
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.loginState.collect { state ->
                when (state) {
                    is LoginState.Idle -> {
                        hideLoading()
                    }
                    is LoginState.Loading -> {
                        showLoading()
                    }
                    is LoginState.Success -> {
                        hideLoading()
                        navigateToWhiteboard()
                    }
                    is LoginState.Error -> {
                        hideLoading()
                        showError(state.message)
                    }
                }
            }
        }
    }

    private fun createTextWatcher(): TextWatcher {
        return object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                validateForm()
            }
        }
    }

    private fun validateForm() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        val isEmailValid = android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
        val isPasswordValid = password.length >= 6

        // Update email field error
        if (email.isNotEmpty() && !isEmailValid) {
            binding.tilEmail.error = "Please enter a valid email"
        } else {
            binding.tilEmail.error = null
        }

        // Update password field error
        if (password.isNotEmpty() && !isPasswordValid) {
            binding.tilPassword.error = "Password must be at least 6 characters"
        } else {
            binding.tilPassword.error = null
        }

        // Enable/disable login button
        binding.btnLogin.isEnabled = isEmailValid && isPasswordValid
        binding.btnLogin.alpha = if (binding.btnLogin.isEnabled) 1.0f else 0.6f
    }

    private fun animateInputField(view: View, hasFocus: Boolean) {
        val scaleX = if (hasFocus) 1.02f else 1.0f
        val scaleY = if (hasFocus) 1.02f else 1.0f

        ObjectAnimator.ofFloat(view, "scaleX", scaleX).apply {
            duration = 200
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }

        ObjectAnimator.ofFloat(view, "scaleY", scaleY).apply {
            duration = 200
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun performLogin() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }

        viewModel.login(email, password)
    }

    private fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnLogin.text = ""
        binding.btnLogin.isEnabled = false
    }

    private fun hideLoading() {
        binding.progressBar.visibility = View.GONE
        binding.btnLogin.text = "LOGIN"
        binding.btnLogin.isEnabled = true
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()

        // Shake animation for error
        val shakeAnimator = ObjectAnimator.ofFloat(binding.cardLogin, "translationX", 0f, 25f, -25f, 25f, -25f, 15f, -15f, 6f, -6f, 0f)
        shakeAnimator.duration = 600
        shakeAnimator.start()
    }

    private fun navigateToWhiteboard() {
        startActivity(Intent(this, WhiteboardActivity::class.java))
        overridePendingTransition(R.drawable.slide_in_right, R.drawable.slide_out_left)
        finish()
    }

    private fun startEnterAnimations() {
        // Initially hide views
        binding.ivLogo.alpha = 0f
        binding.ivLogo.translationY = -50f

        binding.tvTitle.alpha = 0f
        binding.tvTitle.translationY = -30f

        binding.cardLogin.alpha = 0f
        binding.cardLogin.translationY = 100f

        binding.tvForgotPassword.alpha = 0f

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

        // Animate login card
        binding.cardLogin.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(800)
            .setStartDelay(600)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        // Animate forgot password
        binding.tvForgotPassword.animate()
            .alpha(1f)
            .setDuration(600)
            .setStartDelay(1000)
            .start()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(R.drawable.slide_out_left, R.drawable.slide_out_right)
    }
}