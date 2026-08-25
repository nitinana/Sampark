package com.sampark.data.status

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AppStatusRepository(private val dataStore: DataStore<Preferences>) {

    private object Keys {
        val DIRECTION = stringPreferencesKey("direction")
        val PHASE = stringPreferencesKey("phase")
        val PERMISSION_REQUESTED_BEFORE = booleanPreferencesKey("permission_requested_before")
    }

    val direction: Flow<Direction> = dataStore.data.map { prefs ->
        prefs[Keys.DIRECTION]?.let { Direction.valueOf(it) } ?: Direction.NONE
    }

    val phase: Flow<Phase> = dataStore.data.map { prefs ->
        prefs[Keys.PHASE]?.let { Phase.valueOf(it) } ?: Phase.COMPLETED
    }

    val permissionRequestedBefore: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.PERMISSION_REQUESTED_BEFORE] ?: false
    }

    suspend fun setDirection(direction: Direction) {
        dataStore.edit { it[Keys.DIRECTION] = direction.name }
    }

    suspend fun setPhase(phase: Phase) {
        dataStore.edit { it[Keys.PHASE] = phase.name }
    }

    suspend fun setPermissionRequestedBefore(value: Boolean) {
        dataStore.edit { it[Keys.PERMISSION_REQUESTED_BEFORE] = value }
    }
}
