package com.example.manufacturer.libraries.urovo.wrapper

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import com.example.manufacturer.base.controllers.ped.*
import com.example.manufacturer.base.models.*
import com.example.manufacturer.base.models.KeyAlgorithm
import com.urovo.sdk.pinpad.PinPadProviderImpl
import com.urovo.sdk.pinpad.listener.PinInputListener
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Arrays
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import com.example.manufacturer.base.models.KeyType as GenericKeyType
import com.example.manufacturer.base.models.KeyAlgorithm as GenericKeyAlgorithm
import com.example.manufacturer.base.models.BlockCipherMode as GenericBlockCipherMode
import com.example.manufacturer.base.models.MacAlgorithm as GenericMacAlgorithm
import com.example.manufacturer.base.models.PinBlockFormatType as GenericPinBlockFormatType

class UrovoPedController(private val context: Context) : IPedController {

    private val TAG = "UrovoPedController"
    private val pinpadInstance: PinPadProviderImpl

    // --- Mappings ---

    private fun mapToUrovoKeyType(generic: GenericKeyType): Int {
        return when (generic) {
            GenericKeyType.MASTER_KEY -> UrovoConstants.KeyType.MAIN_KEY
            GenericKeyType.WORKING_MAC_KEY -> UrovoConstants.KeyType.MAC_KEY
            GenericKeyType.WORKING_PIN_KEY -> UrovoConstants.KeyType.PIN_KEY
            GenericKeyType.WORKING_DATA_ENCRYPTION_KEY -> UrovoConstants.KeyType.TD_KEY
            GenericKeyType.DUKPT_INITIAL_KEY, GenericKeyType.DUKPT_WORKING_KEY -> {
                Log.w(TAG, "DUKPT KeyType mapped to TD_KEY for Urovo ops.")
                UrovoConstants.KeyType.TD_KEY
            }
            GenericKeyType.TRANSPORT_KEY -> UrovoConstants.KeyType.MAIN_KEY // Urovo usa TEK, pero no hay tipo genérico. Mapeamos a MK.
            else -> throw PedKeyException("Unsupported generic KeyType for Urovo: $generic")
        }
    }

    private fun mapToUrovoDukptKeyTypeParam(generic: GenericKeyType): Int {
        return when (generic) {
            GenericKeyType.WORKING_PIN_KEY -> UrovoConstants.DukptKeyTypeParam.PIN
            GenericKeyType.WORKING_MAC_KEY -> UrovoConstants.DukptKeyTypeParam.MAC
            GenericKeyType.WORKING_DATA_ENCRYPTION_KEY -> UrovoConstants.DukptKeyTypeParam.TRACK_DATA
            else -> throw PedKeyException("Unsupported generic KeyType for Urovo DUKPT Param: $generic")
        }
    }

    private fun mapToUrovoDukptKeySetNum(generic: GenericKeyType): Int {
        return when (generic) {
            GenericKeyType.WORKING_DATA_ENCRYPTION_KEY -> UrovoConstants.DukptKeySetNum.TDK_SET
            GenericKeyType.WORKING_PIN_KEY -> UrovoConstants.DukptKeySetNum.PEK_SET
            GenericKeyType.WORKING_MAC_KEY -> UrovoConstants.DukptKeySetNum.MAC_SET
            else -> throw PedKeyException("Unsupported generic KeyType for Urovo DUKPT KeySetNum: $generic")
        }
    }

    private fun mapToUrovoDesParams(alg: GenericKeyAlgorithm, mode: GenericBlockCipherMode?, encrypt: Boolean): Pair<Int, Int> {
        val desMode = if (encrypt) UrovoConstants.DesMode.ENC else UrovoConstants.DesMode.DEC
        val algorithm = when (alg) {
            GenericKeyAlgorithm.DES_SINGLE, GenericKeyAlgorithm.DES_DOUBLE, GenericKeyAlgorithm.DES_TRIPLE ->
                if (mode == GenericBlockCipherMode.CBC) UrovoConstants.Algorithm.DES_CBC else UrovoConstants.Algorithm.DES_ECB
            GenericKeyAlgorithm.SM4 -> UrovoConstants.Algorithm.SM4
            GenericKeyAlgorithm.AES_128, GenericKeyAlgorithm.AES_192, GenericKeyAlgorithm.AES_256 ->
                if (mode == GenericBlockCipherMode.CBC) UrovoConstants.Algorithm.AES_CBC else UrovoConstants.Algorithm.AES_ECB
            else -> throw PedCryptoException("Unsupported algorithm for Urovo 'calculateDes': $alg")
        }
        return Pair(desMode, algorithm)
    }

