package com.example.manufacturer.base.controllers.ped

import android.app.Application
import com.example.manufacturer.base.models.*

/**
 * Generic interface for interacting with a Pin Entry Device (PED).
 */
interface IPedController {

    // --- Lifecycle & Status ---
    suspend fun initializePed(application: Application): Boolean
    fun releasePed()
    @Throws(PedException::class)
    suspend fun getStatus(): PedStatusInfo
    @Throws(PedException::class)
    suspend fun getConfig(): PedConfigInfo

    // --- Key Management ---
    @Throws(PedException::class)
    suspend fun writeKey(
        keyIndex: Int,
        keyType: KeyType,
        keyAlgorithm: KeyAlgorithm, // <<< AÑADIDO: Algoritmo de la llave que se está cargando
        keyData: PedKeyData,
        transportKeyIndex: Int? = null,
        transportKeyType: KeyType? = null
    ): Boolean

    @Throws(PedException::class)
    suspend fun writeKeyPlain(
        keyIndex: Int,
        keyType: KeyType,
        keyAlgorithm: KeyAlgorithm,
        keyBytes: ByteArray,
        kcvBytes: ByteArray?
    ): Boolean

    @Throws(PedException::class)
    suspend fun deleteKey(keyIndex: Int, keyType: KeyType): Boolean
    @Throws(PedException::class)
    suspend fun deleteAllKeys(): Boolean
    @Throws(PedException::class)
    suspend fun isKeyPresent(keyIndex: Int, keyType: KeyType): Boolean
    @Throws(PedException::class)
    suspend fun getKeyInfo(keyIndex: Int, keyType: KeyType): PedKeyInfo?

    // --- DUKPT Management ---
    @Throws(PedException::class)
    suspend fun writeDukptInitialKey(
        groupIndex: Int,
        keyAlgorithm: KeyAlgorithm,
        keyBytes: ByteArray,
        initialKsn: ByteArray
    ): Boolean
    @Throws(PedException::class)
    suspend fun getDukptInfo(groupIndex: Int): DukptInfo?
    @Throws(PedException::class)
    suspend fun incrementDukptKsn(groupIndex: Int): Boolean

    // --- Cryptographic Operations ---
    @Throws(PedException::class)
    suspend fun encrypt(request: PedCipherRequest): PedCipherResult
    @Throws(PedException::class)
    suspend fun decrypt(request: PedCipherRequest): PedCipherResult
    @Throws(PedException::class)
    suspend fun calculateMac(request: PedMacRequest): PedMacResult
    @Throws(PedException::class)
    suspend fun verifyMac(request: PedMacRequest, expectedMac: ByteArray): Boolean {
        val calculatedResult = calculateMac(request)
        return calculatedResult.mac.contentEquals(expectedMac)
    }

    // --- PIN Operations ---
    @Throws(PedException::class, PedTimeoutException::class, PedCancellationException::class)
    suspend fun getPinBlock(request: PedPinRequest): PedPinResult
    fun cancelPinEntry()

    // --- UI Interaction ---
    fun displayMessage(message: String, line: Int? = null, clearPrevious: Boolean = false)
    fun setPinPadStyle(styleInfo: Map<String, Any>)

    // --- Other Utilities ---
    @Throws(PedException::class)
    suspend fun getRandomBytes(length: Int): ByteArray
}

// --- Custom Exceptions (sin cambios) ---
open class PedException(message: String, cause: Throwable? = null) : Exception(message, cause)
class PedTimeoutException(message: String = "PED operation timed out", cause: Throwable? = null) : PedException(message, cause)
class PedCancellationException(message: String = "PED operation cancelled by user", cause: Throwable? = null) : PedException(message, cause)
class PedKeyException(message: String, cause: Throwable? = null) : PedException(message, cause)
class PedCryptoException(message: String, cause: Throwable? = null) : PedException(message, cause)