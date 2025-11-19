package com.vigatec.manufacturer.base.controllers.hardware

import com.vigatec.utils.enums.print.EnumFontSize
import com.vigatec.utils.enums.print.EnumQRSize

/**
 * Interface for controlling thermal printer hardware.
 * Abstracts printer operations for different manufacturers (Newpos, Aisino, Urovo).
 */
interface IPrinterController {

    // Error codes
    val ERROR_PAPERENDING: Int
    val ERROR_PAPERENDED: Int
    val ERROR_PENOFOUND: Int
    val SUCCESS: Int

    // Alignment constants
    val ALIGNLEFT: Int
    val ALIGNCENTER: Int
    val ALIGNRIGHT: Int

    /**
     * Get current printer status
     * @return status code (SUCCESS, ERROR_PAPERENDED, ERROR_PENOFOUND, etc.)
     */
    fun getStatus(): Int

    /**
     * Add a text line to the print queue
     * @param alignment ALIGNLEFT, ALIGNCENTER, or ALIGNRIGHT
     * @param message The text to print
     * @param enumFontSize Font size for the text
     */
    fun addText(alignment: Int, message: String, enumFontSize: EnumFontSize)

    /**
     * Add a QR code to the print queue
     * @param alignment ALIGNLEFT, ALIGNCENTER, or ALIGNRIGHT
     * @param enumQRSize Size of the QR code
     * @param texto The text to encode in the QR code
     */
    fun addQrCode(alignment: Int, enumQRSize: EnumQRSize, texto: String)

    /**
     * Add a line of blank space (feed paper)
     * @param i Number of lines to feed
     */
    fun addFeedLine(i: Int)

    /**
     * Add text with 2 columns
     * Automatically formats text to fit in 2 columns
     * @param alignment ALIGNLEFT, ALIGNCENTER, or ALIGNRIGHT
     * @param message1 First column text
     * @param message2 Second column text
     * @param enumFontSize Font size for the text
     */
    fun add2LineText(
        alignment: Int,
        message1: String,
        message2: String,
        enumFontSize: EnumFontSize
    )

    /**
     * Add text with 3 columns
     * Automatically formats text to fit in 3 columns
     * @param alignment ALIGNLEFT, ALIGNCENTER, or ALIGNRIGHT
     * @param message1 First column text
     * @param message2 Second column text
     * @param message3 Third column text
     * @param enumFontSize Font size for the text
     */
    fun add3LineText(
        alignment: Int,
        message1: String,
        message2: String,
        message3: String,
        enumFontSize: EnumFontSize
    )

    /**
     * Configure the font size for subsequent text operations
     * @param enumFontSize The font size to set
     */
    fun configFontSize(enumFontSize: EnumFontSize)

    /**
     * Start printing all queued elements
     * @param onFinish Callback when printing completes successfully
     * @param onError Callback when printing fails with error code
     */
    fun startPrint(onFinish: () -> Unit, onError: (Int) -> Unit)

    /**
     * Get maximum number of characters for a multi-value line with given font size
     * @param enumFontSize The font size to measure
     * @return Maximum number of characters that fit in the printer width
     */
    fun getMaxCharacterMultiValueLine(enumFontSize: EnumFontSize): Int

    /**
     * Add final paper feed after printing
     * @param dots Number of dots/units to feed
     */
    fun addFinalPaperFeed(dots: Int)

    /**
     * Get printer wear percentage (for maintenance tracking)
     * @return Wear percentage (0-100)
     */
    fun getWearPercentage(): Int
}
