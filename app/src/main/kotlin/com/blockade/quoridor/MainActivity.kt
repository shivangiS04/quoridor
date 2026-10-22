package com.blockade.quoridor

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import com.blockade.quoridor.data.remote.SupabaseService
import com.blockade.quoridor.ui.AppRoot
import com.blockade.quoridor.ui.theme.QuoridorTheme
import dagger.hilt.android.AndroidEntryPoint
import io.github.jan.supabase.gotrue.handleDeeplinks
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var supabase: SupabaseService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The game is always dark, so the system bars use light icons regardless of the phone's theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        // Google sign-in returns here as quoridor://login-callback#access_token=…
        supabase.client.handleDeeplinks(intent)
        setContent {
            QuoridorTheme {
                AppRoot()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        supabase.client.handleDeeplinks(intent)
    }
}
