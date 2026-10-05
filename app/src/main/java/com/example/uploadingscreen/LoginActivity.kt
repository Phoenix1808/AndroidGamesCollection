package com.example.uploadingscreen

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.text.InputType
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.uploadingscreen.databinding.ActivityLoginBinding
import com.example.uploadingscreen.game.GameSession
import com.example.uploadingscreen.model.LoginRequest
import com.example.uploadingscreen.network.SessionManager
import com.example.uploadingscreen.utils.Resource
import com.example.uploadingscreen.viewmodel.AuthViewModel

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var viewModel: AuthViewModel
    private var username: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[AuthViewModel::class.java]

        // "Your session expired..." when sent here by SessionManager
        intent.getStringExtra(SessionManager.EXTRA_NOTICE)?.let {
            Toast.makeText(this, it, Toast.LENGTH_LONG).show()
        }

        var isVisible = false

        // Password toggle
        binding.toggle.setOnClickListener {

            if (isVisible) {
                binding.etPassword.inputType =
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                binding.toggle.setImageResource(R.drawable.ic_eye)
            } else {
                binding.etPassword.inputType =
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                binding.toggle.setImageResource(R.drawable.ic_eye_off)
            }

            binding.etPassword.setSelection(binding.etPassword.text.length)
            isVisible = !isVisible
        }

        binding.btnconfirm.setOnClickListener {

            username = binding.etUsername.text.toString().trim()
            val pass = binding.etPassword.text.toString().trim()

            if (username.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Enter all details", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val req = LoginRequest(username, pass)
            viewModel.login(req)
        }

        val isAutoLogin = intent.getBooleanExtra("isAutoLogin", false)
        if (isAutoLogin) {
            val userExtra = intent.getStringExtra("username")
            val passExtra = intent.getStringExtra("password")
            if (!userExtra.isNullOrEmpty() && !passExtra.isNullOrEmpty()) {
                binding.etUsername.setText(userExtra)
                binding.etPassword.setText(passExtra)
                username = userExtra
                val req = LoginRequest(userExtra, passExtra)
                viewModel.login(req)
            }
        }

        viewModel.loginRes.observe(this) { resource ->

            when (resource) {

                is Resource.Loading -> {
                    binding.loaderLogin.visibility = View.VISIBLE
                    binding.btnconfirm.isEnabled = false
                }

                is Resource.Success -> {

                    binding.loaderLogin.visibility = View.GONE
                    binding.btnconfirm.isEnabled = true

                    val response = resource.data

                    Toast.makeText(this, "Login Success", Toast.LENGTH_SHORT).show()

                    getSharedPreferences("auth", MODE_PRIVATE)
                        .edit()
                        .putString("token", response?.accessToken)
                        .putString("username", response?.user?.username ?: username)
                        .putString("userId", response?.user?.id)
                        .apply()
                    GameSession.loadUser(this)
                    SessionManager.onLoggedIn()

                    val intent = Intent(this, LobbyActivity::class.java)
                    intent.putExtra("token", response?.accessToken)
                    startActivity(intent)
                    finish()
                }

                is Resource.Error -> {

                    binding.loaderLogin.visibility = View.GONE
                    binding.btnconfirm.isEnabled = true

                    Toast.makeText(
                        this,
                        resource.message ?: "Login Failed",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
}