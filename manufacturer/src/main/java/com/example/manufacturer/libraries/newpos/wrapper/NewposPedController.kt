package com.example.manufacturer.libraries.newpos.wrapper

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import com.example.manufacturer.base.controllers.ped.*
import com.example.manufacturer.base.models.*
import com.example.manufacturer.base.models.KeyAlgorithm as GenericKeyAlgorithm
import com.example.manufacturer.base.models.KeyType as GenericKeyType
import com.example.manufacturer.base.models.BlockCipherMode as GenericBlockCipherMode
import com.example.manufacturer.base.models.MacAlgorithm as GenericMacAlgorithm
import com.example.manufacturer.base.models.PinBlockFormatType as GenericPinBlockFormatType
import com.pos.device.ped.*
import com.pos.device.ped.KeySystem as NewposKeySystem
import com.pos.device.ped.KeyType as NewposKeyType
import com.pos.device.ped.MACMode as NewposMACMode
import com.pos.device.ped.PinBlockFormat as NewposPinBlockFormat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * IPedController implementation for NewPOS devices.
 *
 * Uses the NewPOS SDK `com.pos.device.ped.Ped` to interact with the hardware.
 *
 * @param context The application context, needed to get the PED instance.
 */
class NewposPedController(private val context: Context) : IPedController {

    private val TAG = "NewposPedController"
    private val pedInstance: Ped


    // --- Mappings ---

    private fun mapToNewposKeySystem(generic: GenericKeyType, algorithm: GenericKeyAlgorithm? = null): NewposKeySystem {
        return when (generic) {
            GenericKeyType.MASTER_KEY, GenericKeyType.TRANSPORT_KEY -> {
                when (algorithm) {
                    GenericKeyAlgorithm.SM4 -> NewposKeySystem.MS_SM4
                    GenericKeyAlgorithm.AES_128, GenericKeyAlgorithm.AES_192, GenericKeyAlgorithm.AES_256 -> NewposKeySystem.MS_AES
                    else -> NewposKeySystem.MS_DES // Default for DES/TDES
                }
            }
            GenericKeyType.WORKING_PIN_KEY,
            GenericKeyType.WORKING_MAC_KEY,
            GenericKeyType.WORKING_DATA_ENCRYPTION_KEY -> {
                when (algorithm) {
                    GenericKeyAlgorithm.SM4 -> NewposKeySystem.FIXED_SM4
                    GenericKeyAlgorithm.AES_128, GenericKeyAlgorithm.AES_192, GenericKeyAlgorithm.AES_256 -> NewposKeySystem.FIXED_AES
                    else -> NewposKeySystem.FIXED_DES // Default for DES/TDES
                }
            }
            GenericKeyType.DUKPT_INITIAL_KEY, GenericKeyType.DUKPT_WORKING_KEY -> {
                when (algorithm) {
                    GenericKeyAlgorithm.AES_128, GenericKeyAlgorithm.AES_192, GenericKeyAlgorithm.AES_256 -> NewposKeySystem.DUKPT_AES
                    else -> NewposKeySystem.DUKPT_DES // Default for DES/TDES
                }
            }
            GenericKeyType.RSA_PUBLIC_KEY, GenericKeyType.RSA_PRIVATE_KEY -> NewposKeySystem.TMS_RSA
            else -> {
                Log.w(TAG, "Unsupported generic KeyType to NewposKeySystem mapping: $generic. Defaulting to MS_DES.")
                NewposKeySystem.MS_DES
            }
        }
    }

    private fun mapToNewposKeyType(generic: GenericKeyType): NewposKeyType? {
        return when (generic) {
            GenericKeyType.MASTER_KEY -> NewposKeyType.KEY_TYPE_MASTK
            GenericKeyType.WORKING_PIN_KEY -> NewposKeyType.KEY_TYPE_PINK
            GenericKeyType.WORKING_MAC_KEY -> NewposKeyType.KEY_TYPE_MACK
            GenericKeyType.WORKING_DATA_ENCRYPTION_KEY -> NewposKeyType.KEY_TYPE_EAK
            GenericKeyType.DUKPT_INITIAL_KEY, GenericKeyType.DUKPT_WORKING_KEY -> NewposKeyType.KEY_TYPE_DUKPTK
            GenericKeyType.RSA_PRIVATE_KEY -> NewposKeyType.KEY_TYPE_RSA_PRIK
            GenericKeyType.TRANSPORT_KEY -> NewposKeyType.KEY_TYPE_TMSK
            // CORRECTED: There is no direct type for RSA public keys in the NewPOS SDK.
            // Returning null to indicate no direct storage type.
            GenericKeyType.RSA_PUBLIC_KEY -> null
        }
    }
    private fun mapToNewposPinFormat(generic: GenericPinBlockFormatType): NewposPinBlockFormat? {
        return when (generic) {
            GenericPinBlockFormatType.ISO9564_0 -> NewposPinBlockFormat.PIN_BLOCK_FORMAT_0
            GenericPinBlockFormatType.ISO9564_1 -> NewposPinBlockFormat.PIN_BLOCK_FORMAT_1
            GenericPinBlockFormatType.ISO9564_3 -> NewposPinBlockFormat.PIN_BLOCK_FORMAT_3
            // NewPOS API does not have a direct mapping for ISO9564_4.
            // This is often linked to DUKPT AES, which is handled via the KeySystem in NewPOS.
            else -> {
                Log.w(TAG, "Unsupported generic PinBlockFormatType mapping to NewposPinBlockFormat: $generic")
                null
            }
        }
    }

