package com.vigatec.manufacturer.base.models

/**
 * Defines the format for constructing the PIN block before encryption.
 * Based on ISO 9564 formats.
 */
enum class PinBlockFormatType {
    /** ISO 9564-1 Format 0: PAN included. */
    ISO9564_0,
    /** ISO 9564-1 Format 1: No PAN included (used for offline PIN). */
    ISO9564_1,
    /** ISO 9564-1 Format 3: PAN included (like Format 0 but different padding). */
    ISO9564_3,
    /** ISO 9564-1 Format 4: (Newer format, often used with AES). */
    ISO9564_4
    // Format 2 is less common
}