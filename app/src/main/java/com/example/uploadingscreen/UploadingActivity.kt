package com.example.uploadingscreen

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import android.widget.ProgressBar
import android.widget.Toast

class UploadingActivity : AppCompatActivity() {

    private lateinit var progressBar: ProgressBar
    private val handler = Handler(Looper.getMainLooper())
    private var progress = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_uploading)

        progressBar = findViewById(R.id.uploadProgress)

        disableBackPress()
        startFakeUpload()
    }

    private fun startFakeUpload() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (progress < 100) {
                    progress += 1
                    progressBar.progress = progress
                    handler.postDelayed(this, 80) // speed control
                } else {
                    onUploadComplete()
                }
            }
        }, 200)
    }

    private fun onUploadComplete() {
        Toast.makeText(this, "Task Completed", Toast.LENGTH_SHORT).show()
        setResult(RESULT_OK)
        finish()
    }

    private fun disableBackPress() {
        onBackPressedDispatcher.addCallback(this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                }
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
