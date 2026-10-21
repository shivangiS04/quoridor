package com.blockade.quoridor.data.repository

import com.blockade.quoridor.data.local.UserPreferences
import com.blockade.quoridor.data.remote.SupabaseService
import com.blockade.quoridor.data.remote.dto.CreateRoomResponse
import com.blockade.quoridor.data.remote.dto.GetRoomResponse
import com.blockade.quoridor.data.remote.dto.JoinRoomRequest
import com.blockade.quoridor.data.remote.dto.JoinRoomResponse
import com.blockade.quoridor.data.remote.dto.MoveAction
import com.blockade.quoridor.data.remote.dto.SubmitMoveRequest
import com.blockade.quoridor.data.remote.dto.SubmitWallRequest
import com.blockade.quoridor.data.remote.dto.WallAction
import com.blockade.quoridor.domain.model.GameState
import com.blockade.quoridor.domain.model.MatchSnapshot
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GameRepository @Inject constructor(
    private val supabase: SupabaseService,
    private val prefs: UserPreferences,
) {
    private val client get() = supabase.client
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun ensureSignedIn() {
        val session = client.auth.currentSessionOrNull()
        if (session == null) client.auth.signInAnonymously()
    }

    suspend fun createRoom(): CreateRoomResponse {
        ensureSignedIn()
        return client.functions.invoke("create-room") { method = HttpMethod.Post }
            .let { json.decodeFromString(it.bodyAsText()) }
    }

    suspend fun joinRoom(code: String): JoinRoomResponse {
        ensureSignedIn()
        return client.functions.invoke("join-room", JoinRoomRequest(code))
            .let { json.decodeFromString(it.bodyAsText()) }
    }

    suspend fun getRoom(roomId: String): GetRoomResponse {
        ensureSignedIn()
        return client.functions.invoke("get-room?room_id=$roomId") { method = HttpMethod.Get }
            .let { json.decodeFromString(it.bodyAsText()) }
    }

    suspend fun submitMove(roomId: String, revision: Int, row: Int, col: Int) {
        client.functions.invoke(
            "submit-action",
            SubmitMoveRequest(roomId, revision, MoveAction(row = row, col = col)),
        )
    }

    suspend fun submitWall(roomId: String, revision: Int, row: Int, col: Int, horizontal: Boolean) {
        client.functions.invoke(
            "submit-action",
            SubmitWallRequest(roomId, revision, WallAction(row = row, col = col, horizontal = horizontal)),
        )
    }

    fun observeMatch(roomId: String): Flow<MatchSnapshot> = flow {
        val channel = client.realtime.channel("match-$roomId")
        val changes = channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
            table = "matches"
            filter("room_id", FilterOperator.EQ, roomId)
        }
        client.realtime.connect()
        channel.subscribe(blockUntilSubscribed = true)
        try {
            emitAll(
                changes.map { change ->
                    MatchSnapshot(
                        state = json.decodeFromJsonElement(change.record.getValue("state")),
                        revision = json.decodeFromJsonElement(change.record.getValue("revision")),
                    )
                },
            )
        } finally {
            withContext(NonCancellable) { channel.unsubscribe() }
        }
    }

    val activeRoomId = prefs.activeRoomId
    suspend fun saveRoom(roomId: String) = prefs.saveRoom(roomId)
    suspend fun clearRoom() = prefs.clearRoom()
}
