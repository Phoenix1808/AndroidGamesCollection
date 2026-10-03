package com.example.uploadingscreen

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class TriviaResultActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trivia_result)

        val score = intent.getIntExtra("SCORE", 0)

        var maxScore = intent.getIntExtra("MAX_SCORE", -1)
        if (maxScore <= 0) {
            val questionsCount = intent.getIntExtra("QUESTIONS_COUNT", -1)
            val pointsPerQuestion = intent.getIntExtra("POINTS_PER_Q", 10)
            maxScore = if (questionsCount > 0) questionsCount * pointsPerQuestion else 1
        }

        val tvFinalScore = findViewById<TextView>(R.id.tvFinalScore)
        val tvPercent = findViewById<TextView>(R.id.tvPercent)
        val circleProgress = findViewById<ProgressBar>(R.id.circleProgress)
        val btnRestart = findViewById<Button>(R.id.btnRestart)
        val resultCard = findViewById<android.widget.LinearLayout>(R.id.resultCard)

        tvFinalScore.text = "Your Score: $score"

        val percent = ((score.toFloat() / maxScore.toFloat()) * 100f)
            .coerceIn(0f, 100f)
            .toInt()


        resultCard.startAnimation(AnimationUtils.loadAnimation(this, R.anim.scale_in))
        animateProgress(circleProgress, tvPercent, percent)

        val isSuccess = score >= 20 // passing score is 50% (20 out of 40)
        if (isSuccess) {
            btnRestart.text = "Task Done"
            btnRestart.setOnClickListener {
                setResult(RESULT_OK)
                finish()
            }
        } else {
            btnRestart.text = "Retry Task"
            btnRestart.setOnClickListener {
                setResult(2) // custom RESULT_RETRY code
                finish()
            }
        }
    }


    private fun animateProgress(progressBar: ProgressBar, tvPercent: TextView, target: Int) {
        val handler = Handler(Looper.getMainLooper())
        var current = 0

        val runnable = object : Runnable {
            override fun run() {
                if (current <= target) {

                    progressBar.progress = current
                    tvPercent.text = "$current%"

                    tvPercent.startAnimation(
                        AnimationUtils.loadAnimation(this@TriviaResultActivity, R.anim.bounce)
                    )

                    current++
                    handler.postDelayed(this, 15)

                } else {

                    progressBar.startAnimation(
                        AnimationUtils.loadAnimation(this@TriviaResultActivity, R.anim.drop)
                    )
                }
            }
        }

        handler.post(runnable)
    }
}
