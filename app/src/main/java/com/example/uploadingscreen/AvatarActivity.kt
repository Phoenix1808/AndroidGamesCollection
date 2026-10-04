package com.example.uploadingscreen

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class AvatarActivity : AppCompatActivity() {

    private var selectedAvatarRes = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_avatar)

        val rotationContainer = findViewById<View>(R.id.avatarContainer)
        val btnContinue = findViewById<ImageView>(R.id.btnconfirm)

        ObjectAnimator.ofFloat(rotationContainer, View.ROTATION, 0f, 360f).apply {
            duration = 12000
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }

        val avatarMap = mapOf(
            R.id.avatar1 to R.drawable.av1,
            R.id.avatar2 to R.drawable.av2,
            R.id.avatar3 to R.drawable.av3,
            R.id.avatar4 to R.drawable.av4,
            R.id.avatar5 to R.drawable.av5
        )

        avatarMap.forEach { (viewId, drawableId) ->
            val img = findViewById<ImageView>(viewId)
            img.setOnClickListener {
                selectAvatar(img, drawableId, avatarMap.keys)
            }
        }

        btnContinue.setOnClickListener {
            if (selectedAvatarRes == -1) {
                Toast.makeText(this, "Select an avatar first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val prefs = getSharedPreferences("users_prefs", MODE_PRIVATE)
            prefs.edit()
                .putInt("selectedAvatar", selectedAvatarRes)
                .apply()

            val username = intent.getStringExtra("username")
            val password = intent.getStringExtra("password")

            val loginIntent = Intent(this, LoginActivity::class.java).apply {
                putExtra("username", username)
                putExtra("password", password)
                putExtra("isAutoLogin", true)
            }
            startActivity(loginIntent)
            finish()
        }
    }

    private fun selectAvatar(
        selectedView: ImageView,
        avatarRes: Int,
        allViewIds: Set<Int>
    ) {

        allViewIds.forEach {
            findViewById<ImageView>(it).animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(150)
                .start()
        }


        selectedView.animate()
            .scaleX(1.3f)
            .scaleY(1.3f)
            .setDuration(150)
            .start()

        selectedAvatarRes = avatarRes
    }
}