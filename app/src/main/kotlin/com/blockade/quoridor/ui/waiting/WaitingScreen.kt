package com.blockade.quoridor.ui.waiting

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.blockade.quoridor.ui.components.GameBackground
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun WaitingScreen(
    roomId: String,
    code: String,
    myPlayerIndex: Int = 0,
    onMatchStarted: (playerIndex: Int) -> Unit,
    vm: WaitingViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(roomId) { vm.startObserving(roomId, myPlayerIndex) }

    LaunchedEffect(state.matchStarted) {
        if (state.matchStarted) onMatchStarted(state.myPlayerIndex)
    }

    GameBackground {
    Scaffold(containerColor = Color.Transparent) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Waiting for opponent…", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(32.dp))
            Text("Share this code:", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    code,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 6.sp,
                )
                Spacer(Modifier.width(12.dp))
                IconButton(onClick = { clipboard.setText(AnnotatedString(code)) }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy code")
                }
            }
            Spacer(Modifier.height(40.dp))
            CircularProgressIndicator()
        }
    }
    }
}
