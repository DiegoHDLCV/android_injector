package com.vigatec.manufacturer.libraries.newpos.wrapper

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vigatec.manufacturer.base.controllers.ped.PedCancellationException
import com.vigatec.manufacturer.base.controllers.ped.PedException
import com.vigatec.manufacturer.base.controllers.ped.PedTimeoutException
import com.vigatec.manufacturer.base.models.*
import com.vigatec.manufacturer.libraries.newpos.NewposKeyManager
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

@RunWith(AndroidJUnit4::class)
class NewposKeyInjectionTests {

    companion object {
        private lateinit var pedController: NewposPedController

        @BeforeClass
        @JvmStatic
        fun initializeSdk() {
            println("--- @BeforeClass: Initializing NewposKeyManager SDK ---")
            val appContext = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
            runBlocking {
                NewposKeyManager.initialize(appContext)
            }
            pedController = NewposKeyManager.getPedController() as? NewposPedController
                ?: throw IllegalStateException("PED Controller could not be initialized. Check KeyManager.")
            println("--- SDK Initialized Successfully ---")
        }

        @AfterClass
        @JvmStatic
        fun releaseSdk() {
            println("--- @AfterClass: Releasing NewposKeyManager SDK ---")
            NewposKeyManager.release()
            println("--- SDK Released ---")
        }
    }

    // --- Definiciones de Claves y Slots ---
    private val masterKeyIndex = 3
    private val masterKeyType = KeyType.MASTER_KEY
    private val plainMasterKeyBytes = "CB79E0898F2907C24A13516BEAE904A2".hexToBytes()

    // Nueva definición para Transport Key
    private val transportKeyIndex = 15
    private val transportKeyType = KeyType.TRANSPORT_KEY
    private val plainTransportKeyBytes = "A1B2C3D4E5F60708090A0B0C0D0E0F10".hexToBytes()

    private val dataKeyIndex = 11
    private val dataKeyType = KeyType.WORKING_DATA_KEY
    private val plainDataKeyBytes = "892FF24F80C13461760E1349083862D9".hexToBytes()
    private val macKeyIndex = 12
    private val macKeyType = KeyType.WORKING_MAC_KEY
    private val plainMacKeyBytes = "F2AD8CA77AFD85C168A2DA022CD9F751".hexToBytes()
    private val pinKeyIndex = 13
    private val pinKeyType = KeyType.WORKING_PIN_KEY
    private val plainPinKeyBytes = "A46137A25289EFFD10C7EF0E0E167FA8".hexToBytes()
    private val dukptGroupIndex = 1
    private val plainIpekBytes = "63A1EA98B4342A20B29E326FFF50742E".hexToBytes()
    private val initialKsn = "F8765432100000000000".hexToBytes()
    private val keyAlgorithm = KeyAlgorithm.DES_TRIPLE
    private val samplePan = "4000123456789010"
    private val plaintextForCipher = "0123456789ABCDEF".toByteArray(Charsets.UTF_8)

    @Before
    fun setUp() {
        runBlocking {
            println("SETUP: Deleting all keys before the test...")
            pedController.deleteAllKeys()
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            println("TEARDOWN: Deleting all keys after the test...")
            try {
                pedController.deleteAllKeys()
            } catch (e: Exception) {
                println("Warning: Could not clear keys in tearDown: ${e.message}")
            }
        }
    }

    // ========================================================================
    // --- NUEVO TEST: VERIFICACIÓN DE CIFRADO SIMÉTRICO ---
    // ========================================================================

