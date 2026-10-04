package com.example.uploadingscreen

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

class TriviaQuizActivity : AppCompatActivity() {

    private lateinit var tvQuestion: TextView
    private lateinit var radioGroup: RadioGroup
    private lateinit var btnNext: Button

    private lateinit var option1: RadioButton
    private lateinit var option2: RadioButton
    private lateinit var option3: RadioButton
    private lateinit var option4: RadioButton

    private var score = 0
    private var currentIndex = 0

    private val questions = listOf(
        "Which planet is known as the Red Planet?",
        "Who invented the telephone?",
        "Which is the largest mammal?",
        "What is the capital of France?"
    )

    private val options = listOf(
        listOf("Earth", "Mars", "Jupiter", "Venus"),
        listOf("Einstein", "Newton", "Alexander Graham Bell", "Tesla"),
        listOf("Elephant", "Whale", "Giraffe", "Hippopotamus"),
        listOf("London", "Berlin", "Paris", "Madrid")
    )

    private val correctAnswers = listOf(1, 2, 1, 2)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_trivia_quiz)

        tvQuestion = findViewById(R.id.tvQuestion)
        radioGroup = findViewById(R.id.radioGroup)
        btnNext = findViewById(R.id.btnNext)

        option1 = findViewById(R.id.option1)
        option2 = findViewById(R.id.option2)
        option3 = findViewById(R.id.option3)
        option4 = findViewById(R.id.option4)

        loadQuestion()

        btnNext.setOnClickListener {
            checkAnswer()
        }

        radioGroup.setOnCheckedChangeListener { _, _ ->
            btnNext.isEnabled = true
            btnNext.setBackgroundResource(R.drawable.btn_enabled)
        }
    }

    private fun loadQuestion() {


        resetOptionStyles()

        tvQuestion.text = questions[currentIndex]

        btnNext.isEnabled = false
        btnNext.setBackgroundResource(R.drawable.btn_disabled)

        radioGroup.clearCheck()

        option1.text = options[currentIndex][0]
        option2.text = options[currentIndex][1]
        option3.text = options[currentIndex][2]
        option4.text = options[currentIndex][3]
    }

    private fun resetOptionStyles() {
        option1.setBackgroundResource(R.drawable.option_bg_selector)
        option2.setBackgroundResource(R.drawable.option_bg_selector)
        option3.setBackgroundResource(R.drawable.option_bg_selector)
        option4.setBackgroundResource(R.drawable.option_bg_selector)

        option1.isEnabled = true
        option2.isEnabled = true
        option3.isEnabled = true
        option4.isEnabled = true
    }

    private fun checkAnswer() {
        val selectedId = radioGroup.checkedRadioButtonId
        if (selectedId == -1) return

        val selectedIndex = when (selectedId) {
            R.id.option1 -> 0
            R.id.option2 -> 1
            R.id.option3 -> 2
            R.id.option4 -> 3
            else -> -1
        }

        val correctIndex = correctAnswers[currentIndex]


        disableAllOptions()


        highlightCorrect(correctIndex)


        if (selectedIndex != correctIndex) {
            highlightWrong(selectedIndex)
        } else {
            score += 10
        }


        Handler(Looper.getMainLooper()).postDelayed({
            goToNextQuestion()
        }, 800)
    }

    private fun disableAllOptions() {
        option1.isEnabled = false
        option2.isEnabled = false
        option3.isEnabled = false
        option4.isEnabled = false
    }

    private fun highlightCorrect(index: Int) {
        when (index) {
            0 -> option1.setBackgroundResource(R.drawable.correct_opt)
            1 -> option2.setBackgroundResource(R.drawable.correct_opt)
            2 -> option3.setBackgroundResource(R.drawable.correct_opt)
            3 -> option4.setBackgroundResource(R.drawable.correct_opt)
        }
    }

    private fun highlightWrong(index: Int) {
        when (index) {
            0 -> option1.setBackgroundResource(R.drawable.wrong_opt)
            1 -> option2.setBackgroundResource(R.drawable.wrong_opt)
            2 -> option3.setBackgroundResource(R.drawable.wrong_opt)
            3 -> option4.setBackgroundResource(R.drawable.wrong_opt)
        }
    }

    private fun goToNextQuestion() {
        currentIndex++

        if (currentIndex < questions.size) {
            loadQuestion()
        } else {
            val intent = Intent(this, TriviaResultActivity::class.java)
            intent.putExtra("SCORE", score)
            intent.putExtra("MAX_SCORE", questions.size * 10)
            startActivityForResult(intent, TRIVIA_RESULT_REQUEST_CODE)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == TRIVIA_RESULT_REQUEST_CODE) {
            if (resultCode == 2) { // custom RESULT_RETRY code
                score = 0
                currentIndex = 0
                loadQuestion()
            } else {
                setResult(resultCode)
                finish()
            }
        }
    }

    companion object {
        private const val TRIVIA_RESULT_REQUEST_CODE = 201
    }
}
