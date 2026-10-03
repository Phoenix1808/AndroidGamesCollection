package com.example.uploadingscreen

import android.graphics.*
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ConnectDotActivity : AppCompatActivity() {

    private lateinit var overlay: LineOverlayView
    private val completedColors = mutableSetOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_connect_dot)

        val container = findViewById<FrameLayout>(R.id.overlayContainer)
        overlay = LineOverlayView(this)
        container.addView(overlay)


        val colors = mapOf(
            "RED" to Color.parseColor("#FF4B4B"),
            "BLUE" to Color.parseColor("#4BA6FF"),
            "YELLOW" to Color.parseColor("#FFD24D"),
            "PURPLE" to Color.parseColor("#B86BFF")
        )

        val leftDots = listOf(
            R.id.leftRed to colors["RED"]!!,
            R.id.leftBlue to colors["BLUE"]!!,
            R.id.leftYellow to colors["YELLOW"]!!,
            R.id.leftPurple to colors["PURPLE"]!!
        )

        val rightDots = mapOf(
            colors["RED"]!! to R.id.rightRed,
            colors["BLUE"]!! to R.id.rightBlue,
            colors["YELLOW"]!! to R.id.rightYellow,
            colors["PURPLE"]!! to R.id.rightPurple
        )


        leftDots.forEach { (id, color) ->
            findViewById<View>(id).setOnTouchListener { v, event ->
                val overlayLoc = IntArray(2).apply { container.getLocationOnScreen(this) }
                val viewLoc = IntArray(2).apply { v.getLocationOnScreen(this) }

               
                val startX = viewLoc[0] - overlayLoc[0] + v.width / 2f
                val startY = viewLoc[1] - overlayLoc[1] + v.height / 2f

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        if (completedColors.contains(color)) return@setOnTouchListener false
                        overlay.startLine(startX, startY, color)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {

                        overlay.updateLine(event.rawX - overlayLoc[0], event.rawY - overlayLoc[1])
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        val targetView = findViewById<View>(rightDots[color]!!)
                        if (isInside(event.rawX, event.rawY, targetView)) {
                            val targetLoc = IntArray(2).apply { targetView.getLocationOnScreen(this) }
                            val endX = targetLoc[0] - overlayLoc[0] + targetView.width / 2f
                            val endY = targetLoc[1] - overlayLoc[1] + targetView.height / 2f

                            overlay.snapAndEndLine(endX, endY) // connecting in st. direction
                            completedColors.add(color)
                            checkWin()
                        } else {
                            overlay.cancelLine()
                        }
                        true
                    }
                    else -> false
                }
            }
        }

    }

    private fun isInside(rawX: Float, rawY: Float, v: View): Boolean {
        val loc = IntArray(2).apply { v.getLocationOnScreen(this) }
        val buffer = 50f
        return rawX in (loc[0].toFloat() - buffer)..(loc[0] + v.width + buffer).toFloat() &&
                rawY in (loc[1].toFloat() - buffer)..(loc[1] + v.height + buffer).toFloat()
    }

    private fun checkWin() {
        if (completedColors.size == 4) {
            findViewById<TextView>(R.id.winText).apply {
                visibility = View.VISIBLE
                alpha = 0f
                scaleX = 0f
                scaleY = 0f
                animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(700)
                    .setInterpolator(OvershootInterpolator()).start()
            }
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                setResult(RESULT_OK)
                finish()
            }, 1500)
        }
    }

    inner class LineOverlayView(context: android.content.Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeWidth = 26f
            strokeCap = Paint.Cap.ROUND
            style = Paint.Style.STROKE
        }
        private val lines = mutableListOf<Triple<PointF, PointF, Int>>()
        private var startPoint: PointF? = null
        private var endPoint: PointF? = null
        private var currentColor = Color.WHITE

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            lines.forEach {
                paint.color = it.third
                canvas.drawLine(it.first.x, it.first.y, it.second.x, it.second.y, paint)
            }

            if (startPoint != null && endPoint != null) {
                paint.color = currentColor
                canvas.drawLine(startPoint!!.x, startPoint!!.y, endPoint!!.x, endPoint!!.y, paint)
            }
        }

        fun startLine(x: Float, y: Float, color: Int) {
            startPoint = PointF(x, y); endPoint = PointF(x, y)
            currentColor = color; invalidate()
        }

        fun updateLine(x: Float, y: Float) {
            endPoint?.set(x, y); invalidate()
        }

        fun snapAndEndLine(x: Float, y: Float) {
            startPoint?.let { lines.add(Triple(PointF(it.x, it.y), PointF(x, y), currentColor)) }
            startPoint = null; endPoint = null; invalidate()
        }

        fun cancelLine() {
            startPoint = null; endPoint = null; invalidate()
        }

        fun clearLines() {
            lines.clear(); invalidate()
        }
    }
}