    private fun mapToNewposMacMode(generic: GenericMacAlgorithm): NewposMACMode? {
        return when (generic) {
            GenericMacAlgorithm.RETAIL_MAC_ANSI_X9_19 -> NewposMACMode.MAC_MODE_1
            GenericMacAlgorithm.UNIONPAY_CBC_MAC -> NewposMACMode.MAC_MODE_CUP
            GenericMacAlgorithm.CBC_MAC_ISO9797_1_M1, GenericMacAlgorithm.CBC_MAC_ISO9797_1_M2 -> NewposMACMode.MAC_MODE_EMV
            GenericMacAlgorithm.CMAC_AES -> NewposMACMode.MAC_MODE_2
            else -> {
                Log.w(TAG, "Unsupported generic MacAlgorithm mapping to NewposMACMode: $generic")
                null
            }
        }
    }

    private fun mapToNewposDukptType(generic: GenericKeyAlgorithm): DukptType? {
        return when (generic) {
            GenericKeyAlgorithm.DES_TRIPLE, GenericKeyAlgorithm.DES_DOUBLE -> DukptType.DUKPT_TYPE_3TDEA
            GenericKeyAlgorithm.AES_128 -> DukptType.DUKPT_TYPE_AES128
            GenericKeyAlgorithm.AES_192 -> DukptType.DUKPT_TYPE_AES192
            GenericKeyAlgorithm.AES_256 -> DukptType.DUKPT_TYPE_AES256
            else -> {
                Log.w(TAG, "Unsupported generic KeyAlgorithm for DukptType mapping: $generic")
                null
            }
        }
    }

    private fun mapToNewposDesUnifiedMode(alg: GenericKeyAlgorithm, mode: GenericBlockCipherMode?, encrypt: Boolean): Int {
        val operation = when (alg) {
            GenericKeyAlgorithm.DES_SINGLE, GenericKeyAlgorithm.DES_DOUBLE, GenericKeyAlgorithm.DES_TRIPLE -> if (encrypt) Ped.TDEA_ENCRYPT else Ped.TDEA_DECRYPT
            GenericKeyAlgorithm.SM4 -> if (encrypt) Ped.SM4_ENCRYPT else Ped.SM4_DECRYPT
            GenericKeyAlgorithm.AES_128, GenericKeyAlgorithm.AES_192, GenericKeyAlgorithm.AES_256 -> if (encrypt) Ped.AES_ENCRYPT else Ped.AES_DECRYPT
            else -> throw PedCryptoException("Unsupported algorithm for desDencryptUnified: $alg")
        }

        val blockModeVal = when (mode) {
            GenericBlockCipherMode.ECB -> when (alg) {
                GenericKeyAlgorithm.DES_SINGLE, GenericKeyAlgorithm.DES_DOUBLE, GenericKeyAlgorithm.DES_TRIPLE -> Ped.TDEA_MODE_ECB
                GenericKeyAlgorithm.SM4 -> Ped.SM4_MODE_ECB
                GenericKeyAlgorithm.AES_128, GenericKeyAlgorithm.AES_192, GenericKeyAlgorithm.AES_256 -> Ped.AES_MODE_ECB
                else -> throw PedCryptoException("ECB mode is not supported for algorithm: $alg")
            }
            GenericBlockCipherMode.CBC -> when (alg) {
                GenericKeyAlgorithm.DES_SINGLE, GenericKeyAlgorithm.DES_DOUBLE, GenericKeyAlgorithm.DES_TRIPLE -> Ped.TDEA_MODE_CBC
                GenericKeyAlgorithm.SM4 -> Ped.SM4_MODE_CBC
                GenericKeyAlgorithm.AES_128, GenericKeyAlgorithm.AES_192, GenericKeyAlgorithm.AES_256 -> Ped.AES_MODE_CBC
                else -> throw PedCryptoException("CBC mode is not supported for algorithm: $alg")
            }
            null -> throw PedCryptoException("Block mode (ECB/CBC) cannot be null for symmetric operations.")
        }
        return operation or blockModeVal
    }

