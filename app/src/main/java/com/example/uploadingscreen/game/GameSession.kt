package com.example.uploadingscreen.game

import android.content.Context

// Single source of truth for the current player and room, shared by every screen.
// Only write to it from the main thread.
object GameSession {

    var myUserId: String? = null
    var myUsername: String? = null

    var roomCode: String? = null
    var hostId: String? = null
    var role: String? = null
    var maxPlayers: Int = 6

    // userId -> username, in join order
    val players = LinkedHashMap<String, String>()
    val deadPlayers = mutableSetOf<String>()

    val isHost: Boolean
        get() = myUserId != null && myUserId == hostId

    val isImposter: Boolean
        get() = role == "imposter"

    val amIDead: Boolean
        get() = myUserId != null && myUserId in deadPlayers

    fun alivePlayers(): Map<String, String> = players.filterKeys { it !in deadPlayers }

    fun usernameOf(userId: String?): String = players[userId] ?: "Unknown"

    // Restores the logged-in user after the app process was recreated
    fun loadUser(context: Context) {
        val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)
        myUserId = prefs.getString("userId", null)
        myUsername = prefs.getString("username", null)
    }

    // Called when entering a new room, so nothing leaks over from the previous game
    fun enterRoom(code: String, maxPlayers: Int?, hostId: String?) {
        roomCode = code
        this.maxPlayers = maxPlayers ?: 6
        this.hostId = hostId
        role = null
        players.clear()
        deadPlayers.clear()
    }

    fun setPlayers(list: Map<String, String>, hostId: String?) {
        players.clear()
        players.putAll(list)
        if (hostId != null) this.hostId = hostId
    }
}
