package com.example.uploadingscreen

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import com.example.uploadingscreen.model.SignUpRequest
import com.example.uploadingscreen.utils.Resource
import com.example.uploadingscreen.viewmodel.AuthViewModel

class RegisterActivity : AppCompatActivity() {

    private lateinit var viewModel: AuthViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        viewModel = ViewModelProvider(this)[AuthViewModel::class.java]

        val usernameEt = findViewById<EditText>(R.id.etUserName)
        val emailEt = findViewById<EditText>(R.id.etEmail)
        val passwordEt = findViewById<EditText>(R.id.etPassword)
        val btnconfirm = findViewById<ImageView>(R.id.btnconfirm)
        val loader = findViewById<FrameLayout>(R.id.signLoader)
        val isToggle = findViewById<ImageView>(R.id.toggle)

//        viewModel.signLoad.observe(this){signLoad->
//            loader.visibility = if(signLoad) View.VISIBLE else View.GONE
//            btnconfirm.isEnabled = !signLoad
//        }

        var isVisible = false
        isToggle.setOnClickListener {
            if(isVisible){
                passwordEt.inputType= InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                isToggle.setImageResource(R.drawable.ic_eye)
            } else{
                passwordEt.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                isToggle.setImageResource(R.drawable.ic_eye_off)
            }
            passwordEt.setSelection(passwordEt.text.length)
            isVisible = !isVisible
        }

        btnconfirm.setOnClickListener {
            val username = usernameEt.text.toString().trim()
            val email = emailEt.text.toString().trim().lowercase()
            val password = passwordEt.text.toString().trim()

            if (username.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "All Fields Required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if(!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()){
                emailEt.error = "Invalid Format"
                emailEt.requestFocus()
                return@setOnClickListener
            }
            if(!(email.endsWith("@gmail.com") || email.endsWith("@yahoo.com") || email.endsWith("@outlook.com")||email.endsWith("@hotmail.com"))){
                emailEt.error="Only Gmail,Yahoo,Outlook or Hotmail allowed"
                emailEt.requestFocus()
                return@setOnClickListener
            }
            if(password.length<6){
                passwordEt.error = "At least 6 characters required"
                passwordEt.requestFocus()
                return@setOnClickListener
            }

            val req = SignUpRequest(username, email, password)
            viewModel.register(req)
        }


//        viewModel.signUpRes.observe(this) { response ->
//            if (response.message == "User created") {
//                Toast.makeText(this, "SignUp Successful", Toast.LENGTH_SHORT).show()
//                startActivity(Intent(this, FormActivity::class.java))
//                finish()
//            } else {
//                Toast.makeText(this, "SignUp Failed", Toast.LENGTH_SHORT).show()
//            }
//        }
        viewModel.signUpRes.observe(this) { resource ->

            when(resource) {

                is Resource.Loading -> {
                    loader.visibility = View.VISIBLE
                    btnconfirm.isEnabled = false
                }

                is Resource.Success -> {
                    loader.visibility = View.GONE
                    btnconfirm.isEnabled = true

                    Toast.makeText(this, "SignUp Successful", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this, FormActivity::class.java).apply {
                        putExtra("username", usernameEt.text.toString().trim())
                        putExtra("password", passwordEt.text.toString().trim())
                    }
                    startActivity(intent)
                    finish()
                }

                is Resource.Error -> {
                    loader.visibility = View.GONE
                    btnconfirm.isEnabled = true

                    Toast.makeText(
                        this,
                        resource.message ?: "SignUp Failed",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
}
