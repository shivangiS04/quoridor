package com.blockade.quoridor.ui.auth

import androidx.lifecycle.ViewModel
import com.blockade.quoridor.data.repository.CommunityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class PresenceViewModel @Inject constructor(private val community: CommunityRepository) : ViewModel() {
    suspend fun beat() = community.heartbeat()
}
