package com.example.uploadingscreen

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.GridLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class NumSeqActivity : AppCompatActivity() {

    private lateinit var gridLayout: GridLayout
    private lateinit var layoutSuccess: View
    private lateinit var layoutFailure: View
    private lateinit var btnSuccessDone: Button
    private lateinit var btnRetry: Button
    private lateinit var btnQuit: Button
    private lateinit var btnClose: View
    private lateinit var btnHelp: View

    private val cells = mutableListOf<TextView>()
    private var nextExpected = 1
    private var isGameActive = true
    private var gridCreated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_num_seq)

        gridLayout = findViewById(R.id.gridLayout)
        layoutSuccess = findViewById(R.id.layoutSuccess)
        layoutFailure = findViewById(R.id.layoutFailure)
        btnSuccessDone = findViewById(R.id.btnSuccessDone)
        btnRetry = findViewById(R.id.btnRetry)
        btnQuit = findViewById(R.id.btnQuit)
        btnClose = findViewById(R.id.btnClose)
        btnHelp = findViewById(R.id.btnHelp)

        // Close button at top
        btnClose.setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }

        // Help button at bottom
        btnHelp.setOnClickListener {
            android.widget.Toast.makeText(
                this,
                "Tap the numbers in ascending order from 1 to 10!",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }

        // Success overlay "Task Done" button
        btnSuccessDone.setOnClickListener {
            setResult(Activity.RESULT_OK)
            finish()
        }

        // Failure overlay "Retry" button
        btnRetry.setOnClickListener {
            layoutFailure.visibility = View.GONE
            startNewRound()
        }

        // Failure overlay "Quit" button
        btnQuit.setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }

        // Build the grid
        generateGrid()
    }

    private fun dpToPx(dp: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()

    private fun generateGrid() {
        gridLayout.post {
            if (gridCreated) return@post
            gridCreated = true

            startNewRound()
        }
    }

    private fun startNewRound() {
        gridLayout.removeAllViews()
        cells.clear()
        nextExpected = 1
        isGameActive = true

        val totalWidth = gridLayout.width
        val margin = dpToPx(4)
        val size = (totalWidth - margin * 12) / 5

        // Generate numbers 1 to 10 and shuffle them
        val jumbledNumbers = (1..10).shuffled()

        for (i in 0 until 10) {
            val num = jumbledNumbers[i]
            val tv = TextView(this).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    width = size
                    height = size
                    setMargins(margin, margin, margin, margin)
                }

                gravity = Gravity.CENTER
                textSize = 22f
                setTextColor(Color.WHITE)
                android.graphics.Typeface.defaultFromStyle(android.graphics.Typeface.BOLD)
                background = ContextCompat.getDrawable(
                    this@NumSeqActivity,
                    R.drawable.num_button_default
                )
                text = num.toString()
                tag = num
                isClickable = true
            }

            tv.setOnClickListener {
                if (!isGameActive) return@setOnClickListener
                handleTileTap(tv, num)
            }

            cells.add(tv)
            gridLayout.addView(tv)
        }
    }

    private fun handleTileTap(tv: TextView, num: Int) {
        if (num == nextExpected) {
            // Correct tap
            tv.background = ContextCompat.getDrawable(this, R.drawable.num_button_correct)
            
            // Pop animation
            val scaleX = PropertyValuesHolder.ofFloat("scaleX", 1f, 1.12f, 1f)
            val scaleY = PropertyValuesHolder.ofFloat("scaleY", 1f, 1.12f, 1f)
            ObjectAnimator.ofPropertyValuesHolder(tv, scaleX, scaleY).apply {
                duration = 180
                start()
            }

            nextExpected++

            if (nextExpected > 10) {
                // All numbers completed successfully
                isGameActive = false
                layoutSuccess.visibility = View.VISIBLE
            }
        } else {
            // Wrong tap
            isGameActive = false
            tv.background = ContextCompat.getDrawable(this, R.drawable.num_button_incorrect)
            
            // Shake animation
            val shake = android.view.animation.AnimationUtils.loadAnimation(this, android.R.anim.slide_in_left)
            tv.startAnimation(shake)

            // Show failure overlay after a small delay
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                layoutFailure.visibility = View.VISIBLE
            }, 300)
        }
    }
}
