package com.blockade.quoridor.ui

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blockade.quoridor.ui.auth.AuthGate
import com.blockade.quoridor.ui.auth.SessionViewModel
import com.blockade.quoridor.ui.auth.WelcomeScreen
import com.blockade.quoridor.ui.components.GameBackground
import com.blockade.quoridor.ui.theme.GameTheme

/** Signed out → welcome screen; signed in → the game. Signing out anywhere returns here automatically. */
@Composable
fun AppRoot(session: SessionViewModel = hiltViewModel()) {
    val gate by session.gate.collectAsStateWithLifecycle()
    when (gate) {
        AuthGate.LOADING -> GameBackground {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = GameTheme.colors.onSurface)
            }
        }
        AuthGate.SIGNED_OUT -> WelcomeScreen()
        AuthGate.SIGNED_IN -> NavGraph()
    }
}
