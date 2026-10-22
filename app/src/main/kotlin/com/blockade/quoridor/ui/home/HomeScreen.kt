package com.blockade.quoridor.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.blockade.quoridor.ui.components.GameBackground
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blockade.quoridor.domain.ai.AiDifficulty
import com.blockade.quoridor.ui.theme.GameTheme
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun HomeScreen(
    onRoomCreated: (roomId: String, code: String) -> Unit,
    onRoomJoined: (roomId: String, playerIndex: Int) -> Unit,
    onResumeWaiting: (roomId: String, code: String) -> Unit,
    onPlayComputer: (AiDifficulty) -> Unit,
    vm: HomeViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    GameBackground {
    Scaffold(containerColor = Color.Transparent) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Quoridor", fontSize = 40.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(48.dp))

            state.resume?.let { r ->
                Button(
                    onClick = {
                        if (r.started) onRoomJoined(r.roomId, r.playerIndex) else onResumeWaiting(r.roomId, r.code)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Resume game") }
                TextButton(onClick = vm::dismissResume) { Text("Forget this game") }
                Spacer(Modifier.height(16.dp))
            }

            Text("Play the computer", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AiDifficulty.entries.forEach { d ->
                    FilledTonalButton(
                        onClick = { onPlayComputer(d) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = GameTheme.colors.surfaceHigh,
                            contentColor = GameTheme.colors.onSurface,
                        ),
                    ) {
                        Text(d.label)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(24.dp))

            Button(
                onClick = { vm.hostGame(onRoomCreated) },
                enabled = !state.loading,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Host Game") }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = state.joinCode,
                onValueChange = vm::setJoinCode,
                label = { Text("Room code") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { vm.joinGame(onRoomJoined) },
                enabled = !state.loading,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Join Game") }

            if (state.loading) {
                Spacer(Modifier.height(24.dp))
                CircularProgressIndicator()
            }
            state.error?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    }
}
