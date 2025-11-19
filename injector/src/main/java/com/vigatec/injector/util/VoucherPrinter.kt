package com.vigatec.injector.util

import android.util.Log
import com.vigatec.injector.model.VoucherData
import com.vigatec.manufacturer.ManufacturerHardwareManager
import com.vigatec.manufacturer.base.controllers.hardware.IPrinterController
import com.vigatec.utils.enums.print.EnumFontSize
import com.vigatec.utils.enums.print.EnumQRSize

/**
 * Utility class for printing key injection vouchers using thermal printer.
 * Handles formatting, layout, and printing of voucher data.
 */
object VoucherPrinter {
    private const val TAG = "VoucherPrinter"
    private const val SEPARATOR = "================================"
    private const val SMALL_SEPARATOR = "--------------------------------"

    /**
     * Print an injection voucher with all key information
     * @param voucherData The voucher data to print
     * @param onSuccess Callback when printing succeeds
     * @param onError Callback when printing fails with error message
     */
    fun printInjectionVoucher(
        voucherData: VoucherData,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        Log.d(TAG, "Starting voucher printing for profile: ${voucherData.profileName}")

        try {
            val printer = ManufacturerHardwareManager.printerController()

            // Check printer status before starting
            val status = printer.getStatus()
            if (status != printer.SUCCESS) {
                val errorMessage = mapPrinterStatus(status)
                Log.e(TAG, "Printer error before print: $errorMessage (code: $status)")
                onError(errorMessage)
                return
            }

            // Build voucher content
            buildVoucher(voucherData, printer)

            // Add final paper feed
            printer.addFinalPaperFeed(100)

            // Start printing
            printer.startPrint(
                onFinish = {
                    Log.i(TAG, "Voucher printed successfully")
                    onSuccess()
                },
                onError = { errorCode ->
                    val errorMessage = mapPrinterStatus(errorCode)
                    Log.e(TAG, "Printing failed with code: $errorCode ($errorMessage)")
                    onError(errorMessage)
                }
            )

        } catch (e: Exception) {
            Log.e(TAG, "Exception during voucher printing", e)
            onError("Error de impresión: ${e.localizedMessage}")
        }
    }

