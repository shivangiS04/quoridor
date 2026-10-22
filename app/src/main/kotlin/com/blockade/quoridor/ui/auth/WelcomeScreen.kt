package com.blockade.quoridor.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blockade.quoridor.ui.components.GameBackground
import com.blockade.quoridor.ui.theme.Dimens
import com.blockade.quoridor.ui.theme.GameTheme

/** First screen for signed-out players: Google, or a guest account that can be upgraded later. */
@Composable
fun WelcomeScreen(vm: WelcomeViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val c = GameTheme.colors
    GameBackground {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Quoridor", style = MaterialTheme.typography.displayMedium, color = c.onSurface)
            Spacer(Modifier.height(8.dp))
            Text(
                "Race to the other side. Block your opponent with walls.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.onSurfaceMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(48.dp))

            Button(
                onClick = vm::google,
                enabled = !state.loading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(Dimens.cardRadius),
                colors = ButtonDefaults.buttonColors(containerColor = c.onSurface, contentColor = c.background),
            ) { Text("Continue with Google", style = MaterialTheme.typography.titleMedium) }

            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = vm::guest,
                enabled = !state.loading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(Dimens.cardRadius),
                border = BorderStroke(1.dp, c.outline),
            ) { Text("Play as guest", style = MaterialTheme.typography.titleMedium, color = c.onSurface) }

            Spacer(Modifier.height(20.dp))
            if (state.loading) CircularProgressIndicator(color = c.onSurface)
            state.error?.let {
                Text(it, color = c.invalid, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "Guests can link a Google account later and keep their stats.",
                style = MaterialTheme.typography.labelSmall,
                color = c.onSurfaceMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}
