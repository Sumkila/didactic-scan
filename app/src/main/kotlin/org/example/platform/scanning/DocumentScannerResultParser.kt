package org.example.platform.scanning

import android.app.Activity
import android.content.Intent
import android.net.Uri
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

enum class ScanFailure {
    UNAVAILABLE,
    LAUNCH_FAILED,
    INVALID_RESULT,
    MISSING_PDF,
    INVALID_PAGE_COUNT,
    UNREADABLE_RESULT
}

sealed interface DocumentScanResult {
    data class Ready(val pdfUri: Uri, val pageCount: Int) : DocumentScanResult

    data object Cancelled : DocumentScanResult

    data class Failed(
        val reason: ScanFailure,
        val cause: Throwable? = null
    ) : DocumentScanResult
}

object DocumentScannerResultParser {
    const val MAX_PAGE_COUNT = 5

    fun parse(resultCode: Int, data: Intent?): DocumentScanResult {
        if (resultCode == Activity.RESULT_CANCELED) return DocumentScanResult.Cancelled
        if (resultCode != Activity.RESULT_OK) {
            return DocumentScanResult.Failed(ScanFailure.INVALID_RESULT)
        }

        return try {
            val pdf = GmsDocumentScanningResult.fromActivityResultIntent(data)?.pdf
            validatePdfOutput(pdf?.uri, pdf?.pageCount)
        } catch (failure: Exception) {
            DocumentScanResult.Failed(ScanFailure.UNREADABLE_RESULT, failure)
        }
    }

    fun validatePdfOutput(uri: Uri?, pageCount: Int?): DocumentScanResult {
        if (uri == null || uri == Uri.EMPTY) {
            return DocumentScanResult.Failed(ScanFailure.MISSING_PDF)
        }
        if (pageCount == null || pageCount !in 1..MAX_PAGE_COUNT) {
            return DocumentScanResult.Failed(ScanFailure.INVALID_PAGE_COUNT)
        }
        return DocumentScanResult.Ready(uri, pageCount)
    }
}