    /**
     * Build the voucher content in the printer queue
     */
    private fun buildVoucher(voucherData: VoucherData, printer: IPrinterController) {
        Log.d(TAG, "Building voucher content")

        // Header
        printHeader(printer)

        // Device information section
        printer.addFeedLine(1)
        printer.addText(printer.ALIGNCENTER, "DISPOSITIVO", EnumFontSize.SMALL)
        printer.addText(printer.ALIGNLEFT, SMALL_SEPARATOR, EnumFontSize.XSMALL)
        printer.add2LineText(
            printer.ALIGNLEFT,
            "Serial:",
            voucherData.deviceSerial,
            EnumFontSize.SMALL
        )
        printer.add2LineText(
            printer.ALIGNLEFT,
            "Modelo:",
            voucherData.deviceModel,
            EnumFontSize.SMALL
        )

        // Injection information section
        printer.addFeedLine(1)
        printer.addText(printer.ALIGNCENTER, "INYECCIÓN", EnumFontSize.SMALL)
        printer.addText(printer.ALIGNLEFT, SMALL_SEPARATOR, EnumFontSize.XSMALL)
        printer.add2LineText(
            printer.ALIGNLEFT,
            "Perfil:",
            voucherData.profileName,
            EnumFontSize.SMALL
        )
        printer.add2LineText(
            printer.ALIGNLEFT,
            "Usuario:",
            voucherData.username,
            EnumFontSize.SMALL
        )
        printer.add2LineText(
            printer.ALIGNLEFT,
            "Fecha:",
            voucherData.injectionDate,
            EnumFontSize.SMALL
        )
        printer.add2LineText(
            printer.ALIGNLEFT,
            "Hora:",
            voucherData.injectionTime,
            EnumFontSize.SMALL
        )
        printer.add2LineText(
            printer.ALIGNLEFT,
            "Estado:",
            voucherData.injectionStatus,
            EnumFontSize.SMALL
        )

        // Summary section
        printer.addFeedLine(1)
        printer.addText(printer.ALIGNCENTER, "RESUMEN", EnumFontSize.SMALL)
        printer.addText(printer.ALIGNLEFT, SMALL_SEPARATOR, EnumFontSize.XSMALL)
        val summaryLine = "Inyectadas: ${voucherData.successfulKeys}/${voucherData.totalKeys}"
        printer.addText(printer.ALIGNCENTER, summaryLine, EnumFontSize.SMALL)

        // Keys detail section
        if (voucherData.keysInjected.isNotEmpty()) {
            //printer.addFeedLine(1)
            printer.addText(printer.ALIGNCENTER, "DETALLE DE LLAVES", EnumFontSize.SMALL)
            printer.addText(printer.ALIGNLEFT, SMALL_SEPARATOR, EnumFontSize.XSMALL)

            voucherData.keysInjected.forEachIndexed { index, key ->
                if (index > 0) {
                    printer.addFeedLine(1)
                }

                // Key header with usage
                printer.addText(
                    printer.ALIGNLEFT,
                    "Llave ${index + 1}: ${key.keyUsage}",
                    EnumFontSize.SMALL
                )

                // Key details in 2-column format
                printer.add2LineText(
                    printer.ALIGNLEFT,
                    "Slot:",
                    key.keySlot,
                    EnumFontSize.XSMALL
                )
                printer.add2LineText(
                    printer.ALIGNLEFT,
                    "Tipo:",
                    key.keyType,
                    EnumFontSize.XSMALL
                )
                printer.add2LineText(
                    printer.ALIGNLEFT,
                    "KCV:",
                    key.kcv,
                    EnumFontSize.XSMALL
                )
                printer.add2LineText(
                    printer.ALIGNLEFT,
                    "Estado:",
                    key.status,
                    EnumFontSize.XSMALL
                )
            }
        }

        // QR Code section
        printer.addFeedLine(1)
        val qrContent = voucherData.getQrCodeContent()
        Log.d(TAG, "Adding QR code with content: $qrContent")
        printer.addQrCode(printer.ALIGNCENTER, EnumQRSize.MEDIUM, qrContent)

        // Footer
        //printer.addFeedLine(1)
        printFooter(printer)
    }

    /**
     * Print voucher header
     */
    private fun printHeader(printer: IPrinterController) {
        printer.addText(printer.ALIGNCENTER, SEPARATOR, EnumFontSize.XSMALL)
        //printer.addFeedLine(1)
        printer.addText(
            printer.ALIGNCENTER,
            "VOUCHER DE INYECCIÓN",
            EnumFontSize.MEDIUM
        )
        printer.addText(
            printer.ALIGNCENTER,
            "DE LLAVES",
            EnumFontSize.MEDIUM
        )
        //printer.addFeedLine(1)
        printer.addText(printer.ALIGNCENTER, SEPARATOR, EnumFontSize.XSMALL)
    }

    /**
     * Print voucher footer
     */
    private fun printFooter(printer: IPrinterController) {
        printer.addText(printer.ALIGNCENTER, SEPARATOR, EnumFontSize.XSMALL)
        //printer.addFeedLine(1)
        printer.addText(
            printer.ALIGNCENTER,
            "Fin del Voucher",
            EnumFontSize.XSMALL
        )

        printer.addText(printer.ALIGNCENTER, SEPARATOR, EnumFontSize.XSMALL)
        printer.addFeedLine(1)
    }

    /**
     * Map printer status code to human-readable error message
     */
    private fun mapPrinterStatus(statusCode: Int): String {
        return when (statusCode) {
            0x01, 0x02, 0x03 -> "Error de impresora: Sin papel"
            0xaa -> "Error de impresora: En uso"
            0x88 -> "Impresora lista"
            else -> "Error de impresora: Código $statusCode"
        }
    }
}
