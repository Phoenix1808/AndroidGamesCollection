package com.example.uploadingscreen

import android.os.Bundle
import android.widget.GridLayout
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import android.os.Looper
import android.widget.TextView
import android.os.Handler
import androidx.core.content.ContextCompat

class MemoryGameActivity : AppCompatActivity() {

    private var score = 0
    private lateinit var gridlayout : GridLayout
    private lateinit var cards : Array<ImageView>

    private val imgs = arrayListOf(
        R.drawable.img, R.drawable.img,
        R.drawable.img_2,R.drawable.img_2,
        R.drawable.img_3,R.drawable.img_3,
        R.drawable.img_5,R.drawable.img_5,
        R.drawable.img_4,R.drawable.img_4,
        R.drawable.img_1,R.drawable.img_1
    )
    private var firstimg = -1
    private var secimg = -1
    private var isBusy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_memory_game)

        gridlayout = findViewById(R.id.gridLayout)
        imgs.shuffle() //random pattern ban gya new learning

        cards = Array(12){index->
            val image = ImageView(this)

            image.background= ContextCompat.getDrawable(this,R.drawable.card_bg)
            image.clipToOutline = true

            image.setImageResource(R.drawable.img_6)
            image.scaleType = ImageView.ScaleType.CENTER_INSIDE
            image.setPadding(16,16,16,16)

            image.layoutParams = GridLayout.LayoutParams().apply{
                width = 0
                height = 0
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED,1f)
                rowSpec = GridLayout.spec(GridLayout.UNDEFINED,1f)
                setMargins(16,16,16,16)
            }
            image.setOnClickListener{handleCardClick(index)}
            gridlayout.addView(image)
            image
        }
    }
    private fun handleCardClick(index:Int){
        if(isBusy) return
        if(cards[index].tag == "open") return

        flipOpen(cards[index],imgs[index])


        if(firstimg == -1){
            firstimg = index
        } else{
           secimg = index
           checkMatch()
        }
    }
    private fun checkMatch() {
        isBusy = true

        Handler(Looper.getMainLooper()).postDelayed({

            if (imgs[firstimg] == imgs[secimg]) {

                bounce(cards[firstimg])
                bounce(cards[secimg])

                score += 10
                findViewById<TextView>(R.id.scoreCalc).text = "Score: $score"


                cards[firstimg].tag = "open"
                cards[secimg].tag = "open"

                if (score >= 60) {
                    findViewById<TextView>(R.id.txtSubtitle).text = "✨ TASK COMPLETED ✨"
                    findViewById<TextView>(R.id.txtSubtitle).setTextColor(ContextCompat.getColor(this@MemoryGameActivity, android.R.color.holo_green_light))
                    Handler(Looper.getMainLooper()).postDelayed({
                        setResult(RESULT_OK)
                        finish()
                    }, 1500)
                }

            } else {

               
                flipClose(cards[firstimg])
                flipClose(cards[secimg])
            }

        
            firstimg= -1
            secimg = -1
            isBusy = false

        }, 800) 
    }

    private fun flipOpen(card:ImageView,img:Int){
    card.animate().withLayer()
        .rotationY(90f)
        .setDuration(120)
        .withEndAction {
            card.setImageResource(img)
            card.rotationY = -90f
            card.animate().rotationY(0f).setDuration(120).start()
        }.start()
    }

    private fun flipClose(card:ImageView){
        card.animate().withLayer()
            .rotationY(90f)
            .setDuration(120)
            .withEndAction {
                card.setImageResource(R.drawable.img_6)
                card.rotationY = -90f
                card.animate().rotationY(0f).setDuration(120).start()
            }.start()
    }
    private fun bounce(card: ImageView) {
        card.animate()
            .scaleX(1.15f)
            .scaleY(1.15f)
            .setDuration(120)
            .withEndAction {
                card.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }.start()
    }

}
