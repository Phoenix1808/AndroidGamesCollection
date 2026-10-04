package com.example.uploadingscreen

import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.example.uploadingscreen.adapter.VotingPlayerAdapter
import com.example.uploadingscreen.databinding.ActivityMeetingActivityBinding
import com.example.uploadingscreen.game.GameSession
import com.example.uploadingscreen.network.SocketManager
import io.socket.emitter.Emitter
import org.json.JSONObject
import java.util.Random

class MeetingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMeetingActivityBinding
    private lateinit var adapter: VotingPlayerAdapter

    private var roomCode: String? = null
    private val votedPlayer = mutableSetOf<String>()
    private val playerMap = mutableMapOf<String, String>() // userId -> username, alive players only
    private val userIdsList = mutableListOf<String>()

    private var timer: CountDownTimer? = null

    // host only: make sure we ask the server to resolve at most once
    private var resolveRequested = false
    private var resultReceived = false

    // own listener instance so we never remove GameActivity's game:ended handler
    private val gameEndedListener = Emitter.Listener { runOnUiThread { finish() } }

    // Offline sandbox, only reachable from the debug panel (roomCode == MOCK_LOBBY)
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

    companion object {
        const val MOCK_ROOM = "MOCK_LOBBY"
        // server's MEETING_DURATION_MS
        private const val MEETING_SECONDS = 120
        // how long the result stays on screen before going back to the map
        private const val RESULT_DISPLAY_MS = 3000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMeetingActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        roomCode = intent.getStringExtra("roomCode") ?: GameSession.roomCode
        isMockMode = roomCode == MOCK_ROOM

        if (isMockMode) {
            Log.d("SOCKET_DEBUG", "Running MeetingActivity in OFFLINE MOCK MODE")
            val userIdsArray = intent.getStringArrayExtra("userIds")
            val usernamesArray = intent.getStringArrayExtra("usernames")
            if (userIdsArray != null && usernamesArray != null) {
                for (i in userIdsArray.indices) {
                    playerMap[userIdsArray[i]] = usernamesArray[i]
                    userIdsList.add(userIdsArray[i])
                }
            }
        } else {
            // only living players can be voted for
            val alive = GameSession.alivePlayers()
            playerMap.putAll(alive)
            userIdsList.addAll(alive.keys)
        }

        setupRecyclerView()
        startTimer(intent.getIntExtra("duration", MEETING_SECONDS))

        if (isMockMode) {
            runMockSimulation()
        } else {
            listenVoteUpdate()
            listenVoteResult()
            listenFreeplayResumed()
            SocketManager.getSocket()?.on("game:ended", gameEndedListener)
        }

        binding.btnSkip.setOnClickListener {
            sendVote(null)
        }

        binding.btnVote.setOnClickListener {
            val selectedId = adapter.getSelectedPlayerId()
            if (selectedId != null) {
                sendVote(selectedId)
            } else {
                Toast.makeText(this, "Select a player first!", Toast.LENGTH_SHORT).show()
            }
        }

        // the server only accepts game:resolve-votes from the host
        binding.btnEndVoting.visibility = if (isMockMode || GameSession.isHost) View.VISIBLE else View.GONE
        binding.btnEndVoting.setOnClickListener {
            resolveVoteEarly()
        }

        // leaving the meeting screen would put us back on the map while voting continues
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Toast.makeText(this@MeetingActivity, "Meeting in progress", Toast.LENGTH_SHORT).show()
            }
        })

        if (!isMockMode && GameSession.amIDead) {
            disableVotingInput()
            showStatus("You are dead, watching the vote", android.R.color.darker_gray)
        }
    }

    private fun setupRecyclerView() {
        adapter = VotingPlayerAdapter(userIdsList, playerMap, votedPlayer, GameSession.myUserId) { selectedPlayerId ->
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
                if (isMockMode) {
                    binding.tvTimer.text = "Voting Ended"
                    disableVotingInput()
                    showMockEjectionResult()
                    return
                }
                // The server decides when voting is over; keep inputs open until game:vote-result
                binding.tvTimer.text = "Waiting for results…"
                autoResolveIfHost("timer ended")
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
            put("targetId", targetId ?: JSONObject.NULL)
        }

        Log.d("VOTE_DEBUG", "Emitting game:vote with payload: $payload")
        socket.emit("game:vote", payload, io.socket.client.Ack { ackArgs ->
            val ack = ackArgs.firstOrNull() as? JSONObject ?: return@Ack
            val ok = ack.optBoolean("ok")
            val message = ack.optString("message", "")

            runOnUiThread {
                if (ok) {
                    Log.d("VOTE_DEBUG", "Vote processed successfully on server.")
                    disableVotingInput()
                    GameSession.myUserId?.let { onVoterSeen(it) }
                } else {
                    Log.e("VOTE_DEBUG", "Vote rejected by server: $message")
                    Toast.makeText(this@MeetingActivity, "Vote error: $message", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun listenVoteUpdate() {
        val socket = SocketManager.getSocket() ?: return
        socket.off("game:vote-update")
        socket.on("game:vote-update") { args ->
            val data = args.firstOrNull() as? JSONObject ?: return@on
            val voterId = data.optString("voterId", "")
            if (voterId.isEmpty()) return@on
            Log.d("VOTE_DEBUG", "Player voted: ${playerMap[voterId] ?: "Unknown"} ($voterId)")
            runOnUiThread { onVoterSeen(voterId) }
        }
    }

    private fun onVoterSeen(voterId: String) {
        if (!votedPlayer.add(voterId)) return
        adapter.notifyDataSetChanged()
        // the server sometimes misses that everyone has voted (lost updates), so the host nudges it
        if (userIdsList.isNotEmpty() && votedPlayer.containsAll(userIdsList)) {
            autoResolveIfHost("all players voted")
        }
    }

    private fun autoResolveIfHost(reason: String) {
        if (isMockMode || !GameSession.isHost || resultReceived) return
        Log.d("VOTE_DEBUG", "Host auto-resolving votes: $reason")
        requestResolve(showErrors = false)
    }

    private fun resolveVoteEarly() {
        if (isMockMode) {
            runOnUiThread {
                Toast.makeText(this, "Voting ended early by host (Mock).", Toast.LENGTH_SHORT).show()
                disableVotingInput()
                binding.btnEndVoting.isEnabled = false
                showMockEjectionResult()
            }
            return
        }
        requestResolve(showErrors = true)
    }

    private fun requestResolve(showErrors: Boolean) {
        if (resolveRequested || resultReceived) return
        val socket = SocketManager.getSocket() ?: return
        resolveRequested = true
        val payload = JSONObject().apply {
            put("roomCode", roomCode)
        }

        Log.d("VOTE_DEBUG", "Emitting game:resolve-votes")
        socket.emit("game:resolve-votes", payload, io.socket.client.Ack { ackArgs ->
            val ack = ackArgs.firstOrNull() as? JSONObject
            runOnUiThread {
                if (ack?.optBoolean("ok") == true) {
                    Log.d("VOTE_DEBUG", "resolve-votes accepted")
                } else {
                    // allow another attempt (e.g. the timer firing after a failed early end)
                    resolveRequested = false
                    Log.e("VOTE_DEBUG", "resolve-votes rejected: ${ack?.optString("message")}")
                    if (showErrors) {
                        Toast.makeText(this@MeetingActivity, "Could not end voting", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun listenVoteResult() {
        val socket = SocketManager.getSocket() ?: return
        socket.off("game:vote-result")
        socket.on("game:vote-result") { args ->
            val data = args.firstOrNull() as? JSONObject ?: return@on
            val result = data.optJSONObject("result") ?: return@on
            val type = result.optString("type", "")
            // the server sends "eject" with a null playerId when nobody got votes
            val ejectedId = if (result.isNull("playerId")) null else result.optString("playerId").takeIf { it.isNotEmpty() }

            runOnUiThread {
                resultReceived = true
                timer?.cancel()
                binding.tvTimer.text = "Voting Ended"
                disableVotingInput()
                binding.btnEndVoting.isEnabled = false

                if (type == "eject" && ejectedId != null) {
                    GameSession.deadPlayers.add(ejectedId)
                    val text = if (ejectedId == GameSession.myUserId) {
                        "You were ejected."
                    } else {
                        "${GameSession.usernameOf(ejectedId)} was ejected."
                    }
                    showStatus(text, android.R.color.holo_red_light)
                    Log.d("VOTE_DEBUG", "Vote result: ejected $ejectedId")
                } else {
                    showStatus(if (type == "tie") "Vote tied. No one was ejected." else "No one was ejected.", android.R.color.white)
                    Log.d("VOTE_DEBUG", "Vote result: no ejection ($type)")
                }
                // a non-null winner is followed by game:ended, which closes this screen
            }
        }
    }

    private fun listenFreeplayResumed() {
        val socket = SocketManager.getSocket() ?: return
        socket.off("game:freeplay-resumed")
        socket.on("game:freeplay-resumed") {
            Log.d("VOTE_DEBUG", "Freeplay resumed. Finishing MeetingActivity.")
            runOnUiThread {
                // leave the result on screen for a moment before going back to the map
                mockHandler.postDelayed({ if (!isFinishing) finish() }, RESULT_DISPLAY_MS)
            }
        }
    }

    private fun showStatus(text: String, colorRes: Int) {
        binding.tvPlayers.visibility = View.VISIBLE
        binding.tvPlayers.text = text
        binding.tvPlayers.setTextColor(getColor(colorRes))
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
        if (isTied || userIdsList.isEmpty()) {
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

    // END EARLY stays usable for the host after they voted; it's switched off once the result is in
    private fun disableVotingInput() {
        binding.btnVote.isEnabled = false
        binding.btnSkip.isEnabled = false
        adapter.isEnabled = false
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
        mockHandler.removeCallbacksAndMessages(null)

        val socket = SocketManager.getSocket()
        socket?.off("game:vote-update")
        socket?.off("game:vote-result")
        socket?.off("game:freeplay-resumed")
        socket?.off("game:ended", gameEndedListener)
    }
}
