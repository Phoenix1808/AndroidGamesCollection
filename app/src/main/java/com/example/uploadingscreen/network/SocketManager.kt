package com.example.uploadingscreen.network

import android.util.Log
import io.socket.client.Ack
import io.socket.client.IO
import io.socket.client.Socket
import com.example.uploadingscreen.utils.Constant.BASE_URL
import org.json.JSONObject

object SocketManager {

    private var socket: Socket? = null
    private var currentRoomCode: String? = null

    // Keys are "<socketId>:<roomCode>". The server is not safe against concurrent duplicate joins
    // (two arriving together create two player records), so never send a second join while one is
    // in flight, and don't auto-rejoin a connection that already joined.
    private var inFlightKey: String? = null
    private var joinedKey: String? = null
    // waiting room's callback for the pending join, if any
    private var pendingAck: ((JSONObject?) -> Unit)? = null

    fun init(token: String) {
        try {

            if (socket != null) {
                Log.d("SOCKET", "Socket already initialized")
                return
            }

            val opts = IO.Options()
            opts.auth = mapOf("token" to token)
            opts.reconnection = true
            opts.reconnectionAttempts = Int.MAX_VALUE
            opts.reconnectionDelay = 2000

            val s = IO.socket(BASE_URL, opts)

            // registered once here; connect() may be called many times
            s.on(Socket.EVENT_CONNECT) {
                Log.d("SOCKET", "Connected ID: ${s.id()}")
                // first connect or reconnect: (re)join the room we belong to, once for this connection
                if (currentRoomCode != null) emitJoin(s, force = false)
            }

            s.on(Socket.EVENT_DISCONNECT) {
                Log.d("SOCKET", "Disconnected")
            }

            s.on(Socket.EVENT_CONNECT_ERROR) {
                Log.e("SOCKET", "Connection error")
            }

            socket = s

        } catch (e: Exception) {
            Log.e("SOCKET", "Initialization error: ${e.message}")
        }
    }

    fun connect() {

        val s = socket ?: return

        if (s.connected()) {
            Log.d("SOCKET", "Already connected")
            return
        }

        s.connect()
    }

    // The only place lobby:join-room is sent from. onAck receives the server's ack (null if no socket).
    fun joinRoom(roomCode: String, onAck: (JSONObject?) -> Unit) {
        val s = socket
        if (s == null) {
            onAck(null)
            return
        }
        currentRoomCode = roomCode
        pendingAck = onAck
        // if we're offline, the EVENT_CONNECT handler joins as soon as we're back
        if (s.connected()) emitJoin(s, force = true)
    }

    // force = an explicit join from the waiting room; false = automatic rejoin on (re)connect
    @Synchronized
    private fun emitJoin(s: Socket, force: Boolean) {
        val room = currentRoomCode ?: return
        val key = "${s.id()}:$room"
        if (key == inFlightKey) {
            // the in-flight join's ack will also answer pendingAck
            Log.d("SOCKET", "Join for $room already in flight, not sending another")
            return
        }
        if (!force && key == joinedKey) {
            Log.d("SOCKET", "Already joined $room on this connection, skipping rejoin")
            return
        }
        inFlightKey = key

        val payload = JSONObject().apply {
            put("roomCode", room)
        }

        s.emit("lobby:join-room", payload, Ack { args ->
            val ack = args.firstOrNull() as? JSONObject
            Log.d("SOCKET", "join-room $room ack: $ack")
            val callback = synchronized(this) {
                if (inFlightKey == key) inFlightKey = null
                joinedKey = if (ack?.optBoolean("ok") == true) key else null
                pendingAck.also { pendingAck = null }
            }
            callback?.invoke(ack)
        })
    }

    @Synchronized
    fun clrRoom() {
        currentRoomCode = null
        inFlightKey = null
        joinedKey = null
        pendingAck = null
    }

    fun getSocket(): Socket? = socket

    fun disconnect() {
        socket?.disconnect()
        socket?.off()
        socket = null
        clrRoom()
    }
}
