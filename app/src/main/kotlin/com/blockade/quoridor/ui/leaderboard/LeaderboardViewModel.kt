package com.blockade.quoridor.ui.leaderboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blockade.quoridor.data.model.LeaderboardEntry
import com.blockade.quoridor.data.repository.AuthRepository
import com.blockade.quoridor.data.repository.CommunityRepository
import com.blockade.quoridor.data.repository.GameException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LeaderboardUiState(
    val entries: List<LeaderboardEntry> = emptyList(),
    val myId: String? = null,
    val loading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class LeaderboardViewModel @Inject constructor(
    private val community: CommunityRepository,
    auth: AuthRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(LeaderboardUiState(myId = auth.userId))
    val state = _state.asStateFlow()

    fun load() = viewModelScope.launch {
        try {
            val rows = community.leaderboard()
            _state.update { it.copy(entries = rows, loading = false, error = null) }
        } catch (e: GameException) {
            _state.update { it.copy(loading = false, error = e.message) }
        }
    }
}
