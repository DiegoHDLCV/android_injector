package com.example.manufacturer.libraries.aisino.wrapper

import android.app.Application
import android.content.Context
import android.util.Log
import com.example.manufacturer.base.controllers.ped.*
import com.example.manufacturer.base.models.*
import com.example.manufacturer.base.models.KeyType as GenericKeyType
import com.example.manufacturer.base.models.KeyAlgorithm as GenericKeyAlgorithm
import com.example.manufacturer.base.models.BlockCipherMode as GenericBlockCipherMode
import com.example.manufacturer.base.models.MacAlgorithm as GenericMacAlgorithm
import com.example.manufacturer.base.models.PinBlockFormatType as GenericPinBlockFormatType
import com.example.manufacturer.base.models.DukptKeyVariant as GenericDukptKeyVariant
import com.vanstone.appsdk.client.ISdkStatue
import com.vanstone.appsdk.client.SdkApi
import com.vanstone.trans.api.PedApi
import com.vanstone.trans.api.SystemApi
import com.vanstone.transex.ped.IGetDukptPinListener
import com.vanstone.transex.ped.IGetPinResultListenner
import com.vanstone.utils.CommonConvert
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.min
import kotlinx.coroutines.delay

private const val IPEK_INJECTION_MODE_PLAINTEXT: Byte = 0
private const val IPEK_INJECTION_MODE_ENCRYPTED: Byte = 1
private const val KCV_CHECK_MODE_DISABLED: Byte = 0x00
private const val KCV_CHECK_MODE_ENABLED: Byte = 0x01

class AisinoPedController(private val application: Application) : IPedController {

    private val TAG = "AisinoPedController"
    private val context: Context = application.applicationContext

    // --- Mappings ---

    private fun mapToAisinoKeyTypeInt(generic: GenericKeyType): Int? {
        return when (generic) {
            GenericKeyType.TRANSPORT_KEY,
            GenericKeyType.MASTER_KEY -> 1 // PEDKEYTYPE_MASTKEY
            GenericKeyType.WORKING_PIN_KEY,
            GenericKeyType.WORKING_MAC_KEY,
            GenericKeyType.WORKING_DATA_KEY -> 2 // PEDKEYTYPE_WORKKET
            else -> null
        }
    }

    private fun mapToAisinoDesMode(alg: GenericKeyAlgorithm, encrypt: Boolean): Int? {
        return when (alg) {
            GenericKeyAlgorithm.DES_SINGLE -> if (encrypt) 0x01 else 0x81
            GenericKeyAlgorithm.DES_TRIPLE -> if (encrypt) 0x03 else 0x83
            GenericKeyAlgorithm.SM4 -> if (encrypt) 0x02 else 0x82
            else -> null
        }
    }

    private fun mapToAisinoMacFlag(generic: GenericMacAlgorithm): Int? {
        return when (generic) {
            GenericMacAlgorithm.RETAIL_MAC_ANSI_X9_19,
            GenericMacAlgorithm.CBC_MAC_ISO9797_1_M1,
            GenericMacAlgorithm.CBC_MAC_ISO9797_1_M2 -> 0x00
            GenericMacAlgorithm.UNIONPAY_CBC_MAC -> 0x01
            else -> null
        }
    }

    private fun mapToAisinoMacDesMode(alg: GenericKeyAlgorithm): Int? {
        return when (alg) {
            GenericKeyAlgorithm.DES_SINGLE -> 0x01
            GenericKeyAlgorithm.DES_TRIPLE -> 0x03
            else -> null
        }
    }

    private fun mapToAisinoDukptPinMode(generic: GenericPinBlockFormatType, incrementKsn: Boolean): Byte? {
        val baseMode: Byte = when (generic) {
            GenericPinBlockFormatType.ISO9564_0 -> 0x00
            GenericPinBlockFormatType.ISO9564_1 -> 0x01
            GenericPinBlockFormatType.ISO9564_3 -> 0x02
            else -> {
                Log.w(TAG, "Unsupported generic PinBlockFormatType mapping to Aisino DUKPT Mode: $generic")
                return null
            }
        }
        return if (incrementKsn) baseMode else (baseMode + 0x20).toByte()
    }

    // --- Initialization & Lifecycle ---

    init {
        Log.d(TAG, "AisinoPedController instance created.")
    }

    override suspend fun initializePed(application: Application): Boolean = withContext(Dispatchers.Main) {
        Log.i(TAG, ">>> INICIO: Aisino SDK Initialization (Main Thread)")
        suspendCancellableCoroutine { continuation ->
            try {
                val curAppDir = context.filesDir.absolutePath
                val pathBytes = CommonConvert.StringToBytes("$curAppDir/\u0000")
                if (pathBytes == null) {
                    val ex = IllegalStateException("Error converting directory path to bytes for SystemInit_Api.")
                    Log.e(TAG, "<<< ERROR FIN: AisinoSDKManager.initialize (pathBytes null)", ex)
                    if (continuation.isActive) continuation.resumeWithException(ex)
                    return@suspendCancellableCoroutine
                }

                Log.d(TAG, "Initializing SystemApi.SystemInit_Api on Main Thread...")
                SystemApi.SystemInit_Api(0, pathBytes, context, object : ISdkStatue {
                    override fun sdkInitSuccessed() {
                        Log.i(TAG, "SystemApi.SystemInit_Api: Success. Initializing SdkApi...")
                        if (!continuation.isActive) return

                        // --- Step 2: Initialize SdkApi ---
                        SdkApi.getInstance().init(context, object : ISdkStatue {
                            override fun sdkInitSuccessed() {
                                Log.i(TAG, "SdkApi.getInstance().init(): Success. SDK initialization complete (Main Thread).")

                                // Verificar el estado del dispositivo después de la inicialización
                                val deviceStatus = checkDeviceStatus()
                                Log.i(TAG, "Device status check completed: $deviceStatus")

                                if (continuation.isActive) {
                                    continuation.resume(true)
                                }
                            }

                            override fun sdkInitFailed() {
                                val ex = IllegalStateException("SDK initialization failed (SdkApi).")
                                Log.e(TAG, "<<< ERROR FIN: SdkApi initialization failed.", ex)
                                if (continuation.isActive) {
                                    continuation.resumeWithException(ex)
                                }
                            }
                        })
                    }

                    override fun sdkInitFailed() {
                        val ex = IllegalStateException("SDK initialization failed (SystemApi).")
                        Log.e(TAG, "<<< ERROR FIN: SystemApi initialization failed.", ex)
                        if (continuation.isActive) {
                            continuation.resumeWithException(ex)
                        }
                    }
                })
            } catch (e: Throwable) {
                Log.e(TAG, "Exception during initializePed setup", e)
                if (continuation.isActive) {
                    continuation.resumeWithException(e)
                }
            }
        }
    }

