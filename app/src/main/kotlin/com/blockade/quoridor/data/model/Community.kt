package com.blockade.quoridor.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Numbers shown on the home screen. */
@Serializable
data class CommunityStats(
    @SerialName("online_count") val onlineCount: Int = 0,
    @SerialName("total_users") val totalUsers: Int = 0,
    @SerialName("games_played") val gamesPlayed: Int = 0,
)

@Serializable
data class LeaderboardEntry(
    val id: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("online_wins") val onlineWins: Int = 0,
    @SerialName("games_played") val gamesPlayed: Int = 0,
)
