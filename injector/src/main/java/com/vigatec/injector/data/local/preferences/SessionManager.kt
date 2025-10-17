package com.vigatec.injector.data.local.preferences

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * SessionManager - Gestiona la sesión del usuario actual.
 *
 * Este componente rastrea quién está actualmente logueado en el sistema.
 * NO confundir con el campo User.isActive que indica si un usuario está habilitado/deshabilitado.
 *
 * Responsabilidades:
 * - Guardar el usuario que inició sesión (por ID y username)
 * - Recuperar el usuario de la sesión actual
 * - Cerrar sesión (limpiar la sesión)
 */
class SessionManager(private val context: Context) {

    companion object {
        private const val TAG = "SessionManager"
        private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore("user_session")
        private val LOGGED_USER_ID_KEY = intPreferencesKey("logged_user_id")
        private val LOGGED_USERNAME_KEY = stringPreferencesKey("logged_username")
        private val LOGGED_USER_ROLE_KEY = stringPreferencesKey("logged_user_role")
    }

    /**
     * Guarda la sesión del usuario que se acaba de loguear.
     */
    suspend fun saveSession(userId: Int, username: String, role: String) {
        Log.d(TAG, "═══════════════════════════════════════════════════════════")
        Log.d(TAG, "Guardando sesión de usuario")
        Log.d(TAG, "  - User ID: $userId")
        Log.d(TAG, "  - Username: $username")
        Log.d(TAG, "  - Role: $role")

        context.sessionDataStore.edit { preferences ->
            preferences[LOGGED_USER_ID_KEY] = userId
            preferences[LOGGED_USERNAME_KEY] = username
            preferences[LOGGED_USER_ROLE_KEY] = role
        }

        Log.i(TAG, "✓ Sesión guardada exitosamente")
        Log.d(TAG, "═══════════════════════════════════════════════════════════")
    }

    /**
     * Obtiene el ID del usuario actualmente logueado.
     * @return ID del usuario o null si no hay sesión activa
     */
    fun getLoggedUserId(): Flow<Int?> {
        return context.sessionDataStore.data.map { preferences ->
            preferences[LOGGED_USER_ID_KEY]
        }
    }

    /**
     * Obtiene el username del usuario actualmente logueado.
     * @return Username o null si no hay sesión activa
     */
    fun getLoggedUsername(): Flow<String?> {
        return context.sessionDataStore.data.map { preferences ->
            preferences[LOGGED_USERNAME_KEY]
        }
    }

    /**
     * Obtiene el rol del usuario actualmente logueado.
     * @return Rol (ADMIN o USER) o null si no hay sesión activa
     */
    fun getLoggedUserRole(): Flow<String?> {
        return context.sessionDataStore.data.map { preferences ->
            preferences[LOGGED_USER_ROLE_KEY]
        }
    }

    /**
     * Verifica si el usuario actual es administrador.
     */
    suspend fun isCurrentUserAdmin(): Boolean {
        val role = getLoggedUserRole().first()
        val isAdmin = role == "ADMIN"
        Log.d(TAG, "Verificación de admin: role=$role, isAdmin=$isAdmin")
        return isAdmin
    }

    /**
     * Verifica si hay una sesión activa.
     */
    suspend fun hasActiveSession(): Boolean {
        val userId = getLoggedUserId().first()
        val hasSession = userId != null
        Log.d(TAG, "Verificación de sesión: userId=$userId, hasSession=$hasSession")
        return hasSession
    }

    /**
     * Cierra la sesión actual (limpia todos los datos de sesión).
     */
    suspend fun clearSession() {
        Log.d(TAG, "═══════════════════════════════════════════════════════════")
        Log.d(TAG, "Cerrando sesión de usuario")

        context.sessionDataStore.edit { preferences ->
            preferences.remove(LOGGED_USER_ID_KEY)
            preferences.remove(LOGGED_USERNAME_KEY)
            preferences.remove(LOGGED_USER_ROLE_KEY)
        }

        Log.i(TAG, "✓ Sesión cerrada exitosamente")
        Log.d(TAG, "═══════════════════════════════════════════════════════════")
    }

    /**
     * Obtiene todos los datos de la sesión actual.
     * @return Triple(userId, username, role) o null si no hay sesión
     */
    suspend fun getCurrentSession(): Triple<Int, String, String>? {
        val userId = getLoggedUserId().first()
        val username = getLoggedUsername().first()
        val role = getLoggedUserRole().first()

        return if (userId != null && username != null && role != null) {
            Triple(userId, username, role)
        } else {
            null
        }
    }
}
