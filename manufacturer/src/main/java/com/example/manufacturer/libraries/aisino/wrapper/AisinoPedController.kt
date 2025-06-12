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
            GenericKeyType.WORKING_DATA_ENCRYPTION_KEY -> 2 // PEDKEYTYPE_WORKKET
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

    override suspend fun initializePed(application: Application): Boolean = withContext(Dispatchers.IO) {
        Log.i(TAG, ">>> INICIO: Aisino SDK Initialization")
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

                Log.d(TAG, "Initializing SystemApi.SystemInit_Api...")
                SystemApi.SystemInit_Api(0, pathBytes, context, object : ISdkStatue {
                    override fun sdkInitSuccessed() {
                        Log.i(TAG, "SystemApi.SystemInit_Api: Success. Initializing SdkApi...")
                        if (!continuation.isActive) return

                        // --- Step 2: Initialize SdkApi ---
                        SdkApi.getInstance().init(context, object : ISdkStatue {
                            override fun sdkInitSuccessed() {
                                Log.i(TAG, "SdkApi.getInstance().init(): Success. SDK initialization complete.")
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
        transportKeyType: GenericKeyType?
    ): Boolean = withContext(Dispatchers.IO) {
        // Log de entrada para registrar todos los parámetros recibidos
        Log.i(TAG, "--- Starting writeKey (for encrypted key) ---")
        Log.d(TAG, "Attempting to write an encrypted key with the following parameters:")
        Log.d(TAG, "-> Destination Key Index (keyIndex): $keyIndex")
        Log.d(TAG, "-> Destination Key Type (keyType): $keyType")
        Log.d(TAG, "-> Key Algorithm: $keyAlgorithm")
        Log.d(TAG, "-> Encrypted Key Data (Hex): ${keyData.keyBytes.joinToString("") { "%02X".format(it) }}")
        Log.d(TAG, "-> Encrypted Key Data Length: ${keyData.keyBytes.size}")
        Log.d(TAG, "-> Transport Key Index (transportKeyIndex): $transportKeyIndex")
        Log.d(TAG, "-> Transport Key Type (transportKeyType): $transportKeyType")


        // Validación de parámetros de la llave de transporte
        if (transportKeyIndex == null || transportKeyType == null) {
            val errorMsg = "Aisino requires transport key details for encrypted key loading. transportKeyIndex or transportKeyType is null."
            Log.e(TAG, errorMsg)
            throw PedKeyException(errorMsg)
        }

        // Mapeo del tipo de llave de destino
        val aisinoDKeyType = mapToAisinoKeyTypeInt(keyType)
            ?: throw PedKeyException("Unsupported destination key type for writeKey: $keyType.")
        Log.d(TAG, "Mapped destination key type '$keyType' to Aisino type '$aisinoDKeyType'.")

        // Advertencia si la llave de transporte no es una Master Key
        if (transportKeyType != GenericKeyType.MASTER_KEY) {
            Log.w(TAG, "Aisino PEDWriteKey_Api typically uses a Master Key (MASTER_KEY) as the transport key. Current transport key type is: $transportKeyType")
        }

        // Se asume el modo 0x83 para descifrado 3DES
        val aisinoMode = 0x83 // Corresponde a 3DES Decrypt
        Log.d(TAG, "Using hardcoded Aisino mode '0x83' for 3DES Decryption.")

        try {
            // Log justo antes de invocar la API del hardware con los parámetros finales
            Log.i(TAG, "Calling PedApi.PEDWriteKey_Api with SKeyIndex: $transportKeyIndex, DKeyIndex: $keyIndex, DKeyType: $aisinoDKeyType, Mode: $aisinoMode")

            val result = PedApi.PEDWriteKey_Api(
                transportKeyIndex,      // SKeyIndex (Source Key)
                keyIndex,               // DKeyIndex (Destination Key)
                keyData.keyBytes,       // DKey (Encrypted key data)
                aisinoDKeyType,         // DKeyType (Destination key type)
                aisinoMode,             // Mode for 3DES Decrypt
                ByteArray(0)       // KVRData (Not used in this scenario)
            )

            Log.i(TAG, "PedApi.PEDWriteKey_Api finished with result code: $result")

            // Verificación del resultado
            if (result != 0) {
                val errorMsg = "Failed to write key (encrypted). Aisino Error Code: $result"
                Log.e(TAG, errorMsg)
                throw PedKeyException(errorMsg)
            }

            // Log de éxito
            Log.d(TAG, "Successfully wrote encrypted key to index: $keyIndex using transport key from index: $transportKeyIndex")
            Log.i(TAG, "--- writeKey Finished Successfully ---")

            true
        } catch (e: Exception) {
            // Captura de cualquier otra excepción durante el proceso
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
        // Log de entrada para registrar los parámetros recibidos
        Log.i(TAG, "--- Starting writeKeyPlain ---")
        Log.d(TAG, "Attempting to write a plaintext key with the following parameters:")
        Log.d(TAG, "-> Key Index: $keyIndex")
        Log.d(TAG, "-> Key Type: $keyType")
        Log.d(TAG, "-> Key Algorithm: $keyAlgorithm")
        Log.d(TAG, "-> Key Bytes (Hex): ${keyBytes.joinToString("") { "%02X".format(it) }}")
        Log.d(TAG, "-> Key Bytes Length: ${keyBytes.size}")
        Log.d(TAG, "-> KCV Bytes (Hex): ${kcvBytes?.joinToString("") { "%02X".format(it) } ?: "Not Provided"}")

        // Validación de tipo de llave
        if (keyType != GenericKeyType.MASTER_KEY && keyType != GenericKeyType.TRANSPORT_KEY) {
            val errorMsg = "Plaintext loading via writeKeyPlain is only for MASTER_KEY or TRANSPORT_KEY on Aisino. Received: $keyType"
            Log.e(TAG, errorMsg)
            throw PedKeyException(errorMsg)
        }

        // Mapeo del algoritmo al modo específico del SDK de Aisino
        val aisinoMode = when (keyAlgorithm) {
            GenericKeyAlgorithm.DES_SINGLE -> 0x01
            GenericKeyAlgorithm.DES_TRIPLE -> 0x03
            else -> {
                val errorMsg = "Unsupported algorithm for plaintext Master/Transport Key: $keyAlgorithm"
                Log.e(TAG, errorMsg)
                throw PedKeyException(errorMsg)
            }
        }
        Log.d(TAG, "Mapped generic algorithm '$keyAlgorithm' to Aisino mode '$aisinoMode'.")

        try {
            // Log justo antes de invocar la API del hardware
            Log.i(TAG, "Calling PedApi.PEDWriteMKey_Api with index: $keyIndex, mode: $aisinoMode")

            val result = PedApi.PEDWriteMKey_Api(keyIndex, aisinoMode, keyBytes)

            Log.i(TAG, "PedApi.PEDWriteMKey_Api finished with result code: $result")

            // Verificación del resultado
            if (result != 0) {
                val errorMsg = "Failed to write key (plaintext). Aisino Error Code: $result"
                Log.e(TAG, errorMsg) // Registrar el error antes de lanzar la excepción
                throw PedKeyException(errorMsg)
            }

            // Log de éxito
            val kcvHex = kcvBytes?.joinToString("") { "%02X".format(it) } ?: "N/A"
            Log.d(TAG, "Successfully wrote key (plaintext). Index: $keyIndex, KCV (for reference): $kcvHex")
            Log.i(TAG, "--- writeKeyPlain Finished Successfully ---")

            true
        } catch (e: Exception) {
            // Captura de cualquier otra excepción durante el proceso
            Log.e(TAG, "An unexpected exception occurred during plaintext key writing", e)
            throw PedKeyException("Failed to write key (plaintext): ${e.message}", e)
        }
    }


    @Throws(PedException::class)
    override suspend fun deleteKey(keyIndex: Int, keyType: GenericKeyType): Boolean = withContext(Dispatchers.IO) {
        val aisinoKeyType = mapToAisinoKeyTypeInt(keyType)
            ?: throw PedKeyException("Unsupported key type for deleteKey: $keyType.")

        try {
            val success = PedApi.PedErase(aisinoKeyType, keyIndex)
            if (!success) {
                Log.w(TAG, "PedErase failed for KeyType: $aisinoKeyType, Index: $keyIndex.")
                return@withContext false
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting key", e)
            throw PedKeyException("Failed to delete key [$keyType/$keyIndex]: ${e.message}", e)
        }
    }

    @Throws(PedException::class)
    override suspend fun deleteAllKeys(): Boolean = withContext(Dispatchers.IO) {
        try {
            val success = PedApi.PedErase()
            if (!success) {
                throw PedKeyException("Failed to delete all keys (PedErase returned false).")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting all keys", e)
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
            // Algorithm cannot be determined from the device
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
        // Log de entrada para registrar todos los parámetros recibidos
        Log.i(TAG, "--- Starting writeDukptInitialKey (for plaintext IPEK) ---")
        Log.d(TAG, "Attempting to write a PLAINTEXT DUKPT IPEK with the following parameters:")
        Log.d(TAG, "-> DUKPT Group Index: $groupIndex")
        Log.d(TAG, "-> Key Algorithm: $keyAlgorithm")
        Log.d(TAG, "-> IPEK Bytes (Hex): ${keyBytes.joinToString("") { "%02X".format(it) }}")
        Log.d(TAG, "-> IPEK Bytes Length: ${keyBytes.size}")
        Log.d(TAG, "-> Initial KSN (Hex): ${initialKsn.joinToString("") { "%02X".format(it) }}")
        Log.d(TAG, "-> Key Checksum (KCV): ${keyChecksum ?: "Not Provided"}")

        // Cálculo de la longitud de la llave para la API
        val keyLenByte: Byte = when (keyAlgorithm) {
            GenericKeyAlgorithm.DES_TRIPLE -> if (keyBytes.size >= 16) 16 else 8
            GenericKeyAlgorithm.AES_128 -> 16
            GenericKeyAlgorithm.AES_192 -> 24
            GenericKeyAlgorithm.AES_256 -> 32
            else -> {
                val errorMsg = "Unsupported algorithm for Aisino DUKPT initial key: $keyAlgorithm"
                Log.e(TAG, errorMsg)
                throw PedKeyException(errorMsg)
            }
        }
        Log.d(TAG, "Calculated key length byte for API call: $keyLenByte")

        // --- LÓGICA DE VERIFICACIÓN ---
        val checkMode: Byte
        val checkBuffer: ByteArray

        if (keyChecksum.isNullOrBlank()) {
            checkMode = 0x00 // Sin verificación si no hay checksum
            checkBuffer = ByteArray(0)
            Log.w(TAG, "No keyChecksum provided. Will attempt injection without KCV verification (iCheckMode=0x00).")
        } else {
            checkMode = 0x01 // iCheckMode 0x01: usar KCV
            Log.d(TAG, "keyChecksum provided. Preparing buffer for KCV verification (iCheckMode=0x01).")
            val checksumBytes = CommonConvert.hexStringToByte(keyChecksum)

            // Formato: [longitud_del_kcv, kcv_bytes...]
            checkBuffer = ByteArray(1 + checksumBytes.size)
            checkBuffer[0] = checksumBytes.size.toByte()
            System.arraycopy(checksumBytes, 0, checkBuffer, 1, checksumBytes.size)
            Log.d(TAG, "Prepared checkBuffer for KCV '${keyChecksum}': ${checkBuffer.joinToString("") { "%02X".format(it) }}")
        }
        // --- FIN DE LA LÓGICA DE VERIFICACIÓN ---

        try {
            // Definir explícitamente el SrcKeyIdx para inyección en claro
            val sourceKeyIndex: Byte = 0

            // Log final antes de la llamada a la API con todos los parámetros finales
            Log.i(TAG, "Calling PedApi.PedDukptWriteTIK_Api with parameters:")
            Log.i(TAG, "--> GroupIdx: ${groupIndex.toByte()}")
            Log.i(TAG, "--> SrcKeyIdx: $sourceKeyIndex (0 = Plaintext IPEK)")
            Log.i(TAG, "--> KeyLen: $keyLenByte")
            Log.i(TAG, "--> KeyValueIn (Plaintext IPEK): ${keyBytes.joinToString("") { "%02X".format(it) }}")
            Log.i(TAG, "--> KsnIn: ${initialKsn.joinToString("") { "%02X".format(it) }}")
            Log.i(TAG, "--> iCheckMode: $checkMode")
            Log.i(TAG, "--> aucCheckBuf: ${checkBuffer.joinToString("") { "%02X".format(it) }}")

            val result = PedApi.PedDukptWriteTIK_Api(
                groupIndex.toByte(),
                sourceKeyIndex, // SrcKeyIdx = 0: KeyValueIn es la llave en claro (Plaintext).
                keyLenByte,
                keyBytes, // El IPEK en claro.
                initialKsn,
                checkMode, // Se pasa el modo de verificación.
                checkBuffer // Se pasa el buffer con el KCV.
            )

            Log.i(TAG, "PedApi.PedDukptWriteTIK_Api finished with result code: $result")

            if (result != 0) {
                val errorMsg = "Failed to write DUKPT initial key (plaintext). Aisino Error Code: $result"
                Log.e(TAG, errorMsg)
                throw PedKeyException(errorMsg)
            }

            Log.d(TAG, "Successfully wrote plaintext DUKPT IPEK to group index: $groupIndex")
            Log.i(TAG, "--- writeDukptInitialKey Finished Successfully ---")

            true // Éxito
        } catch (e: Exception) {
            Log.e(TAG, "An unexpected exception occurred while writing plaintext DUKPT initial key", e)
            if (e is PedKeyException) {
                throw e
            } else {
                throw PedKeyException("Failed to write DUKPT initial key: ${e.message}", e)
            }
        }
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
            // Counter is embedded in KSN
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
            throw PedCryptoException("Aisino only supports DES/TDES for DUKPT cipher via PedCalcDESDukpt_Api, requested: ${request.algorithm}")
        }

        val keyVarType: Byte = when(request.dukptKeyVariant) {
            GenericDukptKeyVariant.PIN -> 0x02 // PED PIN KEY ECB
            GenericDukptKeyVariant.MAC -> 0x00 // PED MAC KEY
            GenericDukptKeyVariant.DATA_ENCRYPT, GenericDukptKeyVariant.DATA_DECRYPT -> 0x01 // PED DES KEY
            null -> 0x01 // Default to DES KEY
        }

        val aisinoMode: Byte = when(request.mode) {
            GenericBlockCipherMode.ECB -> if(request.encrypt) 0x01 else 0x00
            GenericBlockCipherMode.CBC -> if(request.encrypt) 0x03 else 0x02
            null -> throw PedCryptoException("Block cipher mode (ECB/CBC) is required for DUKPT DES cipher.")
        }

        if (keyVarType == 0x02.toByte() && aisinoMode != 0x01.toByte()) {
            throw PedCryptoException("Aisino PedCalcDESDukpt_Api requires ECB encryption mode (0x01) when KeyVarType is PIN (0x02).")
        }

        val blockSize = 8
        val outputBufferSize = if (request.data.isNotEmpty()) ((request.data.size + blockSize - 1) / blockSize) * blockSize else 0
        val dataOut = ByteArray(outputBufferSize)
        val ksnOut = ByteArray(10)

        try {
            val result = PedApi.PedCalcDESDukpt_Api(groupIndex.toByte(), keyVarType, request.iv, request.data, aisinoMode, dataOut, ksnOut)
            if (result != 0) {
                throw PedCryptoException("DUKPT cipher operation failed. Aisino Error Code: $result")
            }
            val actualResultData = dataOut.copyOf(outputBufferSize)
            val finalDukpt = DukptInfo(ksn = ksnOut, counter = null)
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
            // Añadir 'return' aquí
            return PedMacResult(macOut)
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating standard MAC", e)
            throw PedCryptoException("Standard MAC calculation failed: ${e.message}", e)
        }
        // El 'return' vacío se elimina de aquí
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
            // Añadir 'return' aquí
            return PedMacResult(macOut, finalDukpt)
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating DUKPT MAC", e)
            throw PedCryptoException("DUKPT MAC calculation failed: ${e.message}", e)
        }
        // El 'return' vacío se elimina de aquí
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

    @Throws(PedException::class)
    override suspend fun writeDukptInitialKeyEncrypted(
        groupIndex: Int,
        keyAlgorithm: GenericKeyAlgorithm,
        encryptedIpek: ByteArray,
        initialKsn: ByteArray,
        transportKeyIndex: Int,
        keyChecksum: String?
    ): Boolean = withContext(Dispatchers.IO) {
        // Log de entrada para registrar todos los parámetros recibidos
        Log.i(TAG, "--- Starting writeDukptInitialKeyEncrypted ---")
        Log.d(TAG, "Attempting to write an ENCRYPTED DUKPT IPEK with the following parameters:")
        Log.d(TAG, "-> DUKPT Group Index: $groupIndex")
        Log.d(TAG, "-> Key Algorithm: $keyAlgorithm")
        Log.d(TAG, "-> Transport Key Index (KEK/TLK): $transportKeyIndex")
        Log.d(TAG, "-> Encrypted IPEK (Hex): ${encryptedIpek.joinToString("") { "%02X".format(it) }}")
        Log.d(TAG, "-> Encrypted IPEK Length: ${encryptedIpek.size}")
        Log.d(TAG, "-> Initial KSN (Hex): ${initialKsn.joinToString("") { "%02X".format(it) }}")
        Log.d(TAG, "-> Key Checksum (KCV): ${keyChecksum ?: "Not Provided"}")

        // Cálculo de la longitud de la llave para la API
        val keyLenByte: Byte = when (keyAlgorithm) {
            GenericKeyAlgorithm.DES_TRIPLE -> if(encryptedIpek.size >= 16) 16 else 8
            GenericKeyAlgorithm.AES_128 -> 16
            else -> {
                val errorMsg = "Unsupported algorithm for encrypted DUKPT IPEK: $keyAlgorithm"
                Log.e(TAG, errorMsg)
                throw PedKeyException(errorMsg)
            }
        }
        Log.d(TAG, "Calculated key length byte for API call: $keyLenByte")


        // Preparación del modo y buffer de verificación (Checksum/KCV)
        val checkMode: Byte
        val checkBuffer: ByteArray

        if (keyChecksum.isNullOrBlank()) {
            // Si no se proporciona checksum, no hacemos verificación.
            checkMode = 0x00
            checkBuffer = ByteArray(0)
            Log.w(TAG, "No keyChecksum provided. Will attempt injection without KCV verification (iCheckMode=0x00).")
        } else {
            // Si hay checksum, preparamos el buffer para la verificación.
            checkMode = 0x01 // iCheckMode 0x01: usar KCV para verificar
            Log.d(TAG, "keyChecksum provided. Preparing buffer for KCV verification (iCheckMode=0x01).")
            val checksumBytes = CommonConvert.hexStringToByte(keyChecksum)

            // El formato es: [longitud_del_kcv, kcv_byte_1, kcv_byte_2, ...]
            checkBuffer = ByteArray(1 + checksumBytes.size)
            checkBuffer[0] = checksumBytes.size.toByte() // Longitud del KCV
            System.arraycopy(checksumBytes, 0, checkBuffer, 1, checksumBytes.size)
            Log.d(TAG, "Prepared checkBuffer for KCV '${keyChecksum}': ${checkBuffer.joinToString("") { "%02X".format(it) }}")
        }

        try {
            // Definir explícitamente el SrcKeyIdx para inyección cifrada
            val sourceKeyIndex: Byte = 1

            // Log final antes de la llamada a la API con todos los parámetros finales
            Log.i(TAG, "Calling PedApi.PedDukptWriteTIK_Api with parameters:")
            Log.i(TAG, "--> GroupIdx: ${groupIndex.toByte()}")
            Log.i(TAG, "--> SrcKeyIdx: $sourceKeyIndex (1 = Encrypted IPEK)")
            Log.i(TAG, "--> KeyLen: $keyLenByte")
            Log.i(TAG, "--> KeyValueIn (Encrypted IPEK): ${encryptedIpek.joinToString("") { "%02X".format(it) }}")
            Log.i(TAG, "--> KsnIn: ${initialKsn.joinToString("") { "%02X".format(it) }}")
            Log.i(TAG, "--> iCheckMode: $checkMode")
            Log.i(TAG, "--> aucCheckBuf: ${checkBuffer.joinToString("") { "%02X".format(it) }}")

            val result = PedApi.PedDukptWriteTIK_Api(
                groupIndex.toByte(),
                sourceKeyIndex, // SrcKeyIdx = 1: KeyValueIn es la IPEK cifrada. El PED usará la TLK para descifrarla.
                keyLenByte,
                encryptedIpek,
                initialKsn,
                checkMode, // Pasar el modo de verificación correcto
                checkBuffer  // Pasar el buffer con el KCV y su longitud
            )

            Log.i(TAG, "PedApi.PedDukptWriteTIK_Api finished with result code: $result")

            if (result != 0) {
                // Si falla, registramos y lanzamos una excepción con el código de error del SDK
                val errorMsg = "Failed to write encrypted DUKPT IPEK. Aisino Error Code: $result"
                Log.e(TAG, errorMsg)
                throw PedKeyException(errorMsg)
            }

            Log.d(TAG, "Successfully wrote encrypted DUKPT IPEK to group index: $groupIndex")
            Log.i(TAG, "--- writeDukptInitialKeyEncrypted Finished Successfully ---")

            true // Éxito
        } catch (e: Exception) {
            Log.e(TAG, "An unexpected exception occurred while writing encrypted DUKPT IPEK", e)
            // Re-lanzar como PedKeyException para mantener la consistencia
            if (e is PedKeyException) {
                throw e
            } else {
                throw PedKeyException("Failed to write encrypted DUKPT IPEK: ${e.message}", e)
            }
        }
    }


    // --- Other Utilities ---

    @Throws(PedException::class)
    override suspend fun getRandomBytes(length: Int): ByteArray {
        throw UnsupportedOperationException("Getting random bytes not supported by Aisino PedApi.")
    }
}