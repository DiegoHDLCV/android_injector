package com.example.manufacturer.libraries.newpos.wrapper

// Import generic types (Adjust package if needed)
import com.example.manufacturer.base.controllers.ped.*
import com.example.manufacturer.base.models.*
import com.example.manufacturer.base.models.KeyType as GenericKeyType
import com.example.manufacturer.base.models.KeyAlgorithm as GenericKeyAlgorithm
import com.example.manufacturer.base.models.BlockCipherMode as GenericBlockCipherMode
import com.example.manufacturer.base.models.MacAlgorithm as GenericMacAlgorithm
import com.example.manufacturer.base.models.PinBlockFormatType as GenericPinBlockFormatType

// Import NewPOS specific types
import com.pos.device.ped.DukptType as NewposDukptType
import com.pos.device.ped.EccKeyFormat // Not used in generic interface currently
import com.pos.device.ped.EccKeyInfo // Not used in generic interface currently
import com.pos.device.ped.KeySystem as NewposKeySystem
import com.pos.device.ped.KeyType as NewposKeyType
import com.pos.device.ped.MACMode as NewposMACMode
import com.pos.device.ped.Ped
import com.pos.device.ped.PedConfig
import com.pos.device.ped.PinBlockCallback
import com.pos.device.ped.PinBlockFormat as NewposPinBlockFormat
// Missing NewPOS types from generic models (if any were used)
// import com.pos.device.ped.KeyUsage // Missing from provided NewPOS docs

