package com.example.manufacturer.base.controllers.ped

import android.os.Bundle // Keep for style bundle, but consider generic type
import com.example.manufacturer.base.models.* // Import generic types

/**
 * Generic interface for interacting with a Pin Entry Device (PED).
 * Provides common functionalities for key management, cryptographic operations,
 * and secure PIN entry, abstracting away manufacturer-specific details.
 *
 * Implementations should handle mapping to the specific device SDK and manage errors appropriately,
 * typically by throwing exceptions for failures.
 */
interface IPedController {

    // --- Lifecycle & Status ---

    /**
     * Performs any necessary initialization steps for the PED controller or hardware.
     * Should be called before other operations.
     * @return True if initialization was successful, false otherwise. Implementations might throw exceptions on critical failures.
     */
    suspend fun initializePed(): Boolean

    /**
     * Releases any resources held by the PED controller.
     */
    fun releasePed()

    /**
     * Retrieves the current status of the PED.
     * @return A [PedStatusInfo] object containing status details.
     * @throws PedException On communication errors or if the PED reports a critical failure state.
     */
    @Throws(PedException::class)
    suspend fun getStatus(): PedStatusInfo

    /**
     * Retrieves configuration or identification information from the PED.
     * @return A [PedConfigInfo] object containing configuration details.
     * @throws PedException On communication errors.
     */
    @Throws(PedException::class)
    suspend fun getConfig(): PedConfigInfo

    // --- Key Management ---

    /**
     * Writes a key into the PED. The key data might be plaintext or encrypted under a transport key.
     *
     * @param keyIndex The target index where the key will be stored.
     * @param keyType The type/purpose of the key being written.
     * @param keyData The key material ([PedKeyData]), potentially including a KCV.
     * @param transportKeyIndex If the keyData is encrypted, the index of the transport (wrapping) key.
     * @param transportKeyType If the keyData is encrypted, the type of the transport key.
     * @return True if the key was written successfully.
     * @throws PedException On errors like index out of bounds, invalid key type, KCV mismatch, transport key failure, etc.
     */
    @Throws(PedException::class)
    suspend fun writeKey(
        keyIndex: Int,
        keyType: KeyType,
        keyData: PedKeyData,
        transportKeyIndex: Int? = null,
        transportKeyType: KeyType? = null
    ): Boolean

    /**
     * Writes a key into the PED directly from plaintext bytes.
     * Use with extreme caution, typically only for initial Master Key loading in a secure environment.
     *
     * @param keyIndex The target index where the key will be stored.
     * @param keyType The type/purpose of the key being written (often MASTER_KEY).
     * @param keyAlgorithm The algorithm associated with the key being loaded.
     * @param keyBytes The plaintext key bytes.
     * @return True if the key was written successfully.
     * @throws PedException On errors or if plaintext loading is not permitted.
     */
    @Throws(PedException::class)
    suspend fun writeKeyPlain(
        keyIndex: Int,
        keyType: KeyType,
        keyAlgorithm: KeyAlgorithm,
        keyBytes: ByteArray
    ): Boolean

    /**
     * Deletes a specific key from the PED.
     *
     * @param keyIndex The index of the key to delete.
     * @param keyType The type of the key to delete.
     * @return True if the key was deleted successfully.
     * @throws PedException On errors or if the key doesn't exist.
     */
    @Throws(PedException::class)
    suspend fun deleteKey(keyIndex: Int, keyType: KeyType): Boolean

    /**
     * Deletes all user-loaded keys from the PED. This is a destructive operation.
     * Some implementations might allow specifying which types of keys to delete.
     * @return True if the operation was successful.
     * @throws PedException On errors.
     */
    @Throws(PedException::class)
    suspend fun deleteAllKeys(): Boolean

    /**
     * Checks if a key exists at the specified index and type.
     *
     * @param keyIndex The key index.
     * @param keyType The key type.
     * @return True if the key exists, false otherwise.
     * @throws PedException On communication errors.
     */
    @Throws(PedException::class)
    suspend fun isKeyPresent(keyIndex: Int, keyType: KeyType): Boolean

    /**
     * Retrieves basic information about a stored key.
     *
     * @param keyIndex The key index.
     * @param keyType The key type.
     * @return A [PedKeyInfo] object if the key exists, null otherwise.
     * @throws PedException On communication errors.
     */
    @Throws(PedException::class)
    suspend fun getKeyInfo(keyIndex: Int, keyType: KeyType): PedKeyInfo?

    // --- DUKPT Management ---

    /**
     * Writes an initial DUKPT key (IPEK/BDK) into a specific group index.
     *
     * @param groupIndex The DUKPT group index (e.g., 1-10).
     * @param keyAlgorithm The algorithm for the DUKPT scheme (e.g., TDES, AES).
     * @param keyBytes The plaintext IPEK/BDK bytes.
     * @param initialKsn The initial Key Serial Number (KSN) associated with this key.
     * @return True if the DUKPT key was loaded successfully.
     * @throws PedException On errors.
     */
    @Throws(PedException::class)
    suspend fun writeDukptInitialKey(
        groupIndex: Int,
        keyAlgorithm: KeyAlgorithm,
        keyBytes: ByteArray,
        initialKsn: ByteArray // KSN format might vary slightly
    ): Boolean

    /**
     * Retrieves the current DUKPT status (KSN and counter) for a group.
     *
     * @param groupIndex The DUKPT group index.
     * @return A [DukptInfo] object containing the current KSN and potentially the counter.
     * @throws PedException If the group index is invalid or on communication errors.
     */
    @Throws(PedException::class)
    suspend fun getDukptInfo(groupIndex: Int): DukptInfo?

