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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import io.github.jan.supabase.exceptions.RestException
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.blockade.quoridor.data.remote.dto.ClaimTimeoutRequest
import com.blockade.quoridor.data.remote.dto.MatchActionResponse
import com.blockade.quoridor.data.remote.dto.MatchPayload
import com.blockade.quoridor.data.remote.dto.SubmitResignRequest
import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Singleton

/** A failed server call with a message that is safe to show the player. */
class GameException(message: String, val status: Int? = null) : Exception(message) {
    /** The board changed under us (opponent moved first); the caller should refetch. */
    val isStale: Boolean get() = status == 409 && message?.contains("Stale", ignoreCase = true) == true
}

@Singleton
class GameRepository @Inject constructor(
    private val supabase: SupabaseService,
    private val prefs: UserPreferences,
) {
    private val client get() = supabase.client
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun ensureSignedIn() {
        val session = client.auth.currentSessionOrNull()
        if (session == null) {
            try {
                client.auth.signInAnonymously()
            } catch (e: RestException) {
                // The server answered (e.g. anonymous sign-ins are disabled): its message is the useful one.
                throw GameException(e.description ?: e.error, e.statusCode)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw GameException("Can't reach the server. Check your connection.")
            }
        }
    }

    /** Runs an edge-function call and turns every failure into a [GameException] with a readable message. */
    private suspend fun call(block: suspend () -> HttpResponse): String {
        val response = try {
            block()
        } catch (e: RestException) {
            throw GameException(e.description ?: e.error, e.statusCode)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw GameException("Can't reach the server. Check your connection.")
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw GameException(parseError(text) ?: "Request failed (${response.status.value})", response.status.value)
        }
        return text
    }

    private fun parseError(body: String): String? = runCatching {
        json.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.content
    }.getOrNull()

    suspend fun createRoom(): CreateRoomResponse {
        ensureSignedIn()
        return json.decodeFromString(call { client.functions.invoke("create-room") { method = HttpMethod.Post } })
    }

    suspend fun joinRoom(code: String): JoinRoomResponse {
        ensureSignedIn()
        return json.decodeFromString(call { client.functions.invoke("join-room", JoinRoomRequest(code)) })
    }

    suspend fun getRoom(roomId: String): GetRoomResponse {
        ensureSignedIn()
        return json.decodeFromString(
            call { client.functions.invoke("get-room?room_id=$roomId") { method = HttpMethod.Get } },
        )
    }

    suspend fun submitMove(roomId: String, revision: Int, row: Int, col: Int): MatchSnapshot {
        return actionSnapshot(call {
            client.functions.invoke(
                "submit-action",
                SubmitMoveRequest(roomId, revision, MoveAction(row = row, col = col)),
            )
        })
    }

    suspend fun submitWall(roomId: String, revision: Int, row: Int, col: Int, horizontal: Boolean): MatchSnapshot {
        return actionSnapshot(call {
            client.functions.invoke(
                "submit-action",
                SubmitWallRequest(roomId, revision, WallAction(row = row, col = col, horizontal = horizontal)),
            )
        })
    }

    suspend fun resign(roomId: String, revision: Int): MatchSnapshot {
        return actionSnapshot(call { client.functions.invoke("submit-action", SubmitResignRequest(roomId, revision)) })
    }

    suspend fun claimTimeout(roomId: String): MatchSnapshot {
        return actionSnapshot(call { client.functions.invoke("claim-timeout", ClaimTimeoutRequest(roomId)) })
    }

    private fun actionSnapshot(body: String): MatchSnapshot =
        snapshotOf(json.decodeFromString<MatchActionResponse>(body).match)

    /** Emits every match row change. The match row is INSERTed when the second player joins, then UPDATEd per move. */
    fun observeMatch(roomId: String): Flow<MatchSnapshot> = flow {
        val channel = client.realtime.channel("match-$roomId")
        val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "matches"
            filter("room_id", FilterOperator.EQ, roomId)
        }
        client.realtime.connect()
        channel.subscribe(blockUntilSubscribed = true)
        try {
            emitAll(
                changes.mapNotNull { change ->
                    val record = when (change) {
                        is PostgresAction.Insert -> change.record
                        is PostgresAction.Update -> change.record
                        else -> null
                    } ?: return@mapNotNull null
                    MatchSnapshot(
                        state = json.decodeFromJsonElement(record.getValue("state")),
                        revision = json.decodeFromJsonElement(record.getValue("revision")),
                        updatedAtMs = parseTimestamp(record["updated_at"]?.jsonPrimitive?.contentOrNull),
                    )
                },
            )
        } finally {
            withContext(NonCancellable) { channel.unsubscribe() }
        }
    }

    fun snapshotOf(payload: MatchPayload) = MatchSnapshot(
        state = json.decodeFromJsonElement(payload.state),
        revision = payload.revision,
        updatedAtMs = parseTimestamp(payload.updatedAt),
    )

    private fun parseTimestamp(value: String?): Long? =
        value?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }

    val activeRoomId = prefs.activeRoomId
    val soundEnabled = prefs.soundEnabled
    suspend fun setSoundEnabled(enabled: Boolean) = prefs.setSoundEnabled(enabled)
    suspend fun saveRoom(roomId: String) = prefs.saveRoom(roomId)
    suspend fun clearRoom() = prefs.clearRoom()
}
