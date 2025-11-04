package com.vigatec.injector.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Gestor de preferencias para el timeout de custodios en ceremonias de llaves.
 * Permite al administrador configurar el tiempo máximo de espera para custodios.
 */
class CustodianTimeoutPreferencesManager(private val context: Context) {

    companion object {
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("custodian_timeout_preferences")
        private val CUSTODIAN_TIMEOUT_MINUTES_KEY = intPreferencesKey("custodian_timeout_minutes")

        // Valor por defecto: 10 minutos
        private const val DEFAULT_TIMEOUT_MINUTES = 10

        // Límites
        const val MIN_TIMEOUT_MINUTES = 1
        const val MAX_TIMEOUT_MINUTES = 60
    }

    /**
     * Guarda el tiempo de timeout para custodios (en minutos)
     */
    suspend fun saveCustodianTimeoutMinutes(minutes: Int) {
        val validatedMinutes = when {
            minutes < MIN_TIMEOUT_MINUTES -> MIN_TIMEOUT_MINUTES
            minutes > MAX_TIMEOUT_MINUTES -> MAX_TIMEOUT_MINUTES
            else -> minutes
        }

        context.dataStore.edit { preferences ->
            preferences[CUSTODIAN_TIMEOUT_MINUTES_KEY] = validatedMinutes
        }
    }

    /**
     * Obtiene el tiempo de timeout para custodios (en minutos)
     */
    fun getCustodianTimeoutMinutes(): Flow<Int> {
        return context.dataStore.data.map { preferences ->
            preferences[CUSTODIAN_TIMEOUT_MINUTES_KEY] ?: DEFAULT_TIMEOUT_MINUTES
        }
    }

    /**
     * Obtiene el tiempo de timeout en segundos
     */
    fun getCustodianTimeoutSeconds(): Flow<Int> {
        return context.dataStore.data.map { preferences ->
            val minutes = preferences[CUSTODIAN_TIMEOUT_MINUTES_KEY] ?: DEFAULT_TIMEOUT_MINUTES
            minutes * 60
        }
    }

    /**
     * Reinicia el timeout a su valor por defecto
     */
    suspend fun resetToDefault() {
        context.dataStore.edit { preferences ->
            preferences[CUSTODIAN_TIMEOUT_MINUTES_KEY] = DEFAULT_TIMEOUT_MINUTES
        }
    }

    /**
     * Limpia las preferencias de timeout
     */
    suspend fun clearTimeoutPreferences() {
        context.dataStore.edit { preferences ->
            preferences.remove(CUSTODIAN_TIMEOUT_MINUTES_KEY)
        }
    }
}