    /**
     * Manually increments the KSN counter for a specific DUKPT group.
     * Use when the PED does not automatically increment or if needed for resynchronization.
     *
     * @param groupIndex The DUKPT group index.
     * @return True if the increment was successful.
     * @throws PedException If the operation is not supported or fails.
     */
    @Throws(PedException::class)
    suspend fun incrementDukptKsn(groupIndex: Int): Boolean

    // --- Cryptographic Operations ---

    /**
     * Performs encryption using a key stored in the PED.
     *
     * @param request Parameters defining the encryption operation ([PedCipherRequest] with encrypt=true).
     * @return A [PedCipherResult] containing the encrypted data and final DUKPT info if applicable.
     * @throws PedException On errors like invalid key, incompatible algorithm/mode, data length issues.
     */
    @Throws(PedException::class)
    suspend fun encrypt(request: PedCipherRequest): PedCipherResult

    /**
     * Performs decryption using a key stored in the PED.
     *
     * @param request Parameters defining the decryption operation ([PedCipherRequest] with encrypt=false).
     * @return A [PedCipherResult] containing the decrypted data and final DUKPT info if applicable.
     * @throws PedException On errors like invalid key, incompatible algorithm/mode, data length issues, crypto failures.
     */
    @Throws(PedException::class)
    suspend fun decrypt(request: PedCipherRequest): PedCipherResult

    /**
     * Calculates a Message Authentication Code (MAC) over the provided data.
     *
     * @param request Parameters defining the MAC calculation ([PedMacRequest]).
     * @return A [PedMacResult] containing the calculated MAC and final DUKPT info if applicable.
     * @throws PedException On errors like invalid key, incompatible algorithm, data length issues.
     */
    @Throws(PedException::class)
    suspend fun calculateMac(request: PedMacRequest): PedMacResult

    /**
     * Verifies a Message Authentication Code (MAC).
     * (Optional - can sometimes be implemented by recalculating and comparing).
     *
     * @param request Parameters defining the MAC calculation ([PedMacRequest]).
     * @param expectedMac The MAC value to verify against.
     * @return True if the calculated MAC matches the expected MAC.
     * @throws PedException On calculation errors.
     */
    @Throws(PedException::class)
    suspend fun verifyMac(request: PedMacRequest, expectedMac: ByteArray): Boolean {
        // Default implementation: Calculate and compare. Specific PEDs might have direct verify commands.
        val calculatedResult = calculateMac(request)
        return calculatedResult.mac.contentEquals(expectedMac)
    }


    // --- PIN Operations ---

    /**
     * Initiates secure PIN entry and returns the encrypted PIN block.
     * This is an asynchronous operation that interacts with the user via the PED hardware.
     *
     * @param request Parameters defining the PIN entry process ([PedPinRequest]).
     * @return A [PedPinResult] containing the encrypted PIN block and final DUKPT info if applicable.
     * @throws PedException On errors like user cancellation, timeout, invalid key, hardware failure.
     * @throws PedTimeoutException Specifically for timeouts during PIN entry.
     * @throws PedCancellationException Specifically for user cancellation during PIN entry.
     */
    @Throws(PedException::class, PedTimeoutException::class, PedCancellationException::class)
    suspend fun getPinBlock(request: PedPinRequest): PedPinResult

    /**
     * Attempts to cancel an ongoing PIN entry operation initiated by [getPinBlock].
     * Success is not guaranteed and depends on the PED state and SDK support.
     */
    fun cancelPinEntry()


    // --- UI Interaction (Potentially less generic) ---

    /**
     * Displays a message on the PED screen, if supported.
     *
     * @param message The text message to display.
     * @param line Optional line number (behavior depends on PED).
     * @param clearPrevious Whether to clear previous messages before displaying.
     */
    fun displayMessage(message: String, line: Int? = null, clearPrevious: Boolean = false)

    /**
     * Sets the style of the PIN Pad UI (e.g., layout, colors).
     * The format of styleInfo is implementation-dependent. Using a Map or custom class
     * might be more generic than Bundle.
     *
     * @param styleInfo Manufacturer-specific style information (e.g., an Android Bundle or a Map).
     */
    fun setPinPadStyle(styleInfo: Map<String, Any>) // Using Map instead of Bundle

    // --- Other Utilities ---

    /**
     * Retrieves cryptographically secure random bytes generated by the PED hardware.
     *
     * @param length The number of random bytes requested.
     * @return A ByteArray containing the random bytes.
     * @throws PedException If the PED cannot generate random data or on communication errors.
     */
    @Throws(PedException::class)
    suspend fun getRandomBytes(length: Int): ByteArray

}

// --- Custom Exceptions ---

/** Base exception for PED related errors. */
open class PedException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Exception indicating a timeout occurred during a PED operation (e.g., PIN entry). */
class PedTimeoutException(message: String = "PED operation timed out", cause: Throwable? = null) : PedException(message, cause)

/** Exception indicating the user cancelled a PED operation (e.g., PIN entry). */
class PedCancellationException(message: String = "PED operation cancelled by user", cause: Throwable? = null) : PedException(message, cause)

/** Exception indicating an issue with key management (e.g., invalid index, key not found, KCV mismatch). */
class PedKeyException(message: String, cause: Throwable? = null) : PedException(message, cause)

/** Exception indicating a cryptographic operation failure (e.g., MAC verification failed, decryption error). */
class PedCryptoException(message: String, cause: Throwable? = null) : PedException(message, cause)
