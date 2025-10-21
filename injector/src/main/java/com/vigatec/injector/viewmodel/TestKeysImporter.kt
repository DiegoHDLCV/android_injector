package com.vigatec.injector.viewmodel

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.example.persistence.repository.InjectedKeyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Importador de llaves de prueba desde archivos JSON generados por el script
 * generar_llaves_prueba.sh
 */
object TestKeysImporter {
    
    private const val TAG = "TestKeysImporter"
    
    data class TestKey(
        @SerializedName("keyType")
        val keyType: String,
        
        @SerializedName("futurexCode")
        val futurexCode: String,
        
        @SerializedName("algorithm")
        val algorithm: String,
        
        @SerializedName("keyHex")
        val keyHex: String,
        
        @SerializedName("kcv")
        val kcv: String,
        
        @SerializedName("bytes")
        val bytes: Int,
        
        @SerializedName("description")
        val description: String
    )
    
    data class TestKeysFile(
        @SerializedName("generated")
        val generated: String,
        
        @SerializedName("description")
        val description: String,
        
        @SerializedName("totalKeys")
        val totalKeys: Int,
        
        @SerializedName("keys")
        val keys: List<TestKey>
    )
    
    /**
     * Importa llaves de prueba desde un archivo JSON
     * @param jsonContent Contenido del archivo JSON
     * @param repository Repositorio para guardar las llaves
     * @return Resultado con el número de llaves importadas
     */
    suspend fun importFromJson(
        jsonContent: String,
        repository: InjectedKeyRepository
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Iniciando importación de llaves de prueba...")
            
            val gson = Gson()
            val testKeysFile = gson.fromJson(jsonContent, TestKeysFile::class.java)
            
            Log.i(TAG, "Archivo JSON parseado exitosamente:")
            Log.i(TAG, "  - Generado: ${testKeysFile.generated}")
            Log.i(TAG, "  - Descripción: ${testKeysFile.description}")
            Log.i(TAG, "  - Total de llaves: ${testKeysFile.totalKeys}")
            
            var imported = 0
            var errors = 0
            
            testKeysFile.keys.forEach { testKey ->
                try {
                    // Determinar algoritmo de llave
                    val algorithm = when (testKey.algorithm) {
                        "3DES-16" -> "DES_DOUBLE"
                        "3DES-24" -> "DES_TRIPLE"
                        "AES-128" -> "AES_128"
                        "AES-192" -> "AES_192"
                        "AES-256" -> "AES_256"
                        else -> "DES_TRIPLE"
                    }
                    
                    // Determinar tipo de llave según el keyType
                    val genericKeyType = when (testKey.keyType) {
                        "KEK_STORAGE" -> "KEK_STORAGE" // KEK Storage del sistema
                        else -> "CEREMONY_KEY" // Llaves genéricas para ceremonia
                    }
                    
                    val isKEK = testKey.keyType == "KEK_STORAGE"
                    val kekType = if (isKEK) "KEK_STORAGE" else ""
                    val customName = if (isKEK) "KEK Storage Sistema" else "Test Key ${testKey.algorithm}"
                    
                    Log.d(TAG, "Importando llave: ${testKey.keyType} (${testKey.algorithm}) - KCV: ${testKey.kcv}")
                    
                    // Guardar en BD
                    repository.recordKeyInjectionWithData(
                        keySlot = -1, // Sin slot asignado (será asignado durante la inyección)
                        keyType = genericKeyType,
                        keyAlgorithm = algorithm,
                        kcv = testKey.kcv,
                        keyData = testKey.keyHex,
                        status = "GENERATED",
                        isKEK = isKEK,
                        kekType = kekType,
                        customName = customName
                    )
                    
                    imported++
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error importando llave ${testKey.keyType}: ${e.message}")
                    errors++
                }
            }
            
            Log.i(TAG, "Importación completada:")
            Log.i(TAG, "  - Llaves importadas exitosamente: $imported")
            Log.i(TAG, "  - Errores: $errors")
            
            Result.success(imported)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error durante la importación: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Valida el contenido del archivo JSON antes de importar
     * @param jsonContent Contenido del archivo JSON
     * @return Resultado con información de validación
     */
    fun validateJson(jsonContent: String): Result<TestKeysFile> {
        return try {
            val gson = Gson()
            val testKeysFile = gson.fromJson(jsonContent, TestKeysFile::class.java)
            
            // Validaciones básicas
            if (testKeysFile.keys.isEmpty()) {
                return Result.failure(Exception("El archivo JSON no contiene llaves"))
            }
            
            if (testKeysFile.totalKeys != testKeysFile.keys.size) {
                Log.w(TAG, "Advertencia: totalKeys (${testKeysFile.totalKeys}) no coincide con keys.size (${testKeysFile.keys.size})")
            }
            
            // Validar que todas las llaves tengan los campos requeridos
            testKeysFile.keys.forEachIndexed { index, key ->
                if (key.keyHex.isBlank()) {
                    return Result.failure(Exception("Llave en índice $index no tiene keyHex"))
                }
                if (key.kcv.isBlank()) {
                    return Result.failure(Exception("Llave en índice $index no tiene kcv"))
                }
                if (key.keyType.isBlank()) {
                    return Result.failure(Exception("Llave en índice $index no tiene keyType"))
                }
            }
            
            Log.i(TAG, "Archivo JSON validado exitosamente")
            Result.success(testKeysFile)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error validando JSON: ${e.message}")
            Result.failure(e)
        }
    }
}
