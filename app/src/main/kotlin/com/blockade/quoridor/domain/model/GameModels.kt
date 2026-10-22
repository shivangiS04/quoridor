package com.blockade.quoridor.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WallPos(val row: Int, val col: Int)

@Serializable
data class WallsState(
    val horizontal: List<WallPos> = emptyList(),
    val vertical: List<WallPos> = emptyList(),
)

@Serializable
data class PlayerState(
    val row: Int,
    val col: Int,
    @SerialName("walls_left") val wallsLeft: Int,
)

@Serializable
data class GameState(
    val players: List<PlayerState>,
    val walls: WallsState,
    @SerialName("current_turn") val currentTurn: Int,
    val status: String,
    val winner: Int? = null,
)

@Serializable
data class MatchSnapshot(
    val state: GameState,
    val revision: Int,
    /** Server time (epoch ms) of the last accepted action; drives the turn timer. */
    @kotlinx.serialization.Transient val updatedAtMs: Long? = null,
)

data class RoomInfo(
    val roomId: String,
    val code: String,
    val myPlayerIndex: Int,
    val match: MatchSnapshot?,
)
