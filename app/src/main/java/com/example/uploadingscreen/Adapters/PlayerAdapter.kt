package com.example.uploadingscreen.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.uploadingscreen.R

class PlayerAdapter(
    private val players: List<String>
) : RecyclerView.Adapter<PlayerAdapter.PlayerViewHolder>() {

    class PlayerViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvPlayerName)
        val tvAvatar: TextView = view.findViewById(R.id.tvPlayerAvatar)
        val tvHost: TextView = view.findViewById(R.id.tvHostLabel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlayerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_player, parent, false)
        return PlayerViewHolder(view)
    }

    override fun getItemCount(): Int = players.size

    override fun onBindViewHolder(holder: PlayerViewHolder, position: Int) {
        val item: Any? = if (position >= 0 && position < players.size) players[position] else null
        val rawText = item?.toString() ?: "Player"
        val isHost = rawText.endsWith(" (Host)")
        val displayName = if (isHost) {
            rawText.substring(0, rawText.length - 7).trim()
        } else {
            rawText
        }
        holder.tvName.text = displayName
        holder.tvHost.visibility = if (isHost) View.VISIBLE else View.GONE
        holder.tvAvatar.text = "P${position + 1}"
    }
}