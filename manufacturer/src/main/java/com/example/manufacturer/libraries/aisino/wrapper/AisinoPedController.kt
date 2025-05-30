package com.example.manufacturer.libraries.aisino.wrapper // Using your specified package

// Import generic types (Ensure these paths are correct)
import android.app.Application
import com.example.manufacturer.base.controllers.ped.*
import com.example.manufacturer.base.models.*
import com.example.manufacturer.base.models.KeyType as GenericKeyType
import com.example.manufacturer.base.models.KeyAlgorithm as GenericKeyAlgorithm
import com.example.manufacturer.base.models.BlockCipherMode as GenericBlockCipherMode
import com.example.manufacturer.base.models.MacAlgorithm as GenericMacAlgorithm
import com.example.manufacturer.base.models.PinBlockFormatType as GenericPinBlockFormatType
import com.example.manufacturer.base.models.DukptKeyVariant as GenericDukptKeyVariant // Corrected import

// Import Aisino specific types (from com.vanstone.trans.api)
import com.vanstone.trans.api.PedApi
// Import Aisino listener interfaces (Ensure these paths are correct in your project)
import com.vanstone.transex.ped.IGetPinResultListenner
import com.vanstone.transex.ped.IGetDukptPinListener

import android.content.Context
import android.os.IBinder // Needed for Stub implementation
import android.os.RemoteException
import android.util.Log
import com.vanstone.appsdk.client.ISdkStatue
import com.vanstone.appsdk.client.SdkApi
import com.vanstone.trans.api.SystemApi
import com.vanstone.utils.CommonConvert
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.nio.charset.StandardCharsets
import kotlin.math.min
import java.util.Arrays // For Arrays.fill

class AisinoPedController(private val context: Context) : IPedController {

    private val TAG = "AisinoPedController"
    private val initDeferred = CompletableDeferred<Unit>()


    // --- Mappings (Same as before - keep them here) ---

    // Map Generic KeyType to Aisino key type constant (1=Master, 2=Work)
    private fun mapToAisinoKeyTypeInt(generic: GenericKeyType): Int? {
        return when (generic) {
            GenericKeyType.MASTER_KEY -> 1 // PEDKEYTYPE_MASTKEY
            GenericKeyType.WORKING_PIN_KEY,
            GenericKeyType.WORKING_MAC_KEY,
            GenericKeyType.WORKING_DATA_ENCRYPTION_KEY -> 2 // PEDKEYTYPE_WORKKET
            else -> null // DUKPT/RSA/Transport handled differently
        }
    }

    // Map Generic KeyAlgorithm/Mode/Direction to Aisino PEDDes_Api Mode
    private fun mapToAisinoDesMode(alg: GenericKeyAlgorithm, encrypt: Boolean): Int? {
        return when (alg) {
            GenericKeyAlgorithm.DES_SINGLE -> if (encrypt) 0x01 else 0x81
            GenericKeyAlgorithm.DES_TRIPLE -> if (encrypt) 0x03 else 0x83
            GenericKeyAlgorithm.SM4 -> if (encrypt) 0x02 else 0x82
            else -> null // AES not in PEDDes_Api
        }
    }

    // Map Generic MacAlgorithm to Aisino PEDMac_Api flag
    private fun mapToAisinoMacFlag(generic: GenericMacAlgorithm): Int? {
        return when (generic) {
            GenericMacAlgorithm.RETAIL_MAC_ANSI_X9_19 -> 0x00
            GenericMacAlgorithm.UNIONPAY_CBC_MAC -> 0x01
            GenericMacAlgorithm.CBC_MAC_ISO9797_1_M1, GenericMacAlgorithm.CBC_MAC_ISO9797_1_M2 -> 0x00 // Defaulting to X9.19
            else -> null // CMAC_AES etc. not mapped
        }
    }

    // Map Generic KeyAlgorithm to Aisino PEDMac_Api mode (DES/3DES cipher mode)
    private fun mapToAisinoMacDesMode(alg: GenericKeyAlgorithm): Int? {
        return when (alg) {
            GenericKeyAlgorithm.DES_SINGLE -> 0x01
            GenericKeyAlgorithm.DES_TRIPLE -> 0x03
            else -> null // SM4/AES not used for PEDMac_Api mode
        }
    }