    @Test
    fun testTdesEncryptionDecryptionWithWorkingKey() = runBlocking {
        println("\n--- START TEST: NEWPOS - SYMMETRIC CIPHER (3DES) ---")
        // --- PREPARACIÓN: Inyectar una llave de datos en texto plano ---
        println("STEP 1: Injecting a plain text WORKING_DATA_KEY into slot $dataKeyIndex.")
        pedController.writeKeyPlain(dataKeyIndex, dataKeyType, keyAlgorithm, plainDataKeyBytes, null)
        assertTrue("The data key must be present after plain text injection", pedController.isKeyPresent(dataKeyIndex, dataKeyType))

        // --- EJECUCIÓN: Cifrar y Descifrar con la llave de trabajo ---
        println("STEP 2: Encrypting data with the injected working key.")
        val encryptRequest = PedCipherRequest(dataKeyIndex, dataKeyType, plaintextForCipher, keyAlgorithm, BlockCipherMode.ECB, null, true)
        val encryptionResult = pedController.encrypt(encryptRequest)
        val ciphertext = encryptionResult.resultData

        println("  -> Plaintext (Hex): ${plaintextForCipher.toHexString()}")
        println("  -> Ciphertext (Hex): ${ciphertext.toHexString()}")
        assertFalse("Ciphertext must not be equal to plaintext", plaintextForCipher.contentEquals(ciphertext))

        println("STEP 3: Decrypting the ciphertext with the same working key.")
        val decryptRequest = PedCipherRequest(dataKeyIndex, dataKeyType, ciphertext, keyAlgorithm, BlockCipherMode.ECB, null, false)
        val decryptionResult = pedController.decrypt(decryptRequest)
        val decryptedText = decryptionResult.resultData
        println("  -> Decrypted (Hex): ${decryptedText.toHexString()}")

        // --- VERIFICACIÓN ---
        assertArrayEquals("Decrypted data must match the original plaintext", plaintextForCipher, decryptedText)
        println("SUCCESS: Symmetric encryption and decryption flow completed successfully.")
    }

    // ========================================================================
    // --- NUEVOS TESTS: INYECCIÓN DE WORKING KEYS Y VALIDACIÓN KCV ---
    // ========================================================================

    @Test
    fun testInjectWorkingDataKeyPlainAndVerifyKcv() = runBlocking {
        println("\n--- START TEST: NEWPOS - INJECT WORKING DATA KEY PLAIN + VERIFY KCV ---")

        // Llave de prueba en texto plano (3DES - 24 bytes)
        val testDataKeyBytes = "0123456789ABCDEFFEDCBA98765432100123456789ABCDEF".hexToBytes()
        val testKeySlot = 5
        val testKeyType = KeyType.WORKING_DATA_KEY
        val testAlgorithm = KeyAlgorithm.DES_TRIPLE

        println("STEP 1: Injecting WORKING_DATA_KEY in plain text.")
        println("  -> Key Slot: $testKeySlot")
        println("  -> Key Algorithm: $testAlgorithm")
        println("  -> Key Value (Hex): ${testDataKeyBytes.toHexString()}")

        // Inyectar la llave en texto plano
        val writeSuccess = pedController.writeKeyPlain(testKeySlot, testKeyType, testAlgorithm, testDataKeyBytes, null)
        assertTrue("Working key injection must succeed", writeSuccess)

        println("STEP 2: Verifying the key is present in the PED.")
        val isPresent = pedController.isKeyPresent(testKeySlot, testKeyType)
        assertTrue("Working key must be present after injection", isPresent)

        println("STEP 3: Calculating KCV by encrypting a block of zeros.")
        // Crear bloque de ceros (8 bytes para DES/3DES)
        val zeroBlock = ByteArray(8) { 0 }
        println("  -> Zero Block (8 bytes): ${zeroBlock.toHexString()}")

        // Cifrar bloque de ceros
        val encryptRequest = PedCipherRequest(
            keyIndex = testKeySlot,
            keyType = testKeyType,
            data = zeroBlock,
            algorithm = testAlgorithm,
            mode = BlockCipherMode.ECB,
            iv = null,
            encrypt = true
        )

        val encryptResult = pedController.encrypt(encryptRequest)
        val encryptedZeros = encryptResult.resultData
        println("  -> Encrypted Zeros (Hex): ${encryptedZeros.toHexString()}")

        // Tomar primeros 3 bytes como KCV
        val calculatedKcv = encryptedZeros.take(3).toByteArray()
        println("  -> Calculated KCV (first 3 bytes): ${calculatedKcv.toHexString()}")

        println("STEP 4: Calculating expected KCV using software crypto.")
        val expectedKcv = softwareCalculateKcv(testDataKeyBytes, testAlgorithm)
        println("  -> Expected KCV (software): ${expectedKcv.toHexString()}")

        // Verificar que coincidan
        assertArrayEquals("KCV calculated by PED must match software KCV", expectedKcv, calculatedKcv)
        println("SUCCESS: Working key injected and KCV verified correctly.")
    }

