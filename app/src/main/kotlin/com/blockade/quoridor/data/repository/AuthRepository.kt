package com.blockade.quoridor.data.repository

import com.blockade.quoridor.data.remote.SupabaseService
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.gotrue.SessionStatus
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.Google
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Sign-in, linking and sign-out. All failures are turned into a [GameException] with a readable message. */
@Singleton
class AuthRepository @Inject constructor(private val supabase: SupabaseService) {
    private val auth get() = supabase.client.auth

    val sessionStatus: StateFlow<SessionStatus> get() = auth.sessionStatus

    val userId: String? get() = auth.currentUserOrNull()?.id

    /** Opens the browser for Google; the session arrives through the quoridor://login-callback deep link. */
    suspend fun signInWithGoogle() = guard("Google sign-in failed") { auth.signInWith(Google) }

    suspend fun continueAsGuest() = guard("Could not start a guest session") { auth.signInAnonymously() }

    /** Turns the current guest into a Google account without losing its stats. */
    suspend fun linkGoogle() = guard("Could not link your Google account") { auth.linkIdentity(Google) }

    suspend fun signOut() = guard("Could not sign out") { auth.signOut() }

    private suspend fun guard(fallback: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: RestException) {
            throw GameException(e.description ?: e.error, e.statusCode)
        } catch (e: Exception) {
            throw GameException("$fallback. Check your connection and try again.")
        }
    }
}
