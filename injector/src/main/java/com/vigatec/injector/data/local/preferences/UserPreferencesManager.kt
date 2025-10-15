package com.vigatec.injector.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Gestor de preferencias del usuario usando DataStore.
 * Permite persistir configuraciones como el último usuario ingresado.
 */
class UserPreferencesManager(private val context: Context) {

    companion object {
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("user_preferences")
        private val LAST_USERNAME_KEY = stringPreferencesKey("last_username")
        private val REMEMBER_USER_KEY = booleanPreferencesKey("remember_user")
    }

    /**
     * Guarda el último nombre de usuario ingresado
     */
    suspend fun saveLastUsername(username: String) {
        context.dataStore.edit { preferences ->
            preferences[LAST_USERNAME_KEY] = username
        }
    }

    /**
     * Obtiene el último nombre de usuario ingresado
     */
    fun getLastUsername(): Flow<String?> {
        return context.dataStore.data.map { preferences ->
            preferences[LAST_USERNAME_KEY]
        }
    }

    /**
     * Guarda la preferencia de recordar usuario
     */
    suspend fun saveRememberUser(remember: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[REMEMBER_USER_KEY] = remember
        }
    }

    /**
     * Obtiene la preferencia de recordar usuario
     */
    fun getRememberUser(): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[REMEMBER_USER_KEY] ?: false
        }
    }

    /**
     * Limpia las preferencias de usuario
     */
    suspend fun clearUserPreferences() {
        context.dataStore.edit { preferences ->
            preferences.remove(LAST_USERNAME_KEY)
            preferences.remove(REMEMBER_USER_KEY)
        }
    }
}
