package com.example.manufacturer.libraries.aisino.wrapper

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.manufacturer.base.controllers.ped.PedCancellationException
import com.example.manufacturer.base.controllers.ped.PedException
import com.example.manufacturer.base.controllers.ped.PedTimeoutException
import com.example.manufacturer.base.models.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.*

/**
 * =================================================================================
 * Casos de Prueba de QA para Inyección de Claves y Criptografía (Versión Corregida)
 * =================================================================================
 *
 * Objetivo: Validar el ciclo de vida completo de la gestión de claves en el PED.
 * Dispositivo Bajo Prueba: AisinoPedController
 *
 * Pre-requisitos:
 * 1. Dispositivo POS físico conectado y operativo.
 * 2. SDK de Aisino correctamente inicializado.
 *
 * Escenarios de Prueba (Master/Session):
 * 1. Inyección de Clave de Datos (WK-DATA) cifrada con KTK y su posterior uso.
 * 2. Inyección de Clave de MAC (WK-MAC) cifrada con KTK y su posterior uso.
 * 3. Inyección de Clave de PIN (WK-PIN) cifrada con KTK y verificación.
 *
 * Escenarios de Prueba (DUKPT):
 * 4. Inyección de Clave DUKPT (IPEK) en texto plano y su posterior uso.
 * 5. Inyección de Clave DUKPT (IPEK) cifrada con KTK y su posterior uso.
 *
 * Escenario Adicional:
 * 6. Verificación de borrado de todas las claves.
 */
@RunWith(AndroidJUnit4::class)
class AisinoKeyInjectionTests {

    private lateinit var pedController: AisinoPedController

    // --- Definiciones de Claves y Slots ---

    // Clave Maestra / de Transporte (KTK)
    private val masterKeyIndex = 10
    private val masterKeyType = KeyType.MASTER_KEY
    private val plainMasterKeyBytes = "CB79E0898F2907C24A13516BEAE904A2".hexToBytes()
    private val masterKeyKcv = "4A6B4F"

    // Clave de Trabajo de Datos (cifrada con KTK)
    private val dataKeyIndex = 11
    private val dataKeyType = KeyType.WORKING_DATA_ENCRYPTION_KEY
    private val plainDataKeyBytes = "892FF24F80C13461760E1349083862D9".hexToBytes()

    // Clave de Trabajo de MAC (cifrada con KTK)
    private val macKeyIndex = 12
    private val macKeyType = KeyType.WORKING_MAC_KEY
    private val plainMacKeyBytes = "F2AD8CA77AFD85C168A2DA022CD9F751".hexToBytes()

    // Clave de Trabajo de PIN (cifrada con KTK)
    private val pinKeyIndex = 13
    private val pinKeyType = KeyType.WORKING_PIN_KEY
    private val plainPinKeyBytes = "A46137A25289EFFD10C7EF0E0E167FA8".hexToBytes()

    // Clave DUKPT (IPEK y KSN)
    private val dukptGroupIndex = 1
    private val plainIpekBytes = "63A1EA98B4342A20B29E326FFF50742E".hexToBytes()
    private val ipekKcv = "44D949"
    private val initialKsn = "F8765432100000000000".hexToBytes()

    // Datos comunes para las pruebas
    private val keyAlgorithm = KeyAlgorithm.DES_TRIPLE
    private val plaintextData = "1234567890ABCDEF".toByteArray(Charsets.UTF_8)
    private val samplePan = "4000123456789010"

    /**
     * Prepara el entorno antes de cada prueba.
     */
    @Before
    fun setUp() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        pedController = AisinoPedController(appContext)

