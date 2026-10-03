package com.example.uploadingscreen.model

import com.google.gson.annotations.SerializedName

data class User(
    // backend sends Mongo's "_id"
    @SerializedName(value = "_id", alternate = ["id"])
    val id : String?,
    val username : String,
    val email : String? = null,
    )