    private fun mapToUrovoMacMode(generic: GenericMacAlgorithm): Int {
        return when (generic) {
            GenericMacAlgorithm.CBC_MAC_ISO9797_1_M1 -> UrovoConstants.MacMode.ANSI_X9_9
            GenericMacAlgorithm.RETAIL_MAC_ANSI_X9_19 -> UrovoConstants.MacMode.ANSI_X9_19
            GenericMacAlgorithm.UNIONPAY_CBC_MAC -> UrovoConstants.MacMode.XOR
            GenericMacAlgorithm.CMAC_AES -> UrovoConstants.MacMode.CMAC
            else -> throw PedCryptoException("Unsupported generic MacAlgorithm for Urovo: $generic")
        }
    }

    private fun mapToUrovoKeyAlgorithm(generic: GenericKeyAlgorithm): Int {
        return when (generic) {
            GenericKeyAlgorithm.DES_SINGLE, GenericKeyAlgorithm.DES_DOUBLE, GenericKeyAlgorithm.DES_TRIPLE -> UrovoConstants.KeyAlgorithm.DES
            GenericKeyAlgorithm.SM4 -> UrovoConstants.KeyAlgorithm.SM4
            GenericKeyAlgorithm.AES_128, GenericKeyAlgorithm.AES_192, GenericKeyAlgorithm.AES_256 -> UrovoConstants.KeyAlgorithm.AES
            else -> UrovoConstants.KeyAlgorithm.DES // Default
        }
    }

    private fun mapToUrovoAesDukptWorkKeyType(generic: GenericKeyAlgorithm): Int {
        return when (generic) {
            GenericKeyAlgorithm.DES_TRIPLE -> UrovoConstants.DukptAesWorkKeyType._3TDEA
            GenericKeyAlgorithm.AES_128 -> UrovoConstants.DukptAesWorkKeyType._AES128
            GenericKeyAlgorithm.AES_192 -> UrovoConstants.DukptAesWorkKeyType._AES192
            GenericKeyAlgorithm.AES_256 -> UrovoConstants.DukptAesWorkKeyType._AES256
            else -> throw PedKeyException("Unsupported KeyAlgorithm for Urovo AES DUKPT WorkKeyType: $generic")
        }
    }


    // --- Initialization ---
    init {
        try {
            Log.i(TAG, "Obteniendo instancia de Urovo PinPadProviderImpl...")
            pinpadInstance = PinPadProviderImpl.getInstance()
            Log.i(TAG, "Instancia de Urovo PinPadProviderImpl obtenida.")
        } catch (e: Throwable) {
            Log.e(TAG, "!!! FALLO al obtener instancia de Urovo PinPadProviderImpl !!!", e)
            throw PedException("Fallo al inicializar Urovo PED: ${e.message}", e)
        }
    }

    // --- Interface Implementation ---

    override suspend fun initializePed(application: Application): Boolean {
        Log.d(TAG, "initializePed: Urovo se inicializa al obtener la instancia.")
        // Establecer algoritmo por defecto a DES, ya que es común.
        setKeyAlgorithm(GenericKeyAlgorithm.DES_TRIPLE)
        return true
    }

    override fun releasePed() {
        Log.d(TAG, "releasePed: No hay API explícita en Urovo PinPad.")
    }

    override suspend fun getStatus(): PedStatusInfo {
        Log.w(TAG, "getStatus: No soportado por Urovo PinPad API.")
        return PedStatusInfo(isTampered = false, errorMessage = "Status check not supported")
    }

    override suspend fun getConfig(): PedConfigInfo {
        Log.w(TAG, "getConfig: No soportado por Urovo PinPad API.")
        return PedConfigInfo(null, null, null)
    }

