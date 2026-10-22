package com.blockade.quoridor.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A row of public.profiles. Stats are written only by the server. */
@Serializable
data class Profile(
    val id: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("is_guest") val isGuest: Boolean = true,
    @SerialName("games_played") val gamesPlayed: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    @SerialName("ai_wins") val aiWins: Int = 0,
    @SerialName("online_wins") val onlineWins: Int = 0,
)
