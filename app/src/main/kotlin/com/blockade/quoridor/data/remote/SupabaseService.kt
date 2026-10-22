package com.blockade.quoridor.data.remote

import com.blockade.quoridor.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseService @Inject constructor() {
    val client: SupabaseClient = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
    ) {
        install(Auth) {
            // Where Google sign-in returns to: quoridor://login-callback (see AndroidManifest and the README).
            scheme = "quoridor"
            host = "login-callback"
        }
        install(Postgrest)
        install(Realtime)
        install(Functions)
    }
}