    override suspend fun writeKey(
        keyIndex: Int, // Índice de la nueva llave a cargar (wkId)
        keyType: GenericKeyType, // Tipo de la nueva llave a cargar
        keyAlgorithm: GenericKeyAlgorithm, // Algoritmo de la nueva llave (no usado directamente por loadWorkKey/loadEncryptMainKey pero sí por setKeyAlgorithm)
        keyData: PedKeyData, // Datos de la nueva llave (cifrada) y su KCV (en claro)
        transportKeyIndex: Int?, // Índice de la llave que cifra (mkId o tekId)
        transportKeyType: GenericKeyType? // Tipo de la llave que cifra
    ): Boolean {
        if (transportKeyIndex == null || transportKeyType == null) {
            throw PedKeyException("La carga de llaves cifradas requiere transportKeyIndex y transportKeyType")
        }

        // Establecer el algoritmo GLOBAL del Pinpad ANTES de la operación de carga de llave.
        // El SDK de Urovo usa un estado global mAlg.
        setKeyAlgorithm(keyAlgorithm)

        try {
            // CASO 1: Cargando una Llave Maestra (ej. TMK) cifrada por otra Maestra/Transporte (ej. KEK)
            if (keyType == GenericKeyType.MASTER_KEY) {
                Log.d(TAG, "Cargando Main Key Cifrada: MK_ID(KEK_ID)=$transportKeyIndex, WK_ID(TMK_ID)=$keyIndex, KCV=${keyData.kcv != null}")
                // El SDK de Urovo usa tekId para la llave de cifrado de una Main Key
                val result = pinpadInstance.loadEncryptMainKey(
                    transportKeyIndex, // tekId (índice de la KEK)
                    keyIndex,          // keyId (índice de la nueva TMK)
                    keyData.keyBytes,  // TMK cifrada
                    keyData.kcv        // KCV de la TMK en claro
                )
                if (!result) {
                    val lastError = PinPadProviderImpl.lastErrorCode
                    throw PedKeyException("Fallo al escribir Main Key cifrada (loadEncryptMainKey). SDK Error: $lastError")
                }
                return true
            }
            // CASO 2: Cargando una Llave de Trabajo (PIN, MAC, TD) cifrada por una Llave Maestra (ej. TMK)
            else if (keyType == GenericKeyType.WORKING_PIN_KEY ||
                keyType == GenericKeyType.WORKING_MAC_KEY ||
                keyType == GenericKeyType.WORKING_DATA_ENCRYPTION_KEY) {

                // La validación original sobre transportKeyType.
                // Si la TMK se cargó como MASTER_KEY, esto está bien.
                if (transportKeyType != GenericKeyType.MASTER_KEY) {
                    // Podrías flexibilizar esto si tu TMK puede ser de otro tipo genérico pero funciona como MK.
                    throw PedKeyException("Urovo loadWorkKey espera que la llave de transporte (TMK) sea de tipo MASTER_KEY")
                }

                val npKeyType = mapToUrovoKeyType(keyType) // Convierte WORKING_PIN_KEY a 2, WORKING_MAC_KEY a 1, etc.
                // según la especificación de Urovo para el parámetro keyType de loadWorkKey
                Log.d(TAG, "Cargando Work Key: Type(SDK)=$npKeyType, MK_ID(TMK_ID)=$transportKeyIndex, WK_ID=$keyIndex, KCV=${keyData.kcv != null}")

                val result = pinpadInstance.loadWorkKey(
                    npKeyType,         // Tipo de Work Key para el SDK (1, 2, o 3)
                    transportKeyIndex, // mkId (índice de la TMK)
                    keyIndex,          // wkId (índice de la nueva Work Key)
                    keyData.keyBytes,  // Work Key cifrada
                    keyData.kcv        // KCV de la Work Key en claro
                )
                if (!result) {
                    val lastError = PinPadProviderImpl.lastErrorCode
                    throw PedKeyException("Fallo al escribir Work Key (loadWorkKey). SDK Error: $lastError")
                }
                return true
            }
            // Otros tipos de llave cifrada no soportados por esta lógica
            else {
                throw PedKeyException("Tipo de llave '${keyType}' no soportado para carga cifrada con esta función.")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error al escribir llave cifrada ($keyType)", e)
            if (e is PedKeyException) throw e // Re-lanzar si ya es PedKeyException
            throw PedKeyException("Fallo al escribir llave cifrada ($keyType): ${e.message}", e)
        }
    }

    // En UrovoPedController.kt

    override suspend fun writeKeyPlain(
        keyIndex: Int,
        keyType: GenericKeyType,
        keyAlgorithm: GenericKeyAlgorithm,
        keyBytes: ByteArray,
        kcvBytes: ByteArray?
    ): Boolean {
        // Permitir MASTER_KEY o TRANSPORT_KEY para carga en claro.
        // Ambas representan llaves de alto nivel y el SDK las maneja de forma similar para carga en claro.
        if (keyType != GenericKeyType.MASTER_KEY && keyType != GenericKeyType.TRANSPORT_KEY) {
            throw PedKeyException("Carga en claro (writeKeyPlain) implementada solo para MASTER_KEY o TRANSPORT_KEY.")
        }

        try {
            setKeyAlgorithm(keyAlgorithm) // Establecer el algoritmo antes de cargar

            val keyPurposeDescription = if (keyType == GenericKeyType.TRANSPORT_KEY) "Transport Key (KEK)" else "Main Key"
            Log.d(TAG, "Cargando $keyPurposeDescription (Claro): ID=$keyIndex, Alg=$keyAlgorithm, KCV presente: ${kcvBytes != null}")

            // PinPadProviderImpl.loadMainKey() y loadTEK() son funcionalmente equivalentes para carga en claro.
            // Podemos seguir usando loadMainKey() o usar loadTEK() si keyType es TRANSPORT_KEY
            // para mayor alineación semántica con el SDK, aunque su implementación interna es casi idéntica.
            val result = if (keyType == GenericKeyType.TRANSPORT_KEY) {
                pinpadInstance.loadTEK(keyIndex, keyBytes, kcvBytes) // Usar loadTEK para KEKs
            } else {
                pinpadInstance.loadMainKey(keyIndex, keyBytes, kcvBytes) // Usar loadMainKey para otras Master Keys
            }

            if (!result) {
                var sdkErrorCodeMessage = ""
                try {
                    val lastError = PinPadProviderImpl.lastErrorCode
                    sdkErrorCodeMessage = " (SDK Error Code: $lastError)"
                } catch (e_sdk: Exception) {
                    Log.w(TAG, "No se pudo obtener el lastErrorCode del SDK de Urovo", e_sdk)
                }
                throw PedKeyException("Fallo al escribir llave ($keyPurposeDescription - claro).$sdkErrorCodeMessage")
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error al escribir llave ($keyType - claro)", e) // Loguear el tipo genérico
            if (e is PedKeyException) throw e
            throw PedKeyException("Fallo al escribir llave (claro): ${e.message}", e)
        }
    }

    override suspend fun writeDukptInitialKeyEncrypted(
        groupIndex: Int,
        keyAlgorithm: KeyAlgorithm,
        encryptedIpek: ByteArray,
        initialKsn: ByteArray,
        transportKeyIndex: Int
    ): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun deleteKey(keyIndex: Int, keyType: GenericKeyType): Boolean {
        val npKeyType = mapToUrovoKeyType(keyType)
        try {
            Log.d(TAG, "Borrando Llave: Type=$npKeyType, ID=$keyIndex")
            val result = pinpadInstance.deleteKey(npKeyType, keyIndex)
            if (result == UrovoConstants.ErrorCode.SUCCESS || result == UrovoConstants.ErrorCode.KEY_NOT_EXIST) {
                return true
            } else {
                throw PedKeyException("Fallo al borrar llave. Urovo Error Code: $result")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al borrar llave", e)
            throw PedKeyException("Fallo al borrar llave: ${e.message}", e)
        }
    }

    override suspend fun deleteAllKeys(): Boolean {
        Log.w(TAG, "deleteAllKeys no soportado por Urovo PinPad API.")
        throw UnsupportedOperationException("deleteAllKeys no soportado.")
    }

    override suspend fun isKeyPresent(keyIndex: Int, keyType: GenericKeyType): Boolean {
        val npKeyType = mapToUrovoKeyType(keyType)
        return try {
            pinpadInstance.isKeyExist(npKeyType, keyIndex)
        } catch (e: Exception) {
            Log.e(TAG, "Error verificando presencia de llave", e)
            throw PedException("Fallo al verificar presencia de llave: ${e.message}", e)
        }
    }

    override suspend fun getKeyInfo(keyIndex: Int, keyType: GenericKeyType): PedKeyInfo? {
        if (isKeyPresent(keyIndex, keyType)) {
            return PedKeyInfo(index = keyIndex, type = keyType, algorithm = null) // Algoritmo no se puede obtener
        }
        return null
    }

    override suspend fun writeDukptInitialKey(
        groupIndex: Int,
        keyAlgorithm: GenericKeyAlgorithm,
        keyBytes: ByteArray, // BDK
        initialKsn: ByteArray // KSN
    ): Boolean {
        if (groupIndex < 1 || groupIndex > 4) {
            throw PedKeyException("Índice de grupo DUKPT Urovo debe ser entre 1 y 4.")
        }
        // TODO: La API `downloadKeyDukpt` requiere IPEK. ¿Cómo se obtiene o se pasa?
        // La API `DukptAesInitial` también puede requerir IPEK.
        // ASUNCIÓN: La API puede manejar IPEK nulo si BDK se proporciona. ¡¡VERIFICAR!!
        Log.w(TAG, "writeDukptInitialKey: La API de Urovo puede requerir IPEK, intentando con null.")

        try {
            val result = when (keyAlgorithm) {
                GenericKeyAlgorithm.DES_TRIPLE, GenericKeyAlgorithm.DES_DOUBLE -> {
                    pinpadInstance.downloadKeyDukpt(groupIndex, keyBytes, keyBytes.size, initialKsn, initialKsn.size, null, 0)
                }
                GenericKeyAlgorithm.AES_128, GenericKeyAlgorithm.AES_192, GenericKeyAlgorithm.AES_256 -> {
                    val deriveKeyType = mapToUrovoAesDukptWorkKeyType(keyAlgorithm)
                    pinpadInstance.DukptAesInitial(groupIndex, keyBytes, keyBytes.size, null, 0, deriveKeyType, initialKsn, initialKsn.size)
                }
                else -> throw PedKeyException("Algoritmo DUKPT no soportado: $keyAlgorithm")
            }
            if (result != 0) {
                throw PedKeyException("Fallo al escribir llave inicial DUKPT. Urovo Error: $result")
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error al escribir llave inicial DUKPT", e)
            throw PedKeyException("Fallo al escribir llave inicial DUKPT: ${e.message}", e)
        }
    }

    override suspend fun getDukptInfo(groupIndex: Int): DukptInfo? {
        val ksnOut = ByteArray(12) // Buffer grande
        return try {
            var result = pinpadInstance.DukptAesGetKsn(groupIndex, ksnOut)
            if (result != 0) {
                result = pinpadInstance.DukptGetKsn(groupIndex, ksnOut) // Intenta TDES
            }

            if (result == 0) {
                // La API no devuelve la longitud real, KSN TDES es 10, AES puede ser 12.
                // TODO: ¿Cómo saber la longitud real o si es TDES/AES? Asumimos 10 para TDES y 12 para AES si el primer intento funcionó.
                // Por simplicidad, si DukptGetKsn funcionó, usamos 10 bytes. Si DukptAesGetKsn funcionó, 12.
                // Aquí, si llega con result==0, no sabemos cuál funcionó. Es un problema.
                // Devolvemos 10 bytes como la opción más probable para TDES.
                Log.w(TAG, "Obtenido KSN, pero longitud exacta y tipo (TDES/AES) ambiguos. Devolviendo 10 bytes.")
                DukptInfo(ksnOut.copyOf(10), null)
            } else {
                Log.w(TAG, "Fallo al obtener KSN DUKPT para índice $groupIndex. Error: $result")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al obtener KSN DUKPT", e)
            throw PedException("Fallo al obtener KSN DUKPT: ${e.message}", e)
        }
    }

    override suspend fun incrementDukptKsn(groupIndex: Int): Boolean {
        val ksnOut = ByteArray(12)
        return try {
            Log.w(TAG, "Intentando incrementar KSN DUKPT AES (puede ser automático).")
            pinpadInstance.DukptAesUpdateKsn(groupIndex, ksnOut) == 0
        } catch (e: Exception) {
            Log.e(TAG, "Error al incrementar KSN DUKPT (solo AES soportado explícitamente)", e)
            false
        }
    }

    override suspend fun encrypt(request: PedCipherRequest): PedCipherResult {
        if (!request.encrypt) throw IllegalArgumentException("Usar decrypt para descifrar")
        return performCipher(request)
    }

    override suspend fun decrypt(request: PedCipherRequest): PedCipherResult {
        if (request.encrypt) throw IllegalArgumentException("Usar encrypt para cifrar")
        return performCipher(request)
    }

    private suspend fun performCipher(request: PedCipherRequest): PedCipherResult {
        return when {
            request.isDukpt -> performDukptCipher(request)
            request.keyType == GenericKeyType.RSA_PRIVATE_KEY || request.keyType == GenericKeyType.RSA_PUBLIC_KEY
                -> throw PedCryptoException("RSA no soportado por Urovo PinPad API.")
            else -> performSymmetricCipher(request)
        }
    }

    private suspend fun performSymmetricCipher(request: PedCipherRequest): PedCipherResult {
        val npKeyType = mapToUrovoKeyType(request.keyType)
        val (desMode, algorithm) = mapToUrovoDesParams(request.algorithm, request.mode, request.encrypt)
        val dataOut = ByteArray(request.data.size + 16) // Buffer con padding

        try {
            setKeyAlgorithm(request.algorithm) // Asegurar el algoritmo correcto
            Log.d(TAG, "Cifrado Simétrico: Type=$npKeyType, ID=${request.keyIndex}, Mode=$desMode, Alg=$algorithm")
            val result = pinpadInstance.calculateDes(
                desMode, algorithm, npKeyType, request.keyIndex, request.data, dataOut
            )
            if (result == 0) {
                // Urovo no devuelve longitud. Esto es un problema.
                // Asumimos que la salida tiene la misma longitud que la entrada (ECB) o múltiplo (CBC).
                // TODO: ¡VERIFICAR ESTA LÓGICA! Es muy probable que se necesite manejo de padding.
                Log.w(TAG, "Cifrado simétrico exitoso, pero longitud de salida es incierta. Asumiendo longitud de entrada.")
                return PedCipherResult(dataOut.copyOf(request.data.size))
            } else {
                throw PedCryptoException("Fallo en cifrado simétrico. Urovo Error: $result")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en cifrado simétrico", e)
            throw PedCryptoException("Fallo en cifrado simétrico: ${e.message}", e)
        }
    }

    private suspend fun performDukptCipher(request: PedCipherRequest): PedCipherResult {
        val groupIndex = request.dukptGroupIndex ?: throw PedCryptoException("Índice de grupo DUKPT requerido")
        val npDukptKeyType = mapToUrovoDukptKeyTypeParam(request.keyType)
        val iv = request.iv ?: ByteArray(8)
        val dataOut = ByteArray(request.data.size + 16)
        val outLen = IntArray(1) { dataOut.size } // Inicializar con tamaño máximo
        val outKsn = ByteArray(12)
        val ksnLen = IntArray(1) { outKsn.size }

        try {
            val isAes = request.algorithm.name.startsWith("AES")
            val result: Int

            if (isAes) {
                val workKeyType = mapToUrovoAesDukptWorkKeyType(request.algorithm)
                val encMode = if (request.mode == GenericBlockCipherMode.CBC) UrovoConstants.DukptAesEncMode.CBC_ENCRYPT else UrovoConstants.DukptAesEncMode.ECB_ENCRYPT
                val cipherMode = if (request.encrypt) encMode else encMode + 0x10 // +0x10 para descifrar
                val keyTypeForAlg = (npDukptKeyType or (0x00 shl 8)) // 0x00 = Data encryption

                result = pinpadInstance.DukptAesEncryptDataIV(
                    keyTypeForAlg, groupIndex, cipherMode, workKeyType,
                    iv, iv.size, request.data, request.data.size,
                    dataOut, outLen, outKsn, ksnLen
                )
            } else { // TDES
                val encMode = if (request.mode == GenericBlockCipherMode.CBC) UrovoConstants.DukptEncModeTdes.CBC_ENCRYPT else UrovoConstants.DukptEncModeTdes.ECB_ENCRYPT
                val cipherMode = if (request.encrypt) encMode else encMode + 0x10 // +0x10 para descifrar

                result = pinpadInstance.DukptEncryptDataIV(
                    npDukptKeyType, groupIndex, cipherMode,
                    iv, iv.size, request.data, request.data.size,
                    dataOut, outLen, outKsn, ksnLen
                )
            }

            if (result == 0) {
                val finalData = dataOut.copyOf(outLen[0])
                val finalKsn = outKsn.copyOf(ksnLen[0])
                return PedCipherResult(finalData, DukptInfo(finalKsn, null))
            } else {
                throw PedCryptoException("Fallo en cifrado DUKPT. Urovo Error: $result")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en cifrado DUKPT", e)
            throw PedCryptoException("Fallo en cifrado DUKPT: ${e.message}", e)
        }
    }

    override suspend fun calculateMac(request: PedMacRequest): PedMacResult {
        return when {
            request.isDukpt -> calculateDukptMac(request)
            else -> calculateStandardMac(request)
        }
    }

    private suspend fun calculateStandardMac(request: PedMacRequest): PedMacResult {
        val npMacMode = mapToUrovoMacMode(request.algorithm)
        try {
            // La línea `setKeyAlgorithm(request.algorithm)` ha sido eliminada.
            Log.d(TAG, "Calculando MAC Estándar: ID=${request.keyIndex}, Mode=$npMacMode")
            val mac = pinpadInstance.calcMAC(request.keyIndex, request.data, npMacMode)
            if (mac != null) {
                return PedMacResult(mac)
            } else {
                throw PedCryptoException("Cálculo de MAC estándar falló (null).")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculando MAC estándar", e)
            throw PedCryptoException("Cálculo de MAC estándar falló: ${e.message}", e)
        }
    }


    private suspend fun calculateDukptMac(request: PedMacRequest): PedMacResult {
        val groupIndex = request.dukptGroupIndex ?: throw PedCryptoException("Índice de grupo DUKPT requerido")
        val dataOut = ByteArray(16)
        val outDataLen = IntArray(1) { dataOut.size }
        val outKsn = ByteArray(12)
        val ksnLen = IntArray(1) { outKsn.size }

        try {
            val isAes = request.algorithm.name.contains("AES") || request.algorithm == GenericMacAlgorithm.CMAC_AES
            val result: Int

            if (isAes) {
                // Establece el algoritmo base a AES
                setKeyAlgorithm(GenericKeyAlgorithm.AES_128) // O AES_192 / AES_256 si se sabe
                val workKeyType = mapToUrovoAesDukptWorkKeyType(GenericKeyAlgorithm.AES_128) // O el específico
                val macAlg = when (request.algorithm) {
                    GenericMacAlgorithm.RETAIL_MAC_ANSI_X9_19 -> UrovoConstants.DukptAesMacMode.MAC_ALG_X9_19
                    // TODO: Mapear CMAC_AES si Urovo lo soporta via DukptAesEncryptDataIV
                    else -> throw PedCryptoException("Algoritmo MAC AES DUKPT no soportado.")
                }
                val keyTypeForAlg = (UrovoConstants.DukptKeyTypeParam.MAC or (0x01 shl 8)) // 0x02, 0x01 = Generación

                Log.d(TAG, "Calculando MAC DUKPT AES: Index=$groupIndex, Mode=$macAlg")
                result = pinpadInstance.DukptAesEncryptDataIV(
                    keyTypeForAlg, groupIndex, macAlg, workKeyType,
                    request.iv ?: ByteArray(8), (request.iv ?: ByteArray(8)).size,
                    request.data, request.data.size,
                    dataOut, outDataLen, outKsn, ksnLen
                )
            } else { // TDES
                // Establece el algoritmo base a TDES
                setKeyAlgorithm(GenericKeyAlgorithm.DES_TRIPLE)
                // Se asume que calculateMACOfDUKPTExtend usa TDES por defecto y el keySetNum correcto.
                val keySetNum = mapToUrovoDukptKeySetNum(GenericKeyType.WORKING_MAC_KEY) // 0x04

                Log.d(TAG, "Calculando MAC DUKPT TDES: Index=$groupIndex, KeySet=$keySetNum")
                result = pinpadInstance.calculateMACOfDUKPTExtend(
                    keySetNum, // Usa keySetNum 0x04 para MAC DUKPT
                    request.data, request.data.size,
                    dataOut, outDataLen, outKsn, ksnLen
                )
            }

            if (result == 0) {
                val mac = dataOut.copyOf(outDataLen[0])
                val ksn = outKsn.copyOf(ksnLen[0])
                return PedMacResult(mac, DukptInfo(ksn, null))
            } else {
                throw PedCryptoException("Cálculo de MAC DUKPT falló. Urovo Error: $result")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error calculando MAC DUKPT", e)
            throw PedCryptoException("Cálculo de MAC DUKPT falló: ${e.message}", e)
        }
    }

    override suspend fun getPinBlock(request: PedPinRequest): PedPinResult {
        val bundle = Bundle()
        bundle.putString("card_no", request.pan ?: "")
        bundle.putInt("TimeoutMs", request.timeoutSeconds * 1000)
        // La API espera un ShortArray para supportPinLen, pero la doc dice String. Usamos String. [cite: 25]
        bundle.putString("supportPinLen", request.pinLengthConstraints) // "0,4,5,6,7,8,9,10,11,12"
        bundle.putBoolean("Bypass", request.allowBypass)
        bundle.putString("title", request.promptMessage ?: "Enter PIN")
        bundle.putBoolean("sound", true)
        bundle.putBoolean("FullScreen", true)
        // TODO: Añadir más opciones del bundle si es necesario (random, colores, etc.)

        return suspendCancellableCoroutine { continuation ->
            // Asegúrate de que PinInputListener es la interfaz correcta
            val listener = object : com.urovo.sdk.pinpad.listener.PinInputListener { // Usamos el FQN para claridad
                override fun onInput(len: Int, key: Int) { Log.d(TAG, "PIN Input: L=$len") }
                override fun onCancel() { if (continuation.isActive) continuation.resumeWithException(PedCancellationException("Cancelado por usuario.")) }
                override fun onTimeOut() { if (continuation.isActive) continuation.resumeWithException(PedTimeoutException("Timeout PIN.")) }
                override fun onError(errorCode: Int) { if (continuation.isActive) continuation.resumeWithException(PedException("Error PIN: $errorCode")) }

                override fun onConfirm(data: ByteArray?, isNonePin: Boolean) {
                    if (continuation.isActive) {
                        when {
                            isNonePin && request.allowBypass -> continuation.resumeWithException(PedCancellationException("Bypass PIN."))
                            data != null -> continuation.resume(PedPinResult(data))
                            else -> continuation.resumeWithException(PedException("Confirmación PIN con datos nulos."))
                        }
                    }
                }

                // --- CORRECCIÓN ---
                // Se corrigió el nombre de 'onConfim_dukpt' a 'onConfirm_dukpt' para coincidir con la interfaz.
                override fun onConfirm_dukpt(pinBlock: ByteArray?, ksn: ByteArray?) {
                    // --- FIN CORRECCIÓN ---
                    if (continuation.isActive) {
                        when {
                            pinBlock != null && ksn != null -> continuation.resume(PedPinResult(pinBlock, DukptInfo(ksn, null)))
                            request.allowBypass -> continuation.resumeWithException(PedCancellationException("Bypass PIN DUKPT."))
                            else -> continuation.resumeWithException(PedException("Confirmación DUKPT con datos nulos."))
                        }
                    }
                }
            }

            try {
                // setKeyAlgorithm(request.algorithm) // <--- LÍNEA ELIMINADA (Resuelto en paso anterior)
                when {
                    // Ahora `request.algorithm` existe gracias a la modificación de PedPinRequest
                    request.isDukpt && request.algorithm.name.startsWith("AES") -> {
                        bundle.putInt("WorkKeyType", mapToUrovoAesDukptWorkKeyType(request.algorithm))
                        bundle.putInt("PINKeyNo", request.dukptGroupIndex ?: throw PedException("Índice DUKPT requerido"))
                        Log.d(TAG, "Pidiendo PinBlock DUKPT AES...")
                        pinpadInstance.GetDukptAesPinBlock(bundle, listener)
                    }
                    request.isDukpt -> {
                        bundle.putInt("PINKeyNo", request.dukptGroupIndex ?: throw PedException("Índice DUKPT requerido"))
                        Log.d(TAG, "Pidiendo PinBlock DUKPT TDES...")
                        pinpadInstance.GetDukptPinBlock(bundle, listener)
                    }
                    else -> {
                        bundle.putInt("PINKeyNo", request.keyIndex)
                        Log.d(TAG, "Pidiendo PinBlock MK/SK...")
                        pinpadInstance.getPinBlockEx(bundle, listener)
                    }
                }
            } catch (e: Exception) {
                if (continuation.isActive) continuation.resumeWithException(PedException("Fallo al iniciar GetPinBlock: ${e.message}", e))
            }

            continuation.invokeOnCancellation {
                Log.d(TAG, "getPinBlock cancelado. Intentando cancelar en PED.")
                cancelPinEntry()
            }
        }
    }


    override fun cancelPinEntry() {
        try {
            Log.d(TAG, "Cancelando PIN entry via EndPinInputEvent.")
            pinpadInstance.EndPinInputEvent(UrovoConstants.PinInputEvent.CANCEL)
        } catch (e: Exception) {
            Log.e(TAG, "Error al cancelar PIN entry", e)
        }
    }

    override fun displayMessage(message: String, line: Int?, clearPrevious: Boolean) {
        Log.w(TAG, "displayMessage no soportado. Usar promptMessage en getPinBlock.")
    }

    override fun setPinPadStyle(styleInfo: Map<String, Any>) {
        Log.w(TAG, "setPinPadStyle no soportado via Map. Usar JSON/Bundle en getPinBlockEx.")
    }

    override suspend fun getRandomBytes(length: Int): ByteArray {
        Log.w(TAG, "getRandomBytes no soportado por Urovo PinPad API.")
        throw UnsupportedOperationException("getRandomBytes no soportado.")
    }

    // --- Urovo Specific Helpers ---

    private fun setKeyAlgorithm(keyAlgorithm: GenericKeyAlgorithm) {
        val urovoAlg = mapToUrovoKeyAlgorithm(keyAlgorithm)
        try {
            pinpadInstance.setKeyAlgorithm(urovoAlg)
            Log.d(TAG, "Algoritmo de Llave establecido a: $urovoAlg")
        } catch (e: Exception) {
            Log.e(TAG, "Fallo al establecer algoritmo de llave", e)
            throw PedException("Fallo al establecer algoritmo de llave", e)
        }
    }

    private fun ByteArray.toHexString(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }
}