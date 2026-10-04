package com.example.uploadingscreen.network

import android.content.Intent
import android.util.Log
import com.example.uploadingscreen.GameOverActivity
import org.json.JSONObject

class GameEndHandler(
    private val activity: android.app.Activity,
    private val role: String?
) {
    private val socket = SocketManager.getSocket()

    //game:ended
    fun gameEnd(){
        socket?.off("game:ended")
        socket?.on("game:ended"){args->
            if(args.isNotEmpty() && args[0] is JSONObject){
                val data = args[0] as JSONObject
                val winner = data.optString("winner")
                Log.d("GAME_ENDED","Winner: $winner")

                activity.runOnUiThread {
                    val intent = Intent(activity, GameOverActivity::class.java)
                    intent.putExtra("winner",winner)
                    intent.putExtra("role",role)

                    activity.startActivity(intent)
                    activity.finish()
                }
            }
        }
    }


    //game:error
    // onError(event, message) runs on the socket thread
    fun gameError(onError: ((String, String) -> Unit)? = null){
        socket?.off("game:error")
        socket?.on("game:error"){args->
            if(args.isNotEmpty() && args[0] is JSONObject){
                val data = args[0] as JSONObject
                val event = data.optString("event")
                val msg = data.optString("message")
                Log.d("GAME_ERROR","$event -> $msg")
                // position updates fire every few seconds; don't spam the player about them
                if (event != "game:move" && msg.isNotEmpty()) onError?.invoke(event, msg)
            }
        }
    }

    fun cleanup(){
        socket?.off("game:ended")
        socket?.off("game:error")
    }
}