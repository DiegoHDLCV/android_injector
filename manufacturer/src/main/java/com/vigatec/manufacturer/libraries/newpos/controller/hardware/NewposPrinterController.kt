package com.vigatec.manufacturer.libraries.newpos.controller.hardware

import android.util.Log
import com.vigatec.manufacturer.base.controllers.hardware.IPrinterController
import com.vigatec.utils.enums.print.EnumFontSize
import com.vigatec.utils.enums.print.EnumQRSize

/**
 * Newpos Printer Controller - Stub implementation
 * Full implementation requires Newpos SDK dependencies (com.pos.device.printer)
 */
class NewposPrinterController : IPrinterController {
    private val TAG = "NewposPrinterController"
    private val printQueue = mutableListOf<String>()

    override val ERROR_PAPERENDING: Int = 0x02
    override val ERROR_PAPERENDED: Int = 0x03
    override val ERROR_PENOFOUND: Int = 0x01
    override val ALIGNCENTER: Int = 1
    override val ALIGNRIGHT: Int = 2
    override val ALIGNLEFT: Int = 0
    override val SUCCESS: Int = 0x88

    override fun getStatus(): Int {
        Log.d(TAG, "getStatus() called")
        return SUCCESS // Assume printer is ready
    }

    override fun addText(alignment: Int, message: String, enumFontSize: EnumFontSize) {
        Log.d(TAG, "addText: $message (align=$alignment, font=$enumFontSize)")
        printQueue.add(message)
    }

    override fun addQrCode(alignment: Int, enumQRSize: EnumQRSize, texto: String) {
        Log.d(TAG, "addQrCode: $texto (size=$enumQRSize)")
        printQueue.add("[QR: $texto]")
    }

    override fun addFeedLine(i: Int) {
        Log.d(TAG, "addFeedLine: $i lines")
        repeat(i) { printQueue.add("") }
    }

    override fun add2LineText(
        alignment: Int,
        message1: String,
        message2: String,
        enumFontSize: EnumFontSize
    ) {
        Log.d(TAG, "add2LineText: '$message1' | '$message2'")
        printQueue.add("$message1 | $message2")
    }

    override fun add3LineText(
        alignment: Int,
        message1: String,
        message2: String,
        message3: String,
        enumFontSize: EnumFontSize
    ) {
        Log.d(TAG, "add3LineText: '$message1' | '$message2' | '$message3'")
        printQueue.add("$message1 | $message2 | $message3")
    }

    override fun configFontSize(enumFontSize: EnumFontSize) {
        Log.d(TAG, "configFontSize: $enumFontSize")
    }

    override fun addFinalPaperFeed(dots: Int) {
        Log.d(TAG, "addFinalPaperFeed: $dots dots")
    }

    override fun getWearPercentage(): Int = 10

    override fun getMaxCharacterMultiValueLine(enumFontSize: EnumFontSize): Int {
        return when (enumFontSize) {
            EnumFontSize.XSMALL -> 48
            EnumFontSize.SMALL -> 32
            EnumFontSize.MEDIUM -> 24
            EnumFontSize.LARGE -> 16
        }
    }

    override fun startPrint(onFinish: () -> Unit, onError: (Int) -> Unit) {
        Log.i(TAG, "startPrint: Starting print with ${printQueue.size} items")
        try {
            // Log all queued items
            printQueue.forEachIndexed { index, item ->
                Log.d(TAG, "  [$index] $item")
            }
            // Simulate successful print
            printQueue.clear()
            onFinish()
        } catch (e: Exception) {
            Log.e(TAG, "Print failed", e)
            onError(ERROR_PENOFOUND)
        }
    }
}
