package com.petal.browser.passwords

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

/**
 * PetalCredential
 * Model representing a single saved credential in Petal Browser's local vault.
 */
@Keep
data class PetalCredential(
    @SerializedName("id")
    val id: String,

    @SerializedName("domain")
    val domain: String,

    @SerializedName("originUrl")
    val originUrl: String,

    @SerializedName("username")
    val username: String,

    @SerializedName("password")
    val password: String,

    @SerializedName("createdAt")
    val createdAt: Long = System.currentTimeMillis(),

    @SerializedName("updatedAt")
    val updatedAt: Long = System.currentTimeMillis(),

    @SerializedName("notes")
    val notes: String = "",

    @SerializedName("isFavorite")
    val isFavorite: Boolean = false
)
