package com.blockade.quoridor.ui.game.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blockade.quoridor.domain.rules.MoveRecord
import com.blockade.quoridor.ui.theme.Dimens
import com.blockade.quoridor.ui.theme.GameTheme

/** Bottom sheet with every move so far, e.g. "1. You  e1 → e2", "2. CPU  Wall h5 (horizontal)". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveHistorySheet(
    history: List<MoveRecord>,
    myIndex: Int,
    opponentName: String,
    onDismiss: () -> Unit,
) {
    val c = GameTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surface,
        contentColor = c.onSurface,
        shape = RoundedCornerShape(topStart = Dimens.sheetRadius, topEnd = Dimens.sheetRadius),
    ) {
        Text(
            "Move history",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        if (history.isEmpty()) {
            Text(
                "No moves yet — make the first one!",
                style = MaterialTheme.typography.bodyMedium,
                color = c.onSurfaceMuted,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp).navigationBarsPadding(),
            )
            return@ModalBottomSheet
        }
        val listState = rememberLazyListState()
        LaunchedEffect(history.size) { listState.scrollToItem(history.lastIndex) }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).navigationBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp, vertical = 8.dp),
        ) {
            itemsIndexed(history) { i, m ->
                val mine = m.player == myIndex
                val tint = if (mine) c.player else c.opponent
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(28.dp).background(tint.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
                        Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = tint)
                    }
                    Spacer(Modifier.width(12.dp))
                    if (!m.gameEnd) {
                        Text(
                            if (mine) "You" else opponentName,
                            style = MaterialTheme.typography.titleMedium,
                            color = tint,
                            maxLines = 1,
                            modifier = Modifier.widthIn(min = 96.dp).padding(end = 8.dp),
                        )
                    }
                    Text(
                        m.text,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (i == history.lastIndex) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}