    @Test
    fun testInjectWorkingPinKeyPlainAndVerifyKcv() = runBlocking {
        println("\n--- START TEST: NEWPOS - INJECT WORKING PIN KEY PLAIN + VERIFY KCV ---")

        // Llave de prueba en texto plano (3DES - 24 bytes)
        val testPinKeyBytes = "FEDCBA98765432100123456789ABCDEFFEDCBA9876543210".hexToBytes()
        val testKeySlot = 6
        val testKeyType = KeyType.WORKING_PIN_KEY
        val testAlgorithm = KeyAlgorithm.DES_TRIPLE

        println("STEP 1: Injecting WORKING_PIN_KEY in plain text.")
        println("  -> Key Slot: $testKeySlot")
        println("  -> Key Algorithm: $testAlgorithm")
        println("  -> Key Value (Hex): ${testPinKeyBytes.toHexString()}")

        // Inyectar la llave en texto plano
        val writeSuccess = pedController.writeKeyPlain(testKeySlot, testKeyType, testAlgorithm, testPinKeyBytes, null)
        assertTrue("Working PIN key injection must succeed", writeSuccess)

        println("STEP 2: Verifying the key is present in the PED.")
        val isPresent = pedController.isKeyPresent(testKeySlot, testKeyType)
        assertTrue("Working PIN key must be present after injection", isPresent)

        println("STEP 3: Calculating KCV by encrypting a block of zeros.")
        val zeroBlock = ByteArray(8) { 0 }
        println("  -> Zero Block (8 bytes): ${zeroBlock.toHexString()}")

        // Cifrar bloque de ceros
        val encryptRequest = PedCipherRequest(
            keyIndex = testKeySlot,
            keyType = testKeyType,
            data = zeroBlock,
            algorithm = testAlgorithm,
            mode = BlockCipherMode.ECB,
            iv = null,
            encrypt = true
        )

        val encryptResult = pedController.encrypt(encryptRequest)
        val encryptedZeros = encryptResult.resultData
        println("  -> Encrypted Zeros (Hex): ${encryptedZeros.toHexString()}")

        val calculatedKcv = encryptedZeros.take(3).toByteArray()
        println("  -> Calculated KCV (first 3 bytes): ${calculatedKcv.toHexString()}")

        println("STEP 4: Calculating expected KCV using software crypto.")
        val expectedKcv = softwareCalculateKcv(testPinKeyBytes, testAlgorithm)
        println("  -> Expected KCV (software): ${expectedKcv.toHexString()}")

        assertArrayEquals("KCV calculated by PED must match software KCV", expectedKcv, calculatedKcv)
        println("SUCCESS: Working PIN key injected and KCV verified correctly.")
    }

