package com.blockade.quoridor.data.repository

import com.blockade.quoridor.data.model.CommunityStats
import com.blockade.quoridor.data.model.LeaderboardEntry
import com.blockade.quoridor.data.remote.SupabaseService
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/** Player counts, presence and the leaderboard. All of it is read through database functions. */
@Singleton
class CommunityRepository @Inject constructor(private val supabase: SupabaseService) {
    private val postgrest get() = supabase.client.postgrest

    suspend fun stats(): CommunityStats = guard { postgrest.rpc("get_community_stats").decodeAs() }

    suspend fun leaderboard(limit: Int = 10): List<LeaderboardEntry> =
        guard { postgrest.rpc("get_leaderboard", buildJsonObject { put("limit_n", limit) }).decodeAs() }

    /** "I'm here": best effort, never throws (being offline just means we are not counted as online). */
    suspend fun heartbeat() {
        try {
            postgrest.rpc("touch_presence")
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    private suspend fun <T> guard(block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: RestException) {
        throw GameException(e.description ?: e.error, e.statusCode)
    } catch (e: Exception) {
        throw GameException("Can't reach the server. Check your connection.")
    }
}
