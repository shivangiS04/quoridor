package com.blockade.quoridor.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("user_prefs")

@Singleton
class UserPreferences @Inject constructor(@ApplicationContext private val ctx: Context) {

    private val ROOM_ID = stringPreferencesKey("room_id")

    val activeRoomId: Flow<String?> = ctx.dataStore.data.map { it[ROOM_ID] }

    suspend fun saveRoom(roomId: String) {
        ctx.dataStore.edit { it[ROOM_ID] = roomId }
    }

    suspend fun clearRoom() {
        ctx.dataStore.edit { it.remove(ROOM_ID) }
    }
}