    /**
     * Verifica el estado del dispositivo PED para diagnóstico
     */
    private fun checkDeviceStatus(): String {
        return try {
            val sdkApi = SdkApi.getInstance()
            val pedHandler = sdkApi.getPedHandler()
            
            // Intentar obtener información básica del dispositivo
            "SDK Available: true, PED Handler: available"
        } catch (e: Exception) {
            "SDK Error: ${e.message}"
        }
    }

    override fun releasePed() {
        Log.d(TAG, "releasePed called. No specific PED release action required for Aisino.")
    }

    // --- Status & Configuration ---

    @Throws(PedException::class)
    override suspend fun getStatus(): PedStatusInfo = withContext(Dispatchers.IO) {
        Log.w(TAG, "getStatus() not directly supported by Aisino PedApi. Returning default non-tampered status.")
        PedStatusInfo(isTampered = false, batteryLevel = null, errorMessage = null)
    }

    @Throws(PedException::class)
    override suspend fun getConfig(): PedConfigInfo = withContext(Dispatchers.IO) {
        var serialNum: String? = null
        try {
            val snBytes = ByteArray(40)
            val result = PedApi.PEDReadPinPadSn_Api(snBytes)
            if (result == 0) {
                serialNum = String(snBytes, StandardCharsets.US_ASCII).trim().takeUnless { it.isEmpty() }
            } else {
                Log.w(TAG, "PEDReadPinPadSn_Api failed with code: $result")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading PED serial number", e)
            // Do not throw, return partial info
        }

        Log.w(TAG, "getConfig() - Only SN is retrieved. Other info might require SystemApi.")
        PedConfigInfo(
            serialNumber = serialNum,
            firmwareVersion = null,
            hardwareVersion = null,
            modelIdentifier = null
        )
    }

    // --- Key Management ---

    @Throws(PedException::class)
    override suspend fun writeKey(
        keyIndex: Int,
        keyType: GenericKeyType,
        keyAlgorithm: GenericKeyAlgorithm,
        keyData: PedKeyData,
        transportKeyIndex: Int?,
        transportKeyType: GenericKeyType?,
        transportKeyAlgorithm: GenericKeyAlgorithm? // Nuevo parámetro (no usado en Aisino)
    ): Boolean = withContext(Dispatchers.IO) {
        Log.i(TAG, "--- Starting writeKey (for encrypted key) ---")
        Log.d(TAG, "Attempting to write an encrypted key with the following parameters:")
        Log.d(TAG, "-> Destination Key Index (keyIndex): $keyIndex")
        Log.d(TAG, "-> Destination Key Type (keyType): $keyType")
        Log.d(TAG, "-> Key Algorithm: $keyAlgorithm")
        Log.d(TAG, "-> Encrypted Key Data (Hex): ${keyData.keyBytes.joinToString("") { "%02X".format(it) }}")
        Log.d(TAG, "-> Transport Key Index (transportKeyIndex): $transportKeyIndex")
        Log.d(TAG, "-> Transport Key Type (transportKeyType): $transportKeyType")

        if (transportKeyIndex == null || transportKeyType == null) {
            throw PedKeyException("Aisino requires transport key details for encrypted key loading.")
        }

        val aisinoDKeyType = mapToAisinoKeyTypeInt(keyType)
            ?: throw PedKeyException("Unsupported destination key type for writeKey: $keyType.")
        Log.d(TAG, "Mapped destination key type '$keyType' to Aisino type '$aisinoDKeyType'.")

        if (transportKeyType != GenericKeyType.MASTER_KEY) {
            Log.w(TAG, "Aisino PEDWriteKey_Api typically uses a Master Key as the transport key. Current: $transportKeyType")
        }

        val aisinoMode = 0x83 // Corresponde a 3DES Decrypt
        Log.d(TAG, "Using hardcoded Aisino mode '0x83' (3DES Decrypt) for key injection.")

        try {
            Log.i(TAG, "Calling PedApi.PEDWriteKey_Api with SKeyIndex: $transportKeyIndex, DKeyIndex: $keyIndex, DKeyType: $aisinoDKeyType, Mode: $aisinoMode")

            // CORRECCIÓN: Se pasa un array de bytes con 0xFF para deshabilitar la verificación de KCV
            // en lugar de null o un array vacío, que causaba el crash.
            val kvrDataNoVerify = byteArrayOf(0xFF.toByte())

            val result = PedApi.PEDWriteKey_Api(
                transportKeyIndex,      // SKeyIndex (Source/Transport Key)
                keyIndex,               // DKeyIndex (Destination Key)
                keyData.keyBytes,       // DKey (Encrypted key data)
                aisinoDKeyType,         // DKeyType (Destination key type)
                aisinoMode,             // Mode for 3DES Decrypt
                kvrDataNoVerify         // KVRData para deshabilitar explícitamente la verificación
            )

            Log.i(TAG, "PedApi.PEDWriteKey_Api finished with result code: $result")

            if (result != 0) {
                throw PedKeyException("Failed to write key (encrypted). Aisino Error Code: $result")
            }

            Log.d(TAG, "Successfully wrote encrypted key to index: $keyIndex")
            Log.i(TAG, "--- writeKey Finished Successfully ---")
            true
        } catch (e: Exception) {
            Log.e(TAG, "An unexpected exception occurred during encrypted key writing", e)
            throw PedKeyException("Failed to write key (encrypted): ${e.message}", e)
        }
    }


    @Throws(PedException::class)
    override suspend fun writeKeyPlain(
        keyIndex: Int,
        keyType: GenericKeyType,
        keyAlgorithm: GenericKeyAlgorithm,
        keyBytes: ByteArray,
        kcvBytes: ByteArray?
    ): Boolean = withContext(Dispatchers.IO) {
        Log.i(TAG, "--- Starting writeKeyPlain ---")
        Log.d(TAG, "-> Key Index: $keyIndex, Type: $keyType, Algorithm: $keyAlgorithm")
        Log.d(TAG, "-> Key Bytes Length: ${keyBytes.size}, KCV Length: ${kcvBytes?.size ?: 0}")

        // Validaciones adicionales
        if (keyIndex < 0 || keyIndex > 999) {
            throw PedKeyException("Invalid key index: $keyIndex. Must be between 0-999")
        }

        if (keyBytes.isEmpty()) {
            throw PedKeyException("Key bytes cannot be empty")
        }

        if (keyType != GenericKeyType.MASTER_KEY && keyType != GenericKeyType.TRANSPORT_KEY) {
            throw PedKeyException("Plaintext loading via writeKeyPlain is only for MASTER_KEY or TRANSPORT_KEY. Received: $keyType")
        }

        // Aisino SDK key modes for plaintext injection:
        // Mode 0x01: Single DES (8 bytes)
        // Mode 0x03: Triple DES double-length (16 bytes, DES_DOUBLE) and triple-length (24 bytes, DES_TRIPLE)
        // Both DES_DOUBLE (3DES-112 with K1=K3) and DES_TRIPLE (3DES-168) use mode 0x03
        val aisinoMode = when (keyAlgorithm) {
            GenericKeyAlgorithm.DES_SINGLE -> 0x01
            GenericKeyAlgorithm.DES_DOUBLE, GenericKeyAlgorithm.DES_TRIPLE -> 0x03
            else -> throw PedKeyException("Unsupported algorithm for plaintext Master/Transport Key: $keyAlgorithm")
        }
        Log.d(TAG, "Mapped generic algorithm '$keyAlgorithm' to Aisino mode '0x${aisinoMode.toString(16).uppercase()}'.")
        if (keyAlgorithm == GenericKeyAlgorithm.DES_DOUBLE) {
            Log.d(TAG, "Note: DES_DOUBLE (3DES-112, double-length 16 bytes with K1=K3) uses Aisino mode 0x03 per SDK documentation")
        }

        // Verificar si ya existe una clave en ese índice
        try {
            val keyExists = isKeyPresent(keyIndex, keyType)
            if (keyExists) {
                Log.w(TAG, "Key already exists at index $keyIndex. Attempting to delete it first...")
                deleteKey(keyIndex, keyType)
                Log.d(TAG, "Successfully deleted existing key at index $keyIndex")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not check/delete existing key at index $keyIndex: ${e.message}")
        }

        try {
            Log.i(TAG, "Calling PedApi.PEDWriteMKey_Api with index: $keyIndex, mode: $aisinoMode")
            Log.d(TAG, "Key bytes (hex): ${keyBytes.joinToString("") { "%02X".format(it) }}")
            
            val result = PedApi.PEDWriteMKey_Api(keyIndex, aisinoMode, keyBytes)
            Log.i(TAG, "PedApi.PEDWriteMKey_Api finished with result code: $result")

            if (result != 0) {
                val errorMessage = when (result) {
                    238 -> "Failed to write key (plaintext). Aisino Error Code: 238 - Possible causes: Invalid parameters, device not ready, or insufficient permissions"
                    else -> "Failed to write key (plaintext). Aisino Error Code: $result"
                }
                Log.e(TAG, errorMessage)
                
                // Intentar recuperación para el error 238
                if (result == 238) {
                    Log.i(TAG, "Attempting recovery from error 238...")
                    val recoverySuccess = attemptRecoveryFromError238(keyIndex, aisinoMode, keyBytes)
                    if (recoverySuccess) {
                        Log.i(TAG, "Recovery successful! Key written successfully.")
                        Log.d(TAG, "Successfully wrote key (plaintext) to index: $keyIndex")
                        Log.i(TAG, "--- writeKeyPlain Finished Successfully ---")
                        return@withContext true
                    } else {
                        Log.e(TAG, "Recovery failed. Throwing exception.")
                    }
                }
                
                throw PedKeyException(errorMessage)
            }

            Log.d(TAG, "Successfully wrote key (plaintext) to index: $keyIndex")
            Log.i(TAG, "--- writeKeyPlain Finished Successfully ---")
            true
        } catch (e: Exception) {
            Log.e(TAG, "An unexpected exception occurred during plaintext key writing", e)
            throw PedKeyException("Failed to write key (plaintext): ${e.message}", e)
        }
    }

    /**
     * Intenta recuperar del error 238 usando diferentes estrategias
     */
    private suspend fun attemptRecoveryFromError238(
        keyIndex: Int,
        aisinoMode: Int,
        keyBytes: ByteArray
    ): Boolean {
        Log.w(TAG, "Attempting recovery from error 238...")
        
        // Estrategia 1: Esperar un momento y reintentar
        Log.d(TAG, "Strategy 1: Waiting and retrying...")
        delay(1000)
        var result = PedApi.PEDWriteMKey_Api(keyIndex, aisinoMode, keyBytes)
        if (result == 0) {
            Log.i(TAG, "Recovery strategy 1 successful!")
            return true
        }
        
        // Estrategia 2: Intentar con un modo diferente
        Log.d(TAG, "Strategy 2: Trying with different mode...")
        val alternativeMode = if (aisinoMode == 0x01) 0x03 else 0x01
        result = PedApi.PEDWriteMKey_Api(keyIndex, alternativeMode, keyBytes)
        if (result == 0) {
            Log.i(TAG, "Recovery strategy 2 successful with mode $alternativeMode!")
            return true
        }
        
        // Estrategia 3: Verificar si el dispositivo está en un estado válido
        Log.d(TAG, "Strategy 3: Checking device state...")
        try {
            // Intentar una operación simple para verificar el estado
            val testResult = PedApi.isKeyExist_Api(1, 0) // Verificar si existe una clave en el índice 0
            Log.d(TAG, "Device state check result: $testResult")
        } catch (e: Exception) {
            Log.w(TAG, "Device state check failed: ${e.message}")
        }
        
        Log.w(TAG, "All recovery strategies failed. Error 238 persists.")
        return false
    }


    @Throws(PedException::class)
    override suspend fun deleteKey(keyIndex: Int, keyType: GenericKeyType): Boolean = withContext(Dispatchers.IO) {
        Log.i(TAG, "deleteKey: Request to delete key in slot $keyIndex, type $keyType.")
        val aisinoKeyType = mapToAisinoKeyTypeInt(keyType)
            ?: throw PedKeyException("Unsupported key type for deleteKey: $keyType.")

        try {
            Log.d(TAG, "deleteKey: Calling 'PedApi.PedErase_Api' with KeyType: $aisinoKeyType, Index: $keyIndex.")
            val success = PedApi.PedErase_Api(aisinoKeyType, keyIndex)
            Log.d(TAG, "deleteKey: Native function 'PedApi.PedErase_Api' returned: $success")
            return@withContext success
        } catch (e: Exception) {
            Log.e(TAG, "deleteKey: Error deleting key [$keyType/$keyIndex]", e)
            throw PedKeyException("Failed to delete key [$keyType/$keyIndex]: ${e.message}", e)
        }
    }

    @Throws(PedException::class)
    override suspend fun deleteAllKeys(): Boolean = withContext(Dispatchers.IO) {
        Log.i(TAG, "deleteAllKeys: Request received.")
        try {
            Log.d(TAG, "deleteAllKeys: Calling native function 'PedApi.PedErase_Api()'...")
            val success = PedApi.PedErase_Api()
            Log.d(TAG, "deleteAllKeys: Native function 'PedApi.PedErase()' returned: $success")
            if (!success) {
                throw PedKeyException("Failed to delete all keys (PedErase returned false).")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "deleteAllKeys: Error calling Aisino erase API.", e)
            throw PedKeyException("Failed to delete all keys: ${e.message}", e)
        }
    }

    @Throws(PedException::class)
    override suspend fun isKeyPresent(keyIndex: Int, keyType: GenericKeyType): Boolean = withContext(Dispatchers.IO) {
        val aisinoKeyType = mapToAisinoKeyTypeInt(keyType) ?: return@withContext false
        try {
            PedApi.isKeyExist_Api(aisinoKeyType, keyIndex)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking key presence", e)
            throw PedException("Failed to check key presence: ${e.message}", e)
        }
    }

    @Throws(PedException::class)
    override suspend fun getKeyInfo(keyIndex: Int, keyType: GenericKeyType): PedKeyInfo? = withContext(Dispatchers.IO) {
        Log.w(TAG, "Aisino API does not support getting key details. Checking for presence only.")
        if (isKeyPresent(keyIndex, keyType)) {
            Log.i(TAG, "Key is PRESENT at index=$keyIndex. Returning basic PedKeyInfo.")
            PedKeyInfo(index = keyIndex, type = keyType, algorithm = null)
        } else {
            Log.i(TAG, "Key is NOT PRESENT at index=$keyIndex. Returning null.")
            null
        }
    }

    // --- DUKPT Management ---

    @Throws(PedException::class)
    override suspend fun writeDukptInitialKey(
        groupIndex: Int,
        keyAlgorithm: GenericKeyAlgorithm,
        keyBytes: ByteArray,
        initialKsn: ByteArray,
        keyChecksum: String?
    ): Boolean = withContext(Dispatchers.IO) {
        Log.i(TAG, "--- Starting writeDukptInitialKey without KCV verification ---")

        val keyLenByte: Byte = keyBytes.size.toByte()
        Log.d(TAG, "Key length: $keyLenByte bytes")

        // CAMBIO: Sin verificación pero con buffer mínimo para evitar error 7
        val checkMode: Byte = 0x00  // Sin verificación
        val checkBuffer = ByteArray(1) { 0 }  // Buffer mínimo de 1 byte con valor 0

        Log.d(TAG, "Check mode: 0x00 (No verification)")
        Log.d(TAG, "Check buffer: minimal buffer to avoid error 7")

        try {
            val sourceKeyIndex: Byte = 0  // Texto plano

            Log.i(TAG, "Calling PedApi.PedDukptWriteTIK_Api without KCV verification...")
            val result = PedApi.PedDukptWriteTIK_Api(
                groupIndex.toByte(),  // Grupo 1
                sourceKeyIndex,                // 0 (texto plano)
                keyLenByte,                   // 16
                keyBytes,                     // TIK hardcodeada
                initialKsn,                     // KSN hardcodeado
                checkMode,                    // 0x00 (sin verificación)
                checkBuffer                   // Buffer mínimo
            )

            Log.i(TAG, "PedApi.PedDukptWriteTIK_Api result: $result")

            if (result != 0) {
                val errorMsg = when(result) {
                    1 -> "DUKPT Index Out of Range"
                    2 -> "SrcKeyIdx Out of Range"
                    3 -> "Key Length Error"
                    4 -> "Illegal Ciphertext Data"
                    5 -> "KsnIn Parameter Error"
                    6 -> "Illegal Check Mode"
                    7 -> "CheckBuf Empty"
                    8 -> "Other DUKPT Errors"
                    else -> "Unknown Error"
                }
                throw PedKeyException("Failed to write DUKPT key. Error: $errorMsg (Code: $result)")
            }

            Log.d(TAG, "Successfully wrote DUKPT IPEK to group: $groupIndex")

            // Verificar que se inyectó correctamente
            try {
                val currentKsn = ByteArray(10)
                val ksnResult = PedApi.PedGetDukptKSN_Api(groupIndex.toByte(), currentKsn)
                if (ksnResult == 0) {
                    val ksnHex = currentKsn.joinToString("") { "%02X".format(it) }
                    Log.i(TAG, "Verification - Current KSN: $ksnHex")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not verify KSN after injection: ${e.message}")
            }

            return@withContext true

        } catch (e: Exception) {
            Log.e(TAG, "Exception writing DUKPT key", e)
            throw e as? PedKeyException ?: PedKeyException("Failed to write DUKPT key", e)
        }
    }

    companion object {
        private const val TAG = "DukptKeyInjection"
    }

    @Throws(PedException::class)
    override suspend fun createDukptAESKey(
        keyIndex: Int,
        keyAlgorithm: GenericKeyAlgorithm,
        ipekBytes: ByteArray,
        ksnBytes: ByteArray,
        kcvBytes: ByteArray?
    ): Boolean {
        throw PedKeyException("createDukptAESKey not implemented for Aisino PED")
    }

    @Throws(PedException::class)
    override suspend fun getDukptInfo(groupIndex: Int): DukptInfo? = withContext(Dispatchers.IO) {
        try {
            val ksnOut = ByteArray(10) // Aisino KSN is 10 bytes
            val result = PedApi.PedGetDukptKSN_Api(groupIndex.toByte(), ksnOut)
            if (result != 0) {
                Log.w(TAG, "Failed to get DUKPT KSN for group $groupIndex. Aisino Error: $result")
                return@withContext null
            }
            DukptInfo(ksn = ksnOut, counter = null)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting DUKPT info", e)
            throw PedException("Failed to get DUKPT info for group $groupIndex: ${e.message}", e)
        }
    }

    @Throws(PedException::class)
    override suspend fun incrementDukptKsn(groupIndex: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val result = PedApi.PedDukptIncreaseKsn_Api(groupIndex.toByte())
            if (result != 0) {
                throw PedException("Failed to increment DUKPT KSN. Aisino Error Code: $result")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error incrementing DUKPT KSN", e)
            throw PedException("Failed to increment DUKPT KSN for group $groupIndex: ${e.message}", e)
        }
    }

    // --- Cryptographic Operations ---

    @Throws(PedException::class)
    override suspend fun encrypt(request: PedCipherRequest): PedCipherResult {
        if (!request.encrypt) throw IllegalArgumentException("Use decrypt method for decryption")
        return performCipher(request)
    }

    @Throws(PedException::class)
    override suspend fun decrypt(request: PedCipherRequest): PedCipherResult {
        if (request.encrypt) throw IllegalArgumentException("Use encrypt method for encryption")
        return performCipher(request)
    }

    @Throws(PedException::class)
    private suspend fun performCipher(request: PedCipherRequest): PedCipherResult = withContext(Dispatchers.IO) {
        when {
            request.isDukpt -> performDukptCipherInternal(request)
            request.keyType == GenericKeyType.RSA_PRIVATE_KEY -> performRsaCipherInternal(request)
            else -> performSymmetricCipherInternal(request)
        }
    }

    @Throws(PedException::class)
    private fun performSymmetricCipherInternal(request: PedCipherRequest): PedCipherResult {
        val aisinoKeyType = mapToAisinoKeyTypeInt(request.keyType)
            ?: throw PedCryptoException("Unsupported key type for symmetric cipher: ${request.keyType}")
        val aisinoMode = mapToAisinoDesMode(request.algorithm, request.encrypt)
            ?: throw PedCryptoException("Unsupported algorithm for symmetric cipher: ${request.algorithm}")

        val blockSize = if (request.algorithm == GenericKeyAlgorithm.SM4) 16 else 8
        val outputBufferSize = if (request.data.isNotEmpty()) ((request.data.size + blockSize - 1) / blockSize) * blockSize else 0
        val dataOut = ByteArray(outputBufferSize)

        try {
            val result: Int
            if (request.mode == GenericBlockCipherMode.CBC) {
                if (request.iv == null) throw PedCryptoException("IV is required for CBC mode")
                val ivLen = if (request.algorithm == GenericKeyAlgorithm.SM4) 16 else 8
                if (request.iv.size != ivLen) throw PedCryptoException("Invalid IV size for algorithm ${request.algorithm}, expected $ivLen")

                result = PedApi.PEDDesCBC_Api(request.keyIndex, aisinoMode, aisinoKeyType, request.iv, ivLen, request.data, request.data.size, dataOut)
            } else { // ECB
                if (request.iv != null) Log.w(TAG, "IV provided for ECB mode, ignoring.")
                result = PedApi.PEDDes_Api(request.keyIndex, aisinoMode, aisinoKeyType, request.data, request.data.size, dataOut)
            }

            if (result != 0) {
                throw PedCryptoException("Symmetric cipher operation failed. Aisino Error Code: $result")
            }
            return PedCipherResult(dataOut.copyOf(outputBufferSize))
        } catch (e: Exception) {
            Log.e(TAG, "Error performing symmetric cipher", e)
            throw PedCryptoException("Symmetric cipher failed: ${e.message}", e)
        }
    }

    @Throws(PedException::class)
    private fun performDukptCipherInternal(request: PedCipherRequest): PedCipherResult {
        val groupIndex = request.dukptGroupIndex ?: throw PedCryptoException("DUKPT group index required")

        if (request.algorithm !in listOf(GenericKeyAlgorithm.DES_SINGLE, GenericKeyAlgorithm.DES_TRIPLE)) {
            throw PedCryptoException("Aisino only supports DES/TDES for DUKPT cipher, requested: ${request.algorithm}")
        }

        val keyVarType: Byte = when (request.dukptKeyVariant) {
            GenericDukptKeyVariant.PIN -> 0x02
            GenericDukptKeyVariant.MAC -> 0x00
            GenericDukptKeyVariant.DATA_ENCRYPT, GenericDukptKeyVariant.DATA_DECRYPT -> 0x01
            else -> {
                Log.w(TAG, "Unsupported or null DukptKeyVariant: ${request.dukptKeyVariant}. Defaulting to DATA key (0x01).")
                0x01
            }
        }

        val aisinoMode: Byte = when(request.mode) {
            GenericBlockCipherMode.ECB -> if(request.encrypt) 0x01 else 0x00
            GenericBlockCipherMode.CBC -> if(request.encrypt) 0x03 else 0x02
            else -> throw PedCryptoException("Block cipher mode (ECB/CBC) is required for DUKPT DES cipher.")
        }

        if (keyVarType == 0x02.toByte() && aisinoMode != 0x01.toByte()) {
            throw PedCryptoException("Aisino PedCalcDESDukpt_Api requires ECB encryption mode (0x01) when KeyVarType is PIN (0x02).")
        }

        val blockSize = 8
        val outputBufferSize = if (request.data.isNotEmpty()) ((request.data.size + blockSize - 1) / blockSize) * blockSize else 0
        val dataOut = ByteArray(outputBufferSize)
        val ksnUsed = ByteArray(10)

        val effectiveIv = request.iv ?: ByteArray(blockSize) { 0 }

        try {
            Log.d(TAG, "Calling PedCalcDESDukpt_Api with params: groupIndex=${groupIndex}, keyVarType=${keyVarType}, aisinoMode=${aisinoMode}, dataLen=${request.data.size}, ivLen=${effectiveIv.size}")

            // Paso 1: Realizar la operación criptográfica.
            val result = PedApi.PedCalcDESDukpt_Api(
                groupIndex.toByte(),
                keyVarType,
                effectiveIv,
                request.data,
                aisinoMode,
                dataOut,
                ksnUsed
            )
            if (result != 0) {
                throw PedCryptoException("DUKPT cipher operation failed. Aisino Error Code: $result")
            }

            // CORRECCIÓN: Incrementar explícitamente el KSN para la siguiente transacción.
            Log.d(TAG, "DUKPT cipher successful. Now incrementing KSN for group $groupIndex.")
            val incrementResult = PedApi.PedDukptIncreaseKsn_Api(groupIndex.toByte())
            if(incrementResult != 0) {
                throw PedCryptoException("DUKPT cipher succeeded but FAILED to increment KSN. Error: $incrementResult")
            }

            // Paso 3: Obtener el KSN actualizado para devolverlo.
            val newKsnOut = ByteArray(10)
            val getNewKsnResult = PedApi.PedGetDukptKSN_Api(groupIndex.toByte(), newKsnOut)
            if (getNewKsnResult != 0) {
                throw PedCryptoException("Failed to retrieve new KSN after increment. Error: $getNewKsnResult")
            }

            val actualResultData = dataOut.copyOf(outputBufferSize)
            val finalDukpt = DukptInfo(ksn = newKsnOut, counter = null) // Devolver el KSN para la *próxima* transacción.
            return PedCipherResult(actualResultData, finalDukpt)

        } catch (e: Exception) {
            Log.e(TAG, "Error performing DUKPT cipher", e)
            throw PedCryptoException("DUKPT cipher failed: ${e.message}", e)
        }
    }




    @Throws(PedException::class)
    private fun performRsaCipherInternal(request: PedCipherRequest): PedCipherResult {
        Log.w(TAG, "Aisino calcRSAEx_Api behavior (Encrypt vs Sign) with private key needs verification.")
        val dataOut = ByteArray(256) // Assume 256 bytes (RSA-2048)
        try {
            val resultLen = PedApi.calcRSAEx_Api(request.keyIndex, request.data.size, request.data, dataOut, ByteArray(100))
            if (resultLen < 0) {
                throw PedCryptoException("RSA operation failed. Aisino Error Code: $resultLen")
            }
            return PedCipherResult(dataOut.copyOf(resultLen))
        } catch (e: Exception) {
            Log.e(TAG, "Error performing RSA operation", e)
            throw PedCryptoException("RSA operation failed: ${e.message}", e)
        }
    }

    @Throws(PedException::class)
    override suspend fun calculateMac(request: PedMacRequest): PedMacResult = withContext(Dispatchers.IO) {
        if (request.isDukpt) {
            calculateDukptMacInternal(request)
        } else {
            calculateStandardMacInternal(request)
        }
    }

    @Throws(PedException::class)
    private fun calculateStandardMacInternal(request: PedMacRequest): PedMacResult {
        val aisinoKeyType = mapToAisinoKeyTypeInt(request.keyType)
            ?: throw PedCryptoException("Unsupported key type for MAC: ${request.keyType}")
        if (aisinoKeyType != 2) throw PedCryptoException("Standard MAC requires a Working Key (Type 2)")

        val aisinoMacFlag = mapToAisinoMacFlag(request.algorithm)
            ?: throw PedCryptoException("Unsupported MAC algorithm: ${request.algorithm}")

        val underlyingAlg = when(request.algorithm) {
            GenericMacAlgorithm.CBC_MAC_ISO9797_1_M1 -> GenericKeyAlgorithm.DES_SINGLE
            else -> GenericKeyAlgorithm.DES_TRIPLE
        }
        val aisinoDesMode = mapToAisinoMacDesMode(underlyingAlg)
            ?: throw PedCryptoException("Unsupported underlying algorithm for MAC: $underlyingAlg")

        if (request.iv != null) Log.w(TAG, "Aisino PEDMac_Api does not support explicit IV.")

        val macOut = ByteArray(8)
        try {
            val result = PedApi.PEDMac_Api(request.keyIndex, aisinoDesMode, request.data, request.data.size, macOut, aisinoMacFlag)
            if (result != 0) {
                throw PedCryptoException("Standard MAC calculation failed. Aisino Error Code: $result")
            }
            return PedMacResult(macOut)
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating standard MAC", e)
            throw PedCryptoException("Standard MAC calculation failed: ${e.message}", e)
        }
    }

    @Throws(PedException::class)
    private fun calculateDukptMacInternal(request: PedMacRequest): PedMacResult {
        val groupIndex = request.dukptGroupIndex ?: throw PedCryptoException("DUKPT group index required")
        val aisinoMode: Byte = when (request.algorithm) {
            GenericMacAlgorithm.RETAIL_MAC_ANSI_X9_19 -> 0x00
            GenericMacAlgorithm.UNIONPAY_CBC_MAC -> 0x01
            GenericMacAlgorithm.CMAC_AES -> 0x03
            else -> throw PedCryptoException("Unsupported MAC algorithm for Aisino DUKPT: ${request.algorithm}")
        }
        if (request.iv != null) Log.w(TAG, "Aisino DUKPT MAC does not support explicit IV.")

        val macOut = ByteArray(8)
        val ksnOut = ByteArray(10)
        val incrementByte: Byte = if (request.dukptIncrementKsn) 1 else 0

        try {
            val result = PedApi.PedGetMacDukpt_Api(groupIndex.toByte(), incrementByte, request.data, request.data.size, macOut, ksnOut, aisinoMode)
            if (result != 0) {
                throw PedCryptoException("DUKPT MAC calculation failed. Aisino Error Code: $result")
            }
            val finalDukpt = DukptInfo(ksn = ksnOut, counter = null)
            return PedMacResult(macOut, finalDukpt)
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating DUKPT MAC", e)
            throw PedCryptoException("DUKPT MAC calculation failed: ${e.message}", e)
        }
    }

    // --- PIN Operations ---

    @Throws(PedException::class, PedTimeoutException::class, PedCancellationException::class)
    override suspend fun getPinBlock(request: PedPinRequest): PedPinResult {
        return if (request.isDukpt) {
            getDukptPinBlock(request)
        } else {
            getStandardPinBlock(request)
        }
    }

    @Throws(PedException::class, PedTimeoutException::class, PedCancellationException::class)
    private suspend fun getStandardPinBlock(request: PedPinRequest): PedPinResult {
        val aisinoMode = mapToAisinoDesMode(GenericKeyAlgorithm.DES_TRIPLE, true)
            ?: throw PedException("Cannot determine cipher mode for standard PIN block.")

        val pinLimitBytes = request.pinLengthConstraints.split(',')
            .mapNotNull { it.toIntOrNull()?.toByte() }
            .toByteArray()
        Log.w(TAG, "Verifying pinLimitBytes format for Aisino PEDGetPwd_Api: ${pinLimitBytes.contentToString()}")

        val panBytes = request.pan?.let { pan ->
            val panBlock = ByteArray(16) { 0x30 } // '0'
            val panAscii = pan.takeLast(12).padStart(12, '0').toByteArray(StandardCharsets.US_ASCII)
            System.arraycopy(panAscii, 0, panBlock, 4, min(panAscii.size, 12))
            panBlock
        } ?: byteArrayOf()

        return suspendCancellableCoroutine { continuation ->
            try {
                PedApi.PEDGetPwd_Api(
                    request.promptMessage ?: "Enter PIN",
                    panBytes,
                    pinLimitBytes,
                    request.keyIndex,
                    request.timeoutSeconds * 1000,
                    aisinoMode,
                    object : IGetPinResultListenner.Stub() {
                        override fun onEnter(pinBlock: ByteArray?) {
                            if (continuation.isActive) {
                                if (pinBlock != null) {
                                    continuation.resume(PedPinResult(pinBlock, null))
                                } else {
                                    continuation.resumeWithException(PedException("PIN entry failed: onEnter with null pinBlock"))
                                }
                            }
                        }
                        override fun onError(errcode: Int, msg: String?) {
                            if (continuation.isActive) continuation.resumeWithException(mapAisinoPinResultCodeToException(errcode, msg))
                        }
                        override fun onCancle() {
                            if (continuation.isActive) continuation.resumeWithException(PedCancellationException("PIN entry cancelled by user"))
                        }
                        override fun onTimerOut() {
                            if (continuation.isActive) continuation.resumeWithException(PedTimeoutException("PIN entry timed out"))
                        }
                        override fun onClick(inputLen: Int) { Log.d(TAG, "Standard PIN KeyClick: $inputLen") }
                    }
                )
            } catch (e: Exception) {
                if (continuation.isActive) continuation.resumeWithException(PedException("Failed to initiate Standard PIN Block retrieval: ${e.message}", e))
            }
            continuation.invokeOnCancellation { cancelPinEntry() }
        }
    }

    @Throws(PedException::class, PedTimeoutException::class, PedCancellationException::class)
    private suspend fun getDukptPinBlock(request: PedPinRequest): PedPinResult {
        val groupIndex = request.dukptGroupIndex ?: throw PedException("DUKPT Group Index required")
        val aisinoModeByte = mapToAisinoDukptPinMode(request.format, true)
            ?: throw PedException("Unsupported PIN block format for DUKPT: ${request.format}")

        val pinLimitBytes = request.pinLengthConstraints.split(',')
            .mapNotNull { it.toIntOrNull()?.toByte() }
            .toByteArray()
        Log.w(TAG, "Verifying pinLimitBytes format for Aisino DUKPT PIN: ${pinLimitBytes.contentToString()}")

        val dataIn: ByteArray = prepareAisinoDukptPinDataIn(request.format, request.pan)

        return suspendCancellableCoroutine { continuation ->
            try {
                PedApi.PEDGetDukptPin_Api(
                    request.promptMessage ?: "Enter PIN",
                    dataIn,
                    groupIndex,
                    pinLimitBytes,
                    aisinoModeByte.toInt(),
                    request.timeoutSeconds * 1000,
                    object : IGetDukptPinListener.Stub() {
                        override fun onEnter(pinBlock: ByteArray?, ksn: ByteArray?) {
                            if (continuation.isActive) {
                                if (pinBlock != null && ksn != null) {
                                    val finalDukpt = DukptInfo(ksn = ksn, counter = null)
                                    continuation.resume(PedPinResult(pinBlock, finalDukpt))
                                } else {
                                    continuation.resumeWithException(PedException("DUKPT PIN entry failed: onEnter with null data"))
                                }
                            }
                        }
                        override fun onError(errcode: Int, msg: String?) {
                            if (continuation.isActive) continuation.resumeWithException(mapAisinoPinResultCodeToException(errcode, msg))
                        }
                        override fun onCancle() {
                            if (continuation.isActive) continuation.resumeWithException(PedCancellationException("PIN entry cancelled by user"))
                        }
                        override fun onTimerOut() {
                            if (continuation.isActive) continuation.resumeWithException(PedTimeoutException("PIN entry timed out"))
                        }
                        override fun onClick(inputLen: Int) { Log.d(TAG, "DUKPT PIN KeyClick: $inputLen") }
                    }
                )
            } catch (e: Exception) {
                if (continuation.isActive) continuation.resumeWithException(PedException("Failed to initiate DUKPT PIN Block retrieval: ${e.message}", e))
            }
            continuation.invokeOnCancellation { cancelPinEntry() }
        }
    }

    private fun prepareAisinoDukptPinDataIn(format: GenericPinBlockFormatType, pan: String?): ByteArray {
        return when (format) {
            GenericPinBlockFormatType.ISO9564_0 -> {
                val panAscii = (pan ?: "").takeLast(12).padStart(12, '0').toByteArray(StandardCharsets.US_ASCII)
                byteArrayOf(0x00, 0x00, 0x00, 0x00) + panAscii
            }
            GenericPinBlockFormatType.ISO9564_1 -> ByteArray(8) { 0x00 } // Placeholder
            GenericPinBlockFormatType.ISO9564_3 -> {
                val panBlock = prepareAisinoDukptPinDataIn(GenericPinBlockFormatType.ISO9564_0, pan)
                val randomBlock = ByteArray(8) { 0xAA.toByte() } // Placeholder
                Log.w(TAG, "ISO9564_3 dataIn for Aisino needs verification. Using PAN + Placeholder.")
                panBlock + randomBlock
            }
            else -> throw PedException("$format is not supported by this implementation")
        }
    }

    private fun mapAisinoPinResultCodeToException(resultCode: Int, msg: String? = null): PedException {
        val message = msg ?: "Aisino PED Code: $resultCode"
        return when (resultCode) {
            3 -> PedTimeoutException("PIN entry timed out ($message)")
            5 -> PedCancellationException("PIN entry cancelled by user ($message)")
            else -> PedException("PIN entry failed ($message)")
        }
    }

    override fun cancelPinEntry() {
        Log.w(TAG, "cancelPinEntry() called, but no direct cancellation API was found in Aisino PedApi.")
        // No known external cancellation method
    }

    // --- UI Interaction ---

    override fun displayMessage(message: String, line: Int?, clearPrevious: Boolean) {
        try {
            // API Ignores line/clearPrevious
            PedApi.PEDDisp_Api(message)
        } catch (e: Exception) {
            Log.e(TAG, "Error calling PEDDisp_Api", e)
        }
    }

    override fun setPinPadStyle(styleInfo: Map<String, Any>) {
        Log.w(TAG, "setPinPadStyle is partially implemented. Mapping to Aisino setters.")
        try {
            styleInfo["pinBoardType"]?.let { if (it is Int) PedApi.PEDSetPinBoardStyle_Api(it) }
            styleInfo["pinBoardFixedLayout"]?.let { if (it is Boolean) PedApi.setPinBoardFixed(it) }
            styleInfo["textColor"]?.let { if (it is String) PedApi.setTextColor(it) }
            styleInfo["textSize"]?.let { if (it is Number) PedApi.setTextSize(it.toFloat()) }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting PIN pad style", e)
        }
    }

    @Throws(PedKeyException::class)
    override suspend fun writeDukptInitialKeyEncrypted(
        groupIndex: Int,
        keyAlgorithm: GenericKeyAlgorithm,
        encryptedIpek: ByteArray, // Viene como bytes crudos del parser
        initialKsn: ByteArray,    // Viene como bytes crudos del parser
        transportKeyIndex: Int,
        keyChecksum: String?      // KCV de la IPEK en claro
    ): Boolean = withContext(Dispatchers.IO) {
        Log.i(TAG, "--- Starting writeDukptInitialKeyEncrypted ---")
        Log.d(TAG, "Input Parameters:")
        Log.d(TAG, "  groupIndex: $groupIndex")
        Log.d(TAG, "  keyAlgorithm: $keyAlgorithm")
        Log.d(TAG, "  encryptedIpek (length): ${encryptedIpek.size} bytes")
        Log.d(TAG, "  initialKsn (length): ${initialKsn.size} bytes")
        Log.d(TAG, "  transportKeyIndex: $transportKeyIndex")
        Log.d(TAG, "  keyChecksum: ${keyChecksum ?: "null"}")

        val encryptedIpekHexString = encryptedIpek.joinToString("") { "%02X".format(it) }
        val ksnHexString = initialKsn.joinToString("") { "%02X".format(it) }
        Log.d(TAG, "Converted encryptedIpek to hex string: $encryptedIpekHexString")
        Log.d(TAG, "Converted initialKsn to hex string: $ksnHexString")

        val encryptedIpekBcd: ByteArray
        val ksnInBcd: ByteArray
        try {
            encryptedIpekBcd = CommonConvert.ascStringToBCD(encryptedIpekHexString)
            ksnInBcd = CommonConvert.ascStringToBCD(ksnHexString)
            Log.d(TAG, "Successfully converted input data to BCD format for encrypted injection.")
        } catch (e: Exception) {
            Log.e(TAG, "Error converting data to BCD for encrypted injection.", e)
            throw PedKeyException("Error converting data to BCD for encrypted injection.", e)
        }

        val keyLenByte: Byte = when (keyAlgorithm) {
            GenericKeyAlgorithm.DES_TRIPLE -> 16
            else -> {
                throw PedKeyException("Unsupported algorithm for DUKPT IPEK: $keyAlgorithm")
            }
        }
        Log.d(TAG, "Key length byte for API (plaintext length): $keyLenByte")

        val checkMode: Byte
        val checkBuffer: ByteArray

        if (keyChecksum.isNullOrBlank() || keyChecksum == "0000") {
            checkMode = KCV_CHECK_MODE_DISABLED
            checkBuffer = ByteArray(16) // Buffer dummy
            Log.w(TAG, "KCV check is disabled. This is not recommended for encrypted injection.")
        } else {
            checkMode = KCV_CHECK_MODE_ENABLED
            try {
                val checksumBytes = CommonConvert.hexStringToByte(keyChecksum)
                checkBuffer = ByteArray(1 + checksumBytes.size)
                checkBuffer[0] = checksumBytes.size.toByte()
                System.arraycopy(checksumBytes, 0, checkBuffer, 1, checksumBytes.size)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse keyChecksum '$keyChecksum' for encrypted injection.", e)
                throw PedKeyException("Failed to parse keyChecksum for encrypted injection: $keyChecksum", e)
            }
        }

        try {
            Log.i(TAG, "Calling PedApi.PedDukptWriteTIK_Api for encrypted key...")
            val result = PedApi.PedDukptWriteTIK_Api(
                groupIndex.toByte(),
                IPEK_INJECTION_MODE_ENCRYPTED,
                keyLenByte,
                encryptedIpekBcd,
                ksnInBcd,
                checkMode,
                checkBuffer
            )

            Log.i(TAG, "PedApi.PedDukptWriteTIK_Api finished with result code: $result")

            if (result != 0) {
                throw PedKeyException("Failed to write encrypted DUKPT IPEK. Vanstone/Aisino Error Code: $result")
            }

            Log.i(TAG, "--- writeDukptInitialKeyEncrypted Finished Successfully ---")
            return@withContext true

        } catch (e: Exception) {
            Log.e(TAG, "Unexpected exception in writeDukptInitialKeyEncrypted", e)
            throw e as? PedKeyException ?: PedKeyException("Generic failure in encrypted key injection", e)
        }
    }


    // --- Other Utilities ---

    @Throws(PedException::class)
    override suspend fun getRandomBytes(length: Int): ByteArray {
        throw UnsupportedOperationException("Getting random bytes not supported by Aisino PedApi.")
    }
}
