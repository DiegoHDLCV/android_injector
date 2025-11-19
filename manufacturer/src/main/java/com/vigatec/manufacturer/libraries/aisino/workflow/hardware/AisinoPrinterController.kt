package com.vigatec.manufacturer.libraries.aisino.workflow.hardware

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.vanstone.trans.api.PrinterApi
import com.vigatec.manufacturer.base.controllers.hardware.IPrinterController
import com.vigatec.utils.enums.print.EnumFontSize
import com.vigatec.utils.enums.print.EnumQRSize
import java.util.concurrent.CountDownLatch

/**
 * Aisino Printer Controller - Implementation using Vanstone SDK
 */
class AisinoPrinterController(private val context: Context?) : IPrinterController {
    private val TAG = "AisinoPrinterController"
    
    // Queue to hold print commands before execution
    private val printQueue = mutableListOf<PrintCommand>()

    override val ERROR_PAPERENDING: Int = 0x02
    override val ERROR_PAPERENDED: Int = 0x03
    override val ERROR_PENOFOUND: Int = 0x01
    override val ALIGNCENTER: Int = 1
    override val ALIGNRIGHT: Int = 2
    override val ALIGNLEFT: Int = 0
    override val SUCCESS: Int = 0x88

    // Sealed class to represent different print operations
    private sealed class PrintCommand {
        data class Text(val text: String, val fontSize: EnumFontSize, val align: Int) : PrintCommand()
        data class QrCode(val content: String, val size: EnumQRSize, val align: Int) : PrintCommand()
        data class FeedLine(val lines: Int) : PrintCommand()
        data class Image(val bitmap: Bitmap, val align: Int) : PrintCommand()
    }

    override fun getStatus(): Int {
        Log.d(TAG, "getStatus() called")
        return try {
            if (context != null) {
                // Try to use the context-aware status check if available or set context first
                PrinterApi.setContext(context)
                val status = PrinterApi.PrnStatus_Api()
                Log.d(TAG, "Printer status: $status")
                status
            } else {
                 Log.w(TAG, "Context is null, cannot check status reliably")
                 ERROR_PENOFOUND
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting printer status", e)
            ERROR_PENOFOUND
        }
    }

    override fun addText(alignment: Int, message: String, enumFontSize: EnumFontSize) {
        printQueue.add(PrintCommand.Text(message, enumFontSize, alignment))
    }

    override fun addQrCode(alignment: Int, enumQRSize: EnumQRSize, texto: String) {
        printQueue.add(PrintCommand.QrCode(texto, enumQRSize, alignment))
    }

    override fun addFeedLine(i: Int) {
        printQueue.add(PrintCommand.FeedLine(i))
    }

    override fun add2LineText(
        alignment: Int,
        message1: String,
        message2: String,
        enumFontSize: EnumFontSize
    ) {
        val combined = "$message1 $message2"
        printQueue.add(PrintCommand.Text(combined, enumFontSize, alignment))
    }

    override fun add3LineText(
        alignment: Int,
        message1: String,
        message2: String,
        message3: String,
        enumFontSize: EnumFontSize
    ) {
        val combined = "$message1 $message2 $message3"
        printQueue.add(PrintCommand.Text(combined, enumFontSize, alignment))
    }

    override fun configFontSize(enumFontSize: EnumFontSize) {
        // Not used in queue-based approach
    }

    override fun addFinalPaperFeed(dots: Int) {
        printQueue.add(PrintCommand.FeedLine(3))
    }

    override fun getWearPercentage(): Int = 0

    override fun getMaxCharacterMultiValueLine(enumFontSize: EnumFontSize): Int {
        return when (enumFontSize) {
            EnumFontSize.XSMALL -> 48
            EnumFontSize.SMALL -> 32
            EnumFontSize.MEDIUM -> 24
            EnumFontSize.LARGE -> 16
        }
    }

    override fun startPrint(onFinish: () -> Unit, onError: (Int) -> Unit) {
        Log.i(TAG, "startPrint: Starting print process with ${printQueue.size} items")
        
        if (context == null) {
            Log.e(TAG, "Context is null, cannot print")
            onError(ERROR_PENOFOUND)
            return
        }

        Thread {
            try {
                // 1. Initialize/Set Context
                PrinterApi.setContext(context)
                
                // 2. Clear Buffer
                PrinterApi.PrnClrBuff_Api()

                // 3. Process Queue
                for (cmd in printQueue) {
                    when (cmd) {
                        is PrintCommand.Text -> {
                            // Set Alignment
                            PrinterApi.printSetAlign_Api(cmd.align)
                            
                            // Set Font Size (Mapping to int size expected by SDK)
                            // Assuming 16, 24, 32 etc. based on typical POS printer sizes
                            val fontSize = when (cmd.fontSize) {
                                EnumFontSize.XSMALL -> 16
                                EnumFontSize.SMALL -> 24
                                EnumFontSize.MEDIUM -> 24
                                EnumFontSize.LARGE -> 32
                            }
                            PrinterApi.printSetTextSize_Api(fontSize)
                            PrinterApi.printSetGray_Api(5) // Moderate gray level
                            
                            PrinterApi.PrnStr_Api(cmd.text)
                        }
                        is PrintCommand.FeedLine -> {
                            PrinterApi.printFeedLine_Api(cmd.lines)
                        }
                        is PrintCommand.QrCode -> {
                            PrinterApi.printSetAlign_Api(cmd.align)
                            // Using printAddQrCode_Api(width, height, content) or similar
                            // Based on javap: printAddQrCode_Api(int, int, String)
                            // Likely (width, height, content) or (offset, size, content)
                            val size = when (cmd.size) {
                                EnumQRSize.XSMALL -> 150
                                EnumQRSize.SMALL -> 200
                                EnumQRSize.MEDIUM -> 300
                                EnumQRSize.LARGE -> 380
                            }
                            // Doc: printAddQrCodeApi(int align, int height, java.lang.String qrCode)
                            PrinterApi.printAddQrCode_Api(cmd.align, size, cmd.content)
                        }
                        is PrintCommand.Image -> {
                             // PrinterApi.PrnLogo_Api(cmd.bitmap)
                        }
                    }
                }

                // 4. Start Printing
                Log.i(TAG, "PrnStart_Api calling...")
                val ret = PrinterApi.PrnStart_Api()
                
                if (ret == 0) {
                    Log.i(TAG, "Print success")
                    onFinish()
                } else {
                    Log.e(TAG, "Print failed: $ret")
                    onError(ret)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Exception during print", e)
                onError(ERROR_PENOFOUND)
            } finally {
                printQueue.clear()
            }
        }.start()
    }
}
