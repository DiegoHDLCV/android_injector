package com.vigatec.keyreceiver.data.storage // O la ruta correcta a tu archivo

import android.content.Context
import android.content.SharedPreferences // Importante para el tipo de retorno de create() y para edit()
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey // Cambio aquí: MasterKey en singular
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS_FILE_KEY_STATUS = "secure_key_status_prefs"
private const val MASTER_KEY_A_LOADED_KEY = "master_key_a_loaded"
private const val MASTER_KEY_A_KCV_KEY = "master_key_a_kcv" // Si decides usar KCV

@Singleton
class SecureStorageManager @Inject constructor(@ApplicationContext private val context: Context) {

    // Construye la MasterKey para EncryptedSharedPreferences
    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM) // Esquema de clave recomendado
            .build()
    }

    private val sharedPreferences: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            context, // El contexto de la aplicación
            PREFS_FILE_KEY_STATUS, // Nombre del archivo de preferencias
            masterKey, // La MasterKey que creaste arriba
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, // Esquema de cifrado para las claves de las prefs
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM  // Esquema de cifrado para los valores de las prefs
        )
    }

    fun setMasterKeyALoaded(isLoaded: Boolean) {
        try {
            with(sharedPreferences.edit()) { // edit() devuelve un SharedPreferences.Editor
                putBoolean(MASTER_KEY_A_LOADED_KEY, isLoaded)
                apply() // O commit()
            }
            Log.i("SecureStorage", "Master Key A loaded status set to: $isLoaded")
        } catch (e: Exception) {
            Log.e("SecureStorage", "Error setting Master Key A loaded status", e)
        }
    }

    fun isMasterKeyALoaded(): Boolean {
        return try {
            sharedPreferences.getBoolean(MASTER_KEY_A_LOADED_KEY, false)
        } catch (e: Exception) {
            Log.e("SecureStorage", "Error reading Master Key A loaded status", e)
            false // Devuelve false en caso de error para estar del lado seguro
        }
    }

    fun setMasterKeyAKcv(kcvHex: String?) {
        try {
            with(sharedPreferences.edit()) {
                if (kcvHex == null) {
                    remove(MASTER_KEY_A_KCV_KEY)
                } else {
                    putString(MASTER_KEY_A_KCV_KEY, kcvHex)
                }
                apply()
            }
            Log.i("SecureStorage", "Master Key A KCV stored/cleared.")
        } catch (e: Exception) {
            Log.e("SecureStorage", "Error storing/clearing Master Key A KCV", e)
        }
    }

    fun getMasterKeyAKcv(): String? {
        return try {
            sharedPreferences.getString(MASTER_KEY_A_KCV_KEY, null)
        } catch (e: Exception) {
            Log.e("SecureStorage", "Error reading Master Key A KCV", e)
            null
        }
    }
}