    @Test
    fun testInjectWorkingMacKeyPlainAndVerifyKcv() = runBlocking {
        println("\n--- START TEST: NEWPOS - INJECT WORKING MAC KEY PLAIN + VERIFY KCV ---")

        // Llave de prueba en texto plano (3DES - 24 bytes)
        val testMacKeyBytes = "AABBCCDDEEFF00112233445566778899AABBCCDDEEFF0011".hexToBytes()
        val testKeySlot = 7
        val testKeyType = KeyType.WORKING_MAC_KEY
        val testAlgorithm = KeyAlgorithm.DES_TRIPLE

        println("STEP 1: Injecting WORKING_MAC_KEY in plain text.")
        println("  -> Key Slot: $testKeySlot")
        println("  -> Key Algorithm: $testAlgorithm")
        println("  -> Key Value (Hex): ${testMacKeyBytes.toHexString()}")

        // Inyectar la llave en texto plano
        val writeSuccess = pedController.writeKeyPlain(testKeySlot, testKeyType, testAlgorithm, testMacKeyBytes, null)
        assertTrue("Working MAC key injection must succeed", writeSuccess)

        println("STEP 2: Verifying the key is present in the PED.")
        val isPresent = pedController.isKeyPresent(testKeySlot, testKeyType)
        assertTrue("Working MAC key must be present after injection", isPresent)

        println("STEP 3: Calculating KCV by encrypting a block of zeros.")
        val zeroBlock = ByteArray(8) { 0 }
        println("  -> Zero Block (8 bytes): ${zeroBlock.toHexString()}")

        // Cifrar bloque de ceros
        val encryptRequest = PedCipherRequest(
            keyIndex = testKeySlot,
            keyType = testKeyType,
            data = zeroBlock,
            algorithm = testAlgorithm,
            mode = BlockCipherMode.ECB,
            iv = null,
            encrypt = true
        )

        val encryptResult = pedController.encrypt(encryptRequest)
        val encryptedZeros = encryptResult.resultData
        println("  -> Encrypted Zeros (Hex): ${encryptedZeros.toHexString()}")

        val calculatedKcv = encryptedZeros.take(3).toByteArray()
        println("  -> Calculated KCV (first 3 bytes): ${calculatedKcv.toHexString()}")

        println("STEP 4: Calculating expected KCV using software crypto.")
        val expectedKcv = softwareCalculateKcv(testMacKeyBytes, testAlgorithm)
        println("  -> Expected KCV (software): ${expectedKcv.toHexString()}")

        assertArrayEquals("KCV calculated by PED must match software KCV", expectedKcv, calculatedKcv)
        println("SUCCESS: Working MAC key injected and KCV verified correctly.")
    }

    @Test
    fun testInjectAesWorkingKeyPlainAndVerifyKcv() = runBlocking {
        println("\n--- START TEST: NEWPOS - INJECT AES-256 WORKING KEY PLAIN + VERIFY KCV ---")

        // Llave AES-256 de prueba (32 bytes)
        val testAesKeyBytes = "0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF".hexToBytes()
        val testKeySlot = 8
        val testKeyType = KeyType.WORKING_DATA_KEY
        val testAlgorithm = KeyAlgorithm.AES_256

        println("STEP 1: Injecting AES-256 WORKING_DATA_KEY in plain text.")
        println("  -> Key Slot: $testKeySlot")
        println("  -> Key Algorithm: $testAlgorithm")
        println("  -> Key Value (Hex): ${testAesKeyBytes.toHexString()}")

        // Inyectar la llave en texto plano
        val writeSuccess = pedController.writeKeyPlain(testKeySlot, testKeyType, testAlgorithm, testAesKeyBytes, null)
        assertTrue("AES-256 working key injection must succeed", writeSuccess)

        println("STEP 2: Verifying the key is present in the PED.")
        val isPresent = pedController.isKeyPresent(testKeySlot, testKeyType)
        assertTrue("AES-256 working key must be present after injection", isPresent)

        println("STEP 3: Calculating KCV by encrypting a block of zeros.")
        // AES usa bloques de 16 bytes
        val zeroBlock = ByteArray(16) { 0 }
        println("  -> Zero Block (16 bytes): ${zeroBlock.toHexString()}")

        // Cifrar bloque de ceros
        val encryptRequest = PedCipherRequest(
            keyIndex = testKeySlot,
            keyType = testKeyType,
            data = zeroBlock,
            algorithm = testAlgorithm,
            mode = BlockCipherMode.ECB,
            iv = null,
            encrypt = true
        )

        val encryptResult = pedController.encrypt(encryptRequest)
        val encryptedZeros = encryptResult.resultData
        println("  -> Encrypted Zeros (Hex): ${encryptedZeros.toHexString()}")

        val calculatedKcv = encryptedZeros.take(3).toByteArray()
        println("  -> Calculated KCV (first 3 bytes): ${calculatedKcv.toHexString()}")

        println("STEP 4: Calculating expected KCV using software crypto.")
        val expectedKcv = softwareCalculateKcv(testAesKeyBytes, testAlgorithm)
        println("  -> Expected KCV (software): ${expectedKcv.toHexString()}")

        assertArrayEquals("KCV calculated by PED must match software KCV", expectedKcv, calculatedKcv)
        println("SUCCESS: AES-256 working key injected and KCV verified correctly.")
    }

