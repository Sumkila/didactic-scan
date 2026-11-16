package org.example.platform.documents

import android.content.ClipData
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import org.example.data.local.files.DocumentFileStore

sealed interface PdfUriResolution {
    data class Available(val uri: Uri) : PdfUriResolution

    data object MissingFile : PdfUriResolution

    data object Failed : PdfUriResolution
}

object DocumentIntents {
    const val PDF_MIME_TYPE = "application/pdf"

    suspend fun resolvePdfUri(
        context: Context,
        fileStore: DocumentFileStore,
        relativePath: String
    ): PdfUriResolution {
        val file = try {
            fileStore.existingPdfFile(relativePath)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            Log.e(TAG, "Could not resolve a managed PDF file.", failure)
            return PdfUriResolution.Failed
        } ?: return PdfUriResolution.MissingFile

        return try {
            PdfUriResolution.Available(
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            )
        } catch (failure: Exception) {
            Log.e(TAG, "Could not create a managed PDF content URI.", failure)
            PdfUriResolution.Failed
        }
    }

    fun createOpenIntent(uri: Uri): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, PDF_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri("PDF document", uri)
        }

    fun createShareChooser(
        uri: Uri,
        chooserTitle: String,
        contentResolver: ContentResolver
    ): Intent {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = PDF_MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(contentResolver, "PDF document", uri)
        }
        return Intent.createChooser(sendIntent, chooserTitle).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = sendIntent.clipData
        }
    }

    private const val TAG = "DocumentIntents"
}
