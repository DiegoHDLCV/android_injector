package com.vigatec.manufacturer.libraries.urovo.wrapper

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vigatec.manufacturer.base.controllers.ped.PedCancellationException
import com.vigatec.manufacturer.base.controllers.ped.PedException
import com.vigatec.manufacturer.base.controllers.ped.PedTimeoutException
import com.vigatec.manufacturer.base.models.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.sql.DriverManager.println
import java.util.*

/**
 * =================================================================================
 * QA Test Cases for Key Injection and Cryptography (Urovo Version)
 * =================================================================================
 *
 * Objective: Validate the complete lifecycle of key management in the PED.
 * Device Under Test: UrovoPedController
 *
 * Prerequisites:
 * 1. Physical Urovo POS device connected and operational.
 * 2. Urovo SDK correctly initialized.
 *
 * Test Scenarios (Master/Session):
 * 1. Injection of Data Key (WK-DATA) encrypted with KTK and its subsequent use.
 * 2. Injection of MAC Key (WK-MAC) encrypted with KTK and its subsequent use.
 * 3. Injection of PIN Key (WK-PIN) encrypted with KTK and verification.
 *
 * Test Scenarios (DUKPT):
 * 4. Injection of DUKPT Key (IPEK) in clear text and its subsequent use.
 * 5. (Omitted) Injection of DUKPT Key (IPEK) encrypted with KTK (not implemented in the controller).
 *
 * Additional Scenario:
 * 6. Verification of key deletion.
 *
 */
@RunWith(AndroidJUnit4::class)
class UrovoKeyInjectionTests {

    private lateinit var pedController: UrovoPedController

    // --- Key and Slot Definitions (identical to Aisino's for equivalence) ---

    // Master / Transport Key (KTK)
    private val masterKeyIndex = 10
    private val masterKeyType = KeyType.MASTER_KEY
    private val plainMasterKeyBytes = "CB79E0898F2907C24A13516BEAE904A2".hexToBytes()
    // Urovo does not use KCV for loading clear keys.

    // Data Working Key (encrypted with KTK)
    private val dataKeyIndex = 11
    private val dataKeyType = KeyType.WORKING_DATA_KEY
    private val plainDataKeyBytes = "892FF24F80C13461760E1349083862D9".hexToBytes()

    // MAC Working Key (encrypted with KTK)
    private val macKeyIndex = 12
    private val macKeyType = KeyType.WORKING_MAC_KEY
    private val plainMacKeyBytes = "F2AD8CA77AFD85C168A2DA022CD9F751".hexToBytes()

    // PIN Working Key (encrypted with KTK)
    private val pinKeyIndex = 13
    private val pinKeyType = KeyType.WORKING_PIN_KEY
    private val plainPinKeyBytes = "A46137A25289EFFD10C7EF0E0E167FA8".hexToBytes()

    // DUKPT Key (IPEK and KSN)
    private val dukptGroupIndex = 1
    private val plainIpekBytes = "63A1EA98B4342A20B29E326FFF50742E".hexToBytes()
    private val initialKsn = "F8765432100000000000".hexToBytes()
    // Urovo does not support KCV when loading IPEK in clear.

    // Common data for tests
    private val keyAlgorithm = KeyAlgorithm.DES_TRIPLE
    private val plaintextData = "1234567890ABCDEF".toByteArray(Charsets.UTF_8)
    private val samplePan = "4000123456789010"

    /**
     * Prepares the environment before each test.
     */
    @Before
    fun setUp() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        pedController = UrovoPedController(appContext)

