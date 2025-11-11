package com.vigatec.injector.manager

import android.util.Log
import com.vigatec.persistence.repository.InjectedKeyRepository
import com.vigatec.utils.KeyStoreManager
import com.vigatec.utils.KcvCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager para gestión de KEK (Key Encryption Key)
 * Esta clase maneja la lógica de negocio de KEK sin ser un ViewModel,
 * permitiendo su inyección en otros componentes.
 */
@Singleton
class KEKManager @Inject constructor(
    private val injectedKeyRepository: InjectedKeyRepository
) {
    private val TAG = "KEKManager"

    /**
     * Verifica si existe una KEK activa en el sistema
     */
    suspend fun hasActiveKEK(): Boolean = withContext(Dispatchers.IO) {
        try {
            val activeKEK = getActiveKEKEntity()
            val hasKEK = activeKEK != null && activeKEK.status == "ACTIVE"
            Log.d(TAG, "¿Hay KEK activa?: $hasKEK")
            hasKEK
        } catch (e: Exception) {
            Log.e(TAG, "Error verificando KEK activa", e)
            false
        }
    }

    /**
     * Obtiene los datos de la KEK activa
     */
    suspend fun getActiveKEKData(): String? = withContext(Dispatchers.IO) {
        try {
            val activeKEK = getActiveKEKEntity()
            val kekData = activeKEK?.keyData
            if (kekData != null) {
                Log.d(TAG, "KEK activa encontrada (primeros 16 bytes): ${kekData.take(32)}")
            } else {
                Log.d(TAG, "No hay KEK activa con datos")
            }
            kekData
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo datos de KEK", e)
            null
        }
    }

    /**
     * Obtiene el KCV de la KEK activa
     */
    suspend fun getActiveKEKKcv(): String? = withContext(Dispatchers.IO) {
        try {
            val activeKEK = getActiveKEKEntity()
            val kcv = activeKEK?.kcv
            Log.d(TAG, "KCV de KEK activa: $kcv")
            kcv
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo KCV de KEK", e)
            null
        }
    }

    /**
     * Obtiene el slot de la KEK activa
     */
    suspend fun getActiveKEKSlot(): Int? = withContext(Dispatchers.IO) {
        try {
            val activeKEK = getActiveKEKEntity()
            val slot = activeKEK?.keySlot
            Log.d(TAG, "Slot de KEK activa: $slot")
            slot
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo slot de KEK", e)
            null
        }
    }

    /**
     * Genera una nueva KEK aleatoria
     */
    suspend fun generateRandomKEK(
        keyLength: Int = 16,
        slot: Int = 0
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "=== GENERANDO NUEVA KEK ===")
            Log.i(TAG, "Longitud solicitada: $keyLength bytes")
            Log.i(TAG, "Slot KEK: $slot")

            // Validar longitud
            Log.i(TAG, "Paso 1: Validando longitud de KEK...")
            if (keyLength !in listOf(16, 24, 32)) {
                throw IllegalArgumentException("Longitud de KEK inválida: $keyLength. Debe ser 16, 24 o 32 bytes.")
            }
            Log.i(TAG, "✓ Longitud válida")

            // Marcar KEK anterior como INACTIVE si existe
            Log.i(TAG, "Paso 2: Verificando si existe KEK anterior...")
            val existingKEK = getActiveKEKEntity()
            if (existingKEK != null) {
                Log.i(TAG, "KEK anterior encontrada (KCV: ${existingKEK.kcv})")
                Log.i(TAG, "Marcando KEK anterior como INACTIVE...")
                injectedKeyRepository.updateKeyStatus(existingKEK.kcv, "INACTIVE")
                Log.i(TAG, "✓ KEK anterior marcada como INACTIVE")
            } else {
                Log.i(TAG, "✓ No hay KEK anterior")
            }

            // Generar KEK usando SecureRandom
            Log.i(TAG, "Paso 3: Generando bytes aleatorios con SecureRandom...")
            val secureRandom = SecureRandom()
            val kekBytes = ByteArray(keyLength)
            secureRandom.nextBytes(kekBytes)
            Log.i(TAG, "✓ Bytes aleatorios generados")

            Log.i(TAG, "Paso 4: Convirtiendo a hexadecimal...")
            val kekHex = kekBytes.joinToString("") { "%02X".format(it) }
            Log.i(TAG, "✓ KEK en hex (primeros 16 bytes): ${kekHex.take(32)}...")

            // Determinar algoritmo
            Log.i(TAG, "Paso 5: Determinando algoritmo...")
            val algorithm = when (keyLength) {
                16 -> "DES_TRIPLE" // 2-key Triple DES
                24 -> "DES_TRIPLE" // 3-key Triple DES
                32 -> "AES_256"    // AES 256
                else -> "DES_TRIPLE"
            }
            Log.i(TAG, "✓ Algoritmo: $algorithm")

            // Calcular KCV
            Log.i(TAG, "Paso 6: Calculando KCV...")
            val kcv = KcvCalculator.calculateKcv(kekHex)
            Log.i(TAG, "✓ KCV calculado: $kcv")

            // Almacenar en Android Keystore
            Log.i(TAG, "Paso 7: Almacenando KEK en Android Keystore...")
            try {
                KeyStoreManager.storeMasterKey("active_kek", kekBytes)
                Log.i(TAG, "✓ KEK almacenada en Keystore exitosamente")
            } catch (e: Exception) {
                Log.e(TAG, "✗ Error al almacenar en Keystore: ${e.message}", e)
                // Continuamos aunque falle el Keystore
            }

            // Guardar en base de datos
            Log.i(TAG, "Paso 8: Guardando KEK en base de datos...")
            Log.i(TAG, "  - keySlot: $slot")
            Log.i(TAG, "  - keyType: KEK")
            Log.i(TAG, "  - keyAlgorithm: $algorithm")
            Log.i(TAG, "  - kcv: $kcv")
            Log.i(TAG, "  - keyData length: ${kekHex.length / 2} bytes")
            Log.i(TAG, "  - status: ACTIVE")

            injectedKeyRepository.recordKeyInjectionWithData(
                keySlot = slot,
                keyType = "KEK",
                keyAlgorithm = algorithm,
                kcv = kcv,
                keyData = kekHex,
                status = "ACTIVE"
            )
            Log.i(TAG, "✓ KEK guardada en base de datos exitosamente")

            Log.i(TAG, "✓ KEK generada exitosamente")
            Log.i(TAG, "  - KCV: $kcv")
            Log.i(TAG, "  - Slot: $slot")
            Log.i(TAG, "  - Algoritmo: $algorithm")
            Log.i(TAG, "================================================")

            Result.success(kcv)

        } catch (e: Exception) {
            Log.e(TAG, "✗ Error al generar KEK", e)
            Result.failure(e)
        }
    }

    /**
     * Marca la KEK como inactiva (ya no se usa)
     */
    suspend fun markKEKAsInactive(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val activeKEK = getActiveKEKEntity()
            if (activeKEK == null) {
                return@withContext Result.failure(Exception("No hay KEK activa para marcar como inactiva"))
            }

            Log.i(TAG, "Marcando KEK como INACTIVE (KCV: ${activeKEK.kcv})")
            injectedKeyRepository.updateKeyStatus(activeKEK.kcv, "INACTIVE")
            Log.i(TAG, "✓ KEK marcada como INACTIVE")

            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Error al marcar KEK como inactiva", e)
            Result.failure(e)
        }
    }

    /**
     * Obtiene la entidad de KEK activa (solo ACTIVE)
     */
    suspend fun getActiveKEKEntity() = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "getActiveKEKEntity: Iniciando búsqueda de KEK activa...")

            // Buscar todas las KEKs en la base de datos usando first() en lugar de collect()
            Log.d(TAG, "getActiveKEKEntity: Obteniendo todas las llaves de la BD...")
            val allKeys = injectedKeyRepository.getAllInjectedKeys().first()
            Log.d(TAG, "getActiveKEKEntity: Se encontraron ${allKeys.size} llaves en total")

            // Filtrar por el flag isKEK (no por keyType, ya que keyType contiene el nombre real como "DUKPT BDK 3DES")
            val kekKeys = allKeys.filter { it.isKEK == true }
            Log.d(TAG, "getActiveKEKEntity: ${kekKeys.size} llaves marcadas como KEK/KTK encontradas (isKEK=true)")
            kekKeys.forEach { kek ->
                Log.d(TAG, "  - KCV: ${kek.kcv}, Tipo: ${kek.keyType}, Estado: ${kek.status}, isKEK: ${kek.isKEK}")
            }

            // Filtrar solo por estado ACTIVE (ya no se usa EXPORTED)
            val activeKeks = kekKeys.filter { it.status == "ACTIVE" }
            Log.d(TAG, "getActiveKEKEntity: ${activeKeks.size} KEKs activas encontradas")

            // Seleccionar la más reciente
            val activeKEK = activeKeks.maxByOrNull { it.injectionTimestamp }

            if (activeKEK != null) {
                Log.d(TAG, "getActiveKEKEntity: KEK activa encontrada - KCV=${activeKEK.kcv}, Estado=${activeKEK.status}")
            } else {
                Log.d(TAG, "getActiveKEKEntity: No se encontró KEK activa")
            }

            activeKEK

        } catch (e: Exception) {
            Log.e(TAG, "getActiveKEKEntity: Error obteniendo entidad de KEK activa", e)
            Log.e(TAG, "getActiveKEKEntity: Stack trace: ${e.stackTraceToString()}")
            null
        }
    }
}
