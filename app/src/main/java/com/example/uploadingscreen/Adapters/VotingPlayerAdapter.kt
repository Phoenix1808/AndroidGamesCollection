package com.example.uploadingscreen.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.uploadingscreen.R

class VotingPlayerAdapter(
    private val userIds: List<String>,
    private val playerMap: Map<String, String>,
    private val votedPlayers: Set<String>,
    private val onPlayerSelected: (String?) -> Unit
) : RecyclerView.Adapter<VotingPlayerAdapter.VotingViewHolder>() {

    private var selectedPosition = -1

    private val avatars = listOf(
        R.drawable.av1,
        R.drawable.av2,
        R.drawable.av3,
        R.drawable.av4,
        R.drawable.av5
    )

    class VotingViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvPlayerName)
        val imgAvatar: ImageView = view.findViewById(R.id.imgPlayerAvatar)
        val imgVoted: ImageView = view.findViewById(R.id.imgVotedStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VotingViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_voting_player, parent, false)
        return VotingViewHolder(view)
    }

    var isEnabled: Boolean = true

    override fun getItemCount(): Int = userIds.size

    override fun onBindViewHolder(holder: VotingViewHolder, position: Int) {
        val userId = userIds[position]
        val username = playerMap[userId] ?: "Player ${position + 1}"

        // Format name to uppercase to fit cyberpunk vibe
        holder.tvName.text = username.uppercase()

        // Assign a consistent character avatar by cycling through available resources
        val avatarRes = avatars[position % avatars.size]
        holder.imgAvatar.setImageResource(avatarRes)

        // Show/hide voted status badge
        if (votedPlayers.contains(userId)) {
            holder.imgVoted.visibility = View.VISIBLE
        } else {
            holder.imgVoted.visibility = View.GONE
        }

        // Highlight card green if selected
        val isSelected = position == selectedPosition
        holder.itemView.isSelected = isSelected

        // Handle item selection clicks
        holder.itemView.setOnClickListener {
            if (!isEnabled) return@setOnClickListener
            val previousSelected = selectedPosition
            if (selectedPosition == position) {
                // Deselect if clicked again
                selectedPosition = -1
                notifyItemChanged(position)
                onPlayerSelected(null)
            } else {
                // Select new item
                selectedPosition = position
                notifyItemChanged(previousSelected)
                notifyItemChanged(selectedPosition)
                onPlayerSelected(userId)
            }
        }
    }

    fun getSelectedPlayerId(): String? {
        if (selectedPosition != -1 && selectedPosition < userIds.size) {
            return userIds[selectedPosition]
        }
        return null
    }

    fun clearSelection() {
        if (selectedPosition != -1) {
            val prev = selectedPosition
            selectedPosition = -1
            notifyItemChanged(prev)
        }
    }
}