    // Map Generic PinBlockFormatType to Aisino DUKPT Mode (for PEDGetDukptPin_Api)
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
        // Add flag 0x20 if KSN should NOT increment
        return if (incrementKsn) baseMode else (baseMode + 0x20).toByte()
    }

    // Map Generic Algorithm/Mode/Direction to Aisino DUKPT Symmetric Cipher Mode (PedDukptCalcSym_Api)
    private fun mapToAisinoDukptSymMode(alg: GenericKeyAlgorithm, mode: GenericBlockCipherMode?, encrypt: Boolean): Byte? {
        val baseMode: Byte = when(alg) {
            GenericKeyAlgorithm.DES_SINGLE, GenericKeyAlgorithm.DES_TRIPLE -> when(mode) {
                GenericBlockCipherMode.ECB -> if(encrypt) 0x01 else 0x00
                GenericBlockCipherMode.CBC -> if(encrypt) 0x11 else 0x10
                null -> return null
            }
            GenericKeyAlgorithm.AES_128, GenericKeyAlgorithm.AES_192, GenericKeyAlgorithm.AES_256 -> when(mode) {
                GenericBlockCipherMode.ECB -> if(encrypt) 0x21 else 0x20
                GenericBlockCipherMode.CBC -> if(encrypt) 0x31 else 0x30
                null -> return null
            }
            GenericKeyAlgorithm.SM4 -> when(mode) {
                GenericBlockCipherMode.ECB -> if(encrypt) 0x41 else 0x40
                GenericBlockCipherMode.CBC -> if(encrypt) 0x51 else 0x50
                null -> return null
            }
            else -> return null
        }
        return baseMode
    }

    // Map Generic DukptKeyVariant to Aisino DUKPT KeyVarType (PedDukptCalcSym_Api, PedCalcDESDukpt_Api)
    private fun mapToAisinoDukptKeyVarType(variant: GenericDukptKeyVariant?): Byte? {
        return when(variant) {
            GenericDukptKeyVariant.PIN -> 0x01
            GenericDukptKeyVariant.DATA_ENCRYPT -> 0x02 // For Request/Both Ways
            GenericDukptKeyVariant.DATA_DECRYPT -> 0x03 // For Response
            GenericDukptKeyVariant.MAC -> 0x00 // For PEDCalcDESDukpt
            null -> {
                Log.w(TAG, "DUKPT Key Variant not specified, defaulting to DATA_ENCRYPT (0x02)")
                0x02
            }
        }
    }

    // --- Initialization ---
    init {
        Log.d(TAG, "AisinoPedController initialized. Assumes SystemApi.SystemInit_Api was called.")
    }

    // --- Interface Implementation ---

    override suspend fun initializePed(application: Application): Boolean {
        Log.d(TAG, "initializePed called. No specific PED initialization required for Aisino.")
        return try {

            Log.i(TAG, ">>> INICIO: AisinoSDKManager.initialize")
            val context: Context = application.applicationContext
            Log.d(TAG, "Contexto de la aplicación obtenido: $context")
            val curAppDir = application.filesDir.absolutePath
            Log.d(TAG, "Directorio de la aplicación obtenido: $curAppDir")

            Log.d(TAG, "Iniciando SystemApi.SystemInit_Api...")
            val pathBytes = CommonConvert.StringToBytes("$curAppDir/\u0000")
            if (pathBytes == null) {
                Log.e(TAG, "SystemApi.SystemInit_Api: Error al convertir la ruta a bytes.")
                initDeferred.completeExceptionally(IllegalStateException("Error al convertir la ruta del directorio a bytes para SystemInit_Api."))
                Log.e(TAG, "<<< ERROR FIN: AisinoSDKManager.initialize (Error en pathBytes)")
                return false
            }
            SystemApi.SystemInit_Api(0, pathBytes, context, object : ISdkStatue {
                override fun sdkInitSuccessed() {
                    Log.i(TAG, "SystemApi.SystemInit_Api: SDK inicializado exitosamente (Callback).")
                    initializeSdk(context)
                }

                override fun sdkInitFailed() {
                    Log.e(TAG, "SystemApi.SystemInit_Api: Falló la inicialización del SDK AISINO (Callback).")
//                if (!initDeferred.isCompleted) {
//                    initDeferred.completeExceptionally(
//                        IllegalStateException("Falló la inicialización del SDK AISINO (SystemApi).")
//                    )
//                }
                    Log.e(TAG, "<<< ERROR FIN: AisinoSDKManager.initialize (SystemApi falló)")
                }
            })
            PedApi.PEDGetLastError_Api() // Simple check if API is callable
            true
        } catch(e: Throwable) {
            Log.e(TAG, "Error checking Aisino PED API availability during initializePed", e)
            false
        }
    }

    private fun initializeSdk(context: Context) {
        Log.i(TAG, ">>> INICIO: AisinoSDKManager.initializeSdk")
        Log.d(TAG, "Llamando a SdkApi.getInstance().init()...")
        SdkApi.getInstance().init(context, object : ISdkStatue {
            override fun sdkInitSuccessed() {
                Log.i(TAG, "SdkApi.getInstance().init(): Inicialización exitosa (Callback).")
                try {
                    //sdkApi = SdkApi.getInstance()
                    Log.d(TAG, "Instancia de SdkApi obtenida y asignada.")
                    //initializeEMVKernel(context)
                    if (!initDeferred.isCompleted) {
                        initDeferred.complete(Unit)
                        Log.i(TAG, "initDeferred completado con éxito en initializeSdk.")
                    }
                } catch (e: NoSuchMethodError) {
                    Log.e(TAG, "Error crítico: NoSuchMethodError durante la asignación de SdkApi o la inicialización del SDK.", e)
                    if (!initDeferred.isCompleted) {
                        initDeferred.completeExceptionally(e)
                    }
                    Log.e(TAG, "<<< ERROR FIN: AisinoSDKManager.initializeSdk (NoSuchMethodError)")
                    return
                } catch (e: Exception) {
                    Log.e(TAG, "Error inesperado durante la inicialización de SdkApi.", e)
                    if (!initDeferred.isCompleted) {
                        initDeferred.completeExceptionally(e)
                    }
                    Log.e(TAG, "<<< ERROR FIN: AisinoSDKManager.initializeSdk (Exception)")
                    return
                }
                Log.i(TAG, "<<< FIN: AisinoSDKManager.initializeSdk (Éxito)")
            }

            override fun sdkInitFailed() {
                Log.e(TAG, "SdkApi.getInstance().init(): Falla en la inicialización del SDK (Callback).")
                if (!initDeferred.isCompleted) {
                    initDeferred.completeExceptionally(
                        IllegalStateException("Falla en la inicialización del SDK (SdkApi).")
                    )
                }
                Log.e(TAG, "<<< ERROR FIN: AisinoSDKManager.initializeSdk (SdkApi falló)")
            }
        })
        Log.d(TAG, "SdkApi.getInstance().init() llamado, esperando callback...")
    }

    override fun releasePed() {
        Log.d(TAG, "releasePed called. No specific PED release action required for Aisino.")
    }

    @Throws(PedException::class)
    override suspend fun getStatus(): PedStatusInfo {
        Log.w(TAG, "getStatus() not directly supported by Aisino PedApi. Returning default non-tampered status.")
        return PedStatusInfo(isTampered = false, batteryLevel = null, errorMessage = null) // Default value
    }

    @Throws(PedException::class)
    override suspend fun getConfig(): PedConfigInfo {
        var serialNum: String? = null
        try {
            val snBytes = ByteArray(40)
            val result = PedApi.PEDReadPinPadSn_Api(snBytes)
            if (result == 0) {
                // Try to parse SN format (2 byte ASCII hex length + SN)
                if (snBytes.size >= 2 && snBytes[0] >= '0'.code.toByte() && snBytes[0] <= '9'.code.toByte() &&
                    snBytes[1] >= '0'.code.toByte() && snBytes[1] <= '9'.code.toByte()) {
                    try {
                        val lenStr = String(snBytes, 0, 2, StandardCharsets.US_ASCII)
                        val snLen = lenStr.toIntOrNull()
                        if (snLen != null && snLen > 0 && snBytes.size >= 2 + snLen) {
                            serialNum = String(snBytes, 2, snLen, StandardCharsets.US_ASCII)
                        } else {
                            serialNum = String(snBytes, StandardCharsets.US_ASCII).trim().takeUnless { it.isEmpty() }
                            Log.w(TAG, "Invalid length format in PEDReadPinPadSn_Api result. Fallback parse: '$serialNum'")
                        }
                    } catch (e: Exception) {
                        serialNum = String(snBytes, StandardCharsets.US_ASCII).trim().takeUnless { it.isEmpty() }
                        Log.w(TAG, "Error parsing SN length. Fallback parse: '$serialNum'", e)
                    }
                } else {
                    serialNum = String(snBytes, StandardCharsets.US_ASCII).trim().takeUnless { it.isEmpty() }
                    Log.w(TAG, "Unexpected SN format in PEDReadPinPadSn_Api result (length bytes). Fallback parse: '$serialNum'")
                }
            } else {
                Log.w(TAG, "PEDReadPinPadSn_Api failed with code: $result")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading PED serial number", e)
            // Don't throw, just return partial info
        }

        Log.w(TAG, "getConfig() - Only SN is retrieved via PedApi. Other info might require SystemApi.")
        return PedConfigInfo(
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
        keyAlgorithm: GenericKeyAlgorithm, // Algoritmo de la nueva llave (no usado directamente por loadWorkKey/loadEncryptMainKey pero sí por setKeyAlgorithm)

        keyData: PedKeyData,
        transportKeyIndex: Int?,
        transportKeyType: GenericKeyType?
    ): Boolean {
        if (transportKeyIndex == null || transportKeyType == null) {
            throw PedKeyException("Aisino requires transport key details for encrypted key loading via PEDWriteKey_Api.")
        }
        val aisinoDKeyType = mapToAisinoKeyTypeInt(keyType)
            ?: throw PedKeyException("Unsupported key type for writeKey: $keyType. Must be MASTER or WORKING.")

        if (transportKeyType != GenericKeyType.MASTER_KEY) {
            Log.w(TAG,"Aisino PEDWriteKey_Api typically uses a Master Key as the source/transport key.")
        }

        val aisinoMode = 0x83 // Assume 3DES Decryption for key transport

        if (keyData.kcv != null) {
            Log.w(TAG, "KCV provided but ignored for Aisino writeKey implementation (complex KVRData mapping required).")
        }
        val kvrData: ByteArray? = null // No KCV support implemented

        try {
            val result = PedApi.PEDWriteKey_Api(
                transportKeyIndex,  // SKeyIndex
                keyIndex,           // DKeyIndex
                keyData.keyBytes,   // DKey (Encrypted key value)
                aisinoDKeyType,     // DKeyType (1=Master, 2=Work)
                aisinoMode,
                kvrData             // KVRData (null = no verification)
            )
            if (result != 0) {
                throw PedKeyException("Failed to write key (encrypted). Aisino Error Code: $result")
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error writing key (encrypted)", e)
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
    ): Boolean {
        if (keyType != GenericKeyType.MASTER_KEY) {
            throw PedKeyException("Plaintext loading via writeKeyPlain typically for MASTER_KEY on Aisino using PEDWriteMKey_Api.")
        }

        val aisinoMode = when (keyAlgorithm) {
            GenericKeyAlgorithm.DES_SINGLE -> 0x01
            GenericKeyAlgorithm.DES_TRIPLE -> 0x03
            else -> throw PedKeyException("Unsupported algorithm for plaintext Master Key loading: $keyAlgorithm")
        }

        try {
            val result = PedApi.PEDWriteMKey_Api(keyIndex, aisinoMode, keyBytes)
            if (result != 0) {
                throw PedKeyException("Failed to write key (plaintext). Aisino Error Code: $result")
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error writing key (plaintext)", e)
            throw PedKeyException("Failed to write key (plaintext): ${e.message}", e)
        }
    }

    @Throws(PedException::class)
    override suspend fun deleteKey(keyIndex: Int, keyType: GenericKeyType): Boolean {
        val aisinoKeyType = mapToAisinoKeyTypeInt(keyType)
            ?: throw PedKeyException("Unsupported key type for deleteKey: $keyType. Only MASTER or WORKING keys supported.")

        try {
            val success = PedApi.PedErase(aisinoKeyType, keyIndex)
            if (!success) {
                val lastError = try { PedApi.PEDGetLastError_Api() } catch (e: Throwable) { "N/A" }
                // Determine if it failed because key didn't exist vs other error
                Log.w(TAG, "PedErase failed for KeyType: $aisinoKeyType, Index: $keyIndex. Last Error: $lastError")
                return false // Indicate failure, could be key not found or other issue
                // throw PedKeyException("Failed to delete key (returned false). KeyType: $aisinoKeyType, Index: $keyIndex. Last Error: $lastError")
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting key", e)
            throw PedKeyException("Failed to delete key [$keyType/$keyIndex]: ${e.message}", e)
        }
    }

    @Throws(PedException::class)
    override suspend fun deleteAllKeys(): Boolean {
        try {
            val success = PedApi.PedErase()
            if (!success) {
                val lastError = try { PedApi.PEDGetLastError_Api() } catch (e: Throwable) { "N/A" }
                throw PedKeyException("Failed to delete all keys (returned false). Last Error: $lastError")
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting all keys", e)
            throw PedKeyException("Failed to delete all keys: ${e.message}", e)
        }
    }

    @Throws(PedException::class)
    override suspend fun isKeyPresent(keyIndex: Int, keyType: GenericKeyType): Boolean {
        val aisinoKeyType = mapToAisinoKeyTypeInt(keyType)
            ?: return false // Not a type Aisino can check with isKeyExist

        return try {
            PedApi.isKeyExist(aisinoKeyType, keyIndex) // Returns boolean
        } catch (e: Exception) {
            Log.e(TAG, "Error checking key presence", e)
            throw PedException("Failed to check key presence: ${e.message}", e)
        }
    }

    @Throws(PedException::class)
    override suspend fun getKeyInfo(keyIndex: Int, keyType: GenericKeyType): PedKeyInfo? {
        Log.w(TAG, "getKeyInfo not supported by Aisino PedApi. Checking presence only.")
        if (isKeyPresent(keyIndex, keyType)) {
            // Cannot determine algorithm
            return PedKeyInfo(index = keyIndex, type = keyType, algorithm = null)
        }
        return null
        // throw UnsupportedOperationException("Getting detailed key info not supported by Aisino PedApi.")
    }


    // --- DUKPT Management ---

    @Throws(PedException::class)
    override suspend fun writeDukptInitialKey(
        groupIndex: Int,
        keyAlgorithm: GenericKeyAlgorithm,
        keyBytes: ByteArray,
        initialKsn: ByteArray
    ): Boolean {
        val keyLenByte: Byte = when (keyAlgorithm) {
            // Aisino docs suggest 8/16 for TDES, let's infer from input keyBytes length
            GenericKeyAlgorithm.DES_TRIPLE -> if(keyBytes.size >= 16) 16 else 8
            GenericKeyAlgorithm.AES_128 -> 16
            GenericKeyAlgorithm.AES_192 -> 24
            GenericKeyAlgorithm.AES_256 -> 32
            else -> throw PedKeyException("Unsupported algorithm for Aisino DUKPT initial key: $keyAlgorithm")
        }

        val srcKeyIdx: Byte = 0 // 0 = Plaintext key loading
        val checkMode: Byte = 0x00 // 0 = No KCV check
        val checkBuf: ByteArray? = null // Parameter for KCV check

        try {
            val result = PedApi.PedDukptWriteTIK_Api(
                groupIndex.toByte(),
                srcKeyIdx,
                keyLenByte,
                keyBytes,
                initialKsn,
                checkMode,
                checkBuf
            )
            if (result != 0) {
                throw PedKeyException("Failed to write DUKPT initial key. Aisino Error Code: $result")
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error writing DUKPT initial key", e)
            throw PedKeyException("Failed to write DUKPT initial key: ${e.message}", e)
        }
    }

    @Throws(PedException::class)
    override suspend fun getDukptInfo(groupIndex: Int): DukptInfo? {
        try {
            val ksnOut = ByteArray(10) // Aisino KSN is 10 bytes
            val result = PedApi.PedGetDukptKSN_Api(groupIndex.toByte(), ksnOut)
            if (result != 0) {
                Log.w(TAG, "Failed to get DUKPT KSN for group $groupIndex. Aisino Error Code: $result")
                return null // Key/Group might not exist or other error
            }
            // Aisino doesn't return counter separately, it's embedded in KSN
            return DukptInfo(ksn = ksnOut, counter = null)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting DUKPT info", e)
            throw PedException("Failed to get DUKPT info for group $groupIndex: ${e.message}", e)
        }
    }

    @Throws(PedException::class)
    override suspend fun incrementDukptKsn(groupIndex: Int): Boolean {
        try {
            // Aisino supports manual increment
            val result = PedApi.PedDukptIncreaseKsn_Api(groupIndex.toByte())
            if (result != 0) {
                throw PedException("Failed to increment DUKPT KSN. Aisino Error Code: $result")
            }
            return true
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
    private suspend fun performCipher(request: PedCipherRequest): PedCipherResult {
        return when {
            request.isDukpt -> performDukptCipher(request)
            request.keyType == GenericKeyType.RSA_PRIVATE_KEY -> performRsaCipher(request)
            else -> performSymmetricCipher(request)
        }
    }

    @Throws(PedException::class)
    private suspend fun performSymmetricCipher(request: PedCipherRequest): PedCipherResult {
        val aisinoKeyType = mapToAisinoKeyTypeInt(request.keyType)
            ?: throw PedCryptoException("Unsupported key type for symmetric cipher: ${request.keyType}")
        val aisinoMode = mapToAisinoDesMode(request.algorithm, request.encrypt)
            ?: throw PedCryptoException("Unsupported algorithm for symmetric cipher with PEDDes_Api/PEDDesCBC_Api: ${request.algorithm}")

        // Estimate output buffer size (input size rounded up to block size)
        val blockSize = when(request.algorithm) {
            GenericKeyAlgorithm.DES_SINGLE, GenericKeyAlgorithm.DES_TRIPLE -> 8
            GenericKeyAlgorithm.SM4 -> 16
            else -> 8 // Should have been caught by mapToAisinoDesMode
        }
        val outputBufferSize = if (request.data.isNotEmpty()) ((request.data.size + blockSize - 1) / blockSize) * blockSize else 0
        val dataOut = ByteArray(outputBufferSize)

        try {
            val result: Int
            if (request.mode == GenericBlockCipherMode.CBC) {
                if (request.iv == null) throw PedCryptoException("IV is required for CBC mode")
                val ivLen = when(request.algorithm) {
                    GenericKeyAlgorithm.DES_SINGLE, GenericKeyAlgorithm.DES_TRIPLE -> 8
                    GenericKeyAlgorithm.SM4 -> 16
                    else -> throw PedCryptoException("Cannot determine IV length for CBC mode with algorithm ${request.algorithm}")
                }
                if (request.iv.size != ivLen) throw PedCryptoException("Invalid IV size (${request.iv.size}) for algorithm ${request.algorithm}, expected $ivLen")

                result = PedApi.PEDDesCBC_Api(
                    request.keyIndex,
                    aisinoMode,
                    aisinoKeyType,
                    request.iv,
                    ivLen,
                    request.data,
                    request.data.size,
                    dataOut
                )
            } else { // ECB
                if (request.iv != null) Log.w(TAG, "IV provided for ECB mode, ignoring.")
                result = PedApi.PEDDes_Api(
                    request.keyIndex,
                    aisinoMode,
                    aisinoKeyType,
                    request.data,
                    request.data.size,
                    dataOut
                )
            }

            if (result != 0) {
                throw PedCryptoException("Symmetric cipher operation failed. Aisino Error Code: $result")
            }
            // API doesn't return length, assume output is exactly outputBufferSize
            return PedCipherResult(dataOut.copyOf(outputBufferSize)) // Return potentially padded data
        } catch (e: Exception) {
            Log.e(TAG, "Error performing symmetric cipher", e)
            throw PedCryptoException("Symmetric cipher failed: ${e.message}", e)
        }
    }

    @Throws(PedException::class)
    private suspend fun performDukptCipher(request: PedCipherRequest): PedCipherResult {
        val groupIndex = request.dukptGroupIndex
            ?: throw PedCryptoException("DUKPT group index required")

        // --- START CORRECTION 1 ---
        // PedCalcDESDukpt_Api only supports DES/TDES based on its name and KeyVarType options
        if (request.algorithm != GenericKeyAlgorithm.DES_SINGLE &&
            request.algorithm != GenericKeyAlgorithm.DES_DOUBLE && // Assuming 2TDEA maps here
            request.algorithm != GenericKeyAlgorithm.DES_TRIPLE) { // Assuming 3TDEA maps here
            throw PedCryptoException("Aisino only supports DES/TDES for DUKPT cipher operations via PedCalcDESDukpt_Api, requested: ${request.algorithm}")
        }

        // Map KeyVariant. Note: 0x00=MAC, 0x01=DES, 0x02=PIN ECB. Mapping generic DATA variants is ambiguous.
        // Defaulting DATA_ENCRYPT/DECRYPT to use the DES KeyVarType (0x01).
        val keyVarType: Byte = when(request.dukptKeyVariant) {
            GenericDukptKeyVariant.PIN -> 0x02 // PED PIN KEY ECB
            GenericDukptKeyVariant.MAC -> 0x00 // PED MAC KEY
            GenericDukptKeyVariant.DATA_ENCRYPT, GenericDukptKeyVariant.DATA_DECRYPT -> 0x01 // PED DES KEY
            null -> 0x01 // Default to DES KEY if variant is null
        }

        // Map BlockCipherMode and encrypt flag to Aisino Mode byte
        val aisinoMode: Byte = when(request.mode) {
            GenericBlockCipherMode.ECB -> if(request.encrypt) 0x01 else 0x00
            GenericBlockCipherMode.CBC -> if(request.encrypt) 0x03 else 0x02
            null -> throw PedCryptoException("Block cipher mode (ECB/CBC) is required for DUKPT DES cipher.")
        }

        // Special case from docs: If KeyVarType is PIN (0x02), mode must be ECB encrypt (0x01)
        if (keyVarType == 0x02.toByte() && aisinoMode != 0x01.toByte()) {
            throw PedCryptoException("Aisino PedCalcDESDukpt_Api requires ECB encryption mode (0x01) when KeyVarType is PIN (0x02).")
        }

        // Prepare output buffers
        // Size calculation needs to account for block padding (DES = 8 bytes)
        val blockSize = 8
        val outputBufferSize = if (request.data.isNotEmpty()) ((request.data.size + blockSize - 1) / blockSize) * blockSize else 0
        val dataOut = ByteArray(outputBufferSize)
        val ksnOut = ByteArray(10) // For final KSN

        try {
            // Call the *existing* API: PedCalcDESDukpt_Api
            val result = PedApi.PedCalcDESDukpt_Api(
                groupIndex.toByte(),
                keyVarType,
                request.iv,    // byte[] KpucIV (Needed for CBC)
                request.data,  // byte[] DataIn
                aisinoMode,    // byte Mode (ECB/CBC Encrypt/Decrypt)
                dataOut,       // byte[] DataOut (Output buffer)
                ksnOut         // byte[] KsnOut
            )
            // --- END CORRECTION 1 ---

            if (result != 0) { // Assume 0 means success
                throw PedCryptoException("DUKPT cipher operation failed. Aisino Error Code: $result")
            }

            // Data is in dataOut buffer, potentially padded. Return the calculated size.
            val actualResultData = dataOut.copyOf(outputBufferSize)
            // KSN always increments implicitly with this API? Documentation unclear. Assume it does.
            val finalDukpt = DukptInfo(ksn = ksnOut, counter = null)

            return PedCipherResult(actualResultData, finalDukpt)

        } catch (e: Exception) {
            Log.e(TAG, "Error performing DUKPT cipher", e)
            throw PedCryptoException("DUKPT cipher failed: ${e.message}", e)
        } catch (err: UnsatisfiedLinkError) {
            Log.e(TAG, "UnsatisfiedLinkError calling PedCalcDESDukpt_Api. Check SDK/JNI signature.", err)
            throw PedException("DUKPT Cipher API call failed (Link Error)", err)
        }
    }

    @Throws(PedException::class)
    private suspend fun performRsaCipher(request: PedCipherRequest): PedCipherResult {
        if (request.keyType != GenericKeyType.RSA_PRIVATE_KEY) {
            throw PedCryptoException("RSA cipher request requires RSA_PRIVATE_KEY type for Aisino.")
        }
        // Aisino calcRSAEx_Api uses stored private key. Encrypt/Decrypt based on context?
        // Assuming this API is for raw RSA operation or signing based on typical PED usage.
        // The `request.encrypt` flag might not directly map if it's a sign operation.
        Log.w(TAG, "Aisino calcRSAEx_Api behavior (Encrypt vs Sign) with private key needs verification.")

        val keyIndex = request.keyIndex
        val dataIn = request.data
        // Determine output buffer size - depends on RSA key size at index. Assume 256 bytes (RSA-2048).
        val dataOut = ByteArray(256)
        val keyInfoOut = ByteArray(100) // Optional key info output

        try {
            val resultLen = PedApi.calcRSAEx_Api(keyIndex, dataIn.size, dataIn, dataOut, keyInfoOut)
            if (resultLen < 0) { // Returns length on success, <0 on error
                throw PedCryptoException("RSA operation failed. Aisino Error Code: $resultLen")
            }
            val actualResultData = dataOut.copyOf(resultLen)
            return PedCipherResult(actualResultData)
        } catch (e: Exception) {
            Log.e(TAG, "Error performing RSA operation", e)
            throw PedCryptoException("RSA operation failed: ${e.message}", e)
        }
    }

    @Throws(PedException::class)
    override suspend fun calculateMac(request: PedMacRequest): PedMacResult {
        if (request.isDukpt) {
            return calculateDukptMac(request)
        } else {
            return calculateStandardMac(request)
        }
    }

    @Throws(PedException::class)
    private suspend fun calculateStandardMac(request: PedMacRequest): PedMacResult {
        val aisinoKeyType = mapToAisinoKeyTypeInt(request.keyType)
            ?: throw PedCryptoException("Unsupported key type for MAC: ${request.keyType}")
        if (aisinoKeyType != 2) throw PedCryptoException("Standard MAC requires a Working Key (Type 2)")

        val aisinoMacFlag = mapToAisinoMacFlag(request.algorithm)
            ?: throw PedCryptoException("Unsupported MAC algorithm: ${request.algorithm}")
        val aisinoDesMode = mapToAisinoMacDesMode(request.algorithm.underlyingAlgorithm())
            ?: throw PedCryptoException("Unsupported underlying algorithm for MAC: ${request.algorithm.underlyingAlgorithm()}")

        if (request.iv != null) Log.w(TAG, "Aisino PEDMac_Api does not support explicit IV.")

        val macOut = ByteArray(8) // Standard MAC length

        try {
            // *** CORRECTED Length type from Short to Int ***
            if (request.data.size > Int.MAX_VALUE) throw PedCryptoException("Data too long for Aisino PEDMac_Api")

            val result = PedApi.PEDMac_Api(
                request.keyIndex,          // wkindex
                aisinoDesMode,             // mode (DES/3DES)
                request.data,              // buf
                request.data.size,         // Len (Int) <-- Correction
                macOut,                    // Out
                aisinoMacFlag              // flag (ANSI X9.19, UnionPay etc.)
            )
            if (result != 0) {
                throw PedCryptoException("Standard MAC calculation failed. Aisino Error Code: $result")
            }
            return PedMacResult(macOut) // No DUKPT info
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating standard MAC", e)
            throw PedCryptoException("Standard MAC calculation failed: ${e.message}", e)
        }
    }

    // Helper to get underlying symmetric algorithm for MAC
    private fun GenericMacAlgorithm.underlyingAlgorithm(): GenericKeyAlgorithm {
        return when(this) {
            GenericMacAlgorithm.CBC_MAC_ISO9797_1_M1 -> GenericKeyAlgorithm.DES_SINGLE
            GenericMacAlgorithm.CBC_MAC_ISO9797_1_M2,
            GenericMacAlgorithm.RETAIL_MAC_ANSI_X9_19,
            GenericMacAlgorithm.UNIONPAY_CBC_MAC -> GenericKeyAlgorithm.DES_TRIPLE
            else -> GenericKeyAlgorithm.DES_TRIPLE // Default assumption for other potential MACs
        }
    }

    @Throws(PedException::class)
    private suspend fun calculateDukptMac(request: PedMacRequest): PedMacResult {
        val groupIndex = request.dukptGroupIndex
            ?: throw PedCryptoException("DUKPT group index required")
        // Aisino PedGetMacDukpt_Api Mode parameter maps to specific MAC algorithms
        val aisinoMode: Byte = when (request.algorithm) {
            GenericMacAlgorithm.RETAIL_MAC_ANSI_X9_19 -> 0x00 // ANSI X9.19 equivalent?
            GenericMacAlgorithm.UNIONPAY_CBC_MAC -> 0x01 // UnionPay CBC?
            GenericMacAlgorithm.CBC_MAC_ISO9797_1_M1 -> 0x00 // Guess
            GenericMacAlgorithm.CBC_MAC_ISO9797_1_M2 -> 0x01 // Guess
            GenericMacAlgorithm.CMAC_AES -> 0x03 // Assume 03 is CMAC (needs verification)
            else -> throw PedCryptoException("Unsupported MAC algorithm for Aisino DUKPT: ${request.algorithm}")
        }

        if (request.iv != null) Log.w(TAG, "Aisino DUKPT MAC does not support explicit IV.")

        val macOut = ByteArray(8)
        val ksnOut = ByteArray(10)
        val incrementByte: Byte = if (request.dukptIncrementKsn) 1 else 0

        try {
            val result = PedApi.PedGetMacDukpt_Api(
                groupIndex.toByte(),
                incrementByte,
                request.data,
                request.data.size,
                macOut,
                ksnOut,
                aisinoMode
            )
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
        if (request.isDukpt) {
            return getDukptPinBlock(request)
        } else {
            return getStandardPinBlock(request)
        }
    }

    @Throws(PedException::class, PedTimeoutException::class, PedCancellationException::class)
    private suspend fun getStandardPinBlock(request: PedPinRequest): PedPinResult {
        val cipherAlg = GenericKeyAlgorithm.DES_TRIPLE // Assume 3DES for standard PIN keys
        val aisinoMode = mapToAisinoDesMode(cipherAlg, true)
            ?: throw PedException("Cannot determine cipher mode for standard PIN block (assuming $cipherAlg)")

        val pinLimitBytes = request.pinLengthConstraints.split(',')
            .mapNotNull { it.toIntOrNull()?.toByte() }
            .toByteArray() // Needs verification for Aisino format
        Log.w(TAG, "pinLimitBytes format for Aisino PEDGetPwd_Api needs verification. Input: '${request.pinLengthConstraints}', Resulting bytes: ${pinLimitBytes.contentToString()}")

        val panBytes = request.pan?.let { pan ->
            val panBlock = ByteArray(16) { 0x30 }
            val panAscii = pan.takeLast(12).padStart(12, '0').toByteArray(StandardCharsets.US_ASCII)
            // Prepare PAN block like ISO Format 0 (0000 + 12 right digits)
            System.arraycopy(panAscii, 0, panBlock, 4, min(panAscii.size, 12))
            panBlock
        } ?: byteArrayOf()

        return suspendCancellableCoroutine { continuation ->
            Log.d(TAG, "Requesting Standard PIN block: KI=${request.keyIndex}, Format=${request.format}, Len='${request.pinLengthConstraints}', PAN='${request.pan}'")
            try {
                PedApi.PEDGetPwd_Api(
                    request.promptMessage ?: "Enter PIN",
                    panBytes, // Formatted PAN or empty
                    pinLimitBytes, // Verify format!
                    request.keyIndex,
                    request.timeoutSeconds * 1000, // ms
                    aisinoMode,
                    // *** CORRECTED: Extend Stub ***
                    object : IGetPinResultListenner.Stub() {
                        override fun onEnter(pinBlock: ByteArray?) {
                            Log.d(TAG, "Standard PEDGetPwd_Api Result: onEnter, PinBlock=${pinBlock?.size ?: "null"} bytes")
                            if (continuation.isActive) {
                                if (pinBlock != null) { // Assume success if onEnter is called with non-null block
                                    continuation.resume(PedPinResult(pinBlock, null))
                                } else {
                                    continuation.resumeWithException(PedException("PIN entry failed: onEnter called with null pinBlock"))
                                }
                            }
                        }
                        override fun onError(errcode: Int, msg: String?) {
                            Log.d(TAG, "Standard PEDGetPwd_Api Result: onError Code=$errcode, Msg=$msg")
                            if (continuation.isActive) {
                                continuation.resumeWithException(mapAisinoPinResultCodeToException(errcode, msg))
                            }
                        }
                        override fun onCancle() {
                            Log.d(TAG, "Standard PEDGetPwd_Api Result: onCancel")
                            if (continuation.isActive) {
                                continuation.resumeWithException(PedCancellationException("PIN entry cancelled by user (onCancel callback)"))
                            }
                        }
                        override fun onTimerOut() {
                            Log.d(TAG, "Standard PEDGetPwd_Api Result: onTimerOut")
                            if (continuation.isActive) {
                                continuation.resumeWithException(PedTimeoutException("PIN entry timed out (onTimerOut callback)"))
                            }
                        }
                        override fun onClick(inputLen: Int) {
                            // Optional: Handle feedback if needed
                            Log.d(TAG, "Standard PEDGetPwd_Api KeyClick: $inputLen")
                        }
                        // asBinder() is handled by Stub
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Exception during Standard PEDGetPwd_Api call setup", e)
                if (continuation.isActive) {
                    continuation.resumeWithException(PedException("Failed to initiate Standard PIN Block retrieval: ${e.message}", e))
                }
            }
            continuation.invokeOnCancellation {
                Log.w(TAG, "Standard PIN entry coroutine cancelled. Aisino cancellation mechanism unknown.")
                cancelPinEntry()
            }
        }
    }

    @Throws(PedException::class, PedTimeoutException::class, PedCancellationException::class)
    private suspend fun getDukptPinBlock(request: PedPinRequest): PedPinResult {
        val groupIndex = request.dukptGroupIndex ?: throw PedException("DUKPT Group Index required")
        // DUKPT PIN KSN should typically increment
        val aisinoModeByte = mapToAisinoDukptPinMode(request.format, true)
            ?: throw PedException("Unsupported PIN block format for DUKPT: ${request.format}")

        val pinLimitBytes = request.pinLengthConstraints.split(',')
            .mapNotNull { it.toIntOrNull()?.toByte() }
            .toByteArray() // Verify format
        Log.w(TAG, "pinLimitBytes format for Aisino PEDGetDukptPin_Api needs verification. Input: '${request.pinLengthConstraints}', Resulting bytes: ${pinLimitBytes.contentToString()}")

        val dataIn: ByteArray = prepareAisinoDukptPinDataIn(request.format, request.pan)

        return suspendCancellableCoroutine { continuation ->
            Log.d(TAG, "Requesting DUKPT PIN block: Group=${groupIndex}, Format=${request.format}, Len='${request.pinLengthConstraints}', PAN='${request.pan}'")
            try {
                PedApi.PEDGetDukptPin_Api(
                    request.promptMessage ?: "Enter PIN",
                    dataIn,
                    groupIndex,
                    pinLimitBytes, // Verify format!
                    aisinoModeByte.toInt(), // API takes int mode
                    request.timeoutSeconds * 1000, // ms
                    // --- START CORRECTION 2 ---
                    object : IGetDukptPinListener.Stub() {
                        // *** Implement methods from IGetDukptPinListener ***

                        // Assuming onEnter provides pinBlock and ksn on success
                        // Check the actual signature of IGetDukptPinListener.onEnter if available
                        // The signature below is an *educated guess* based on required data.
                        // If the decompiled Stub had a different onEnter signature, use that.
                        // For example, if it only provides one byte array, you might need
                        // separate calls to get KSN after success.
                        // ---> ASSUMED SIGNATURE: override fun onEnter(pinBlock: ByteArray?, ksn: ByteArray?)
                        // If the decompiled code showed a different signature like `onEnter(p0: ByteArray?, p1: ByteArray?)`
                        // you'd use that and assign meanings based on context (pinBlock=p0, ksn=p1).
                        override fun onEnter(pinBlock: ByteArray?, ksn: ByteArray?) {
                            Log.d(TAG, "DUKPT PEDGetDukptPin_Api Result: onEnter, PinBlock=${pinBlock?.size ?: "null"} bytes, KSN=${ksn?.size ?: "null"} bytes")
                            if (continuation.isActive) {
                                if (pinBlock != null && ksn != null) { // Check Aisino success code (0?) implicitly via non-null results
                                    val finalDukpt = DukptInfo(ksn = ksn, counter = null)
                                    continuation.resume(PedPinResult(pinBlock, finalDukpt))
                                } else {
                                    continuation.resumeWithException(PedException("DUKPT PIN entry failed: onEnter called with null data (pinBlock=${pinBlock?.size}, ksn=${ksn?.size})"))
                                }
                            }
                        }

                        override fun onError(errcode: Int, msg: String?) {
                            Log.d(TAG, "DUKPT PEDGetDukptPin_Api Result: onError Code=$errcode, Msg=$msg")
                            if (continuation.isActive) {
                                continuation.resumeWithException(mapAisinoPinResultCodeToException(errcode, msg))
                            }
                        }

                        override fun onCancle() {
                            Log.d(TAG, "DUKPT PEDGetDukptPin_Api Result: onCancel")
                            if (continuation.isActive) {
                                continuation.resumeWithException(PedCancellationException("PIN entry cancelled by user (onCancel callback)"))
                            }
                        }

                        override fun onTimerOut() {
                            Log.d(TAG, "DUKPT PEDGetDukptPin_Api Result: onTimerOut")
                            if (continuation.isActive) {
                                continuation.resumeWithException(PedTimeoutException("PIN entry timed out (onTimerOut callback)"))
                            }
                        }

                        override fun onClick(inputLen: Int) {
                            // Optional: Handle feedback if needed
                            Log.d(TAG, "DUKPT PEDGetDukptPin_Api KeyClick: $inputLen")
                        }

                        // asBinder() is implemented by Stub

                        // Remove the unused/auto-generated TODO methods if the interface doesn't have them
                        /*
                        override fun onListenResult(resultCode: Int, pinBlock: ByteArray?, ksn: ByteArray?) {
                             // This method doesn't exist in the Stub, removed.
                        }
                        override fun onEnter(p0: ByteArray?, p1: ByteArray?) { TODO("Use correct signature or remove if duplicate") }
                        override fun onClick(p0: Int) { TODO("Not yet implemented") }
                        override fun onCancle() { TODO("Not yet implemented") }
                        override fun onError(p0: Int, p1: String?) { TODO("Not yet implemented") }
                        override fun onTimerOut() { TODO("Not yet implemented") }
                        */
                    }
                    // --- END CORRECTION 2 ---
                )
            } catch (e: Exception) {
                Log.e(TAG, "Exception during DUKPT PEDGetDukptPin_Api call setup", e)
                if (continuation.isActive) {
                    continuation.resumeWithException(PedException("Failed to initiate DUKPT PIN Block retrieval: ${e.message}", e))
                }
            }
            continuation.invokeOnCancellation {
                Log.w(TAG, "DUKPT PIN entry coroutine cancelled. Aisino cancellation mechanism unknown.")
                cancelPinEntry()
            }
        }
    }

    // Helper to prepare the 'dataIn' buffer for PEDGetDukptPin_Api (Keep from previous response, verify logic)
    private fun prepareAisinoDukptPinDataIn(format: GenericPinBlockFormatType, pan: String?): ByteArray {
        return when (format) {
            GenericPinBlockFormatType.ISO9564_0 -> {
                val panStr = pan ?: ""
                // Mode 0: 16 bytes PAN-derived data (0000 + 12 right digits)
                val panAscii = panStr.takeLast(12).padStart(12, '0').toByteArray(StandardCharsets.US_ASCII)
                byteArrayOf(0x00, 0x00, 0x00, 0x00) + panAscii
            }
            GenericPinBlockFormatType.ISO9564_1 -> {
                // Mode 1: 8 bytes (random/timestamp/etc.)
                ByteArray(8) { 0x00 } // Placeholder
            }
            GenericPinBlockFormatType.ISO9564_3 -> {
                // Mode 2: 16 bytes PAN block + 8 bytes (nibbles 0xA-F)
                val panBlock = prepareAisinoDukptPinDataIn(GenericPinBlockFormatType.ISO9564_0, pan)
                val randomBlock = ByteArray(8) { 0xAA.toByte() } // Placeholder
                Log.w(TAG, "ISO9564_3 dataIn structure for Aisino DUKPT PIN needs clarification/verification. Using PAN + Placeholder.")
                panBlock + randomBlock // Concatenate (Total 24 bytes - Verify API)
            }
            GenericPinBlockFormatType.ISO9564_4 -> throw PedException("ISO9564_4 is not supported by Aisino PEDGetDukptPin_Api")
        }
    }

    // Helper to map Aisino return codes from PIN listeners to exceptions (Keep from previous response)
    private fun mapAisinoPinResultCodeToException(resultCode: Int, msg: String? = null): PedException {
        // Need documentation for Aisino PED return codes
        val message = msg ?: "Aisino PED Code: $resultCode"
        return when (resultCode) {
            0 -> PedException("PIN callback reported success (0) but data was null or invalid. Msg: $message")
            3 -> PedTimeoutException("PIN entry timed out ($message)")
            5 -> PedCancellationException("PIN entry cancelled by user ($message)")
            // Add other mappings based on documentation
            else -> PedException("PIN entry failed ($message)")
        }
    }


    override fun cancelPinEntry() {
        Log.w(TAG, "cancelPinEntry() called, but no direct cancellation API found in Aisino PedApi documentation.")
        // No known way to externally cancel PEDGetPwd_Api or PEDGetDukptPin_Api
    }

    // --- UI Interaction ---

    override fun displayMessage(message: String, line: Int?, clearPrevious: Boolean) {
        try {
            // PEDDisp_Api(String) - Ignores line/clearPrevious
            PedApi.PEDDisp_Api(message)
        } catch (e: Exception) {
            Log.e(TAG, "Error calling PEDDisp_Api", e)
        }
    }

    override fun setPinPadStyle(styleInfo: Map<String, Any>) {
        Log.w(TAG, "setPinPadStyle: Mapping generic Map to individual Aisino setters is complex and partially implemented.")
        try {
            styleInfo["pinBoardType"]?.let { if (it is Int) PedApi.PEDSetPinBoardStyle_Api(it) } // 1=half, 2=full_en, 3=full_cn
            styleInfo["pinBoardFixedLayout"]?.let { if (it is Boolean) PedApi.setPinBoardFixed(it) } // true=fixed, false=random
            // Map colors (assuming hex strings like "#RRGGBB")
            styleInfo["amountColor"]?.let { if (it is String) PedApi.setAmountColor(it) }
            styleInfo["textColor"]?.let { if (it is String) PedApi.setTextColor(it) }
            styleInfo["numColor"]?.let { if (it is String) PedApi.setNumColor(it) }
            styleInfo["bottomTextColor"]?.let { if (it is String) PedApi.setBottomTextColor(it) }
            // Map sizes (API takes float)
            styleInfo["amountSize"]?.let { if (it is Number) PedApi.setAmountSize(it.toFloat()) }
            styleInfo["textSize"]?.let { if (it is Number) PedApi.setTextSize(it.toFloat()) }
            styleInfo["numSize"]?.let { if (it is Number) PedApi.setNumSize(it.toFloat()) }
            styleInfo["bottomTextSize"]?.let { if (it is Number) PedApi.setBottomTextSize(it.toFloat()) }
            // Map fonts (API takes String font name/path?) - Requires knowing valid font names/paths
            // styleInfo["amountFont"]?.let { if (it is String) PedApi.setAmountFont(it) }
            // ... etc.
        } catch (e: Exception) {
            Log.e(TAG, "Error setting PIN pad style via individual Aisino setters", e)
        }
    }

    // --- Other Utilities ---

    @Throws(PedException::class)
    override suspend fun getRandomBytes(length: Int): ByteArray {
        Log.w(TAG, "getRandomBytes not found in Aisino PedApi documentation.")
        throw UnsupportedOperationException("Getting random bytes not supported by Aisino PedApi.")
    }

}