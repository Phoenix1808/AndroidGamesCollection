package com.example.uploadingscreen

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.uploadingscreen.adapter.PlayerAdapter
import com.example.uploadingscreen.databinding.ActivityWaitinRoomBinding
import com.example.uploadingscreen.game.GameSession
import com.example.uploadingscreen.network.SocketManager
import org.json.JSONObject

class WaitinRoomActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWaitinRoomBinding

    private lateinit var adapter: PlayerAdapter
    // display strings for the adapter, rebuilt from GameSession.players
    private val players = mutableListOf<String>()

    private var roomCode: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        binding = ActivityWaitinRoomBinding.inflate(layoutInflater)
        setContentView(binding.root)

        roomCode = intent.getStringExtra("roomCode") ?: GameSession.roomCode

        binding.tvRoomCode.text = roomCode

        adapter = PlayerAdapter(players)

        binding.rvPlayers.layoutManager = LinearLayoutManager(this)
        binding.rvPlayers.adapter = adapter

        binding.btnStartGame.setOnClickListener {

            val socket = SocketManager.getSocket() ?: return@setOnClickListener

            val payload = JSONObject().apply {
                put("roomCode", roomCode)
            }

            android.util.Log.d("GAME_DEBUG", "Host clicked start")

            binding.btnStartGame.isEnabled = false
            socket.emit("game:start", payload, io.socket.client.Ack { args ->
                android.util.Log.d("GAME_DEBUG", "Start game ACK: ${args.firstOrNull()}")
                val ack = args.firstOrNull() as? JSONObject
                runOnUiThread {
                    // on success game:started / game:role take us to the map
                    if (ack?.optBoolean("ok") != true) {
                        binding.btnStartGame.isEnabled = true
                        val reason = ack?.optString("message").takeUnless { it.isNullOrEmpty() }
                        Toast.makeText(this, reason ?: "Could not start the game", Toast.LENGTH_SHORT).show()
                    }
                }
            })
        }

        binding.btnCopyCode.setOnClickListener {
            roomCode?.let { code ->
                val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Room Code", code)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "Room code copied!", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnHelp.setOnClickListener {
            binding.layoutRulesPopup.visibility = android.view.View.VISIBLE
        }

        binding.btnDismissRules.setOnClickListener {
            binding.layoutRulesPopup.visibility = android.view.View.GONE
        }

        binding.btnGotIt.setOnClickListener {
            binding.layoutRulesPopup.visibility = android.view.View.GONE
        }

        renderPlayers()
        // listen first: players-list / game:started can arrive right after the join
        setupSocket()
        joinRoomSocket()
    }

    private fun setupSocket() {

        val socket = SocketManager.getSocket() ?: return

        socket.off("lobby:players-list")
        socket.off("game:started")
        socket.off("game:role")
        socket.off("lobby:player-joined")

        socket.on("lobby:players-list") { args ->

            if (args.isNotEmpty() && args[0] is JSONObject) {

                val data = args[0] as JSONObject
                val playersArray = data.getJSONArray("players")
                val hostId = data.getString("hostId")

                android.util.Log.d("GAME_DEBUG", "players-list received: ${playersArray.length()} players")

                val roster = LinkedHashMap<String, String>()
                for (i in 0 until playersArray.length()) {
                    if (playersArray.isNull(i)) continue
                    val player = playersArray.optJSONObject(i) ?: continue
                    val userId = player.optString("userId", "")
                    if (userId.isEmpty()) continue
                    roster[userId] = player.optString("username", "Player")
                }

                runOnUiThread {
                    GameSession.setPlayers(roster, hostId)
                    renderPlayers()
                }
            }
        }

        socket.on("lobby:player-joined") { args ->

            if (args.isNotEmpty() && args[0] is JSONObject) {

                val data = args[0] as JSONObject
                val username = data.getString("username")
                val userId = data.optString("userId", "")

                runOnUiThread {

                    Toast.makeText(
                        this,
                        "$username joined the room",
                        Toast.LENGTH_SHORT
                    ).show()

                    // Fallback in case players-list is late; it will overwrite this anyway
                    if (userId.isNotEmpty() && userId !in GameSession.players) {
                        GameSession.players[userId] = username
                        renderPlayers()
                    }
                }
            }
        }

        socket.on("game:started") {
               android.util.Log.d("GAME_DEBUG","game:started recieved")
            runOnUiThread {
                // optional loading UI
            }
        }

        socket.on("game:role") { args ->

            android.util.Log.d("GAME_DEBUG","game:role received :$args")
            if (args.isNotEmpty() && args[0] is JSONObject) {

                val data = args[0] as JSONObject
                val role = data.optString("role")

                runOnUiThread {
                    GameSession.role = role

                    // players, host and role are read from GameSession
                    val intent = Intent(this, GameActivity::class.java)
                    intent.putExtra("roomCode", roomCode)
                    startActivity(intent)
                    finish()
                }
            }
        }
    }

    private fun joinRoomSocket() {

        if (SocketManager.getSocket() == null) {
            leaveWithError("Not connected to the server. Please log in again.")
            return
        }

        var answered = false
        val timeout = Runnable {
            if (!answered && !isFinishing) leaveWithError("Server is not responding. Try again.")
        }
        binding.root.postDelayed(timeout, JOIN_TIMEOUT_MS)

        // SocketManager sends the join (and rejoins after reconnects) without ever doubling it up
        SocketManager.joinRoom(roomCode!!) { ack ->
            android.util.Log.d("GAME_DEBUG", "join-room ACK: $ack")
            runOnUiThread {
                answered = true
                binding.root.removeCallbacks(timeout)
                if (ack?.optBoolean("ok") != true) {
                    val reason = ack?.optString("message").takeUnless { it.isNullOrEmpty() }
                    leaveWithError(reason ?: "Could not join the room")
                }
            }
        }
    }

    // "Room not found", "Game already started", "Room is full" etc.
    private fun leaveWithError(message: String) {
        if (isFinishing) return
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        SocketManager.clrRoom()
        finish() // back to LobbyActivity
    }

    override fun onDestroy() {
        super.onDestroy()

        val socket = SocketManager.getSocket()

        socket?.off("lobby:players-list")
        socket?.off("lobby:player-joined")
        socket?.off("game:started")
        socket?.off("game:role")
    }

    private fun renderPlayers() {
        players.clear()
        for ((userId, username) in GameSession.players) {
            players.add(if (userId == GameSession.hostId) "$username (Host)" else username)
        }
        adapter.notifyDataSetChanged()
        updatePlayerCount()

        binding.btnStartGame.visibility =
            if (GameSession.isHost) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun updatePlayerCount() {
        binding.tvPlayerCount.text = "(${players.size}/${GameSession.maxPlayers})"
    }

    companion object {
        private const val JOIN_TIMEOUT_MS = 15_000L
    }
}