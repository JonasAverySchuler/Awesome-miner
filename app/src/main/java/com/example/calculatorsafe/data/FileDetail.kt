package com.example.calculatorsafe.data

import com.google.gson.annotations.SerializedName

data class FileDetail(
    @SerializedName("original_name")
    val originalFileName: String,
    @SerializedName("encrypted_name")
    val encryptedFileName: String,
    @SerializedName("type")
    val mimeType: String,
    @SerializedName("created_at")
    val createdAt: String,
)
