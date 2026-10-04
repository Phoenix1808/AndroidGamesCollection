package com.example.uploadingscreen

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Paint
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.uploadingscreen.databinding.ActivityGameBinding
import com.example.uploadingscreen.game.GameSession
import com.example.uploadingscreen.network.SocketManager
import com.example.uploadingscreen.socket.TaskHandler
import com.example.uploadingscreen.network.GameEndHandler
import com.google.android.gms.location.*
import org.json.JSONObject
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*

data class DeadBody(
    val victimId: String,
    val lat: Double,
    val lng: Double
)

class GameActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityGameBinding

    // userId -> username, shared with the waiting room and meeting screens
    private val playerMap get() = GameSession.players

    private var roomCode: String? = null
    private var role: String? = null

    private var currentVictimId: String? = null

    // unreported bodies, kept in sync with the server via game:get-bodies
    private val deadBodies = mutableListOf<DeadBody>()
    private val bodyMarkers = mutableMapOf<String, Marker>()
    private val deadPlayerIds get() = GameSession.deadPlayers
    private var reportTargetBody: DeadBody? = null

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private lateinit var taskHandler : TaskHandler
    private lateinit var lifeHandler : GameEndHandler

    private lateinit var mMap: GoogleMap
    private val playerMarkers = mutableMapOf<String, Marker>()
    private var myMarker: Marker? = null

    // Debug Mock Location fields
    private var isMockLocation = false
    private var mockLat = 28.6135
    private var mockLng = 77.3594
    private var roomCodeClickCount = 0
    private var isInMeeting = false

    // Latest location trackers (initially 0.0 to prevent default spawning at JSS Noida)
    private var lastKnownLat = 0.0
    private var lastKnownLng = 0.0

    // What we last sent to the server. The server rewrites the whole game state on every game:move,
    // so moves that land together with a kill/report/vote can wipe those out. Send as few as we can.
    private var lastSentLat = 0.0
    private var lastSentLng = 0.0
    private var lastSentAt = 0L
    // no automatic moves until this time (set around kills/reports)
    private var movesPausedUntil = 0L

    // Background sync updates
    private val locationUpdateHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val locationUpdateRunnable = object : Runnable {
        override fun run() {
            if (!isFinishing && !isDestroyed) {
                if (lastKnownLat != 0.0 && lastKnownLng != 0.0) {
                    maybeSendMove(lastKnownLat, lastKnownLng)
                    checkBodyNearby(lastKnownLat, lastKnownLng)
                }
                locationUpdateHandler.postDelayed(this, MOVE_INTERVAL_MS)
            }
        }
    }

    // Track local completion state for each task game
    private val taskStates = mutableMapOf(
        "wires" to false,
        "memory" to false,
        "upload" to false,
        "sensors" to false,
        "trivia" to false
    )

    companion object {
        private const val LOCATION_PERMISSION_REQUEST = 101
        // server accepts reports within 8m; stay a little under it to absorb GPS lag
        private const val REPORT_RANGE_METRES = 7.5f
        // automatic position updates: at most one per interval, and only if we moved (or it's been a while)
        private const val MOVE_INTERVAL_MS = 3000L
        private const val MOVE_MIN_METRES = 1f
        private const val MOVE_KEEPALIVE_MS = 10_000L
        // gap between our fresh position and a kill/report, so the server doesn't process them together
        private const val ACTION_AFTER_MOVE_MS = 700L
        private const val TASK_WIRES_REQUEST = 1001
        private const val TASK_MEMORY_REQUEST = 1002
        private const val TASK_UPLOAD_REQUEST = 1003
        private const val TASK_SENSORS_REQUEST = 1004
        private const val TASK_TRIVIA_REQUEST = 1005
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        binding = ActivityGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // edge-to-edge: keep the top bar (back button) out from under the status bar
        val basePadding = binding.root.paddingTop
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(basePadding + bars.left, basePadding + bars.top, basePadding + bars.right, basePadding + bars.bottom)
            insets
        }

        // Enable emergency broadcast marquee scroll
        binding.tvBroadcast.isSelected = true

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        roomCode = intent.getStringExtra("roomCode") ?: GameSession.roomCode
        role = GameSession.role

        binding.tvRoomCode.text = "Room Code: $roomCode"

        if (role != null) {
            binding.tvRole.text = "Role: ${role!!.uppercase()}"
            binding.tvStatus.text = "Game Started"

            if (role == "imposter") {
                binding.tvRole.setTextColor(getColor(android.R.color.holo_red_dark))
                binding.btnAction.text = "Kill"
                binding.btnAction.setBackgroundResource(R.drawable.btn_red_gradient)
                binding.btnAction.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_skull, 0, 0, 0)
                // enabled by game:nearby-targets once a crewmate is in range
                setKillEnabled(false)
            } else {
                binding.tvRole.setTextColor(getColor(android.R.color.holo_green_dark))
                binding.btnAction.text = "How to Play"
                binding.btnAction.setBackgroundResource(R.drawable.btn_gradient)
                binding.btnAction.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_help, 0, 0, 0)
            }
        } else {
            binding.tvStatus.text = "Role Not Assigned"
        }

        // Leaving mid-game can't be undone (the server won't let us rejoin), so confirm first
        binding.btnBack.setOnClickListener { confirmLeave() }
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = confirmLeave()
        })

        // Action Button click (Kill for Imposters, How to Play for Crewmates)
        binding.btnAction.setOnClickListener {
            if (role == "imposter") {
                val victim = currentVictimId ?: return@setOnClickListener
                sendKill(victim)
            } else {
                Toast.makeText(
                    this,
                    "How to Play: Tap task list items or circular Do Task button at bottom to start tasks. Complete 25 total tasks to win!",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        binding.btnReport.setOnClickListener {
            sendReportBody()
        }

        binding.btnReport.visibility = View.GONE

        // Socket tasks progress listener
        taskHandler = TaskHandler(roomCode) { completed, total ->
            runOnUiThread {
                binding.tvTaskProgress.text = "Tasks: $completed / $total"
                val percent = if (total > 0) (completed.toFloat() / total.toFloat() * 100f).toInt() else 0
                binding.circleProgress.progress = percent
                binding.tvPercent.text = "$percent%"
            }
        }
        taskHandler.TaskProgress()

        // Set Click Listeners for individual Task Items
        binding.layoutTaskWires.setOnClickListener { launchTask("wires") }
        binding.layoutTaskMemory.setOnClickListener { launchTask("memory") }
        binding.layoutTaskUpload.setOnClickListener { launchTask("upload") }
        binding.layoutTaskSensors.setOnClickListener { launchTask("sensors") }
        binding.layoutTaskTrivia.setOnClickListener { launchTask("trivia") }

        // Floating Do Task FAB click listener (launches the next incomplete task)
        binding.btnDoTask.setOnClickListener {
            val nextTask = nextIncompleteTask()
            if (nextTask != null) {
                launchTask(nextTask)
            } else {
                Toast.makeText(this, "All tasks completed locally!", Toast.LENGTH_SHORT).show()
            }
        }

        lifeHandler = GameEndHandler(this, role)
        lifeHandler.gameEnd()
        lifeHandler.gameError { _, message ->
            runOnUiThread { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
        }

        listenForPlayerMovement()
        listenForTargets()
        listenForKillEvent()
        listenMeetingStart()
        // bodies are (re)loaded in onResume

        setupLocation()
        setupDebugMockControls()
    }

    private fun nextIncompleteTask(): String? {
        val order = listOf("wires", "memory", "upload", "sensors", "trivia")
        for (task in order) {
            if (taskStates[task] == false) {
                return task
            }
        }
        return null
    }

    private fun launchTask(taskName: String) {
        if (GameSession.amIDead) {
            Toast.makeText(this, "You are dead and can't do tasks", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = when (taskName) {
            "wires" -> Intent(this, ConnectDotActivity::class.java)
            "memory" -> Intent(this, MemoryGameActivity::class.java)
            "upload" -> Intent(this, UploadingActivity::class.java)
            "sensors" -> Intent(this, NumSeqActivity::class.java)
            "trivia" -> Intent(this, TriviaQuizActivity::class.java)
            else -> return
        }
        val requestCode = when (taskName) {
            "wires" -> TASK_WIRES_REQUEST
            "memory" -> TASK_MEMORY_REQUEST
            "upload" -> TASK_UPLOAD_REQUEST
            "sensors" -> TASK_SENSORS_REQUEST
            "trivia" -> TASK_TRIVIA_REQUEST
            else -> return
        }
        startActivityForResult(intent, requestCode)
    }

    private fun updateTaskUI(taskName: String, completed: Boolean) {
        val titleView: TextView?
        val subView: TextView?
        val indicatorView: View?

        when (taskName) {
            "wires" -> {
                titleView = binding.tvTaskWiresTitle
                subView = binding.tvTaskWiresSub
                indicatorView = binding.indicatorTaskWires
            }
            "memory" -> {
                titleView = binding.tvTaskMemoryTitle
                subView = binding.tvTaskMemorySub
                indicatorView = binding.indicatorTaskMemory
            }
            "upload" -> {
                titleView = binding.tvTaskUploadTitle
                subView = binding.tvTaskUploadSub
                indicatorView = binding.indicatorTaskUpload
            }
            "sensors" -> {
                titleView = binding.tvTaskSensorsTitle
                subView = binding.tvTaskSensorsSub
                indicatorView = binding.indicatorTaskSensors
            }
            "trivia" -> {
                titleView = binding.tvTaskTriviaTitle
                subView = binding.tvTaskTriviaSub
                indicatorView = binding.indicatorTaskTrivia
            }
            else -> return
        }

        runOnUiThread {
            if (completed) {
                titleView.paintFlags = titleView.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                titleView.setTextColor(getColor(android.R.color.darker_gray))
                subView.setTextColor(getColor(android.R.color.darker_gray))
                indicatorView.setBackgroundResource(R.drawable.ic_check_circle)
                indicatorView.backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(android.R.color.holo_green_light))
            } else {
                titleView.paintFlags = titleView.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                titleView.setTextColor(getColor(android.R.color.white))
                subView.setTextColor(android.graphics.Color.parseColor("#80FFFFFF"))
                val indicatorColor = when (taskName) {
                    "wires" -> android.graphics.Color.parseColor("#FFD24D")
                    "memory" -> android.graphics.Color.parseColor("#B86BFF")
                    "upload" -> android.graphics.Color.parseColor("#4BA6FF")
                    "sensors" -> android.graphics.Color.parseColor("#00FF88")
                    "trivia" -> android.graphics.Color.parseColor("#FF4B4B")
                    else -> android.graphics.Color.WHITE
                }
                indicatorView.setBackgroundResource(R.drawable.plus_circle_bg)
                indicatorView.backgroundTintList = android.content.res.ColorStateList.valueOf(indicatorColor)
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.uiSettings.isZoomControlsEnabled = true
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            mMap.isMyLocationEnabled = true
        }

        // bodies may have been loaded before the map was ready
        deadBodies.forEach { addBodyMarker(it) }

        mMap.setOnMarkerClickListener { marker ->
            val title = marker.title
            if (title != "You" && title != "Dead Body" && title != null) {
                val pos = marker.position
                mockLat = pos.latitude
                mockLng = pos.longitude
                if (!isMockLocation) {
                    activateMockGPS()
                }
                updateMockLocation()
                Toast.makeText(this, "Teleported to Player: $title", Toast.LENGTH_SHORT).show()
            }
            false
        }
    }

    private fun setupLocation() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                if (isMockLocation) return
                val location = result.lastLocation ?: return
                val lat = location.latitude
                val lng = location.longitude
                lastKnownLat = lat
                lastKnownLng = lng

                maybeSendMove(lat, lng)

                // update markers
                if (::mMap.isInitialized) {
                    val latLng = LatLng(lat, lng)
                    if (myMarker == null) {
                        myMarker = mMap.addMarker(
                            MarkerOptions().position(latLng).title("You")
                        )
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 17f))
                    } else {
                        myMarker?.position = latLng
                    }
                }

                checkBodyNearby(lat, lng)
                Log.d("REAL_GPS", "$lat,$lng")
            }
        }

        requestLocationPermission()
    }

    private fun requestLocationPermission() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST
            )
        } else {
            startLocationUpdates()
        }
    }

    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            2000
        )
            .setMinUpdateIntervalMillis(1000)
            .build()

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.requestLocationUpdates(
                request,
                locationCallback,
                mainLooper
            )

            // Immediately query the last cached location to initialize coordinates instantly
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null && !isMockLocation) {
                    lastKnownLat = location.latitude
                    lastKnownLng = location.longitude
                    sendMove(lastKnownLat, lastKnownLng)
                    if (::mMap.isInitialized) {
                        val latLng = LatLng(lastKnownLat, lastKnownLng)
                        if (myMarker == null) {
                            myMarker = mMap.addMarker(
                                MarkerOptions().position(latLng).title("You")
                            )
                            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 17f))
                        } else {
                            myMarker?.position = latLng
                        }
                    }
                    Log.d("STARTUP_GPS", "Initialized last known location: $lastKnownLat, $lastKnownLng")
                }
            }
        }
    }

    private fun sendMove(lat: Double, lng: Double) {
        if (isInMeeting) {
            Log.d("MOVE_BLOCKED", "Movement blocked because we are in a meeting.")
            return
        }
        // no GPS fix yet; sending 0,0 would teleport us into the ocean
        if (lat == 0.0 && lng == 0.0) return
        // dead players don't move on other players' maps
        if (GameSession.amIDead) return
        val socket = SocketManager.getSocket() ?: return

        val payload = JSONObject().apply {
            put("roomCode", roomCode)
            put(
                "position",
                JSONObject().apply {
                    put("lat", lat)
                    put("lng", lng)
                }
            )
        }

        socket.emit("game:move", payload)
        lastSentLat = lat
        lastSentLng = lng
        lastSentAt = System.currentTimeMillis()
        Log.d("MOVE_SENT", "My position: $lat,$lng")
    }

    // Throttled sender for GPS updates and the sync loop
    private fun maybeSendMove(lat: Double, lng: Double) {
        val now = System.currentTimeMillis()
        if (now < movesPausedUntil) return
        val sinceLast = now - lastSentAt
        if (sinceLast < MOVE_INTERVAL_MS) return
        val moved = FloatArray(1)
        Location.distanceBetween(lastSentLat, lastSentLng, lat, lng, moved)
        if (moved[0] >= MOVE_MIN_METRES || sinceLast >= MOVE_KEEPALIVE_MS) sendMove(lat, lng)
    }

    // Sends our current position, then runs the action shortly after, with automatic moves paused
    private fun afterFreshPosition(action: () -> Unit) {
        sendMove(lastKnownLat, lastKnownLng)
        movesPausedUntil = System.currentTimeMillis() + ACTION_AFTER_MOVE_MS * 2
        locationUpdateHandler.postDelayed({
            if (!isFinishing && !isDestroyed) action()
        }, ACTION_AFTER_MOVE_MS)
    }

    private fun listenForPlayerMovement() {
        val socket = SocketManager.getSocket() ?: return
        socket.off("game:player-moved")

        socket.on("game:player-moved") { args ->
            if (args.isNotEmpty() && args[0] is JSONObject) {
                val data = args[0] as JSONObject
                val userId = data.getString("userId")

                // the server echoes our own moves back; "You" marker already covers us
                if (userId == GameSession.myUserId) return@on

                val username = playerMap[userId] ?: "Unknown"
                val position = data.getJSONObject("position")
                val lat = position.getDouble("lat")
                val lng = position.getDouble("lng")

                runOnUiThread {
                    // other player markers
                    if (::mMap.isInitialized) {
                        val latLng = LatLng(lat, lng)
                        if (playerMarkers.containsKey(userId)) {
                            playerMarkers[userId]?.position = latLng
                        } else {
                            val marker = mMap.addMarker(
                                MarkerOptions().position(latLng).title(username)
                            )
                            marker?.let { playerMarkers[userId] = it }
                        }
                    }
                    Log.d("PLAYER_MOVED", "$username moved to $lat,$lng")
                }
            }
        }
    }

    private fun listenForTargets() {
        val socket = SocketManager.getSocket() ?: return
        socket.off("game:nearby-targets")

        socket.on("game:nearby-targets") { args ->
            if (args.isNotEmpty() && args[0] is JSONObject) {
                val data = args[0] as JSONObject
                val targets = data.getJSONArray("targets")

                runOnUiThread {
                    val count = targets.length()
                    if (role == "imposter") setKillEnabled(count > 0)

                    if (count > 0) {
                        val victim = targets.getJSONObject(0)
                        currentVictimId = victim.getString("userId")
                    } else {
                        currentVictimId = null
                    }
                }
            }
        }
    }

    // Called only for server-confirmed kills (game:kill-event). Winning is decided by the server via game:ended.
    private fun onKillConfirmed(victimId: String, lat: Double, lng: Double) {
        if (deadPlayerIds.contains(victimId)) return
        deadPlayerIds.add(victimId)

        val body = DeadBody(victimId, lat, lng)
        deadBodies.add(body)
        addBodyMarker(body)

        // the victim no longer walks around
        playerMarkers.remove(victimId)?.remove()

        val username = playerMap[victimId] ?: "Unknown Player"
        if (victimId == GameSession.myUserId) {
            Toast.makeText(this, "You were killed!", Toast.LENGTH_LONG).show()
            applyDeadState()
        } else {
            binding.tvBroadcast.text = "Emergency Broadcast: $username has been found dead!"
            Toast.makeText(this, "$username was eliminated!", Toast.LENGTH_LONG).show()
        }

        checkBodyNearby(lastKnownLat, lastKnownLng)
    }

    private fun sendKill(victimId: String) {
        val socket = SocketManager.getSocket() ?: return
        val payload = JSONObject().apply {
            put("roomCode", roomCode)
            put("victimId", victimId)
        }

        // re-enabled by the next game:nearby-targets; failures arrive as game:error toasts
        setKillEnabled(false)

        // refresh our position first so the server's range check uses where we are now
        afterFreshPosition { socket.emit("game:kill", payload) }
    }

    private fun setKillEnabled(enabled: Boolean) {
        binding.btnAction.isEnabled = enabled
        binding.btnAction.alpha = if (enabled) 1f else 0.5f
    }

    private fun addBodyMarker(body: DeadBody) {
        if (!::mMap.isInitialized || bodyMarkers.containsKey(body.victimId)) return
        mMap.addMarker(
            MarkerOptions()
                .position(LatLng(body.lat, body.lng))
                .title("Dead Body")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
        )?.let { bodyMarkers[body.victimId] = it }
    }

    private fun listenForKillEvent() {
        val socket = SocketManager.getSocket() ?: return
        socket.off("game:kill-event")

        socket.on("game:kill-event") { args ->
            if (args.isNotEmpty() && args[0] is JSONObject) {
                val data = args[0] as JSONObject
                val victimId = data.getString("victimId")
                val position = data.getJSONObject("position")
                val lat = position.getDouble("lat")
                val lng = position.getDouble("lng")

                runOnUiThread {
                    onKillConfirmed(victimId, lat, lng)
                }
            }
        }
    }

    private fun sendReportBody() {
        val victimId = reportTargetBody?.victimId ?: return
        val socket = SocketManager.getSocket() ?: return

        val payload = JSONObject().apply {
            put("roomCode", roomCode)
            put("bodyVictimId", victimId)
        }

        // send exact coordinates first so the server doesn't reject the report due to a stale location
        afterFreshPosition { emitReport(socket, payload) }

        // SOLO DEBUG FALLBACK: If testing alone or offline, locally start the MeetingActivity
        // so that the voting screen is viewable and interactive for layout check
        if (!socket.connected() || playerMap.size <= 1) {
            val intent = Intent(this, MeetingActivity::class.java).apply {
                putExtra("roomCode", MeetingActivity.MOCK_ROOM)
                val mockUserIds = arrayOf("uid1", "uid2", "uid3", "uid4")
                val mockUsernames = arrayOf("Player 1", "Player 2", "Player 3", "Player 4")
                putExtra("userIds", mockUserIds)
                putExtra("usernames", mockUsernames)
                putExtra("duration", 60)
            }
            startActivity(intent)
        }
    }

    private fun emitReport(socket: io.socket.client.Socket, payload: JSONObject) {
        Log.d("REPORT_BODY", "Emitting game:report-body with payload: $payload")
        socket.emit("game:report-body", payload, io.socket.client.Ack { ackArgs ->
            if (ackArgs.isNotEmpty() && ackArgs[0] is JSONObject) {
                val ack = ackArgs[0] as JSONObject
                val ok = ack.optBoolean("ok")
                runOnUiThread {
                    if (ok) {
                        Log.d("REPORT_BODY", "Report successful")
                        binding.btnReport.visibility = View.GONE
                    } else {
                        val message = ack.optString("message", "")
                        Log.e("REPORT_BODY_ERROR", message)
                        Toast.makeText(this@GameActivity, "Report failed: $message", Toast.LENGTH_SHORT).show()
                        // our body list may be stale (already reported, or lost by the server); resync it
                        requestBodies()
                    }
                }
            }
        })
    }

    private fun checkBodyNearby(myLat: Double, myLng: Double) {
        if (GameSession.amIDead) {
            reportTargetBody = null
            binding.btnReport.visibility = View.GONE
            return
        }
        var foundBody: DeadBody? = null

        for (body in deadBodies) {
            val results = FloatArray(1)
            Location.distanceBetween(
                myLat,
                myLng,
                body.lat,
                body.lng,
                results
            )

            if (results[0] <= REPORT_RANGE_METRES) {
                foundBody = body
                break
            }
        }

        if (foundBody != null) {
            reportTargetBody = foundBody
            binding.btnReport.visibility = View.VISIBLE
        } else {
            reportTargetBody = null
            binding.btnReport.visibility = View.GONE
        }
    }

    // Replaces our body list with the server's unreported bodies (reported ones disappear after a meeting)
    private fun requestBodies() {
        val socket = SocketManager.getSocket() ?: return
        val payload = JSONObject().apply {
            put("roomCode", roomCode)
        }
        socket.emit("game:get-bodies", payload, io.socket.client.Ack { args ->
            val ack = args.firstOrNull() as? JSONObject ?: return@Ack
            if (!ack.optBoolean("ok")) return@Ack
            val arr = ack.optJSONArray("bodies") ?: return@Ack
            val fresh = (0 until arr.length()).mapNotNull { i ->
                val b = arr.optJSONObject(i) ?: return@mapNotNull null
                DeadBody(b.optString("victimId"), b.optDouble("lat"), b.optDouble("lng"))
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                bodyMarkers.values.forEach { it.remove() }
                bodyMarkers.clear()
                deadBodies.clear()
                deadBodies.addAll(fresh)
                fresh.forEach {
                    deadPlayerIds.add(it.victimId)
                    playerMarkers.remove(it.victimId)?.remove()
                    addBodyMarker(it)
                }
                applyDeadState()
                checkBodyNearby(lastKnownLat, lastKnownLng)
            }
        })
    }

    private fun listenMeetingStart() {
        val socket = SocketManager.getSocket() ?: return
        socket.off("game:meeting-started")

        socket.on("game:meeting-started") { args ->
            runOnUiThread {
                isInMeeting = true
                fusedLocationClient.removeLocationUpdates(locationCallback)
                locationUpdateHandler.removeCallbacks(locationUpdateRunnable)
                // MeetingActivity reads the living players from GameSession
                val intent = Intent(this, MeetingActivity::class.java).apply {
                    putExtra("roomCode", roomCode)
                    // the server doesn't send a duration today; MeetingActivity defaults to its 120s
                    (args.firstOrNull() as? JSONObject)?.optInt("duration", 0)
                        ?.takeIf { it > 0 }
                        ?.let { putExtra("duration", it) }
                }
                startActivity(intent)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isInMeeting = false
        if (::fusedLocationClient.isInitialized) {
            startLocationUpdates()
        }
        // Start/resume the 3-second background location sync loop
        locationUpdateHandler.removeCallbacks(locationUpdateRunnable)
        locationUpdateHandler.post(locationUpdateRunnable)

        // on first open and after every meeting: reported bodies are gone, ejected players are dead
        requestBodies()
        applyDeadState()
    }

    private fun applyDeadState() {
        if (!GameSession.amIDead) return
        binding.tvStatus.text = "YOU ARE DEAD"
        binding.tvStatus.setTextColor(getColor(android.R.color.holo_red_light))
        binding.tvBroadcast.text = "You are dead. Watch the rest of the game."
        binding.btnReport.visibility = View.GONE
        binding.btnDoTask.visibility = View.GONE
        binding.tvDoTaskLabel.visibility = View.GONE
        if (role == "imposter") setKillEnabled(false)
    }

    private fun confirmLeave() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Leave game?")
            .setMessage("You won't be able to rejoin this game.")
            .setPositiveButton("Leave") { _, _ -> finish() }
            .setNegativeButton("Stay", null)
            .show()
    }

    override fun onPause() {
        super.onPause()
        if (::fusedLocationClient.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
        // Stop the background sync loop to prevent emitting moves during meetings
        locationUpdateHandler.removeCallbacks(locationUpdateRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        locationUpdateHandler.removeCallbacks(locationUpdateRunnable)
        val socket = SocketManager.getSocket()
        socket?.off("game:player-moved")
        socket?.off("game:nearby-targets")
        socket?.off("game:kill-event")
        socket?.off("game:meeting-started")

        taskHandler.cleanup()
        lifeHandler.cleanup()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            val taskName = when (requestCode) {
                TASK_WIRES_REQUEST -> "wires"
                TASK_MEMORY_REQUEST -> "memory"
                TASK_UPLOAD_REQUEST -> "upload"
                TASK_SENSORS_REQUEST -> "sensors"
                TASK_TRIVIA_REQUEST -> "trivia"
                else -> null
            }
            if (taskName != null) {
                taskStates[taskName] = true
                updateTaskUI(taskName, true)
                if (role == "crewmate" && !GameSession.amIDead) {
                    taskHandler.TaskComplete()
                }

                // If all 5 tasks are completed, reset them to allow repetition
                if (taskStates.values.all { it }) {
                    Toast.makeText(this, "All tasks completed locally! Starting a new round...", Toast.LENGTH_LONG).show()
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        taskStates.keys.forEach { key ->
                            taskStates[key] = false
                            updateTaskUI(key, false)
                        }
                    }, 1000)
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startLocationUpdates()
        }
    }

    private fun setupDebugMockControls() {
        binding.tvRoomCode.setOnClickListener {
            roomCodeClickCount++
            if (roomCodeClickCount >= 5) {
                binding.layoutDebugControls.visibility = View.VISIBLE
                Toast.makeText(this, "Debug Mock GPS Panel Activated!", Toast.LENGTH_SHORT).show()
                activateMockGPS()
                teleportToJSS()
            }
        }

        binding.btnToggleMockGPS.setOnClickListener {
            if (isMockLocation) {
                isMockLocation = false
                binding.btnToggleMockGPS.text = "Enable Mock GPS"
                binding.btnToggleMockGPS.setBackgroundColor(getColor(android.R.color.holo_orange_dark))
                startLocationUpdates()
                Toast.makeText(this, "Real GPS Enabled", Toast.LENGTH_SHORT).show()
            } else {
                activateMockGPS()
                Toast.makeText(this, "Mock GPS Enabled", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnMockUp.setOnClickListener { moveMock(0.0001, 0.0) }
        binding.btnMockDown.setOnClickListener { moveMock(-0.0001, 0.0) }
        binding.btnMockLeft.setOnClickListener { moveMock(0.0, -0.0001) }
        binding.btnMockRight.setOnClickListener { moveMock(0.0, 0.0001) }

        binding.btnMockTeleportJSS.setOnClickListener {
            teleportToJSS()
        }

        binding.btnMockSpawnBody.setOnClickListener {
            // Pick a real player ID from playerMap to ensure the server recognizes it as a valid player in the room.
            // Exclude the current socket ID (our own ID) if possible, otherwise fall back.
            val currentSocketId = SocketManager.getSocket()?.id()
            val victimId = playerMap.keys.firstOrNull { it != currentSocketId }
                ?: playerMap.keys.firstOrNull()
                ?: "mock-victim-${System.currentTimeMillis()}"

            deadBodies.add(DeadBody(victimId, mockLat, mockLng))
            if (::mMap.isInitialized) {
                mMap.addMarker(
                    com.google.android.gms.maps.model.MarkerOptions()
                        .position(com.google.android.gms.maps.model.LatLng(mockLat, mockLng))
                        .title("Dead Body")
                        .icon(com.google.android.gms.maps.model.BitmapDescriptorFactory.defaultMarker(com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_RED))
                )
            }
            checkBodyNearby(mockLat, mockLng)
            Toast.makeText(this, "Mock Dead Body Spawned (Victim ID: $victimId)!", Toast.LENGTH_SHORT).show()
        }

        binding.btnMockToggleRole.setOnClickListener {
            if (role == "imposter") {
                role = "crewmate"
                binding.tvRole.text = "Role: CREWMATE"
                binding.tvRole.setTextColor(getColor(android.R.color.holo_green_dark))
                binding.btnAction.text = "How to Play"
                binding.btnAction.setBackgroundResource(R.drawable.btn_gradient)
                binding.btnAction.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_help, 0, 0, 0)
                Toast.makeText(this, "Changed role to CREWMATE locally!", Toast.LENGTH_SHORT).show()
            } else {
                role = "imposter"
                binding.tvRole.text = "Role: IMPOSTER"
                binding.tvRole.setTextColor(getColor(android.R.color.holo_red_dark))
                binding.btnAction.text = "Kill"
                binding.btnAction.setBackgroundResource(R.drawable.btn_red_gradient)
                binding.btnAction.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_skull, 0, 0, 0)
                Toast.makeText(this, "Changed role to IMPOSTER locally!", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnMockSimulateMeeting.setOnClickListener {
            val intent = Intent(this, MeetingActivity::class.java).apply {
                putExtra("roomCode", "MOCK_LOBBY")
                val mockUserIds = arrayOf("uid1", "uid2", "uid3", "uid4", "uid5", "uid6")
                val mockUsernames = arrayOf("PLAYER 1", "PLAYER 2", "PLAYER 3", "PLAYER 4", "PLAYER 5", "PLAYER 6")
                putExtra("userIds", mockUserIds)
                putExtra("usernames", mockUsernames)
                putExtra("duration", 60)
            }
            startActivity(intent)
            Toast.makeText(this, "Launching Offline Meeting Sandbox...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun activateMockGPS() {
        isMockLocation = true
        binding.btnToggleMockGPS.text = "Disable Mock GPS"
        binding.btnToggleMockGPS.setBackgroundColor(getColor(android.R.color.holo_red_dark))
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun teleportToJSS() {
        mockLat = 28.6135
        mockLng = 77.3594
        updateMockLocation()
        Toast.makeText(this, "Teleported to JSS Noida", Toast.LENGTH_SHORT).show()
    }

    private fun moveMock(dLat: Double, dLng: Double) {
        if (!isMockLocation) {
            activateMockGPS()
        }
        mockLat += dLat
        mockLng += dLng
        updateMockLocation()
    }

    private fun updateMockLocation() {
        // the 3s sync loop sends lastKnown*, so it must follow the mock position too
        lastKnownLat = mockLat
        lastKnownLng = mockLng
        sendMove(mockLat, mockLng)
        if (::mMap.isInitialized) {
            val latLng = com.google.android.gms.maps.model.LatLng(mockLat, mockLng)
            if (myMarker == null) {
                myMarker = mMap.addMarker(
                    com.google.android.gms.maps.model.MarkerOptions().position(latLng).title("You")
                )
            } else {
                myMarker?.position = latLng
            }
            mMap.moveCamera(com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(latLng, 18f))
        }
        checkBodyNearby(mockLat, mockLng)
    }
}