package com.blockade.quoridor.data.repository

import com.blockade.quoridor.data.model.Profile
import com.blockade.quoridor.data.remote.SupabaseService
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepository @Inject constructor(private val supabase: SupabaseService) {
    private val client get() = supabase.client

    /** The signed-in player's profile. It is created by a database trigger at sign-up. */
    suspend fun myProfile(): Profile = guard("Could not load your profile") {
        val uid = client.auth.currentUserOrNull()?.id ?: throw GameException("You are not signed in.")
        client.postgrest["profiles"].select { filter { eq("id", uid) } }.decodeSingle<Profile>()
    }

    suspend fun rename(name: String) {
        val clean = name.trim()
        if (clean.isEmpty() || clean.length > 30) throw GameException("Pick a name between 1 and 30 characters.")
        guard("Could not save your name") {
            val uid = client.auth.currentUserOrNull()?.id ?: throw GameException("You are not signed in.")
            client.postgrest["profiles"].update({ set("display_name", clean) }) { filter { eq("id", uid) } }
        }
    }

    /** Best effort: a game against the computer was finished. Never fails the caller (offline is fine). */
    suspend fun recordAiGame(won: Boolean) {
        try {
            client.postgrest.rpc("record_ai_game", buildJsonObject { put("won", won) })
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    private suspend fun <T> guard(fallback: String, block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: GameException) {
        throw e
    } catch (e: RestException) {
        throw GameException(e.description ?: e.error, e.statusCode)
    } catch (e: Exception) {
        throw GameException("$fallback. Check your connection and try again.")
    }
}