    // --- Utility para calcular KCV por software ---
    private fun softwareCalculateKcv(keyBytes: ByteArray, algorithm: KeyAlgorithm): ByteArray {
        val blockSize = when (algorithm) {
            KeyAlgorithm.AES_128, KeyAlgorithm.AES_192, KeyAlgorithm.AES_256 -> 16
            else -> 8 // DES/3DES
        }

        val zeroBlock = ByteArray(blockSize) { 0 }

        val cipher = when (algorithm) {
            KeyAlgorithm.DES_TRIPLE -> {
                Cipher.getInstance("DESede/ECB/NoPadding")
            }
            KeyAlgorithm.AES_128, KeyAlgorithm.AES_192, KeyAlgorithm.AES_256 -> {
                Cipher.getInstance("AES/ECB/NoPadding")
            }
            else -> throw IllegalArgumentException("Algorithm not supported for KCV calculation: $algorithm")
        }

        val keySpec = when (algorithm) {
            KeyAlgorithm.DES_TRIPLE -> SecretKeySpec(keyBytes, "DESede")
            KeyAlgorithm.AES_128, KeyAlgorithm.AES_192, KeyAlgorithm.AES_256 -> SecretKeySpec(keyBytes, "AES")
            else -> throw IllegalArgumentException("Algorithm not supported for KCV calculation: $algorithm")
        }

        cipher.init(Cipher.ENCRYPT_MODE, keySpec)
        val encrypted = cipher.doFinal(zeroBlock)

        // Retornar primeros 3 bytes
        return encrypted.take(3).toByteArray()
    }

    // ========================================================================
    // --- TESTS DE INYECCIÓN (Verifican solo la llamada) ---
    // ========================================================================

    @Test
    fun testInjectEncryptedDataKey_VerifyCallSuccess() = runBlocking {
        println("\n--- START TEST: NEWPOS - DATA KEY INJECTION CALL ---")
        println("STEP 1: Injecting Master Key in clear text into slot $masterKeyIndex.")
        pedController.writeKeyPlain(masterKeyIndex, masterKeyType, keyAlgorithm, plainMasterKeyBytes, null)

        println("STEP 2: Encrypting the DATA key in software and injecting it.")
        val encryptedKeyData = softwareEncrypt(plainMasterKeyBytes, plainDataKeyBytes)
        println("  -> Encrypted DATA Key (Hex): ${encryptedKeyData.toHexString()}") // Log agregado
        val writeSuccess = pedController.writeKey(dataKeyIndex, dataKeyType, keyAlgorithm, PedKeyData(encryptedKeyData), masterKeyIndex, masterKeyType)

        assertTrue("The encrypted data key injection call must complete successfully", writeSuccess)
        println("SUCCESS: Encrypted DATA key injection call completed without errors.")
    }

