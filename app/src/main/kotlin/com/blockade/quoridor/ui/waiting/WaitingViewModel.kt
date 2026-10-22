package com.blockade.quoridor.ui.waiting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blockade.quoridor.data.repository.GameRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WaitingUiState(
    val matchStarted: Boolean = false,
    val myPlayerIndex: Int = 0,
)

@HiltViewModel
class WaitingViewModel @Inject constructor(
    private val repo: GameRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(WaitingUiState())
    val state = _state.asStateFlow()

    fun startObserving(roomId: String, myPlayerIndex: Int) {
        _state.update { it.copy(myPlayerIndex = myPlayerIndex) }
        // The opponent may already have joined before we subscribed.
        viewModelScope.launch {
            runCatching { repo.getRoom(roomId) }.onSuccess { r ->
                if (r.match != null) _state.update { it.copy(matchStarted = true) }
            }
        }
        repo.observeMatch(roomId)
            .onEach { snapshot ->
                if (snapshot.state.status == "playing" || snapshot.state.status == "finished") {
                    _state.update { it.copy(matchStarted = true) }
                }
            }
            .retryWhen { _, _ -> delay(2000); true }
            .launchIn(viewModelScope)
    }
}
