package com.blockade.quoridor.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateRoomResponse(
    @SerialName("room_id") val roomId: String,
    val code: String,
)

@Serializable
data class JoinRoomRequest(val code: String)

@Serializable
data class JoinRoomResponse(
    @SerialName("room_id") val roomId: String,
    @SerialName("player_index") val playerIndex: Int,
    val status: String,
)

@Serializable
data class GetRoomResponse(
    @SerialName("room_id") val roomId: String,
    val code: String,
    @SerialName("player_index") val playerIndex: Int,
    val match: MatchPayload?,
)

@Serializable
data class MatchPayload(
    val state: kotlinx.serialization.json.JsonObject,
    val revision: Int,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class SubmitMoveRequest(
    @SerialName("room_id") val roomId: String,
    val revision: Int,
    val action: MoveAction,
)

@Serializable
data class SubmitWallRequest(
    @SerialName("room_id") val roomId: String,
    val revision: Int,
    val action: WallAction,
)

@Serializable
data class MoveAction(
    val type: String = "move",
    val row: Int,
    val col: Int,
)

@Serializable
data class WallAction(
    val type: String = "wall",
    val row: Int,
    val col: Int,
    val horizontal: Boolean,
)

@Serializable
data class ResignAction(val type: String = "resign")

@Serializable
data class SubmitResignRequest(
    @SerialName("room_id") val roomId: String,
    val revision: Int,
    val action: ResignAction = ResignAction(),
)

@Serializable
data class ClaimTimeoutRequest(
    @SerialName("room_id") val roomId: String,
)

@Serializable
data class MatchActionResponse(val match: MatchPayload)