    @Test
    fun testInjectEncryptedMacKey_VerifyCallSuccess() = runBlocking {
        println("\n--- START TEST: NEWPOS - MAC KEY INJECTION CALL ---")
        println("STEP 1: Injecting Master Key in clear text into slot $masterKeyIndex.")
        pedController.writeKeyPlain(masterKeyIndex, masterKeyType, keyAlgorithm, plainMasterKeyBytes, null)

        println("STEP 2: Encrypting the MAC key in software and injecting it.")
        val encryptedKeyData = softwareEncrypt(plainMasterKeyBytes, plainMacKeyBytes)
        println("  -> Encrypted MAC Key (Hex): ${encryptedKeyData.toHexString()}") // Log agregado
        val writeSuccess = pedController.writeKey(macKeyIndex, macKeyType, keyAlgorithm, PedKeyData(encryptedKeyData), masterKeyIndex, masterKeyType)

        assertTrue("Encrypted MAC key injection call must complete successfully", writeSuccess)
        println("SUCCESS: Encrypted MAC key injection call completed without errors.")
    }

    @Test
    fun testInjectEncryptedPinKey_VerifyCallSuccess() = runBlocking {
        println("\n--- START TEST: NEWPOS - PIN KEY INJECTION CALL ---")
        println("STEP 1: Injecting Master Key in clear text into slot $masterKeyIndex.")
        pedController.writeKeyPlain(masterKeyIndex, masterKeyType, keyAlgorithm, plainMasterKeyBytes, null)

        println("STEP 2: Encrypting the PIN key in software and injecting it.")
        val encryptedKeyData = softwareEncrypt(plainMasterKeyBytes, plainPinKeyBytes)
        println("  -> Encrypted PIN Key (Hex): ${encryptedKeyData.toHexString()}") // Log agregado
        val writeSuccess = pedController.writeKey(pinKeyIndex, pinKeyType, keyAlgorithm, PedKeyData(encryptedKeyData), masterKeyIndex, masterKeyType)

        assertTrue("Encrypted PIN key injection call must complete successfully", writeSuccess)
        println("SUCCESS: Encrypted PIN key injection call completed successfully.")
    }

    @Test
    fun testInjectTransportKeyPlaintext_VerifyCallSuccess() = runBlocking {
        println("\n--- START TEST: NEWPOS - TRANSPORT KEY PLAINTEXT INJECTION ---")
        println("STEP 1: Injecting Transport Key in clear text into slot $transportKeyIndex.")

        try {
            val writeSuccess = pedController.writeKeyPlain(transportKeyIndex, transportKeyType, keyAlgorithm, plainTransportKeyBytes, null)
            assertTrue("Transport key plaintext injection must complete successfully", writeSuccess)

            println("STEP 2: Verifying the Transport Key is present in the PED.")
            val isPresent = pedController.isKeyPresent(transportKeyIndex, transportKeyType)
            assertTrue("Transport key must be present after injection", isPresent)

            println("SUCCESS: Transport Key plaintext injection completed successfully.")
            println("  -> This validates the fix for NewPOS error code 2255 with TRANSPORT_KEY injection.")
        } catch (e: Exception) {
            fail("Transport key injection failed: ${e.message}. This indicates the NewPOS error 2255 fix did not work.")
        }
    }

    // --- El resto de los tests que ya funcionaban ---

    @Test
    fun testDukptPlainKeyInjectionAndVerifyUsage() = runBlocking {
        println("\n--- START TEST: NEWPOS DUKPT - IPEK INJECTION AND USAGE ---")
        println("STEP 1: Injecting IPEK and KSN into DUKPT group $dukptGroupIndex.")
        pedController.writeDukptInitialKey(dukptGroupIndex, keyAlgorithm, plainIpekBytes, initialKsn, null)
        val dukptInfoBefore = pedController.getDukptInfo(dukptGroupIndex)
        assertNotNull("The initial DUKPT info must not be null.", dukptInfoBefore)

        println("STEP 2: Encrypting data to verify KSN increment.")
        val encryptRequest = PedCipherRequest(0, KeyType.DUKPT_WORKING_KEY, "12345678".toByteArray(), keyAlgorithm, BlockCipherMode.ECB, null, true, true, dukptGroupIndex)
        val encryptionResult = pedController.encrypt(encryptRequest)

        val ksnAfter = encryptionResult.finalDukptInfo?.ksn
        assertNotNull("The final KSN cannot be null.", ksnAfter)
        assertFalse("The KSN after encryption must be different from the initial KSN", dukptInfoBefore!!.ksn.contentEquals(ksnAfter))
        println("SUCCESS: DUKPT key injection and usage flow worked.")
    }

