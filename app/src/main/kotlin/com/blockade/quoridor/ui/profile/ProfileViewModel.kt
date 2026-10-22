package com.blockade.quoridor.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blockade.quoridor.data.model.Profile
import com.blockade.quoridor.data.repository.AuthRepository
import com.blockade.quoridor.data.repository.GameException
import com.blockade.quoridor.data.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val profile: Profile? = null,
    val nameDraft: String = "",
    val loading: Boolean = true,
    val saving: Boolean = false,
    val message: String? = null,
    val error: String? = null,
) {
    val nameChanged: Boolean get() = profile != null && nameDraft.trim() != profile.displayName
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profiles: ProfileRepository,
    private val auth: AuthRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ProfileUiState())
    val state = _state.asStateFlow()

    /** Also called when the screen resumes, so a guest who just linked Google sees the upgraded profile. */
    fun load() = viewModelScope.launch {
        try {
            val p = profiles.myProfile()
            _state.update {
                // Don't overwrite a name the player is in the middle of typing.
                it.copy(profile = p, nameDraft = if (it.nameChanged) it.nameDraft else p.displayName, loading = false, error = null)
            }
        } catch (e: GameException) {
            _state.update { it.copy(loading = false, error = e.message) }
        }
    }

    fun setName(name: String) = _state.update { it.copy(nameDraft = name.take(30), message = null) }

    fun save() = viewModelScope.launch {
        _state.update { it.copy(saving = true, error = null, message = null) }
        try {
            profiles.rename(_state.value.nameDraft)
            _state.update { it.copy(saving = false, message = "Name saved") }
            load()
        } catch (e: GameException) {
            _state.update { it.copy(saving = false, error = e.message) }
        }
    }

    fun linkGoogle() = viewModelScope.launch {
        try {
            auth.linkGoogle()
        } catch (e: GameException) {
            _state.update { it.copy(error = e.message) }
        }
    }

    fun signOut() = viewModelScope.launch {
        try {
            auth.signOut()
        } catch (e: GameException) {
            _state.update { it.copy(error = e.message) }
        }
    }
}