        runBlocking {
            pedController.initializePed(appContext)
            println("SETUP: Clearing test key slots ($masterKeyIndex, $dataKeyIndex, $macKeyIndex, $pinKeyIndex, DUKPT $dukptGroupIndex)...")
            try { pedController.deleteKey(masterKeyIndex, masterKeyType) } catch (e: PedException) { /* Ignore */ }
            try { pedController.deleteKey(dataKeyIndex, dataKeyType) } catch (e: PedException) { /* Ignore */ }
            try { pedController.deleteKey(macKeyIndex, macKeyType) } catch (e: PedException) { /* Ignore */ }
            try { pedController.deleteKey(pinKeyIndex, pinKeyType) } catch (e: PedException) { /* Ignore */ }
            // Note: There is no explicit method to delete DUKPT keys; they are overwritten.
        }
    }

    /**
     * Cleans up the environment after each test.
     */
    @After
    fun tearDown() {
        runBlocking {
            println("TEARDOWN: Deleting keys after the test...")
            try { pedController.deleteKey(masterKeyIndex, masterKeyType) } catch (e: PedException) { /* Ignore */ }
            try { pedController.deleteKey(dataKeyIndex, dataKeyType) } catch (e: PedException) { /* Ignore */ }
            try { pedController.deleteKey(macKeyIndex, macKeyType) } catch (e: PedException) { /* Ignore */ }
            try { pedController.deleteKey(pinKeyIndex, pinKeyType) } catch (e: PedException) { /* Ignore */ }
        }
    }

    // ========================================================================
    // --- TEST CASES: MASTER/SESSION ---
    // ========================================================================

    /**
     * TEST 1: Encrypted DATA Key Injection and Usage Verification.
     */
    @Test
    fun testInjectEncryptedDataKeyAndVerifyUsage() = runBlocking {
        println("\n--- START TEST: MASTER/SESSION - DATA KEY INJECTION ---")
        // --- PREPARATION: Inject KTK ---
        println("STEP 1: Injecting Master Key (KTK) in clear text into slot $masterKeyIndex.")
        // The KCV is null because Urovo's `loadMainKey` API for clear key loading does not require it if the format is not specific.
        pedController.writeKeyPlain(masterKeyIndex, masterKeyType, keyAlgorithm, plainMasterKeyBytes, kcvBytes = null)
        assertTrue("The KTK must be present after injection", pedController.isKeyPresent(masterKeyIndex, masterKeyType))

        // --- EXECUTION: Inject the encrypted Data Key ---
        println("STEP 2: Injecting the encrypted DATA key into slot $dataKeyIndex using the KTK from slot $masterKeyIndex.")
        val encryptedKeyData = pedController.encrypt(PedCipherRequest(keyIndex = masterKeyIndex, keyType = masterKeyType, data = plainDataKeyBytes, algorithm = keyAlgorithm, mode = BlockCipherMode.ECB, iv = null, encrypt = true)).resultData
        // Urovo's `loadWorkKey` API expects the encrypted key and the KCV (in clear) to be passed. The KCV is optional (null).
        val writeSuccess = pedController.writeKey(
            keyIndex = dataKeyIndex,
            keyType = dataKeyType,
            keyAlgorithm = keyAlgorithm,
            keyData = PedKeyData(encryptedKeyData), // KCV is null
            transportKeyIndex = masterKeyIndex,
            transportKeyType = masterKeyType
        )
        assertTrue("The encrypted data key injection must be successful", writeSuccess)

        // --- VERIFICATION: Use the injected data key ---
        println("STEP 3: Verifying that the DATA key in slot $dataKeyIndex is operational.")
        assertTrue("The data key must be present in slot $dataKeyIndex", pedController.isKeyPresent(dataKeyIndex, dataKeyType))

        val encryptedData = pedController.encrypt(PedCipherRequest(keyIndex = dataKeyIndex, keyType = dataKeyType, data = plaintextData, algorithm = keyAlgorithm, mode = BlockCipherMode.ECB, iv = null, encrypt = true)).resultData
        val decryptedData = pedController.decrypt(PedCipherRequest(keyIndex = dataKeyIndex, keyType = dataKeyType, data = encryptedData, algorithm = keyAlgorithm, mode = BlockCipherMode.ECB, iv = null, encrypt = false)).resultData

        assertFalse("The encrypted data must not be equal to the clear data", plaintextData.contentEquals(encryptedData))
        assertArrayEquals("The decrypted data must match the original", plaintextData, decryptedData)

        println("SUCCESS: The encrypted DATA key injection and usage flow has completed successfully.")
    }

    /**
     * TEST 2: Encrypted MAC Key Injection and Usage Verification.
     */
    @Test
    fun testInjectEncryptedMacKeyAndVerifyUsage() = runBlocking {
        println("\n--- START TEST: MASTER/SESSION - MAC KEY INJECTION ---")
        // --- PREPARATION: Inject KTK ---
        println("STEP 1: Injecting Master Key (KTK) in clear text into slot $masterKeyIndex.")
        pedController.writeKeyPlain(masterKeyIndex, masterKeyType, keyAlgorithm, plainMasterKeyBytes, kcvBytes = null)
        assertTrue("The KTK must be present", pedController.isKeyPresent(masterKeyIndex, masterKeyType))

        // --- EXECUTION: Inject the encrypted MAC Key ---
        println("STEP 2: Injecting the encrypted MAC key into slot $macKeyIndex.")
        val encryptedKeyData = pedController.encrypt(PedCipherRequest(keyIndex = masterKeyIndex, keyType = masterKeyType, data = plainMacKeyBytes, algorithm = keyAlgorithm, mode = BlockCipherMode.ECB, iv = null, encrypt = true)).resultData
        val writeSuccess = pedController.writeKey(macKeyIndex, macKeyType, keyAlgorithm, PedKeyData(encryptedKeyData), masterKeyIndex, masterKeyType)
        assertTrue("The encrypted MAC key injection must be successful", writeSuccess)

        // --- VERIFICATION: Use the injected MAC key ---
        println("STEP 3: Verifying that the MAC key in slot $macKeyIndex is operational.")
        assertTrue("The MAC key must be present", pedController.isKeyPresent(macKeyIndex, macKeyType))

        val macRequest = PedMacRequest(keyIndex = macKeyIndex, keyType = macKeyType, algorithm = MacAlgorithm.RETAIL_MAC_ANSI_X9_19, data = plaintextData, isDukpt = false, dukptGroupIndex = null)
        val macResult = pedController.calculateMac(macRequest)

        assertNotNull("The MAC calculation result must not be null", macResult)
        assertEquals("The resulting MAC must be 8 bytes long", 8, macResult.mac.size)
        println("Calculated MAC (Hex): ${macResult.mac.toHexString()}")

        println("SUCCESS: The encrypted MAC key injection and usage flow has completed successfully.")
    }

    /**
     * TEST 3: Encrypted PIN Key Injection and Verification.
     */
    @Test
    fun testInjectEncryptedPinKeyAndVerifyUsage() = runBlocking {
        println("\n--- START TEST: MASTER/SESSION - PIN KEY INJECTION ---")
        // --- PREPARATION: Inject KTK ---
        println("STEP 1: Injecting Master Key (KTK) in clear text into slot $masterKeyIndex.")
        pedController.writeKeyPlain(masterKeyIndex, masterKeyType, keyAlgorithm, plainMasterKeyBytes, kcvBytes = null)
        assertTrue("The KTK must be present", pedController.isKeyPresent(masterKeyIndex, masterKeyType))

        // --- EXECUTION: Inject the encrypted PIN Key ---
        println("STEP 2: Injecting the encrypted PIN key into slot $pinKeyIndex.")
        val encryptedKeyData = pedController.encrypt(PedCipherRequest(keyIndex = masterKeyIndex, keyType = masterKeyType, data = plainPinKeyBytes, algorithm = keyAlgorithm, mode = BlockCipherMode.ECB, iv = null, encrypt = true)).resultData
        val writeSuccess = pedController.writeKey(pinKeyIndex, pinKeyType, keyAlgorithm, PedKeyData(encryptedKeyData), masterKeyIndex, masterKeyType)
        assertTrue("The encrypted PIN key injection must be successful", writeSuccess)

        // --- VERIFICATION: Check for key presence ---
        println("STEP 3: Verifying that the PIN key in slot $pinKeyIndex exists.")
        assertTrue("The PIN key must be present after injection", pedController.isKeyPresent(pinKeyIndex, pinKeyType))

        println("SUCCESS: The encrypted PIN key injection flow has completed successfully.")
    }

    // ========================================================================
    // --- TEST CASES: DUKPT ---
    // ========================================================================

    /**
     * TEST 4: DUKPT Key (IPEK) Injection in Clear Text and Usage Verification.
     */
    @Test
    fun testDukptPlainKeyInjectionAndVerifyUsage() = runBlocking {
        println("\n--- START TEST: DUKPT - IPEK INJECTION IN CLEAR TEXT ---")

        // --- EXECUTION: Inject IPEK and KSN ---
        println("STEP 1: Injecting IPEK and KSN into DUKPT group $dukptGroupIndex.")
        // Urovo's `downloadKeyDukpt` API does not accept KCV.
        // The UrovoPedController assumes that keyBytes are the BDK/IPEK.
        val writeSuccess = pedController.writeDukptInitialKey(dukptGroupIndex, keyAlgorithm, plainIpekBytes, initialKsn, keyChecksum = null)
        assertTrue("The IPEK injection in clear text must be successful", writeSuccess)

        // --- VERIFICATION: Encrypt data and check KSN increment ---
        println("STEP 2: Verifying that the initial KSN has been loaded.")
        val dukptInfoBefore = pedController.getDukptInfo(dukptGroupIndex)
        assertNotNull("The initial DUKPT info must not be null.", dukptInfoBefore)
        // Urovo's API returns a 10-byte KSN for TDES.
        println("Initial KSN (read): ${dukptInfoBefore!!.ksn.toHexString()}")

        println("STEP 3: Encrypting data to verify functionality and KSN increment.")
        val encryptRequest = PedCipherRequest(
            isDukpt = true,
            dukptGroupIndex = dukptGroupIndex,
            dukptKeyVariant = DukptKeyVariant.DATA_ENCRYPT,
            data = plaintextData,
            algorithm = keyAlgorithm,
            mode = BlockCipherMode.ECB,
            iv = null,
            encrypt = true,
            keyIndex = 0,
            keyType = KeyType.WORKING_DATA_KEY
        )
        val encryptionResult = pedController.encrypt(encryptRequest)

        assertNotNull("The DUKPT encryption result cannot be null.", encryptionResult)
        assertFalse("The encrypted data must not be equal to the clear data", plaintextData.contentEquals(encryptionResult.resultData))

        val ksnAfter = encryptionResult.finalDukptInfo?.ksn
        assertNotNull("The final KSN cannot be null.", ksnAfter)
        println("Final KSN (read):   ${ksnAfter!!.toHexString()}")
        assertFalse("The KSN after encryption must be different from the initial KSN", dukptInfoBefore.ksn.contentEquals(ksnAfter))

        println("SUCCESS: The DUKPT key injection and usage flow in clear text has worked.")
    }


    // ========================================================================
    // --- TEST CASE: MANAGEMENT ---
    // ========================================================================

    /**
     * TEST 6: Key Deletion Verification.
     * Note: The Urovo SDK does not have a "deleteAllKeys" method, so this test verifies
     * that keys can be deleted individually.
     */
    @Test
    fun testKeyDeletionAndVerify() = runBlocking {
        println("\n--- START TEST: KEY DELETION ---")

        // --- PREPARATION: Inject a key ---
        println("STEP 1: Injecting a master key for the deletion test.")
        pedController.writeKeyPlain(masterKeyIndex, masterKeyType, keyAlgorithm, plainMasterKeyBytes, kcvBytes = null)
        assertTrue("The key to be deleted must be present initially", pedController.isKeyPresent(masterKeyIndex, masterKeyType))

        // --- EXECUTION: Delete the key ---
        println("STEP 2: Executing deleteKey().")
        val deleteSuccess = pedController.deleteKey(masterKeyIndex, masterKeyType)
        assertTrue("The delete operation must be successful", deleteSuccess)

        // --- VERIFICATION: Check that the key has been deleted ---
        println("STEP 3: Verifying that the key no longer exists.")
        assertFalse("The key must not be present after deletion", pedController.isKeyPresent(masterKeyIndex, masterKeyType))

        println("SUCCESS: The key deletion functionality has worked correctly.")
    }

    // ========================================================================
    // --- TEST CASES: PIN RETRIEVAL (INTERACTIVE) ---
    // ========================================================================


    // --- Utilities ---
    private fun String.hexToBytes(): ByteArray {
        check(length % 2 == 0) { "Hexadecimal string must have an even length" }
        return chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

    private fun ByteArray.toHexString(): String {
        return joinToString("") { "%02X".format(it).uppercase(Locale.ROOT) }
    }
}