        runBlocking {
            pedController.initializePed(appContext)
            println("SETUP: Limpiando slots de claves de prueba ($masterKeyIndex, $dataKeyIndex, $macKeyIndex, $pinKeyIndex, DUKPT $dukptGroupIndex)...")
            try { pedController.deleteKey(masterKeyIndex, masterKeyType) } catch (e: PedException) { /* Ignorar */ }
            try { pedController.deleteKey(dataKeyIndex, dataKeyType) } catch (e: PedException) { /* Ignorar */ }
            try { pedController.deleteKey(macKeyIndex, macKeyType) } catch (e: PedException) { /* Ignorar */ }
            try { pedController.deleteKey(pinKeyIndex, pinKeyType) } catch (e: PedException) { /* Ignorar */ }
        }
    }

    /**
     * Limpia el entorno después de cada prueba.
     */
    @After
    fun tearDown() {
        runBlocking {
            println("TEARDOWN: Borrando claves después de la prueba...")
            try { pedController.deleteKey(masterKeyIndex, masterKeyType) } catch (e: PedException) { /* Ignorar */ }
            try { pedController.deleteKey(dataKeyIndex, dataKeyType) } catch (e: PedException) { /* Ignorar */ }
            try { pedController.deleteKey(macKeyIndex, macKeyType) } catch (e: PedException) { /* Ignorar */ }
            try { pedController.deleteKey(pinKeyIndex, pinKeyType) } catch (e: PedException) { /* Ignorar */ }
        }
    }

    // ========================================================================
    // --- CASOS DE PRUEBA: MASTER/SESSION ---
    // ========================================================================

    /**
     * ========================================================================
     * --- PRUEBA AISLADA: INYECCIÓN DE WORKING KEY CIFRADA ---
     * ========================================================================
     *
     * Objetivo: Demostrar el flujo completo para inyectar una clave de trabajo
     * (Working Key - WK) que ha sido previamente cifrada con una clave maestra
     * (Master Key / Key Transport Key - KTK).
     *
     * Este es el escenario de uso más común en producción.
     */
    @Test
    fun testInjectEncryptedWorkingKey() = runBlocking {
        println("\n--- INICIO PRUEBA: INYECCIÓN DE WORKING KEY (CIFRADA CON KTK) ---")

        // --- Definiciones para esta prueba ---
        val ktkIndex = 10 // Slot para la Key Transport Key (Master Key)
        val wkIndex = 11  // Slot para la Working Key (en este caso, de datos)

        val plainKtkBytes = "CB79E0898F2907C24A13516BEAE904A2".hexToBytes() // KTK en texto plano
        val plainWkBytes  = "892FF24F80C13461760E1349083862D9".hexToBytes() // WK en texto plano

        val dataToTestEncryption = "Esto es un texto de prueba".toByteArray(Charsets.UTF_8)

        // --- PASO 1: Inyectar la Clave Maestra (KTK) en texto plano ---
        // En un entorno real, esto se hace en un lugar seguro. Para la prueba, la inyectamos directamente.
        println("PASO 1: Inyectando KTK en texto plano en el slot $ktkIndex.")
        pedController.writeKeyPlain(
            keyIndex = ktkIndex,
            keyType = KeyType.MASTER_KEY,
            keyAlgorithm = KeyAlgorithm.DES_TRIPLE,
            keyBytes = plainKtkBytes,
            kcvBytes = null // No se verifica el KCV para esta prueba
        )
        // Verificación de que la KTK se cargó correctamente
        assertTrue("La KTK debe existir en el slot $ktkIndex después de la inyección.", pedController.isKeyPresent(ktkIndex, KeyType.MASTER_KEY))
        println("KTK inyectada con éxito.")

        // --- PASO 2: Cifrar la Working Key usando la KTK ---
        // Este paso simula lo que haría un Sistema de Gestión de Claves (KMS).
        // El PED usa su KTK interna para realizar este cifrado.
        println("PASO 2: Cifrando la WK con la KTK del slot $ktkIndex.")
        val encryptedWk = pedController.encrypt(
            PedCipherRequest(
                keyIndex = ktkIndex,
                keyType = KeyType.MASTER_KEY,
                data = plainWkBytes,
                algorithm = KeyAlgorithm.DES_TRIPLE,
                mode = BlockCipherMode.ECB,
                iv = null,
                encrypt = true
            )
        ).resultData
        println("WK cifrada (Hex): ${encryptedWk.toHexString()}")
        assertFalse("La WK cifrada no debe ser igual a la WK en texto plano.", plainWkBytes.contentEquals(encryptedWk))

        // --- PASO 3: Inyectar la Working Key Cifrada en el PED ---
        // Esta es la llamada principal. Se le dice al PED que use la KTK del slot 'ktkIndex'
        // para descifrar 'encryptedWk' e instalar el resultado en el slot 'wkIndex'.
        println("PASO 3: Inyectando la WK cifrada en el slot $wkIndex.")
        val writeSuccess = pedController.writeKey(
            keyIndex = wkIndex,
            keyType = KeyType.WORKING_DATA_ENCRYPTION_KEY,
            keyAlgorithm = KeyAlgorithm.DES_TRIPLE,
            keyData = PedKeyData(encryptedWk),
            transportKeyIndex = ktkIndex,
            transportKeyType = KeyType.MASTER_KEY
        )
        assertTrue("La inyección de la WK cifrada debe ser exitosa.", writeSuccess)
        println("Inyección de la WK cifrada finalizada con éxito.")

        // --- PASO 4: Verificar que la Working Key está presente y es funcional ---
        println("PASO 4: Verificando que la WK en el slot $wkIndex está operativa.")
        assertTrue("La WK debe existir en el slot $wkIndex después de la inyección.", pedController.isKeyPresent(wkIndex, KeyType.WORKING_DATA_ENCRYPTION_KEY))

        // Prueba funcional: Usar la nueva WK para cifrar y descifrar datos.
        val encryptedData = pedController.encrypt(
            PedCipherRequest(
                keyIndex = wkIndex,
                keyType = KeyType.WORKING_DATA_ENCRYPTION_KEY,
                data = dataToTestEncryption,
                algorithm = KeyAlgorithm.DES_TRIPLE,
                mode = BlockCipherMode.ECB,
                iv = null,
                encrypt = true
            )
        ).resultData

        val decryptedData = pedController.decrypt(
            PedCipherRequest(
                keyIndex = wkIndex,
                keyType = KeyType.WORKING_DATA_ENCRYPTION_KEY,
                data = encryptedData,
                algorithm = KeyAlgorithm.DES_TRIPLE,
                mode = BlockCipherMode.ECB,
                iv = null,
                encrypt = false
            )
        ).resultData

        // CORRECCIÓN: Recortar el relleno (padding) de los datos descifrados antes de comparar.
        val trimmedDecryptedData = decryptedData.copyOf(dataToTestEncryption.size)

        assertArrayEquals("Los datos descifrados deben coincidir con los datos originales.", dataToTestEncryption, trimmedDecryptedData)
        println("Verificación funcional exitosa: La WK puede cifrar y descifrar datos correctamente.")
        println("\n--- ÉXITO: El flujo de inyección de Working Key cifrada se ha completado correctamente. ---")
    }

    /**
     * PRUEBA 1: Inyección de Clave de DATOS Cifrada y Verificación de Uso.
     */
    @Test
    fun testInjectEncryptedDataKeyAndVerifyUsage() = runBlocking {
        println("\n--- INICIO PRUEBA: MASTER/SESSION - INYECCIÓN DE CLAVE DE DATOS ---")
        // --- PREPARACIÓN: Inyectar KTK ---
        println("PASO 1: Inyectando Clave Maestra (KTK) en texto plano en el slot $masterKeyIndex.")
        pedController.writeKeyPlain(masterKeyIndex, masterKeyType, keyAlgorithm, plainMasterKeyBytes, kcvBytes = null)
        assertTrue("La KTK debe estar presente después de la inyección", pedController.isKeyPresent(masterKeyIndex, masterKeyType))

        // --- EJECUCIÓN: Inyectar la Clave de Datos cifrada ---
        println("PASO 2: Inyectando la clave de DATOS cifrada en el slot $dataKeyIndex usando la KTK del slot $masterKeyIndex.")
        val encryptedKeyData = pedController.encrypt(PedCipherRequest(keyIndex = masterKeyIndex, keyType = masterKeyType, data = plainDataKeyBytes, algorithm = keyAlgorithm, mode = BlockCipherMode.ECB, iv = null, encrypt = true)).resultData
        val writeSuccess = pedController.writeKey(
            keyIndex = dataKeyIndex,
            keyType = dataKeyType,
            keyAlgorithm = keyAlgorithm,
            keyData = PedKeyData(encryptedKeyData),
            transportKeyIndex = masterKeyIndex,
            transportKeyType = masterKeyType
        )
        assertTrue("La inyección de la clave de datos cifrada debe ser exitosa", writeSuccess)

        // --- VERIFICACIÓN: Usar la clave de datos inyectada ---
        println("PASO 3: Verificando que la clave de DATOS en el slot $dataKeyIndex está operativa.")
        assertTrue("La clave de datos debe estar presente en el slot $dataKeyIndex", pedController.isKeyPresent(dataKeyIndex, dataKeyType))

        val encryptedData = pedController.encrypt(PedCipherRequest(keyIndex = dataKeyIndex, keyType = dataKeyType, data = plaintextData, algorithm = keyAlgorithm, mode = BlockCipherMode.ECB, iv = null, encrypt = true)).resultData
        val decryptedData = pedController.decrypt(PedCipherRequest(keyIndex = dataKeyIndex, keyType = dataKeyType, data = encryptedData, algorithm = keyAlgorithm, mode = BlockCipherMode.ECB, iv = null, encrypt = false)).resultData

        assertFalse("Los datos cifrados no deben ser iguales a los datos en claro", plaintextData.contentEquals(encryptedData))

        // CORRECCIÓN: Recortar el relleno (padding) si es necesario.
        val trimmedDecryptedData = decryptedData.copyOf(plaintextData.size)
        assertArrayEquals("Los datos descifrados deben coincidir con los originales", plaintextData, trimmedDecryptedData)

        println("ÉXITO: El flujo de inyección y uso de clave de DATOS cifrada se ha completado correctamente.")
    }

    /**
     * PRUEBA 2: Inyección de Clave de MAC Cifrada y Verificación de Uso.
     */
    @Test
    fun testInjectEncryptedMacKeyAndVerifyUsage() = runBlocking {
        println("\n--- INICIO PRUEBA: MASTER/SESSION - INYECCIÓN DE CLAVE DE MAC ---")
        // --- PREPARACIÓN: Inyectar KTK ---
        println("PASO 1: Inyectando Clave Maestra (KTK) en texto plano en el slot $masterKeyIndex.")
        pedController.writeKeyPlain(masterKeyIndex, masterKeyType, keyAlgorithm, plainMasterKeyBytes, kcvBytes = null)
        assertTrue("La KTK debe estar presente", pedController.isKeyPresent(masterKeyIndex, masterKeyType))

        // --- EJECUCIÓN: Inyectar la Clave de MAC cifrada ---
        println("PASO 2: Inyectando la clave de MAC cifrada en el slot $macKeyIndex.")
        val encryptedKeyData = pedController.encrypt(PedCipherRequest(keyIndex = masterKeyIndex, keyType = masterKeyType, data = plainMacKeyBytes, algorithm = keyAlgorithm, mode = BlockCipherMode.ECB, iv = null, encrypt = true)).resultData
        val writeSuccess = pedController.writeKey(macKeyIndex, macKeyType, keyAlgorithm, PedKeyData(encryptedKeyData), masterKeyIndex, masterKeyType)
        assertTrue("La inyección de la clave de MAC cifrada debe ser exitosa", writeSuccess)

        // --- VERIFICACIÓN: Usar la clave de MAC inyectada ---
        println("PASO 3: Verificando que la clave de MAC en el slot $macKeyIndex está operativa.")
        assertTrue("La clave de MAC debe estar presente", pedController.isKeyPresent(macKeyIndex, macKeyType))

        val macRequest = PedMacRequest(keyIndex = macKeyIndex, keyType = macKeyType, algorithm = MacAlgorithm.RETAIL_MAC_ANSI_X9_19, data = plaintextData, isDukpt = false, dukptGroupIndex = null)
        val macResult = pedController.calculateMac(macRequest)

        assertNotNull("El resultado del cálculo de MAC no debe ser nulo", macResult)
        assertEquals("El MAC resultante debe tener 8 bytes", 8, macResult.mac.size)
        println("MAC Calculado (Hex): ${macResult.mac.toHexString()}")

        println("ÉXITO: El flujo de inyección y uso de clave de MAC cifrada se ha completado correctamente.")
    }

    /**
     * PRUEBA 3: Inyección de Clave de PIN Cifrada y Verificación.
     */
    @Test
    fun testInjectEncryptedPinKeyAndVerifyUsage() = runBlocking {
        println("\n--- INICIO PRUEBA: MASTER/SESSION - INYECCIÓN DE CLAVE DE PIN ---")
        // --- PREPARACIÓN: Inyectar KTK ---
        println("PASO 1: Inyectando Clave Maestra (KTK) en texto plano en el slot $masterKeyIndex.")
        pedController.writeKeyPlain(masterKeyIndex, masterKeyType, keyAlgorithm, plainMasterKeyBytes, kcvBytes = null)
        assertTrue("La KTK debe estar presente", pedController.isKeyPresent(masterKeyIndex, masterKeyType))

        // --- EJECUCIÓN: Inyectar la Clave de PIN cifrada ---
        println("PASO 2: Inyectando la clave de PIN cifrada en el slot $pinKeyIndex.")
        val encryptedKeyData = pedController.encrypt(PedCipherRequest(keyIndex = masterKeyIndex, keyType = masterKeyType, data = plainPinKeyBytes, algorithm = keyAlgorithm, mode = BlockCipherMode.ECB, iv = null, encrypt = true)).resultData
        val writeSuccess = pedController.writeKey(pinKeyIndex, pinKeyType, keyAlgorithm, PedKeyData(encryptedKeyData), masterKeyIndex, masterKeyType)
        assertTrue("La inyección de la clave de PIN cifrada debe ser exitosa", writeSuccess)

        // --- VERIFICACIÓN: Comprobar presencia de la clave ---
        println("PASO 3: Verificando que la clave de PIN en el slot $pinKeyIndex existe.")
        assertTrue("La clave de PIN debe estar presente después de la inyección", pedController.isKeyPresent(pinKeyIndex, pinKeyType))

        println("ÉXITO: El flujo de inyección de clave de PIN cifrada se ha completado correctamente.")
    }

    // ========================================================================
    // --- CASOS DE PRUEBA: DUKPT ---
    // ========================================================================

    /**
     * PRUEBA 4: Inyección de Clave DUKPT (IPEK) en Texto Plano y Verificación de Uso.
     */
    @Test
    fun testDukptPlainKeyInjectionAndVerifyUsage() = runBlocking {
        println("\n--- INICIO PRUEBA: DUKPT - INYECCIÓN DE IPEK EN TEXTO PLANO ---")

        // --- EJECUCIÓN: Inyectar IPEK y KSN ---
        println("PASO 1: Inyectando IPEK y KSN en el grupo DUKPT $dukptGroupIndex con KCV.")
        val writeSuccess = pedController.writeDukptInitialKey(dukptGroupIndex, keyAlgorithm, plainIpekBytes, initialKsn, keyChecksum = ipekKcv)
        assertTrue("La inyección de la IPEK en texto plano debe ser exitosa", writeSuccess)

        // --- VERIFICACIÓN: Cifrar datos y comprobar incremento de KSN ---
        println("PASO 2: Verificando que el KSN inicial se ha cargado.")
        val dukptInfoBefore = pedController.getDukptInfo(dukptGroupIndex)
        assertNotNull("La info DUKPT inicial no debe ser nula.", dukptInfoBefore)
        println("KSN Inicial (leído): ${dukptInfoBefore!!.ksn.toHexString()}")


        println("PASO 3: Cifrando datos para verificar funcionamiento e incremento del KSN.")
        val encryptRequest = PedCipherRequest(
            keyIndex = 0, // Placeholder for DUKPT
            keyType = KeyType.DUKPT_WORKING_KEY, // Placeholder for DUKPT
            data = plaintextData,
            algorithm = keyAlgorithm,
            mode = BlockCipherMode.ECB,
            iv = null,
            encrypt = true,
            isDukpt = true,
            dukptGroupIndex = dukptGroupIndex,
            dukptKeyVariant = DukptKeyVariant.DATA_ENCRYPT
        )
        val encryptionResult = pedController.encrypt(encryptRequest)

        assertNotNull("El resultado del cifrado DUKPT no puede ser nulo.", encryptionResult)
        assertFalse("Los datos cifrados no deben ser iguales a los datos en claro", plaintextData.contentEquals(encryptionResult.resultData))

        val ksnAfter = encryptionResult.finalDukptInfo?.ksn
        assertNotNull("El KSN final no puede ser nulo.", ksnAfter)
        println("KSN Final (leído):   ${ksnAfter!!.toHexString()}")
        assertFalse("El KSN después del cifrado debe ser diferente al KSN inicial", dukptInfoBefore.ksn.contentEquals(ksnAfter))

        println("ÉXITO: El flujo de inyección y uso de clave DUKPT en texto plano ha funcionado.")
    }


    // ========================================================================
    // --- CASO DE PRUEBA: GESTIÓN ---
    // ========================================================================

    /**
     * PRUEBA 6: Verificación de Borrado de Todas las Claves.
     */
    @Test
    fun testDeleteAllKeysAndVerify() = runBlocking {
        println("\n--- INICIO PRUEBA: BORRADO DE TODAS LAS CLAVES ---")

        // --- PREPARACIÓN: Inyectar una clave ---
        println("PASO 1: Inyectando una clave maestra para la prueba de borrado.")
        pedController.writeKeyPlain(masterKeyIndex, masterKeyType, keyAlgorithm, plainMasterKeyBytes, kcvBytes = null)
        assertTrue("La clave a borrar debe estar presente inicialmente", pedController.isKeyPresent(masterKeyIndex, masterKeyType))

        // --- EJECUCIÓN: Borrar todas las claves ---
        println("PASO 2: Ejecutando deleteAllKeys().")
        val deleteSuccess = pedController.deleteAllKeys()
        assertTrue("La operación de borrado total debe ser exitosa", deleteSuccess)

        // --- VERIFICACIÓN: Comprobar que la clave ha sido eliminada ---
        println("PASO 3: Verificando que la clave ya no existe.")
        assertFalse("La clave no debe estar presente después del borrado total", pedController.isKeyPresent(masterKeyIndex, masterKeyType))

        println("ÉXITO: La funcionalidad de borrado de todas las claves ha funcionado correctamente.")
    }



    // ========================================================================
    // --- CASOS DE PRUEBA: OBTENCIÓN DE PIN (INTERACTIVOS) ---
    // ========================================================================

    /**
     * PRUEBA 7: Obtención de PIN Block con Clave de Trabajo (Master/Session).
     *
     * ***** PRUEBA INTERACTIVA *****
     * Este test mostrará una pantalla para ingresar el PIN en el dispositivo.
     * Deberás ingresar un PIN (ej: 1234) y presionar Enter para que el test continúe.
     */
    @Test
    fun testStandardGetPinBlock() = runBlocking {
        println("\n--- INICIO PRUEBA: OBTENCIÓN DE PIN BLOCK (MASTER/SESSION) ---")

        // --- PREPARACIÓN: Inyectar KTK y luego la clave de PIN cifrada ---
        println("PASO 1: Inyectando KTK en slot $masterKeyIndex.")
        pedController.writeKeyPlain(masterKeyIndex, masterKeyType, keyAlgorithm, plainMasterKeyBytes, null)
        println("PASO 2: Inyectando clave de PIN cifrada en slot $pinKeyIndex.")
        val encryptedPinKey = pedController.encrypt(PedCipherRequest(masterKeyIndex, KeyType.MASTER_KEY, plainPinKeyBytes, keyAlgorithm, BlockCipherMode.ECB, null, true)).resultData
        pedController.writeKey(pinKeyIndex, pinKeyType, keyAlgorithm, PedKeyData(encryptedPinKey), masterKeyIndex, masterKeyType)
        assertTrue("La clave de PIN debe estar presente", pedController.isKeyPresent(pinKeyIndex, pinKeyType))

        // --- EJECUCIÓN: Solicitar PIN Block ---
        println("PASO 3: Solicitando PIN Block. ***** SE REQUIERE ACCIÓN EN EL DISPOSITIVO *****")
        println("          >>>>> Por favor, ingrese un PIN (ej. 1234) y presione ENTER en el terminal. <<<<<")

        val request = PedPinRequest(
            keyIndex = pinKeyIndex,
            keyType = pinKeyType,
            promptMessage = "TEST PIN STANDARD\nINGRESE PIN Y ENTER",
            pan = samplePan,
            pinLengthConstraints = "4,5,6",
            format = PinBlockFormatType.ISO9564_0,
            timeoutSeconds = 60,
            isDukpt = false,
            algorithm = keyAlgorithm,
            allowBypass = false
        )

        try {
            val result = pedController.getPinBlock(request)

            // --- VERIFICACIÓN ---
            println("PASO 4: Verificando el resultado.")
            assertNotNull("El resultado de getPinBlock no debe ser nulo.", result)
            assertNotNull("El PIN block no debe ser nulo.", result.pinBlock)
            assertEquals("El PIN block debe tener 8 bytes.", 8, result.pinBlock!!.size)
            println("ÉXITO: PIN Block (Hex): ${result.pinBlock.toHexString()}")

        } catch (e: PedTimeoutException) {
            fail("PRUEBA FALLIDA: La prueba expiró. No se ingresó un PIN en el dispositivo. ${e.message}")
        } catch (e: PedCancellationException) {
            fail("PRUEBA FALLIDA: La entrada de PIN fue cancelada en el dispositivo. ${e.message}")
        } catch (e: Exception) {
            fail("PRUEBA FALLIDA: Ocurrió un error inesperado durante getPinBlock. ${e.message}")
        }
    }

    /**
     * PRUEBA 8: Obtención de PIN Block con DUKPT.
     *
     * ***** PRUEBA INTERACTIVA *****
     * Este test mostrará una pantalla para ingresar el PIN en el dispositivo.
     * Deberás ingresar un PIN (ej: 1234) y presionar Enter para que el test continúe.
     */
    @Test
    fun testDukptGetPinBlock() = runBlocking {
        println("\n--- INICIO PRUEBA: OBTENCIÓN DE PIN BLOCK (DUKPT) ---")

        // --- PREPARACIÓN: Inyectar IPEK ---
        println("PASO 1: Inyectando IPEK en texto plano en el grupo DUKPT $dukptGroupIndex.")
        pedController.writeDukptInitialKey(dukptGroupIndex, keyAlgorithm, plainIpekBytes, initialKsn, ipekKcv)
        val ksnBefore = pedController.getDukptInfo(dukptGroupIndex)?.ksn
        assertNotNull("No se pudo obtener el KSN inicial.", ksnBefore)
        println("KSN antes de pedir PIN: ${ksnBefore!!.toHexString()}")


        // --- EJECUCIÓN: Solicitar PIN Block ---
        println("PASO 2: Solicitando PIN Block. ***** SE REQUIERE ACCIÓN EN EL DISPOSITIVO *****")
        println("          >>>>> Por favor, ingrese un PIN (ej. 1234) y presione ENTER en el terminal. <<<<<")

        val request = PedPinRequest(
            keyIndex = 0, // Placeholder
            keyType = KeyType.DUKPT_WORKING_KEY,
            promptMessage = "TEST PIN DUKPT\nINGRESE PIN Y ENTER",
            pan = samplePan,
            pinLengthConstraints = "4,5,6",
            format = PinBlockFormatType.ISO9564_0,
            timeoutSeconds = 60,
            isDukpt = true,
            dukptGroupIndex = dukptGroupIndex,
            algorithm = keyAlgorithm,
            allowBypass = false
        )

        try {
            val result = pedController.getPinBlock(request)

            // --- VERIFICACIÓN ---
            println("PASO 3: Verificando el resultado.")
            assertNotNull("El resultado de getPinBlock no debe ser nulo.", result)
            assertNotNull("El PIN block no debe ser nulo.", result.pinBlock)
            assertEquals("El PIN block debe tener 8 bytes.", 8, result.pinBlock!!.size)
            println("ÉXITO: PIN Block (Hex): ${result.pinBlock.toHexString()}")

            // La API devuelve el KSN que se USÓ para esta transacción.
            val ksnUsedForPin = result.finalDukptInfo?.ksn
            assertNotNull("La información DUKPT usada para el PIN no debe ser nula.", ksnUsedForPin)
            println("KSN usado en la transacción: ${ksnUsedForPin!!.toHexString()}")

            // Para verificar el incremento, debemos PREGUNTAR DE NUEVO al PED por el KSN actual.
            val ksnAfterPin = pedController.getDukptInfo(dukptGroupIndex)?.ksn
            assertNotNull("No se pudo obtener el KSN después de la operación.", ksnAfterPin)
            println("KSN actual en el PED (post-incremento): ${ksnAfterPin!!.toHexString()}")

            // Ahora la comparación es correcta: el KSN de ANTES vs el KSN de AHORA.
            assertFalse("El KSN debe haberse incrementado después de la operación.", ksnBefore.contentEquals(ksnAfterPin))

        } catch (e: PedTimeoutException) {
            fail("PRUEBA FALLIDA: La prueba expiró. No se ingresó un PIN en el dispositivo. ${e.message}")
        } catch (e: PedCancellationException) {
            fail("PRUEBA FALLIDA: La entrada de PIN fue cancelada en el dispositivo. ${e.message}")
        } catch (e: Exception) {
            fail("PRUEBA FALLIDA: Ocurrió un error inesperado durante getPinBlock. ${e.message}")
        }
    }
}

// --- Funciones de Extensión (Utilidades) ---

/**
 * Convierte una cadena de texto en formato hexadecimal a un ByteArray.
 * La cadena debe tener una longitud par.
 */
private fun String.hexToBytes(): ByteArray {
    check(length % 2 == 0) { "La cadena hexadecimal debe tener una longitud par" }
    return chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}

/**
 * Convierte un ByteArray a su representación en formato de cadena hexadecimal en mayúsculas.
 */
private fun ByteArray.toHexString(): String {
    return joinToString("") { "%02X".format(it).uppercase(Locale.ROOT) }
}
