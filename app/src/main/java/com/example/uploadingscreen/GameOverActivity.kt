package com.example.uploadingscreen

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.uploadingscreen.databinding.ActivityGameOverBinding
import com.example.uploadingscreen.network.SocketManager

class GameOverActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGameOverBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityGameOverBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // the server deletes the game on game:ended; don't auto-rejoin it after a reconnect
        SocketManager.clrRoom()

        val winner = intent.getStringExtra("winner")
        val role = intent.getStringExtra("role")

        if(winner == role){
            binding.tvResult.text = "VICTORY"
            binding.tvResult.setTextColor(getColor(android.R.color.holo_green_dark))
        } else{
            binding.tvResult.text = "DEFEAT"
            binding.tvResult.setTextColor(getColor(android.R.color.holo_red_dark))
        }

        binding.tvWinner.text = "Winner: $winner"
        binding.btnBackToLobby.setOnClickListener {
            val intent= Intent(this,LobbyActivity::class.java)
            intent.flags= Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            finish()
        }
    }
}