    @Test
    fun testDeleteAllKeysAndVerify() = runBlocking {
        println("\n--- START TEST: NEWPOS - DELETE ALL KEYS ---")
        println("STEP 1: Injecting a master key for the deletion test.")
        pedController.writeKeyPlain(masterKeyIndex, masterKeyType, keyAlgorithm, plainMasterKeyBytes, null)
        assertTrue("The key to be deleted must be present initially", pedController.isKeyPresent(masterKeyIndex, masterKeyType))

        println("STEP 2: Executing deleteAllKeys().")
        pedController.deleteAllKeys()

        println("STEP 3: Verifying that the key no longer exists.")
        assertFalse("The key must not be present after a full deletion", pedController.isKeyPresent(masterKeyIndex, masterKeyType))
        println("SUCCESS: The delete all keys functionality worked correctly.")
    }

    /*
    // ESTE TEST SE COMENTA INTENCIONALMENTE.
    // La obtención de PIN con llave de sesión falla debido a la limitación del firmware
    // que impide usar llaves de trabajo inyectadas con writeKey.
    @Test
    fun testStandardGetPinBlock() = runBlocking { ... }
    */

    @Test
    fun testDukptGetPinBlock() = runBlocking {
        println("\n--- START TEST: NEWPOS - GET PIN BLOCK (DUKPT) ---")
        println("STEP 1: Injecting IPEK in clear text into DUKPT group $dukptGroupIndex.")
        pedController.writeDukptInitialKey(dukptGroupIndex, keyAlgorithm, plainIpekBytes, initialKsn, null)
        val ksnBefore = pedController.getDukptInfo(dukptGroupIndex)?.ksn
        assertNotNull("Could not get initial KSN.", ksnBefore)

        println("STEP 2: Requesting PIN Block. ***** ACTION REQUIRED ON DEVICE *****")
        println("          >>>>> Please enter a PIN (e.g., 1234) and press ENTER on the terminal. <<<<<")
        val request = PedPinRequest(0, KeyType.DUKPT_WORKING_KEY, "4,5,6", samplePan, 30, "TEST PIN DUKPT\nENTER PIN", PinBlockFormatType.ISO9564_0, true, dukptGroupIndex, keyAlgorithm, false)

        try {
            val result = pedController.getPinBlock(request)
            println("STEP 3: Verifying the PinBlock result.")
            assertNotNull("getPinBlock result must not be null.", result)
            assertEquals("PIN block must be 8 bytes long.", 8, result.pinBlock!!.size)

            println("STEP 4: Verifying KSN increment separately.")
            val ksnAfter = pedController.getDukptInfo(dukptGroupIndex)?.ksn
            assertNotNull("Could not get KSN after PIN operation.", ksnAfter)
            assertFalse("KSN should have incremented after PIN operation.", ksnBefore!!.contentEquals(ksnAfter))
            println("SUCCESS: DUKPT PIN Block and KSN increment verified.")
        } catch (e: Exception) {
            fail("TEST FAILED: An unexpected error occurred during DUKPT PIN entry. ${e.message}")
        }
    }

    // --- Utilities ---
    private fun softwareEncrypt(keyBytes: ByteArray, data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("DESede/ECB/NoPadding")
        val keySpec = SecretKeySpec(keyBytes, "DESede")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec)
        return cipher.doFinal(data)
    }

    private fun String.hexToBytes(): ByteArray {
        check(length % 2 == 0) { "Hexadecimal string must have an even length" }
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    private fun ByteArray.toHexString(): String {
        return joinToString("") { "%02X".format(it).uppercase(Locale.ROOT) }
    }
}