    // --- Initialization & Lifecycle ---

    init {
        try {
            Log.i(TAG, "Attempting to get NewPOS Ped instance...")
            pedInstance = Ped.getInstance()
            Log.i(TAG, "NewPOS Ped instance obtained successfully.")
        } catch (e: Throwable) {
            Log.e(TAG, "FATAL: Failed to get NewPOS Ped instance. The SDK may not be properly initialized or supported on this device.", e)
            throw PedException("Failed to initialize NewPOS PED: ${e.message}", e)
        }
    }

    override suspend fun initializePed(application: Application): Boolean {
        Log.d(TAG, "initializePed called. NewPOS instance was already obtained in init. Returning true.")
        return true
    }

    override fun releasePed() {
        Log.d(TAG, "releasePed called. No explicit release action is required for the NewPOS Ped singleton.")
    }

    override suspend fun getStatus(): PedStatusInfo {
        try {
            val statusLong = pedInstance.status
            // The interpretation of status bits requires specific documentation.
            // We assume a status other than 0 indicates a problem or tamper.
            val isTampered = statusLong != 0L
            return PedStatusInfo(
                isTampered = isTampered,
                batteryLevel = null, // Not available through the Ped API
                errorMessage = if (isTampered) "Device Tampered or in Error State (Raw Status: $statusLong)" else null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting PED status", e)
            throw PedException("Failed to get PED status: ${e.message}", e)
        }
    }

    override suspend fun getConfig(): PedConfigInfo {
        try {
            val config = pedInstance.config ?: throw PedException("Failed to get PED config instance (returned null)")
            return PedConfigInfo(
                // CORRECTED: The 'TID' property does not exist in 'PedConfig'. Assigning null.
                serialNumber = null,
                firmwareVersion = config.swVer,
                hardwareVersion = config.hwVer,
                modelIdentifier = config.model
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting PED config info", e)
            throw PedException("Failed to get PED config info: ${e.message}", e)
        }
    }

    // --- Key Management ---

    override suspend fun writeKey(
        keyIndex: Int,
        keyType: GenericKeyType,
        keyAlgorithm: GenericKeyAlgorithm,
        keyData: PedKeyData,
        transportKeyIndex: Int?,
        transportKeyType: GenericKeyType?
    ): Boolean {
        if (transportKeyIndex == null || transportKeyType == null) {
            throw PedKeyException("NewPOS requires transportKeyIndex and transportKeyType for encrypted key loading.")
        }

        // CORRECCIÓN: El KeySystem debe ser el de la llave de transporte (la que descifra), no el de la llave de destino.
        val npTransportKeySystem = mapToNewposKeySystem(transportKeyType, keyAlgorithm)
        val npDestKeyType = mapToNewposKeyType(keyType)
            ?: throw PedKeyException("Unsupported destination key type for KeyType mapping: $keyType")

        val npKcvMode = if (keyData.kcv != null) Ped.KEY_VERIFY_KVC else Ped.KEY_VERIFY_NONE
        if (keyData.kcv != null) Log.d(TAG, "KCV check enabled (NewPOS internal check).")

        try {
            Log.d(TAG, "Calling writeKey: TransportKS=$npTransportKeySystem, DestKT=$npDestKeyType, MKeyIdx=$transportKeyIndex, DestKeyIdx=$keyIndex")
            val result = pedInstance.writeKey(
                npTransportKeySystem, // Se usa el KeySystem de la llave de transporte
                npDestKeyType,
                transportKeyIndex,
                keyIndex,
                npKcvMode,
                keyData.keyBytes
            )
            if (result != 0) {
                // Agregar el código de error al mensaje de la excepción es útil para depurar
                throw PedKeyException("Failed to write key (encrypted). NewPOS Error Code: $result")
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error writing key (encrypted)", e)
            throw PedKeyException("Failed to write key (encrypted): ${e.message}", e)
        }
    }

    override suspend fun writeKeyPlain(
        keyIndex: Int,
        keyType: GenericKeyType,
        keyAlgorithm: GenericKeyAlgorithm,
        keyBytes: ByteArray,
        kcvBytes: ByteArray?
    ): Boolean {
        if (keyType != GenericKeyType.MASTER_KEY && keyType != GenericKeyType.TRANSPORT_KEY) {
            Log.w(TAG, "NewPOS injectKey is primarily for MASTER_KEY and TRANSPORT_KEY. Attempting with $keyType.")
        }

        // CORRECCIÓN PARA TRANSPORT_KEY: NewPOS no tiene un método específico para inyectar
        // Transport Keys en texto plano. Funcionalmente, las Transport Keys son similares
        // a las Master Keys (ambas son llaves de nivel superior), por lo que las tratamos
        // como MASTER_KEY para el mapeo de NewPOS.
        val effectiveKeyType = if (keyType == GenericKeyType.TRANSPORT_KEY) {
            Log.d(TAG, "Tratando TRANSPORT_KEY como MASTER_KEY para NewPOS injectKey debido a limitaciones de la API.")
            GenericKeyType.MASTER_KEY
        } else {
            keyType
        }

        val npKeySystem = mapToNewposKeySystem(effectiveKeyType, keyAlgorithm)
        val npKeyType = mapToNewposKeyType(effectiveKeyType)
            ?: throw PedKeyException("Unsupported key type for KeyType mapping: $effectiveKeyType")

        try {
            Log.d(TAG, "Calling injectKey: KS=$npKeySystem, KT=$npKeyType, KeyIdx=$keyIndex (Original type: $keyType)")
            val result = pedInstance.injectKey(npKeySystem, npKeyType, keyIndex, keyBytes)
            if (result != 0) {
                throw PedKeyException("Failed to write key (plaintext). NewPOS Error Code: $result")
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error writing key (plaintext)", e)
            throw PedKeyException("Failed to write key (plaintext): ${e.message}", e)
        }
    }

    override suspend fun writeDukptInitialKeyEncrypted(
        groupIndex: Int,
        keyAlgorithm: GenericKeyAlgorithm,
        encryptedIpek: ByteArray,
        initialKsn: ByteArray,
        transportKeyIndex: Int,
        keyChecksum: String?
    ): Boolean {
        Log.i(TAG, "Attempting to write encrypted DUKPT key using writeDukptIPEK.")
        val npKeySystem = mapToNewposKeySystem(GenericKeyType.DUKPT_INITIAL_KEY, keyAlgorithm)

        // The NewPOS 'writeDukptIPEK' API expects ipekHeader and ipekData.
        // The generic interface only provides one encrypted byte array.
        // This suggests a specific format like TR-31 which we are not handling.
        // We assume ipekData is the main payload and the header might be null or empty.
        Log.w(TAG, "NewPOS writeDukptIPEK requires 'ipekHeader' and 'ipekData'. Mapping entire 'encryptedIpek' to 'ipekData' and using null header. This may fail if a specific format (e.g., TR-31) is expected.")

        try {
            val result = pedInstance.writeDukptIPEK(
                npKeySystem,
                transportKeyIndex, // kbpkIndex (Key Block Protection Key)
                groupIndex,        // ipekIndex
                initialKsn,        // KSN
                null,              // ipekHeader - unavailable in the generic interface
                encryptedIpek      // ipekData
            )

            if (result != 0) {
                throw PedKeyException("Failed to write encrypted DUKPT IPEK. NewPOS Error Code: $result")
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error writing encrypted DUKPT IPEK", e)
            throw PedKeyException("Failed to write encrypted DUKPT IPEK: ${e.message}", e)
        }
    }

    override suspend fun deleteKey(keyIndex: Int, keyType: GenericKeyType): Boolean {
        // To delete, we don't need the algorithm, just the generic type for mapping.
        val npKeySystem = mapToNewposKeySystem(keyType)
        val npKeyType = mapToNewposKeyType(keyType)
            ?: throw PedKeyException("Unsupported key type for deleteKey: $keyType")

        try {
            Log.i(TAG, "--- Starting deleteKey operation ---")
            Log.i(TAG, "Parameters:")
            Log.i(TAG, "  - keyIndex: $keyIndex")
            Log.i(TAG, "  - keyType: $keyType") 
            Log.i(TAG, "  - mapped npKeySystem: $npKeySystem")
            Log.i(TAG, "  - mapped npKeyType: $npKeyType")
            
            Log.d(TAG, "Calling NewPOS pedInstance.deleteKey($npKeySystem, $npKeyType, $keyIndex)")
            pedInstance.deleteKey(npKeySystem, npKeyType, keyIndex)
            Log.i(TAG, "✅ NewPOS deleteKey completed successfully - key removed from slot $keyIndex")
            return true // The function is void, so if no exception is thrown, it was successful.
        } catch (e: Exception) {
            Log.e(TAG, "❌ NewPOS deleteKey failed for [$keyType/$keyIndex]", e)
            Log.e(TAG, "Exception details: ${e.message}")
            throw PedKeyException("Failed to delete key [$keyType/$keyIndex]: ${e.message}", e)
        }
    }

    override suspend fun deleteAllKeys(): Boolean {
        try {
            Log.i(TAG, "--- Starting deleteAllKeys operation ---")
            Log.i(TAG, "Operation: Mass deletion of all working keys from NewPOS device")
            Log.i(TAG, "NewPOS method: clearUserKeys() - eliminates all user injected keys")
            
            Log.d(TAG, "Calling NewPOS pedInstance.clearUserKeys()")
            pedInstance.clearUserKeys()
            
            Log.i(TAG, "✅ NewPOS clearUserKeys completed successfully")
            Log.i(TAG, "✅ ALL working keys have been removed from the device")
            Log.i(TAG, "Note: Master keys and system keys remain untouched")
            return true // The function is void.
        } catch (e: Exception) {
            Log.e(TAG, "❌ NewPOS clearUserKeys failed during mass deletion", e)
            Log.e(TAG, "Exception details: ${e.message}")
            Log.e(TAG, "This means some or all keys may still be present in the device")
            throw PedKeyException("Failed to delete all keys: ${e.message}", e)
        }
    }

    override suspend fun isKeyPresent(keyIndex: Int, keyType: GenericKeyType): Boolean {
        // AÑADIR ESTA LÓGICA DE TRADUCCIÓN
        val effectiveKeyType = if (keyType == GenericKeyType.TRANSPORT_KEY) {
            Log.d(TAG, "isKeyPresent: Tratando TRANSPORT_KEY como MASTER_KEY para la verificación.")
            GenericKeyType.MASTER_KEY
        } else {
            keyType
        }

        val npKeySystem = mapToNewposKeySystem(effectiveKeyType) // Usar el tipo efectivo
        val npKeyType = mapToNewposKeyType(effectiveKeyType) // Usar el tipo efectivo
            ?: throw PedKeyException("Unsupported key type for isKeyPresent: $keyType")

        return try {
            // La llamada ahora será consistente con la inyección
            val result = pedInstance.checkKey(npKeySystem, npKeyType, keyIndex, 0)
            Log.d(TAG, "checkKey for [$keyType/$keyIndex] (as $effectiveKeyType) returned: $result")
            result == 0 // 0 means the key exists.
        } catch (e: Exception) {
            Log.e(TAG, "Error checking key presence for [$keyType/$keyIndex]", e)
            false
        }
    }

    override suspend fun getKeyInfo(keyIndex: Int, keyType: GenericKeyType): PedKeyInfo? {
        Log.w(TAG, "NewPOS API does not support getting key details. Checking for presence only.")
        return if (isKeyPresent(keyIndex, keyType)) {
            Log.i(TAG, "Key is PRESENT at index=$keyIndex. Returning basic PedKeyInfo without algorithm.")
            PedKeyInfo(index = keyIndex, type = keyType, algorithm = null) // We cannot determine the algorithm.
        } else {
            Log.i(TAG, "Key is NOT PRESENT at index=$keyIndex. Returning null.")
            null
        }
    }

    // --- DUKPT Management ---

    override suspend fun writeDukptInitialKey(
        groupIndex: Int,
        keyAlgorithm: GenericKeyAlgorithm,
        keyBytes: ByteArray,
        initialKsn: ByteArray,
        keyChecksum: String?
    ): Boolean {
        if (!keyChecksum.isNullOrBlank()) {
            Log.w(TAG, "NewPOS API for DUKPT key loading does not use a key checksum parameter. It will be ignored.")
        }

        try {
            val result = when (keyAlgorithm) {
                GenericKeyAlgorithm.DES_DOUBLE, GenericKeyAlgorithm.DES_TRIPLE -> {
                    Log.d(TAG, "Calling createDukptKey (TDES) for group $groupIndex")
                    pedInstance.createDukptKey(groupIndex, keyBytes, initialKsn)
                }
                GenericKeyAlgorithm.AES_128, GenericKeyAlgorithm.AES_192, GenericKeyAlgorithm.AES_256 -> {
                    val npDukptType = mapToNewposDukptType(keyAlgorithm)
                        ?: throw PedKeyException("Unsupported AES algorithm for DUKPT: $keyAlgorithm")
                    Log.d(TAG, "Calling createDukptAESKey ($npDukptType) for group $groupIndex")
                    pedInstance.createDukptAESKey(groupIndex, npDukptType, keyBytes, initialKsn)
                }
                else -> throw PedKeyException("Unsupported algorithm for DUKPT initial key: $keyAlgorithm")
            }

            if (result != 0) {
                throw PedKeyException("Failed to write DUKPT initial key. NewPOS Error Code: $result")
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error writing DUKPT initial key for group $groupIndex", e)
            throw PedKeyException("Failed to write DUKPT initial key: ${e.message}", e)
        }
    }

    override suspend fun getDukptInfo(groupIndex: Int): DukptInfo? {
        var ksn: ByteArray? = null
        var keySystemUsed: String? = null
        try {
            // First, try to get the KSN for TDES
            ksn = pedInstance.getDukptKsn(groupIndex)
            if (ksn != null) keySystemUsed = "DUKPT_DES"
        } catch (e: Exception) {
            Log.w(TAG, "getDukptKsn (TDES) failed for group $groupIndex, trying AES. Error: ${e.message}")
        }

        if (ksn == null) {
            try {
                // If that fails, try to get the KSN for AES
                ksn = pedInstance.getDukptAESKsn(groupIndex)
                if (ksn != null) keySystemUsed = "DUKPT_AES"
            } catch (e: Exception) {
                Log.e(TAG, "getDukptAESKsn also failed for group $groupIndex. Key may not exist.", e)
                return null
            }
        }

        return ksn?.let {
            Log.d(TAG, "Successfully retrieved KSN for group $groupIndex using $keySystemUsed system.")
            // NewPOS does not provide the counter separately.
            DukptInfo(ksn = it, counter = null)
        }
    }

    override suspend fun incrementDukptKsn(groupIndex: Int): Boolean {
        Log.e(TAG, "Unsupported Operation: NewPOS does not support manual DUKPT KSN increment.")
        // The increment is implicit in cryptographic operations that use the DUKPT key.
        throw UnsupportedOperationException("Manual DUKPT KSN increment is not supported by NewPOS PED API.")
    }

    // --- Cryptographic Operations ---

    override suspend fun encrypt(request: PedCipherRequest): PedCipherResult {
        if (!request.encrypt) throw IllegalArgumentException("Use decrypt method for decryption")
        return performCipher(request)
    }

    override suspend fun decrypt(request: PedCipherRequest): PedCipherResult {
        if (request.encrypt) throw IllegalArgumentException("Use encrypt method for encryption")
        return performCipher(request)
    }

    private suspend fun performCipher(request: PedCipherRequest): PedCipherResult {
        return when {
            request.isDukpt -> performDukptCipher(request)
            request.keyType == GenericKeyType.RSA_PRIVATE_KEY && request.encrypt -> performRsaCipher(request)
            else -> performSymmetricCipher(request)
        }
    }

    private fun performSymmetricCipher(request: PedCipherRequest): PedCipherResult {
        val npKeySystem = mapToNewposKeySystem(request.keyType, request.algorithm)
        val npKeyType = mapToNewposKeyType(request.keyType)
            ?: throw PedCryptoException("Unsupported key type for symmetric cipher: ${request.keyType}")
        val npMode = mapToNewposDesUnifiedMode(request.algorithm, request.mode, request.encrypt)

        // --- CORRECCIÓN #1: Manejo del IV ---
        // El SDK de NewPOS requiere un IV no nulo incluso para el modo ECB.
        val effectiveIv: ByteArray
        if (request.mode == GenericBlockCipherMode.CBC) {
            effectiveIv = request.iv ?: throw PedCryptoException("IV is required for CBC mode.")
        } else { // Para ECB u otros modos
            val blockSize = when (request.algorithm) {
                GenericKeyAlgorithm.AES_128, GenericKeyAlgorithm.AES_192, GenericKeyAlgorithm.AES_256, GenericKeyAlgorithm.SM4 -> 16
                else -> 8 // DES/TDES
            }
            effectiveIv = request.iv ?: ByteArray(blockSize) { 0 } // Usar IV de ceros si es nulo
        }
        // --- FIN CORRECCIÓN #1 ---

        try {
            val resultData = pedInstance.desDencryptUnified(
                npKeySystem, npKeyType, request.keyIndex, npMode, effectiveIv, request.data
            ) ?: throw PedCryptoException("Symmetric cipher operation failed (returned null).")
            return PedCipherResult(resultData)
        } catch (e: Exception) {
            Log.e(TAG, "Error performing symmetric cipher", e)
            // Envolver la excepción original para obtener el mensaje de error completo del SDK
            throw PedCryptoException("Symmetric cipher failed: ${e.message}", e)
        }
    }

    private suspend fun performDukptCipher(request: PedCipherRequest): PedCipherResult {
        val groupIndex = request.dukptGroupIndex ?: throw PedCryptoException("DUKPT group index required.")

        // API Limitation: KeyUsage is not defined in the provided .class file.
        if (request.algorithm in listOf(GenericKeyAlgorithm.AES_128, GenericKeyAlgorithm.AES_192, GenericKeyAlgorithm.AES_256)) {
            Log.e(TAG, "AES DUKPT cipher requires 'KeyUsage', which is an undefined type in the provided SDK. Operation aborted.")
            throw PedCryptoException("Cannot perform AES DUKPT cipher: Missing 'KeyUsage' type definition.")
        }
        if (request.mode != GenericBlockCipherMode.ECB) {
            Log.w(TAG, "NewPOS DUKPT cipher API (dukptEncrypt/DecryptRequest) does not specify block mode. It likely defaults to ECB. Requested mode was ${request.mode}.")
        }

        try {
            val resultData = if (request.encrypt) {
                pedInstance.dukptEncryptRequest(groupIndex, request.data)
            } else {
                pedInstance.dukptDecryptRequest(groupIndex, request.data)
            } ?: throw PedCryptoException("DUKPT (TDES) cipher operation failed (returned null).")

            // KSN is incremented automatically. We fetch it after the operation.
            val finalDukpt = getDukptInfo(groupIndex)
            return PedCipherResult(resultData, finalDukpt)
        } catch (e: Exception) {
            Log.e(TAG, "Error performing DUKPT (TDES) cipher", e)
            throw PedCryptoException("DUKPT (TDES) cipher failed: ${e.message}", e)
        }
    }

    private fun performRsaCipher(request: PedCipherRequest): PedCipherResult {
        // Encryption with a private key is functionally a signature.
        if (request.keyType != GenericKeyType.RSA_PRIVATE_KEY || !request.encrypt) {
            throw PedCryptoException("Unsupported RSA operation. Only private key encryption (signing) is supported.")
        }
        try {
            val resultData = pedInstance.encryptWithRsaPrivateKey(request.keyIndex, request.data)
                ?: throw PedCryptoException("RSA private key encryption failed (returned null).")
            return PedCipherResult(resultData)
        } catch (e: Exception) {
            Log.e(TAG, "Error performing RSA private key encryption", e)
            throw PedCryptoException("RSA private key encryption failed: ${e.message}", e)
        }
    }

    override suspend fun calculateMac(request: PedMacRequest): PedMacResult {
        return if (request.isDukpt) {
            calculateDukptMac(request)
        } else {
            calculateStandardMac(request)
        }
    }

    /**
     * Infers the underlying key algorithm from a MAC algorithm.
     * This is necessary because NewPOS needs to know the key family (DES, AES)
     * to select the correct key system (MS_DES, MS_AES, etc.).
     */
    private fun inferKeyAlgorithm(macAlgorithm: GenericMacAlgorithm): GenericKeyAlgorithm {
        return when (macAlgorithm) {
            GenericMacAlgorithm.CBC_MAC_ISO9797_1_M1 -> GenericKeyAlgorithm.DES_SINGLE
            GenericMacAlgorithm.CBC_MAC_ISO9797_1_M2,
            GenericMacAlgorithm.RETAIL_MAC_ANSI_X9_19,
            GenericMacAlgorithm.UNIONPAY_CBC_MAC -> GenericKeyAlgorithm.DES_TRIPLE

            // For CMAC, the key is AES. The mapToNewposKeySystem function just needs
            // to know it's in the AES family; it doesn't require the exact size. AES_128 is a safe default.
            GenericMacAlgorithm.CMAC_AES -> GenericKeyAlgorithm.AES_128
        }
    }

    private suspend fun calculateStandardMac(request: PedMacRequest): PedMacResult {
        // 1. Infer the KeyAlgorithm from the request's MacAlgorithm.
        val inferredKeyAlgorithm = inferKeyAlgorithm(request.algorithm)

        // 2. Use the inferred algorithm to get the correct KeySystem.
        val npKeySystem = mapToNewposKeySystem(request.keyType, inferredKeyAlgorithm)

        val npMacMode = mapToNewposMacMode(request.algorithm)
            ?: throw PedCryptoException("Unsupported MAC algorithm for standard MAC: ${request.algorithm}")

        if (request.iv != null) Log.w(TAG, "NewPOS getMac does not support an explicit IV. It will be ignored.")

        try {
            val mac = pedInstance.getMac(npKeySystem, request.keyIndex, npMacMode, request.data)
                ?: throw PedCryptoException("Standard MAC calculation failed (returned null).")
            return PedMacResult(mac)
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating standard MAC", e)
            throw PedCryptoException("Standard MAC calculation failed: ${e.message}", e)
        }
    }

    private suspend fun calculateDukptMac(request: PedMacRequest): PedMacResult {
        val groupIndex = request.dukptGroupIndex ?: throw PedCryptoException("DUKPT group index required for MAC.")
        val npMacMode = mapToNewposMacMode(request.algorithm)
            ?: throw PedCryptoException("Unsupported MAC algorithm for DUKPT MAC: ${request.algorithm}")

        try {
            val mac = pedInstance.dukptCalcMacResponse(groupIndex, npMacMode, request.data)
                ?: throw PedCryptoException("DUKPT MAC calculation failed (returned null).")

            val finalDukpt = getDukptInfo(groupIndex)
            return PedMacResult(mac, finalDukpt)
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating DUKPT MAC", e)
            throw PedCryptoException("DUKPT MAC calculation failed: ${e.message}", e)
        }
    }

    // --- PIN Operations ---

    override suspend fun getPinBlock(request: PedPinRequest): PedPinResult {
        val (keyIdx, npKeySystem) = if (request.isDukpt) {
            Pair(
                request.dukptGroupIndex ?: throw PedException("DUKPT group index missing."),
                mapToNewposKeySystem(GenericKeyType.DUKPT_INITIAL_KEY, request.algorithm)
            )
        } else {
            Pair(
                request.keyIndex,
                mapToNewposKeySystem(request.keyType, request.algorithm)
            )
        }

        val npPinFormat = mapToNewposPinFormat(request.format)
            ?: throw PedException("Unsupported PIN block format: ${request.format}")
        val pan = request.pan ?: ""

        try {
            // --- CORRECCIÓN #2: Timeout en segundos ---
            // La API de NewPOS espera el timeout en segundos, no en milisegundos.
            pedInstance.setPinEntryTimeout(request.timeoutSeconds)
            // --- FIN CORRECCIÓN #2 ---
        } catch (e: Exception) {
            throw PedException("Failed to set PIN entry timeout: ${e.message}", e)
        }

        return suspendCancellableCoroutine { continuation ->
            try {
                Log.d(TAG, "Requesting PIN block: KS=$npKeySystem, KI=$keyIdx, Format=$npPinFormat, Len='${request.pinLengthConstraints}', PAN='$pan'")
                pedInstance.getPinBlock(
                    npKeySystem, keyIdx, npPinFormat, request.pinLengthConstraints, pan,
                    object : PinBlockCallback {
                        override fun onPinBlock(resultCode: Int, pinBlockData: ByteArray?) {
                            if (continuation.isActive) {
                                if (resultCode == 0 && pinBlockData != null) {
                                    continuation.resume(PedPinResult(pinBlockData, null))
                                } else {
                                    val exception = mapNewposPinResultCodeToException(resultCode, pinBlockData == null && resultCode == 0)
                                    continuation.resumeWithException(exception)
                                }
                            }
                        }
                    }
                )
            } catch (e: Exception) {
                if (continuation.isActive) {
                    continuation.resumeWithException(PedException("Failed to initiate PIN Block retrieval: ${e.message}", e))
                }
            }
            continuation.invokeOnCancellation {
                Log.d(TAG, "PIN entry coroutine cancelled. Attempting to cancel PED.")
                cancelPinEntry()
            }
        }
    }

    /**
     * Helper to map NewPOS return codes from PinBlockCallback to exceptions.
     */
    private fun mapNewposPinResultCodeToException(resultCode: Int, successWithNullData: Boolean): PedException {
        if (successWithNullData) {
            return PedException("PIN entry succeeded (code 0) but returned null data.")
        }
        // Mapping based on common PED codes. Should be verified with NewPOS documentation.
        return when (resultCode) {
            -2003, -2005 -> PedTimeoutException("PIN entry timed out (Code: $resultCode)")
            -2004 -> PedCancellationException("PIN entry cancelled by user (Code: $resultCode)")
            -2601 -> PedKeyException("PIN key not found or invalid (Code: $resultCode)")
            else -> PedException("PIN entry failed with NewPOS error code: $resultCode")
        }
    }

    override fun cancelPinEntry() {
        try {
            Log.d(TAG, "Calling cancelInputPin on PED instance.")
            pedInstance.cancelInputPin()
        } catch (e: Exception) {
            Log.e(TAG, "Error calling cancelInputPin", e)
        }
    }

    // --- UI Interaction ---

    override fun displayMessage(message: String, line: Int?, clearPrevious: Boolean) {
        Log.w(TAG, "Unsupported Operation: NewPOS PED does not support displaying arbitrary messages directly during PIN entry. Use setPinPadStyle or custom PadView.")
    }

    override fun setPinPadStyle(styleInfo: Map<String, Any>) {
        val bundle = Bundle()
        styleInfo.forEach { (key, value) ->
            try {
                when (value) {
                    is String -> bundle.putString(key, value)
                    is Int -> bundle.putInt(key, value)
                    is Boolean -> bundle.putBoolean(key, value)
                    is Float -> bundle.putFloat(key, value)
                    else -> Log.w(TAG, "Unsupported value type in styleInfo Map for key '$key': ${value::class.java.name}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error putting key '$key' with value '$value' into Bundle.", e)
            }
        }
        if (!bundle.isEmpty) {
            try {
                Log.d(TAG, "Calling setPadViewStyle with bundle: $bundle")
                pedInstance.setPadViewStyle(bundle)
            } catch (e: Exception) {
                Log.e(TAG, "Error calling setPadViewStyle", e)
            }
        }
    }

    // --- Other Utilities ---

    override suspend fun getRandomBytes(length: Int): ByteArray {
        try {
            return pedInstance.getRandom(length)
                ?: throw PedException("Failed to get random bytes (PED returned null).")
        } catch (e: Exception) {
            Log.e(TAG, "Error getting random bytes", e)
            throw PedException("Failed to get random bytes: ${e.message}", e)
        }
    }
}