import android.content.Context
import android.os.Bundle
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class NewposPedController(private val context: Context) : IPedController {

    private val TAG = "NewposPedController"
    private val pedInstance: Ped // Instance obtained in init

    // --- Mappings ---

    private fun mapToNewposKeySystem(generic: GenericKeyType): NewposKeySystem? {
        // DUKPT keys use specific KeySystems in NewPOS
        if (generic == GenericKeyType.DUKPT_INITIAL_KEY || generic == GenericKeyType.DUKPT_WORKING_KEY) {
            // Assuming DUKPT_DES for TDES variant, DUKPT_AES for AES variant
            // The actual algorithm needs to be known when loading the initial key.
            // This mapping might need context. Defaulting to DUKPT_DES for simplicity here.
            Log.w(TAG, "Ambiguous DUKPT KeySystem mapping for $generic. Defaulting to DUKPT_DES.")
            return NewposKeySystem.DUKPT_DES
        }
        // Add mappings based on NewPOS KeySystem enum documentation
        return when (generic) {
            GenericKeyType.MASTER_KEY -> NewposKeySystem.MS_DES // Or MS_SM4 / MS_AES if differentiating Master Key algorithms
            GenericKeyType.WORKING_PIN_KEY -> NewposKeySystem.FIXED_DES // Assuming FIXED system for working keys? Or MS? Needs clarification.
            GenericKeyType.WORKING_MAC_KEY -> NewposKeySystem.FIXED_DES
            GenericKeyType.WORKING_DATA_ENCRYPTION_KEY -> NewposKeySystem.FIXED_DES
            GenericKeyType.RSA_PRIVATE_KEY, GenericKeyType.RSA_PUBLIC_KEY -> NewposKeySystem.TMS_RSA // Check if correct
            GenericKeyType.TRANSPORT_KEY -> NewposKeySystem.FIXED_TMTK // Assuming transport key maps to TMTK
            else -> {
                Log.w(TAG, "Unsupported generic KeyType to NewposKeySystem mapping: $generic")
                null
            }
        }
    }

    private fun mapToNewposKeyType(generic: GenericKeyType): NewposKeyType? {
        return when (generic) {
            GenericKeyType.MASTER_KEY -> NewposKeyType.KEY_TYPE_MASTK
            GenericKeyType.WORKING_PIN_KEY -> NewposKeyType.KEY_TYPE_PINK // Or KEY_TYPE_FIXPINK? Depends on usage context.
            GenericKeyType.WORKING_MAC_KEY -> NewposKeyType.KEY_TYPE_MACK // Or KEY_TYPE_FIXMACK?
            GenericKeyType.WORKING_DATA_ENCRYPTION_KEY -> NewposKeyType.KEY_TYPE_EAK // Or KEY_TYPE_FIXEAK?
            GenericKeyType.DUKPT_INITIAL_KEY, GenericKeyType.DUKPT_WORKING_KEY -> NewposKeyType.KEY_TYPE_DUKPTK
            GenericKeyType.RSA_PRIVATE_KEY -> NewposKeyType.KEY_TYPE_RSA_PRIK
            GenericKeyType.TRANSPORT_KEY -> NewposKeyType.KEY_TYPE_TMSK // Example mapping, verify based on SDK usage
            // GenericKeyType.RSA_PUBLIC_KEY is not directly mapped as a NewposKeyType for storage? Public keys are often imported/exported differently.
            else -> {
                Log.w(TAG, "Unsupported generic KeyType mapping to NewposKeyType: $generic")
                null
            }
        }
    }

    private fun mapToNewposPinFormat(generic: GenericPinBlockFormatType): NewposPinBlockFormat? {
        return when (generic) {
            GenericPinBlockFormatType.ISO9564_0 -> NewposPinBlockFormat.PIN_BLOCK_FORMAT_0
            GenericPinBlockFormatType.ISO9564_1 -> NewposPinBlockFormat.PIN_BLOCK_FORMAT_1
            GenericPinBlockFormatType.ISO9564_3 -> NewposPinBlockFormat.PIN_BLOCK_FORMAT_3
            GenericPinBlockFormatType.ISO9564_4 -> {
                // This requires setting the AES DUKPT type (128, 192, 256)
                // The generic interface doesn't currently pass this detail explicitly with the format.
                // We'd need context about the AES key being used (e.g., from writeDukptInitialKey).
                // Defaulting to AES128 for now if format 4 is requested for DUKPT AES.
                Log.w(TAG, "Mapping ISO9564_4 requires AES key size context. Assuming AES128 for NewposPinBlockFormat.setDukptAESType.")
                NewposPinBlockFormat.PIN_BLOCK_FORMAT_4.setDukptAESType(NewposDukptType.DUKPT_TYPE_AES128) // Example default
                NewposPinBlockFormat.PIN_BLOCK_FORMAT_4
            }
            // else -> null // Explicitly handle unsupported formats if any
        }
    }

    private fun mapToNewposMacMode(generic: GenericMacAlgorithm): NewposMACMode? {
        return when(generic) {
            // Map based on standard interpretations and NewPOS enum names
            GenericMacAlgorithm.CBC_MAC_ISO9797_1_M1 -> NewposMACMode.MAC_MODE_EMV // Often used for EMV (DES M1)
            GenericMacAlgorithm.CBC_MAC_ISO9797_1_M2 -> NewposMACMode.MAC_MODE_1 // Often TDES M2 maps to ANSI X9.19
            GenericMacAlgorithm.RETAIL_MAC_ANSI_X9_19 -> NewposMACMode.MAC_MODE_1 // Retail MAC is usually ANSI X9.19
            GenericMacAlgorithm.UNIONPAY_CBC_MAC -> NewposMACMode.MAC_MODE_CUP
            // GenericMacAlgorithm.CMAC_AES -> Not directly listed in NewposMACMode? Might require different API call.
            // GenericMacAlgorithm.DUKPT_MAC (if added) -> NewposMACMode.MAC_MODE_DUKPT
            else -> {
                Log.w(TAG, "Unsupported generic MacAlgorithm mapping to NewposMACMode: $generic")
                null
            }
        }
    }

    private fun mapToNewposDukptType(generic: GenericKeyAlgorithm): NewposDukptType? {
        return when(generic) {
            GenericKeyAlgorithm.DES_TRIPLE -> NewposDukptType.DUKPT_TYPE_3TDEA // Assuming 3TDEA for TDES
            GenericKeyAlgorithm.DES_DOUBLE -> NewposDukptType.DUKPT_TYPE_2TDEA // Assuming 2TDEA for 2-key TDES
            GenericKeyAlgorithm.AES_128 -> NewposDukptType.DUKPT_TYPE_AES128
            GenericKeyAlgorithm.AES_192 -> NewposDukptType.DUKPT_TYPE_AES192
            GenericKeyAlgorithm.AES_256 -> NewposDukptType.DUKPT_TYPE_AES256
            else -> {
                Log.w(TAG, "Unsupported generic KeyAlgorithm for NewposDukptType mapping: $generic")
                null
            }
        }
    }

    // Map generic cipher params to NewPOS mode integer for desDencryptUnified
    private fun mapToNewposDesUnifiedMode(alg: GenericKeyAlgorithm, mode: GenericBlockCipherMode?, encrypt: Boolean): Int? {
        val operation = when(alg) {
            GenericKeyAlgorithm.DES_SINGLE, GenericKeyAlgorithm.DES_DOUBLE, GenericKeyAlgorithm.DES_TRIPLE -> if (encrypt) Ped.TDEA_ENCRYPT else Ped.TDEA_DECRYPT
            GenericKeyAlgorithm.SM4 -> if (encrypt) Ped.SM4_ENCRYPT else Ped.SM4_DECRYPT
            GenericKeyAlgorithm.AES_128, GenericKeyAlgorithm.AES_192, GenericKeyAlgorithm.AES_256 -> if (encrypt) Ped.AES_ENCRYPT else Ped.AES_DECRYPT
            else -> { Log.w(TAG,"Unsupported algorithm for desDencryptUnified: $alg"); return null }
        }

        val blockModeVal = when(mode) {
            GenericBlockCipherMode.ECB -> when(alg) {
                GenericKeyAlgorithm.DES_SINGLE, GenericKeyAlgorithm.DES_DOUBLE, GenericKeyAlgorithm.DES_TRIPLE -> Ped.TDEA_MODE_ECB
                GenericKeyAlgorithm.SM4 -> Ped.SM4_MODE_ECB
                GenericKeyAlgorithm.AES_128, GenericKeyAlgorithm.AES_192, GenericKeyAlgorithm.AES_256 -> Ped.AES_MODE_ECB
                else -> { Log.w(TAG,"ECB mode unsupported for algorithm: $alg"); return null }
            }
            GenericBlockCipherMode.CBC -> when(alg) {
                GenericKeyAlgorithm.DES_SINGLE, GenericKeyAlgorithm.DES_DOUBLE, GenericKeyAlgorithm.DES_TRIPLE -> Ped.TDEA_MODE_CBC
                GenericKeyAlgorithm.SM4 -> Ped.SM4_MODE_CBC
                GenericKeyAlgorithm.AES_128, GenericKeyAlgorithm.AES_192, GenericKeyAlgorithm.AES_256 -> Ped.AES_MODE_CBC
                else -> { Log.w(TAG,"CBC mode unsupported for algorithm: $alg"); return null }
            }
            null -> { // Needed for RSA? but desDencryptUnified is likely not for RSA
                Log.w(TAG,"Block mode cannot be null for symmetric algorithms"); return null
            }
        }
        // Combine operation and block mode using bitwise OR
        return operation or blockModeVal
    }


    // --- Initialization ---
    init {
        try {
            Log.d(TAG, "Attempting to get NewPOS Ped instance...")
            pedInstance = Ped.getInstance()
            if (pedInstance == null) {
                throw IllegalStateException("Ped.getInstance() returned null. NewPOS SDK not operational.")
            }
            Log.d(TAG, "NewPOS Ped instance obtained successfully.")
        } catch (e: Throwable) { // Catch Throwable to include ExceptionInInitializerError, UnsatisfiedLinkError etc.
            Log.e(TAG, "Failed to initialize NewPOS PED controller: ${e.message}", e)
            // Propagate as a standard PedException or specific subtype if identifiable
            throw PedException("Failed to initialize NewPOS PED: ${e.message}", e)
        }
    }

    // --- Interface Implementation ---

    override suspend fun initializePed(): Boolean {
        Log.d(TAG, "initializePed called (NewPOS instance already obtained in init).")
        // Could add a status check here if needed
        return true
    }

    override fun releasePed() {
        Log.d(TAG, "releasePed called. No explicit release needed for NewPOS Ped singleton.")
        // Could clear listeners or internal state if necessary
    }

    override suspend fun getStatus(): PedStatusInfo {
        return try {
            val statusLong = pedInstance.status
            // Interpret the NewPOS status bits (this requires specific documentation for the bitmask)
            // Example: Assuming bit 0 indicates tamper, needs verification
            val isTampered = (statusLong and 0x01L) != 0L
            PedStatusInfo(
                isTampered = isTampered,
                // Other fields might require calls to getConfig() or are unavailable
                batteryLevel = null, // Not typically available via Ped API
                errorMessage = if (isTampered) "Device Tampered (Raw Status: $statusLong)" else null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting PED status", e)
            throw PedException("Failed to get PED status: ${e.message}", e)
        }
    }

    override suspend fun getConfig(): PedConfigInfo {
        try {
            val config = pedInstance.config ?: throw PedException("Failed to get PED config instance")
            // Try to read SN (may fail or require specific permissions/API)
            var serialNum: String? = null
            try {
                // PEDReadPinPadSn_Api usage needs confirmation based on actual API availability and signature
                // Assuming it exists and populates a byte array that needs conversion.
                // val snBytes = ByteArray(40) // Adjust size as needed
                // val snResult = pedInstance.PEDReadPinPadSn_Api(snBytes)
                // if (snResult == 0) { serialNum = String(snBytes).trim() /* Or specific parsing */ }
                Log.w(TAG, "Serial number retrieval via PEDReadPinPadSn_Api needs verification/implementation.")
            } catch (snEx: Exception) {
                Log.w(TAG, "Failed to read serial number: ${snEx.message}")
            }

            return PedConfigInfo(
                serialNumber = serialNum,
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
        keyData: PedKeyData,
        transportKeyIndex: Int?,
        transportKeyType: GenericKeyType?
    ): Boolean {
        val npDestKeySystem = mapToNewposKeySystem(keyType)
            ?: throw PedKeyException("Unsupported destination key type for KeySystem mapping: $keyType")
        val npDestKeyType = mapToNewposKeyType(keyType)
            ?: throw PedKeyException("Unsupported destination key type for KeyType mapping: $keyType")

        if (transportKeyIndex == null || transportKeyType == null) {
            throw PedKeyException("Encrypted key loading requires transportKeyIndex and transportKeyType")
        }

        // NewPOS writeKey: MasterKey encrypts WorkKey
        if (transportKeyType != GenericKeyType.MASTER_KEY) {
            throw PedKeyException("NewPOS writeKey expects transport key to be a MASTER_KEY")
        }

        val npKcvMode = if (keyData.kcv != null) {
            // TODO: Implement actual KCV comparison logic if needed, as NewPOS mode only triggers internal check.
            // For now, just use the mode flag if KCV is provided.
            Log.w(TAG, "KCV provided but generic interface doesn't specify check *value*. Using KEY_VERIFY_KVC mode.")
            Ped.KEY_VERIFY_KVC // Assuming KCV check type. NewPOS SDK handles check internally.
        } else {
            Ped.KEY_VERIFY_NONE
        }

        try {
            val result = pedInstance.writeKey(
                npDestKeySystem,
                npDestKeyType,
                transportKeyIndex, // masterKeyIndex
                keyIndex,          // destKeyIndex
                npKcvMode,
                keyData.keyBytes   // Encrypted key value
            )
            if (result != 0) {
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
        keyAlgorithm: GenericKeyAlgorithm, // Algorithm info might be needed for some PEDs, NewPOS injectKey doesn't use it directly
        keyBytes: ByteArray
    ): Boolean {
        // NewPOS uses injectKey for plaintext Master Keys
        if (keyType != GenericKeyType.MASTER_KEY) {
            Log.w(TAG, "NewPOS injectKey is typically used for MASTER_KEY plaintext loading.")
            // Optionally allow other types if injectKey supports them, but log warning.
            // throw PedKeyException("Plaintext loading via injectKey usually for MASTER_KEY")
        }

        val npKeySystem = mapToNewposKeySystem(keyType)
            ?: throw PedKeyException("Unsupported key type for KeySystem mapping: $keyType")
        val npKeyType = mapToNewposKeyType(keyType)
            ?: throw PedKeyException("Unsupported key type for KeyType mapping: $keyType")

        try {
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

    override suspend fun deleteKey(keyIndex: Int, keyType: GenericKeyType): Boolean {
        val npKeySystem = mapToNewposKeySystem(keyType)
            ?: throw PedKeyException("Unsupported key type for KeySystem mapping: $keyType")
        val npKeyType = mapToNewposKeyType(keyType)
            ?: throw PedKeyException("Unsupported key type for KeyType mapping: $keyType")

        try {
            pedInstance.deleteKey(npKeySystem, npKeyType, keyIndex)
            return true // NewPOS deleteKey is void, assume success if no exception
        } catch (e: Exception) { // Catch specific SDKException if defined
            Log.e(TAG, "Error deleting key", e)
            throw PedKeyException("Failed to delete key [$keyType/$keyIndex]: ${e.message}", e)
        }
    }

    override suspend fun deleteAllKeys(): Boolean {
        try {
            pedInstance.clearUserKeys()
            return true // NewPOS clearUserKeys is void
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting all keys", e)
            throw PedKeyException("Failed to delete all keys: ${e.message}", e)
        }
    }

    override suspend fun isKeyPresent(keyIndex: Int, keyType: GenericKeyType): Boolean {
        val npKeySystem = mapToNewposKeySystem(keyType)
            ?: throw PedKeyException("Unsupported key type for KeySystem mapping: $keyType")
        val npKeyType = mapToNewposKeyType(keyType)
            ?: throw PedKeyException("Unsupported key type for KeyType mapping: $keyType")

        return try {
            // Mode is always 0 in current NewPOS doc for checkKey
            pedInstance.checkKey(npKeySystem, npKeyType, keyIndex, 0) == 0
        } catch (e: Exception) {
            Log.e(TAG, "Error checking key presence", e)
            throw PedException("Failed to check key presence: ${e.message}", e) // Or return false?
        }
    }

    override suspend fun getKeyInfo(keyIndex: Int, keyType: GenericKeyType): PedKeyInfo? {
        Log.w(TAG, "getKeyInfo not directly supported by NewPOS Ped API. Checking presence only.")
        // NewPOS checkKey only confirms existence, not algorithm or detailed info.
        if (isKeyPresent(keyIndex, keyType)) {
            // Cannot determine algorithm reliably from NewPOS API alone
            return PedKeyInfo(index = keyIndex, type = keyType, algorithm = null)
        }
        return null
    }


    // --- DUKPT Management ---

    override suspend fun writeDukptInitialKey(
        groupIndex: Int,
        keyAlgorithm: GenericKeyAlgorithm,
        keyBytes: ByteArray,
        initialKsn: ByteArray
    ): Boolean {
        // NewPOS distinguishes between AES and DES/TDES DUKPT loading
        try {
            val result = when (keyAlgorithm) {
                GenericKeyAlgorithm.DES_DOUBLE, GenericKeyAlgorithm.DES_TRIPLE -> {
                    // Use createDukptKey for TDES
                    pedInstance.createDukptKey(groupIndex, keyBytes, initialKsn)
                }
                GenericKeyAlgorithm.AES_128, GenericKeyAlgorithm.AES_192, GenericKeyAlgorithm.AES_256 -> {
                    // Use createDukptAESKey for AES
                    val npDukptType = mapToNewposDukptType(keyAlgorithm)
                        ?: throw PedKeyException("Unsupported AES algorithm for DUKPT: $keyAlgorithm")
                    pedInstance.createDukptAESKey(groupIndex, npDukptType, keyBytes, initialKsn)
                }
                else -> throw PedKeyException("Unsupported algorithm for DUKPT initial key: $keyAlgorithm")
            }
            if (result != 0) {
                throw PedKeyException("Failed to write DUKPT initial key. NewPOS Error Code: $result")
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error writing DUKPT initial key", e)
            throw PedKeyException("Failed to write DUKPT initial key: ${e.message}", e)
        }
    }

    @Throws(PedException::class)
    override suspend fun getDukptInfo(groupIndex: Int): DukptInfo? {
        // ... (Implementation from previous response, ensuring it's suspend if needed) ...
        // Example simple implementation (might need Dispatchers.IO if pedInstance calls block)
        var ksn: ByteArray? = null
        try {
            ksn = pedInstance.getDukptKsn(groupIndex) // Assuming this is for TDES DUKPT
        } catch (e: Exception) {
            Log.w(TAG, "getDukptKsn failed for index $groupIndex, trying getDukptAESKsn", e)
        }

        if (ksn == null) {
            try {
                ksn = pedInstance.getDukptAESKsn(groupIndex) // Assuming this is for AES DUKPT
            } catch (e: Exception) {
                Log.w(TAG, "Error getting DUKPT KSN (AES) for index $groupIndex, key might not exist or be wrong type.", e)
                return null // Return null if neither worked
                // throw PedException("Failed to get DUKPT KSN for group $groupIndex: ${e.message}", e)
            }
        }
        return ksn?.let { DukptInfo(ksn = it, counter = null) }
    }

    override suspend fun incrementDukptKsn(groupIndex: Int): Boolean {
        Log.w(TAG, "Manual DUKPT KSN increment not supported by NewPOS Ped API.")
        // KSN increment is typically implicit during crypto operations.
        // throw UnsupportedOperationException("Manual DUKPT KSN increment not supported")
        return false // Indicate not supported
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
            request.keyType == GenericKeyType.RSA_PRIVATE_KEY && request.encrypt -> performRsaCipher(request) // RSA Private Key Encrypt
            request.keyType == GenericKeyType.RSA_PRIVATE_KEY && !request.encrypt -> throw PedCryptoException("RSA decryption typically uses the public key.") // RSA Private Key Decrypt (uncommon)
            request.keyType == GenericKeyType.RSA_PUBLIC_KEY -> throw PedCryptoException("RSA public key operations not directly supported via standard cipher methods in NewPOS PED API.") // RSA Public Key ops
            else -> performSymmetricCipher(request) // Symmetric keys (Master, Working)
        }
    }

    private suspend fun performSymmetricCipher(request: PedCipherRequest): PedCipherResult {
        val npKeySystem = mapToNewposKeySystem(request.keyType)
            ?: throw PedCryptoException("Unsupported key type for KeySystem mapping: ${request.keyType}")
        val npKeyType = mapToNewposKeyType(request.keyType)
            ?: throw PedCryptoException("Unsupported key type for KeyType mapping: ${request.keyType}")
        val npMode = mapToNewposDesUnifiedMode(request.algorithm, request.mode, request.encrypt)
            ?: throw PedCryptoException("Unsupported algorithm/mode combination: ${request.algorithm}/${request.mode}")

        try {
            // Assuming desDencryptUnified handles TDES, SM4, AES based on KeySystem/KeyType
            val resultData = pedInstance.desDencryptUnified(
                npKeySystem,
                npKeyType,
                request.keyIndex,
                npMode,
                request.iv, // IV needed for CBC modes
                request.data
            ) ?: throw PedCryptoException("Symmetric cipher operation failed (returned null)")
            return PedCipherResult(resultData)
        } catch (e: Exception) {
            Log.e(TAG, "Error performing symmetric cipher operation", e)
            throw PedCryptoException("Symmetric cipher failed: ${e.message}", e)
        }
    }

    private suspend fun performDukptCipher(request: PedCipherRequest): PedCipherResult {
        val groupIndex = request.dukptGroupIndex
            ?: throw PedCryptoException("DUKPT group index is required for DUKPT cipher operation")

        // NewPOS dukptEncrypt/DecryptRequest don't specify algorithm/mode/IV - assume TDES/ECB?
        // Also doesn't specify KeyVariant (PIN/MAC/Data) - which derived key is used?
        // NewPOS dukptAESEncrypt/DecryptRequest requires KeyUsage (not documented)
        Log.w(TAG, "Performing DUKPT cipher: NewPOS API limitations apply (assumes TDES/ECB?, KeyUsage needed for AES). KeyVariant parameter ignored.")

        try {
            val resultData: ByteArray?
            val isAes = request.algorithm == GenericKeyAlgorithm.AES_128 ||
                    request.algorithm == GenericKeyAlgorithm.AES_192 ||
                    request.algorithm == GenericKeyAlgorithm.AES_256

            if (isAes) {
                // AES DUKPT requires KeyUsage which is missing. Cannot proceed reliably.
                throw PedCryptoException("AES DUKPT cipher operations require KeyUsage, which is unavailable/undocumented.")
                // If KeyUsage were available:
                // val npDukptType = mapToNewposDukptType(request.algorithm) ?: throw PedCryptoException(...)
                // val npKeyUsage = mapToNewposKeyUsage(request.dukptKeyVariant) ?: throw PedCryptoException(...)
                // resultData = if (request.encrypt) pedInstance.dukptAESEncryptRequest(groupIndex, npDukptType, npKeyUsage, request.data)
                //             else pedInstance.dukptAESDecryptRequest(groupIndex, npDukptType, npKeyUsage, request.data)
            } else {
                // Assume TDES DUKPT
                resultData = if (request.encrypt) {
                    pedInstance.dukptEncryptRequest(groupIndex, request.data)
                } else {
                    pedInstance.dukptDecryptRequest(groupIndex, request.data)
                }
            }

            if (resultData == null) {
                throw PedCryptoException("DUKPT cipher operation failed (returned null)")
            }

            val finalDukptInfo = if (request.dukptIncrementKsn) getDukptInfo(groupIndex) else null // KSN likely incremented regardless of request flag?

            return PedCipherResult(resultData, finalDukptInfo)

        } catch (e: Exception) {
            Log.e(TAG, "Error performing DUKPT cipher operation", e)
            throw PedCryptoException("DUKPT cipher failed: ${e.message}", e)
        }
    }

    private suspend fun performRsaCipher(request: PedCipherRequest): PedCipherResult {
        if (request.keyType != GenericKeyType.RSA_PRIVATE_KEY || !request.encrypt) {
            throw PedCryptoException("RSA operation mismatch: Expecting private key encryption.")
        }
        // NewPOS `encryptWithRsaPrivateKey` handles encryption with private key (signing/raw encrypt)
        try {
            val resultData = pedInstance.encryptWithRsaPrivateKey(request.keyIndex, request.data)
                ?: throw PedCryptoException("RSA private key encryption failed (returned null)")
            return PedCipherResult(resultData)
        } catch (e: Exception) {
            Log.e(TAG, "Error performing RSA private key encryption", e)
            throw PedCryptoException("RSA private key encryption failed: ${e.message}", e)
        }
    }


    override suspend fun calculateMac(request: PedMacRequest): PedMacResult {
        return when {
            request.isDukpt -> calculateDukptMac(request)
            else -> calculateStandardMac(request)
        }
    }

    private suspend fun calculateStandardMac(request: PedMacRequest): PedMacResult {
        val npKeySystem = mapToNewposKeySystem(request.keyType)
            ?: throw PedCryptoException("Unsupported key type for KeySystem mapping: ${request.keyType}")
        val npKeyType = mapToNewposKeyType(request.keyType)
            ?: throw PedCryptoException("Unsupported key type for KeyType mapping: ${request.keyType}")
        val npMacMode = mapToNewposMacMode(request.algorithm)
            ?: throw PedCryptoException("Unsupported MAC algorithm mapping: ${request.algorithm}")

        if (request.keyType != GenericKeyType.WORKING_MAC_KEY) {
            Log.w(TAG, "Calculating standard MAC with non-MAC key type: ${request.keyType}")
        }
        if (request.iv != null) Log.w(TAG, "NewPOS getMac does not support explicit IV.")

        try {
            val mac = pedInstance.getMac(npKeySystem, request.keyIndex, npMacMode, request.data)
                ?: throw PedCryptoException("Standard MAC calculation failed (returned null)")
            return PedMacResult(mac) // No DUKPT info for standard MAC
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating standard MAC", e)
            throw PedCryptoException("Standard MAC calculation failed: ${e.message}", e)
        }
    }

    private suspend fun calculateDukptMac(request: PedMacRequest): PedMacResult {
        val groupIndex = request.dukptGroupIndex
            ?: throw PedCryptoException("DUKPT group index is required for DUKPT MAC operation")
        val npMacMode = mapToNewposMacMode(request.algorithm)
            ?: throw PedCryptoException("Unsupported MAC algorithm mapping for DUKPT: ${request.algorithm}")

        // NewPOS dukptCalcMacResponse doesn't specify key variant or take IV
        if (request.iv != null) Log.w(TAG, "NewPOS dukptCalcMacResponse does not support explicit IV.")

        try {
            val mac = pedInstance.dukptCalcMacResponse(groupIndex, npMacMode, request.data)
                ?: throw PedCryptoException("DUKPT MAC calculation failed (returned null)")

            // Get KSN after operation if requested (Note: KSN likely increments even if dukptIncrementKsn is false)
            val finalDukptInfo = getDukptInfo(groupIndex) // Always get current KSN after op

            return PedMacResult(mac, finalDukptInfo)
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating DUKPT MAC", e)
            throw PedCryptoException("DUKPT MAC calculation failed: ${e.message}", e)
        }
    }

    // --- PIN Operations ---

    @Throws(PedException::class, PedTimeoutException::class, PedCancellationException::class)
    override suspend fun getPinBlock(request: PedPinRequest): PedPinResult { // Return type updated
        val keyIdx: Int
        val npKeySystem: NewposKeySystem

        if (request.isDukpt) {
            keyIdx = request.dukptGroupIndex ?: throw PedException("DUKPT group index missing for PIN block request")
            // Determine if DUKPT is AES or TDES to pick KeySystem
            // We still need to know this to call getPinBlock correctly.
            // How was the key loaded? Assuming TDES if not specified.
            // A better approach might be to pass the algorithm used for DUKPT in the request.
            Log.w(TAG, "Assuming DUKPT_DES KeySystem for DUKPT PIN block unless known otherwise.")
            npKeySystem = NewposKeySystem.DUKPT_DES // Or DUKPT_AES if known
        } else {
            keyIdx = request.keyIndex
            npKeySystem = mapToNewposKeySystem(request.keyType)
                ?: throw PedException("Unsupported key type for KeySystem mapping: ${request.keyType}")
            if (request.keyType != GenericKeyType.WORKING_PIN_KEY && request.keyType != GenericKeyType.MASTER_KEY) {
                Log.w(TAG, "Requesting PIN block with non-PIN key type: ${request.keyType}")
            }
        }

        val npPinFormat = mapToNewposPinFormat(request.format)
            ?: throw PedException("Unsupported PIN block format: ${request.format}")

        // Handle ISO Format 4 specific requirement for AES DUKPT
        if (request.format == GenericPinBlockFormatType.ISO9564_4) {
            if (!request.isDukpt || npKeySystem != NewposKeySystem.DUKPT_AES) {
                // If we defaulted to DUKPT_DES above, this will fail. Need context.
                throw PedException("ISO9564_4 format requires DUKPT AES key system, but current context is $npKeySystem")
            }
            // Re-apply the AES type setting as mapToNewposPinFormat is stateless
            try {
                // Determine AES type based on context (e.g., stored key info or default)
                val aesDukptType = NewposDukptType.DUKPT_TYPE_AES128 // Example default
                npPinFormat.setDukptAESType(aesDukptType)
                Log.d(TAG,"Set AES DUKPT type ${aesDukptType} for PIN_BLOCK_FORMAT_4")
            } catch(e: Exception) {
                throw PedException("Failed to set AES DUKPT type for PIN_BLOCK_FORMAT_4", e)
            }
        }


        val pan = request.pan ?: "" // NewPOS API requires a String for PAN

        // Set prerequisites
        try {
            pedInstance.setPinEntryTimeout(request.timeoutSeconds * 1000) // ms
            if(request.promptMessage != null) Log.w(TAG, "Generic promptMessage ignored, NewPOS handles prompts internally or via style/displayAmount.")
            // If amount display is needed, call pedInstance.setDisplayAmount(amount) here
        } catch(e: Exception) {
            Log.e(TAG, "Error setting prerequisites for getPinBlock", e)
            throw PedException("Failed to set prerequisites for PIN entry: ${e.message}", e)
        }

        // --- Corrected suspendCancellableCoroutine ---
        return suspendCancellableCoroutine { continuation ->
            Log.d(TAG, "Requesting PIN block: KS=$npKeySystem, KI=$keyIdx, Format=$npPinFormat, Len='${request.pinLengthConstraints}', PAN='$pan'")
            try {
                pedInstance.getPinBlock(
                    npKeySystem,
                    keyIdx,
                    npPinFormat,
                    request.pinLengthConstraints,
                    pan,
                    object : PinBlockCallback {
                        // This callback runs on a thread managed by the NewPOS SDK
                        override fun onPinBlock(resultCode: Int, pinBlockData: ByteArray?) {
                            Log.d(TAG, "onPinBlock: ResultCode=$resultCode, PinBlock=${pinBlockData?.size ?: "null"} bytes")
                            if (continuation.isActive) {
                                if (resultCode == 0 && pinBlockData != null) {
                                    // *** Correction: Do NOT call suspend function getDukptInfo here ***
                                    // Resume only with the direct result of the PIN operation.
                                    continuation.resume(PedPinResult(pinBlockData)) // Resume with PedPinResult containing only the block
                                } else {
                                    // Map resultCode to appropriate exception
                                    val exception = mapNewposPinResultCodeToException(resultCode)
                                    continuation.resumeWithException(exception)
                                }
                            } else {
                                Log.w(TAG, "onPinBlock received but coroutine no longer active.")
                            }
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Exception during getPinBlock call setup or execution", e)
                if (continuation.isActive) {
                    continuation.resumeWithException(PedException("Failed to initiate PIN Block retrieval: ${e.message}", e))
                }
            }

            // Handle coroutine cancellation
            continuation.invokeOnCancellation {
                Log.d(TAG, "getPinBlock coroutine cancelled. Attempting to cancel PED PIN entry.")
                cancelPinEntry() // Call the wrapper's cancel method
            }
        }
        // --- End Corrected suspendCancellableCoroutine ---
    }

    // Helper to map NewPOS return codes from PinBlockCallback to exceptions
    private fun mapNewposPinResultCodeToException(resultCode: Int): PedException {
        // Need documentation for NewPOS PED result codes (PedRetCode?)
        // Example mapping:
        return when (resultCode) {
            0 -> PedException("PIN block callback reported success (0) but data was null") // Should not happen if resultCode is 0
            // Add specific mappings based on PedRetCode documentation
            // e.g., someErrorCodeForTimeout -> PedTimeoutException("PIN entry timed out (Code: $resultCode)")
            // e.g., someErrorCodeForCancel -> PedCancellationException("PIN entry cancelled by user (Code: $resultCode)")
            // e.g., someErrorCodeForKeyError -> PedKeyException("PIN key error (Code: $resultCode)")
            else -> PedException("PIN entry failed with NewPOS PED code: $resultCode") // Generic fallback
        }
    }


    override fun cancelPinEntry() {
        try {
            pedInstance.cancelInputPin()
            Log.d(TAG, "cancelInputPin called on PED instance.")
        } catch (e: Exception) {
            Log.e(TAG, "Error calling cancelInputPin", e)
            // Optionally rethrow as PedException
        }
    }

    // --- UI Interaction ---

    override fun displayMessage(message: String, line: Int?, clearPrevious: Boolean) {
        Log.w(TAG, "displayMessage not directly supported. NewPOS PED handles prompts internally or via styles.")
        // NewPOS has setDisplayAmount, but not generic text display during PIN entry via a simple API.
        // It might be part of the custom PadView or style Bundle.
    }

    override fun setPinPadStyle(styleInfo: Map<String, Any>) {
        Log.d(TAG, "Attempting to set PIN pad style using Map.")
        // Convert the generic Map to an Android Bundle expected by NewPOS setPadViewStyle
        val bundle = Bundle()
        styleInfo.forEach { (key, value) ->
            try {
                when (value) {
                    is String -> bundle.putString(key, value)
                    is Int -> bundle.putInt(key, value)
                    is Boolean -> bundle.putBoolean(key, value)
                    is Float -> bundle.putFloat(key, value)
                    // Add other types as needed (Long, Double, Arrays?)
                    else -> Log.w(TAG, "Unsupported value type in styleInfo Map for key '$key': ${value::class.java.name}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error putting key '$key' with value '$value' into Bundle for setPadViewStyle", e)
            }
        }

        if (!bundle.isEmpty) {
            try {
                pedInstance.setPadViewStyle(bundle)
            } catch (e: Exception) {
                Log.e(TAG, "Error calling setPadViewStyle with Bundle", e)
                // Optionally throw PedException
            }
        } else {
            Log.w(TAG, "Style Map was empty or contained unsupported types, setPadViewStyle not called.")
        }
    }

    // --- Other Utilities ---

    override suspend fun getRandomBytes(length: Int): ByteArray {
        try {
            return pedInstance.getRandom(length)
                ?: throw PedException("Failed to get random bytes (returned null)")
        } catch (e: Exception) {
            Log.e(TAG, "Error getting random bytes", e)
            throw PedException("Failed to get random bytes: ${e.message}", e)
        }
    }

}