package com.example.manufacturer.base.models

// Importa KeyAlgorithm si no está ya importado
import com.example.manufacturer.base.models.KeyAlgorithm as GenericKeyAlgorithm

/**
 * Parameters for requesting a PIN block from the PED.
 *
 * @property keyIndex The index of the PIN encryption key (Master or Working).
 * @property keyType The type of the PIN encryption key (e.g., MASTER_KEY, WORKING_PIN_KEY).
 * @property pinLengthConstraints A string defining allowed PIN lengths (e.g., "4-12", "0,4,6,8").
 * @property pan Primary Account Number, used for formatting certain PIN blocks (e.g., ISO Format 0). Can be null if not needed.
 * @property timeoutSeconds Maximum time allowed for PIN entry.
 * @property promptMessage Optional message to display on the PED screen during entry.
 * @property format The desired PIN block format (e.g., ISO9564_0).
 * @property isDukpt Whether this request uses a DUKPT key scheme.
 * @property dukptGroupIndex If isDukpt is true, the DUKPT group index to use.
 * @property algorithm The underlying cryptographic algorithm (DES/TDES/AES) used, especially crucial for DUKPT.
 * @property allowBypass Whether bypassing PIN entry (entering 0 digits) is allowed.
 */
data class PedPinRequest(
    val keyIndex: Int,
    val keyType: KeyType,
    val pinLengthConstraints: String,
    val pan: String?,
    val timeoutSeconds: Int,
    val promptMessage: String?,
    val format: PinBlockFormatType,
    val isDukpt: Boolean = false,
    val dukptGroupIndex: Int? = null,
    val algorithm: GenericKeyAlgorithm, // <--- CAMPO AÑADIDO
    val allowBypass: Boolean = false // <--- CAMPO AÑADIDO (inferido de tu código)
)