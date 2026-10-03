package com.example.uploadingscreen

import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.example.uploadingscreen.adapter.VotingPlayerAdapter
import com.example.uploadingscreen.databinding.ActivityMeetingActivityBinding
import com.example.uploadingscreen.network.SocketManager
import org.json.JSONObject
import java.util.Random

class MeetingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMeetingActivityBinding
    private lateinit var adapter: VotingPlayerAdapter

    private var roomCode: String? = null
    private val votedPlayer = mutableSetOf<String>()
    private val playerMap = mutableMapOf<String, String>() // userId -> username
    private val userIdsList = mutableListOf<String>()

    private var timer: CountDownTimer? = null

    // Simulation Handlers
    private val mockHandler = Handler(Looper.getMainLooper())
    private var isMockMode = false

    private val mockVotersRunnable = object : Runnable {
        override fun run() {
            // Find a player from userIdsList who hasn't voted yet, and simulate their vote
            val remainingVoters = userIdsList.filter { !votedPlayer.contains(it) }
            if (remainingVoters.isNotEmpty()) {
                val randomVoterId = remainingVoters.random()
                votedPlayer.add(randomVoterId)
                adapter.notifyDataSetChanged()
                
                val username = playerMap[randomVoterId] ?: "Player"
                Log.d("MOCK_VOTE", "$username voted (mock)")
                
                // Cycle next vote in 3 seconds
                mockHandler.postDelayed(this, 3000)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMeetingActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Retrieve intent extras
        roomCode = intent.getStringExtra("roomCode")
        val userIdsArray = intent.getStringArrayExtra("userIds")
        val usernamesArray = intent.getStringArrayExtra("usernames")
        val duration = intent.getIntExtra("duration", 60)

        // Populate player lists
        if (userIdsArray != null && usernamesArray != null) {
            for (i in userIdsArray.indices) {
                val uid = userIdsArray[i]
                val uname = usernamesArray[i]
                playerMap[uid] = uname
                userIdsList.add(uid)
            }
        }

        // Determine if we should run in Offline Mock Simulation mode
        val socket = SocketManager.getSocket()
        isMockMode = (roomCode == "MOCK_LOBBY") || (socket == null) || (!socket.connected())

        if (isMockMode) {
            Log.d("SOCKET_DEBUG", "Running MeetingActivity in OFFLINE MOCK MODE")
        } else {
            if (socket == null || !socket.connected()) {
                Log.e("SOCKET_DEBUG", "Warning: Socket is NOT connected inside MeetingActivity!")
                Toast.makeText(this, "Connection lost! Attempting to reconnect...", Toast.LENGTH_SHORT).show()
            }
        }

        setupRecyclerView()
        startTimer(duration)

        // Setup Socket Listeners or run Sandbox Simulation
        if (isMockMode) {
            runMockSimulation()
        } else {
            listenVoteUpdate()
            listenVoteResult()
            listenFreeplayResumed()
        }

        // Bind Skip Button (emits targetId as null or skips locally)
        binding.btnSkip.setOnClickListener {
            sendVote(null)
        }

        // Bind Submit Vote Button (emits targetId as selectedPlayerId or submits locally)
        binding.btnVote.setOnClickListener {
            val selectedId = adapter.getSelectedPlayerId()
            if (selectedId != null) {
                sendVote(selectedId)
            } else {
                Toast.makeText(this, "Select a player first!", Toast.LENGTH_SHORT).show()
            }
        }

        // Bind Host End Voting Early button
        binding.btnEndVoting.visibility = View.VISIBLE
        binding.btnEndVoting.setOnClickListener {
            resolveVoteEarly()
        }
    }

    private fun setupRecyclerView() {
        adapter = VotingPlayerAdapter(userIdsList, playerMap, votedPlayer) { selectedPlayerId ->
            runOnUiThread {
                if (selectedPlayerId != null) {
                    binding.btnVote.visibility = View.VISIBLE
                } else {
                    binding.btnVote.visibility = View.INVISIBLE
                }
            }
        }
        binding.rvVotingPlayers.layoutManager = GridLayoutManager(this, 2)
        binding.rvVotingPlayers.adapter = adapter
    }

    private fun startTimer(seconds: Int) {
        timer?.cancel()
        timer = object : CountDownTimer((seconds * 1000).toLong(), 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secsLeft = (millisUntilFinished / 1000).toInt()
                binding.tvTimer.text = "Voting Ends In : $secsLeft seconds"
            }

            override fun onFinish() {
                binding.tvTimer.text = "Voting Ended"
                disableVotingInput()
                if (isMockMode) {
                    showMockEjectionResult()
                }
            }
        }.start()
    }

    private fun sendVote(targetId: String?) {
        if (isMockMode) {
            runOnUiThread {
                Toast.makeText(this, if (targetId == null) "You voted to skip." else "Vote submitted locally!", Toast.LENGTH_SHORT).show()
                disableVotingInput()
                mockHandler.postDelayed({
                    showMockEjectionResult()
                }, 3000)
            }
            return
        }

        val socket = SocketManager.getSocket() ?: return
        val payload = JSONObject().apply {
            put("roomCode", roomCode)
            put("targetId", targetId)
        }

        Log.d("VOTE_DEBUG", "Emitting game:vote with payload: $payload")
        socket.emit("game:vote", payload, io.socket.client.Ack { ackArgs ->
            if (ackArgs.isNotEmpty() && ackArgs[0] is JSONObject) {
                val ack = ackArgs[0] as JSONObject
                val ok = ack.optBoolean("ok")
                val message = ack.optString("message", "")

                runOnUiThread {
                    if (ok) {
                        Log.d("VOTE_DEBUG", "Vote processed successfully on server.")
                        disableVotingInput()
                    } else {
                        Log.e("VOTE_DEBUG", "Vote rejected by server: $message")
                        Toast.makeText(this@MeetingActivity, "Vote error: $message", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun listenVoteUpdate() {
        val socket = SocketManager.getSocket() ?: return
        socket.off("game:vote-update")
        socket.on("game:vote-update") { args ->
            if (args.isNotEmpty() && args[0] is JSONObject) {
                val data = args[0] as JSONObject
                val voterId = data.optString("voterId", "")
                if (voterId.isNotEmpty()) {
                    val username = playerMap[voterId] ?: "Unknown"
                    votedPlayer.add(voterId)
                    Log.d("VOTE_DEBUG", "Player voted: $username ($voterId)")
                    runOnUiThread {
                        adapter.notifyDataSetChanged()
                    }
                }
            }
        }
    }

    private fun resolveVoteEarly() {
        if (isMockMode) {
            runOnUiThread {
                Toast.makeText(this, "Voting ended early by host (Mock).", Toast.LENGTH_SHORT).show()
                disableVotingInput()
                showMockEjectionResult()
            }
            return
        }

        val socket = SocketManager.getSocket() ?: return
        val payload = JSONObject().apply {
            put("roomCode", roomCode)
        }

        Log.d("VOTE_DEBUG", "Emitting game:resolve-votes")
        socket.emit("game:resolve-votes", payload, io.socket.client.Ack { ackArgs ->
            if (ackArgs.isNotEmpty() && ackArgs[0] is JSONObject) {
                val ack = ackArgs[0] as JSONObject
                val ok = ack.optBoolean("ok")
                runOnUiThread {
                    if (ok) {
                        Log.d("VOTE_DEBUG", "Host successfully ended voting early.")
                    } else {
                        Log.e("VOTE_DEBUG", "Failed to resolve votes early (likely not host or already resolved).")
                        Toast.makeText(this@MeetingActivity, "Only the Host can end voting early!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun listenVoteResult() {
        val socket = SocketManager.getSocket() ?: return
        socket.off("game:vote-result")
        socket.on("game:vote-result") { args ->
            if (args.isNotEmpty() && args[0] is JSONObject) {
                val data = args[0] as JSONObject
                val result = data.optJSONObject("result") ?: return@on
                val type = result.optString("type", "")

                runOnUiThread {
                    timer?.cancel()
                    disableVotingInput()

                    // Visual Feedback of Vote Results
                    binding.tvPlayers.visibility = View.VISIBLE
                    if (type == "eject") {
                        val playerId = result.optString("playerId", "")
                        val username = playerMap[playerId] ?: "Unknown"
                        binding.tvPlayers.text = "$username was ejected."
                        binding.tvPlayers.setTextColor(getColor(android.R.color.holo_red_light))
                        Log.d("VOTE_DEBUG", "Vote result: Ejected $username")
                    } else {
                        binding.tvPlayers.text = "Vote tied. No one ejected."
                        binding.tvPlayers.setTextColor(getColor(android.R.color.white))
                        Log.d("VOTE_DEBUG", "Vote result: Tie/No one ejected")
                    }
                }
            }
        }
    }

    private fun listenFreeplayResumed() {
        val socket = SocketManager.getSocket() ?: return
        socket.off("game:freeplay-resumed")
        socket.on("game:freeplay-resumed") {
            Log.d("VOTE_DEBUG", "Freeplay resumed. Finishing MeetingActivity.")
            runOnUiThread {
                finish()
            }
        }
    }

    // --- Mock Simulation Handlers ---
    private fun runMockSimulation() {
        // Start simulated voter loop (votes register every 3-4 seconds)
        mockHandler.postDelayed(mockVotersRunnable, 2000)
    }

    private fun showMockEjectionResult() {
        mockHandler.removeCallbacks(mockVotersRunnable)
        timer?.cancel()

        binding.tvPlayers.visibility = View.VISIBLE
        // Randomly decide ejection or tie
        val isTied = Random().nextBoolean()
        if (isTied) {
            binding.tvPlayers.text = "Vote tied. No one ejected (Mock)."
            binding.tvPlayers.setTextColor(getColor(android.R.color.white))
        } else {
            // Randomly eject Player 2 or 3
            val victimIndex = if (userIdsList.size > 2) 1 else 0
            val victimId = userIdsList[victimIndex]
            val victimName = playerMap[victimId] ?: "PLAYER 2"
            binding.tvPlayers.text = "$victimName was ejected (Mock)."
            binding.tvPlayers.setTextColor(getColor(android.R.color.holo_red_light))
        }

        // Return to map screen after 4 seconds
        mockHandler.postDelayed({
            if (!isDestroyed && !isFinishing) {
                finish()
            }
        }, 4000)
    }

    private fun disableVotingInput() {
        binding.btnVote.isEnabled = false
        binding.btnSkip.isEnabled = false
        binding.btnEndVoting.isEnabled = false
        adapter.isEnabled = false
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
        mockHandler.removeCallbacks(mockVotersRunnable)

        val socket = SocketManager.getSocket()
        socket?.off("game:vote-update")
        socket?.off("game:vote-result")
        socket?.off("game:freeplay-resumed")
    }
}
