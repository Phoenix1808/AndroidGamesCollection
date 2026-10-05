package com.example.uploadingscreen.network

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import com.example.uploadingscreen.LobbyActivity
import com.example.uploadingscreen.LoginActivity
import org.json.JSONObject

// Handles the two ways a player can be thrown out of where they are:
//  - the server rejects our login token (it expires after 15 minutes and there is no refresh endpoint)
//  - the server refuses to let us back into our game after a reconnect
// Both can be detected on a network thread while any screen is open, so they navigate from the app context.
object SessionManager {

    const val EXTRA_NOTICE = "session_notice"

    private const val EXPIRY_MARGIN_SECONDS = 30

    private var appContext: Context? = null
    private val main = Handler(Looper.getMainLooper())

    // only send the user to login once, however many requests fail at the same time
    @Volatile private var expiring = false

    fun attach(context: Context) {
        appContext = context.applicationContext
    }

    // Called after a successful login
    fun onLoggedIn() {
        expiring = false
    }

    // Reads the JWT's exp claim. A token we can't parse is left for the server to judge.
    fun isTokenExpired(token: String): Boolean {
        return try {
            val payload = token.split(".")[1]
            val json = JSONObject(String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)))
            val exp = json.optLong("exp", 0L)
            exp != 0L && System.currentTimeMillis() / 1000 >= exp - EXPIRY_MARGIN_SECONDS
        } catch (e: Exception) {
            false
        }
    }

    // Safe to call from any thread
    fun onTokenRejected() {
        main.post {
            if (expiring) return@post
            val ctx = appContext ?: return@post
            expiring = true
            Log.d("SESSION", "Token rejected, sending user to login")

            ctx.getSharedPreferences("auth", Context.MODE_PRIVATE).edit().remove("token").apply()
            // the socket was authorised with the old token; a new one is created after login
            SocketManager.disconnect()

            ctx.startActivity(
                Intent(ctx, LoginActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    .putExtra(EXTRA_NOTICE, "Your session expired. Please log in again.")
            )
        }
    }

    // Safe to call from any thread. message is the server's reason, e.g. "Game already started".
    fun onRemovedFromRoom(message: String?) {
        main.post {
            if (expiring) return@post
            val ctx = appContext ?: return@post
            Log.d("SESSION", "Rejoin refused: $message")

            SocketManager.clrRoom()

            val notice = if (message == "Game already started") {
                "Connection lost. The game continued without you and can't be rejoined."
            } else {
                "Connection lost. You were removed from the room" + (message?.let { " ($it)." } ?: ".")
            }
            ctx.startActivity(
                Intent(ctx, LobbyActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    .putExtra(EXTRA_NOTICE, notice)
            )
        }
    }
}
