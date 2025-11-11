package com.vigatec.manufacturer.libraries.urovo.wrapper

// Constantes basadas en la documentación de Urovo (.doc) y Ped.java
object UrovoConstants {

    // com.urovo.sdk.pinpad.utils.Constant.KeyType [cite: 13, 52, 57]
    object KeyType {
        const val MAIN_KEY = 0
        const val MAC_KEY = 1
        const val PIN_KEY = 2
        const val TD_KEY = 3
    }

    // com.urovo.sdk.pinpad.utils.Constant.DesMode [cite: 19]
    object DesMode {
        const val ENC = 0
        const val DEC = 1
    }

    // com.urovo.sdk.pinpad.utils.Constant.Algorithm [cite: 19]
    // Refinado con Ped.java (calcDes)
    object Algorithm {
        const val DES_ECB = 1
        const val DES_CBC = 2
        const val SM4 = 3
        const val AES_ECB = 7
        const val AES_CBC = 8
    }

    // com.urovo.sdk.pinpad.utils.Constant.KeyAlgorithm [cite: 58]
    // Coincide con cAlg en Ped.java
    object KeyAlgorithm {
        const val DES = 0
        const val SM4 = 1
        const val AES = 2
    }

    // MAC Algorithm Modes [cite: 18]
    // Refinado con Ped.java (calcMac)
    object MacMode {
        const val XOR = 0x00
        const val ANSI_X9_9 = 0x01 // M1 (CBC-MAC M1)
        const val ANSI_X9_19 = 0x11 // M2 (Retail MAC)
        const val POS_ECB = 0x10
        const val CMAC = 0x07
        // const val EMV = 0x01 // Asumiendo que X9.9 es EMV
        // const val CUP = 0x00 // Asumiendo que XOR es CUP
    }

    // DUKPT Key Types (para DukptEncryptDataIV, DukptAesEncryptDataIV) [cite: 24, 54, 60]
    object DukptKeyTypeParam {
        const val PIN = 0x01
        const val MAC = 0x02
        const val TRACK_DATA = 0x03
        const val MAC_ALT = 0x04 // Usado en DukptEncryptDataIV
    }

    // DUKPT Key Set Num (Index) [cite: 22, 24, 54]
    object DukptKeySetNum {
        const val TDK_SET = 0x01
        const val PEK_SET = 0x03
        const val MAC_SET = 0x04
    }

    // DUKPT Encryption Modes (TDES) [cite: 55]
    object DukptEncModeTdes {
        const val ECB_ENCRYPT = 0x00
        const val CBC_ENCRYPT = 0x01
        const val ECB_DECRYPT = 0x10
        const val CBC_DECRYPT = 0x11
    }

    // DUKPT AES WorkKey Types (com.urovo.sdk.pinpad.utils.Constant.DukptKeyType) [cite: 65]
    object DukptAesWorkKeyType {
        const val _2TDEA = 0
        const val _3TDEA = 1
        const val _AES128 = 2
        const val _AES192 = 3
        const val _AES256 = 4
    }

    // DUKPT AES Encrypt Modes [cite: 63, 64]
    object DukptAesEncMode {
        const val ECB_ENCRYPT = 0x00
        const val CBC_ENCRYPT = 0x01
        const val ECB_DECRYPT = 0x10
        const val CBC_DECRYPT = 0x11
    }

    // DUKPT AES MAC Modes [cite: 64]
    object DukptAesMacMode {
        const val MAC_ALG_X9_19 = 0x01
        const val MAC_ALG_ISO_9797_1_MAC_ALG5 = 0x02
    }

    // PIN Input Event [cite: 58] // Es 3.3.18, no 58.
    object PinInputEvent {
        const val CANCEL = 16
    }

    // Error Codes [cite: 66]
    object ErrorCode {
        const val SUCCESS = 0x00
        const val KEY_NOT_EXIST = 0x17 // O 0x14 o 0x23? 0x23 dice doc para deleteKey. 0x17 es genérico.
        const val KCV_ERROR = -1024 // Visto en Ped.java, no en doc.
    }
}