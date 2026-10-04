package com.example.uploadingscreen.model

data class Player(
    val _id: String,
    val userId: String,
    val roomId: String,
    // not included in /room/:code/lookup responses
    val username:String?,
    val socketId: String?,
    val role: String,
    val createdAt: String,
    val updatedAt: String,
    val __v: Int
)
