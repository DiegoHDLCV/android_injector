package com.vigatec.manufacturer.base.models

/**
* Defines the algorithm used for Message Authentication Code (MAC) calculation.
* Naming conventions try to follow standards where possible.
*/
enum class MacAlgorithm {
    /** ISO 9797-1 Algorithm 1, MAC Method 1 (DES/TDES CBC, no padding, zero IV). */
    CBC_MAC_ISO9797_1_M1,
    /** ISO 9797-1 Algorithm 3, MAC Method 2 (DES/TDES CBC, mandatory padding, zero IV). */
    CBC_MAC_ISO9797_1_M2,
    /** ANSI X9.19 Retail MAC (DES/TDES CBC-MAC variant). */
    RETAIL_MAC_ANSI_X9_19,
    /** CMAC using AES (specify key size via KeyAlgorithm). */
    CMAC_AES,
    /** MAC algorithm specific to UnionPay (often a TDES CBC variant). */
    UNIONPAY_CBC_MAC
    // Add others if needed
}