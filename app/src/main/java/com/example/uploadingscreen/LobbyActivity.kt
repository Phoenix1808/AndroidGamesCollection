package com.example.uploadingscreen

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.uploadingscreen.databinding.ActivityLobbyBinding
import com.example.uploadingscreen.game.GameSession
import com.example.uploadingscreen.network.SessionManager
import com.example.uploadingscreen.network.SocketManager
import com.example.uploadingscreen.utils.Resource
import com.example.uploadingscreen.viewmodel.RoomViewModel

class LobbyActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLobbyBinding
    private lateinit var viewModel: RoomViewModel
    private var authToken: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityLobbyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[RoomViewModel::class.java]

        authToken = intent.getStringExtra("token")
        if (!authToken.isNullOrEmpty()) {
            getSharedPreferences("auth", MODE_PRIVATE)
                .edit()
                .putString("token", authToken)
                .apply()
        } else {
            authToken = getSharedPreferences("auth", MODE_PRIVATE).getString("token", null)
        }

        if (authToken.isNullOrEmpty()) {
            toast("Authentication token missing")
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // tokens last 15 minutes; don't wait for the server to reject an old one
        if (SessionManager.isTokenExpired(authToken!!)) {
            SessionManager.onTokenRejected()
            finish()
            return
        }

        GameSession.loadUser(this)

        SocketManager.init(authToken!!)
        SocketManager.connect()

        // e.g. "Connection lost..." after being dropped from a game
        intent.getStringExtra(SessionManager.EXTRA_NOTICE)?.let { notice ->
            AlertDialog.Builder(this)
                .setMessage(notice)
                .setPositiveButton("OK", null)
                .show()
        }

        binding.btnCreateRoom.setOnClickListener {
            viewModel.createRoom(authToken!!)
        }

        binding.btnLookupRoom.setOnClickListener {

            val code = binding.etRoomCode.text.toString().trim().uppercase()

            if (code.isEmpty()) {
                binding.etRoomCode.error = "Enter room code"
                return@setOnClickListener
            }

            viewModel.lookupRoom(authToken!!, code)
        }

        observeViewModel()
    }

    private fun observeViewModel() {

        viewModel.createRoom.observe(this) { resource ->

            when (resource) {

                is Resource.Loading ->
                    binding.progressBar.visibility = View.VISIBLE

                is Resource.Success -> {

                    binding.progressBar.visibility = View.GONE

                    val room = resource.data

                    room?.code?.let {
                        GameSession.enterRoom(it, room.maxPlayers, room.host)
                        openWaitingRoom(it)
                    }
                }

                is Resource.Error -> {
                    binding.progressBar.visibility = View.GONE
                    toast(resource.message)
                }
            }
        }

        viewModel.lookupRoom.observe(this) { resource ->

            when (resource) {

                is Resource.Loading ->
                    binding.progressBar.visibility = View.VISIBLE

                is Resource.Success -> {

                    binding.progressBar.visibility = View.GONE

                    val room = resource.data

                    room?.code?.let {
                        GameSession.enterRoom(it, room.maxPlayers, room.host)
                        // lookup has no usernames; lobby:players-list fills in the real ones after joining
                        val known = room.players.associate { p ->
                            val ownName = if (p.userId == GameSession.myUserId) GameSession.myUsername else null
                            p.userId to (p.username ?: ownName ?: "Player")
                        }
                        GameSession.setPlayers(known, room.host)
                        openWaitingRoom(it)
                    }
                }

                is Resource.Error -> {
                    binding.progressBar.visibility = View.GONE
                    toast(resource.message)
                }
            }
        }
    }

    // room details (host, players, maxPlayers) are already in GameSession
    private fun openWaitingRoom(roomCode: String) {
        val intent = Intent(this, WaitinRoomActivity::class.java)
        intent.putExtra("roomCode", roomCode)
        startActivity(intent)
    }

    private fun toast(msg: String?) {
        Toast.makeText(this, msg ?: "Something went wrong", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
//        SocketManager.clrRoom()
    }
}