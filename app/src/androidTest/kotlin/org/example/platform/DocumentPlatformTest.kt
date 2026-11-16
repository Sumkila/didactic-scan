package org.example.platform

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.example.data.DocumentFailure
import org.example.data.DocumentRepository
import org.example.data.RepositoryResult
import org.example.data.local.files.DocumentFileStore
import org.example.data.model.ScannedDocument
import org.example.platform.documents.DocumentIntents
import org.example.platform.documents.PdfUriResolution
import org.example.platform.scanning.DocumentScanResult
import org.example.platform.scanning.DocumentScannerResultParser
import org.example.platform.scanning.ScanFailure
import org.example.ui.home.OperationFailure
import org.example.ui.scan.ScanViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DocumentPlatformTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun scannerResultValidationHandlesValidMissingInvalidAndCancelledResults() {
        val validUri = Uri.parse("content://scanner/test.pdf")

        assertEquals(
            DocumentScanResult.Ready(validUri, 3),
            DocumentScannerResultParser.validatePdfOutput(validUri, 3)
        )
        assertEquals(
            ScanFailure.MISSING_PDF,
            (DocumentScannerResultParser.validatePdfOutput(null, 1) as DocumentScanResult.Failed)
                .reason
        )
        assertEquals(
            ScanFailure.INVALID_PAGE_COUNT,
            (
                DocumentScannerResultParser.validatePdfOutput(validUri, 0)
                    as DocumentScanResult.Failed
                ).reason
        )
        assertEquals(
            ScanFailure.INVALID_PAGE_COUNT,
            (
                DocumentScannerResultParser.validatePdfOutput(
                    validUri,
                    DocumentScannerResultParser.MAX_PAGE_COUNT + 1
                ) as DocumentScanResult.Failed
                ).reason
        )
        assertEquals(
            DocumentScanResult.Cancelled,
            DocumentScannerResultParser.parse(Activity.RESULT_CANCELED, null)
        )
    }

    @Test
    fun openIntentUsesPdfMimeTypeContentUriAndReadPermission() {
        val uri = Uri.parse("content://org.example.fileprovider/managed_pdfs/test.pdf")

        val intent = DocumentIntents.createOpenIntent(uri)

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(DocumentIntents.PDF_MIME_TYPE, intent.type)
        assertEquals(uri, intent.data)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(uri, intent.clipData?.getItemAt(0)?.uri)
    }

    @Test
    fun shareChooserIncludesPdfStreamClipDataAndReadPermission() {
        val uri = Uri.parse("content://org.example.fileprovider/managed_pdfs/test.pdf")

        val chooser = DocumentIntents.createShareChooser(
            uri = uri,
            chooserTitle = "Share PDF",
            contentResolver = context.contentResolver
        )
        val sendIntent = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)

        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        assertEquals(Intent.ACTION_SEND, sendIntent?.action)
        assertEquals(DocumentIntents.PDF_MIME_TYPE, sendIntent?.type)
        assertEquals(uri, sendIntent?.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        assertTrue(sendIntent!!.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertTrue(chooser.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(uri, sendIntent.clipData?.getItemAt(0)?.uri)
        assertEquals(uri, chooser.clipData?.getItemAt(0)?.uri)
    }

    @Test
    fun fileProviderOnlyResolvesManagedPdfFiles() = runBlocking {
        val managedDirectory = File(context.filesDir, "documents").apply { mkdirs() }
        val managedFile = File(managedDirectory, "platform-test-${UUID.randomUUID()}.pdf")
            .apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val store = DocumentFileStore(context)

        try {
            val result = DocumentIntents.resolvePdfUri(context, store, managedFile.name)
            assertTrue(result is PdfUriResolution.Available)
            val uri = (result as PdfUriResolution.Available).uri
            assertEquals("content", uri.scheme)
            assertEquals("${context.packageName}.fileprovider", uri.authority)

            val outsideFile = File(context.filesDir, "outside-${UUID.randomUUID()}.pdf")
                .apply { writeBytes(byteArrayOf(1)) }
            try {
                assertEquals(
                    PdfUriResolution.Failed,
                    DocumentIntents.resolvePdfUri(context, store, "../${outsideFile.name}")
                )
            } finally {
                outsideFile.delete()
            }
        } finally {
            managedFile.delete()
        }
    }

    @Test
    fun missingManagedPdfReturnsMissingFile() = runBlocking {
        val result = DocumentIntents.resolvePdfUri(
            context,
            DocumentFileStore(context),
            "missing-${UUID.randomUUID()}.pdf"
        )

        assertEquals(PdfUriResolution.MissingFile, result)
    }

    @Test
    fun viewModelImportsPendingScanAndResetsProgress() = runBlocking {
        val repository = FakeDocumentRepository().apply {
            importGate = CompletableDeferred()
        }
        val viewModel = ScanViewModel(repository)
        val uri = Uri.parse("content://scanner/output.pdf")

        withContext(Dispatchers.Main) {
            assertTrue(viewModel.beginScan())
            assertTrue(viewModel.onScanResult(uri, 4))
            viewModel.importScannedPdf("Four pages")
            viewModel.importScannedPdf("Duplicate")
        }

        assertTrue(viewModel.uiState.value.isOperationInProgress)
        assertEquals(1, repository.importCount)
        repository.importGate?.complete(Unit)

        val completedState = viewModel.uiState.first {
            it.operationFeedback != null && !it.isOperationInProgress
        }
        assertEquals(1, repository.importCount)
        assertEquals(uri, repository.importedUri)
        assertEquals("Four pages", repository.importedTitle)
        assertEquals(4, repository.importedPageCount)
        assertFalse(completedState.isOperationInProgress)
        assertEquals(null, completedState.pendingScan)
        assertTrue(completedState.operationFeedback?.succeeded == true)
    }

    @Test
    fun viewModelScanCancellationDoesNotImportAndInvalidResultResetsProgress() = runBlocking {
        val repository = FakeDocumentRepository()
        val viewModel = ScanViewModel(repository)

        withContext(Dispatchers.Main) {
            assertTrue(viewModel.beginScan())
            viewModel.onScannerCancelled()
        }
        assertFalse(viewModel.uiState.value.isScanning)
        assertEquals(0, repository.importCount)

        withContext(Dispatchers.Main) {
            assertTrue(viewModel.beginScan())
            assertFalse(viewModel.onScanResult(Uri.EMPTY, 0))
        }
        val state = viewModel.uiState.first { it.operationFeedback != null }
        assertFalse(state.isScanning)
        assertEquals(0, repository.importCount)
        assertEquals(OperationFailure.SCANNER_RESULT_MISSING, state.operationFeedback?.failure)
    }

    @Test
    fun failedViewModelImportKeepsReadablePendingScanForRetry() = runBlocking {
        val repository = FakeDocumentRepository().apply {
            importResult = RepositoryResult.Failure(DocumentFailure.SOURCE_COPY_FAILED)
        }
        val viewModel = ScanViewModel(repository)

        withContext(Dispatchers.Main) {
            viewModel.beginScan()
            viewModel.onScanResult(Uri.parse("content://scanner/retry.pdf"), 1)
            viewModel.importScannedPdf("Retry")
        }

        val state = viewModel.uiState.first { it.operationFeedback != null }
        assertFalse(state.isOperationInProgress)
        assertTrue(state.pendingScan != null)
        assertEquals(OperationFailure.SCAN_SAVE_FAILED, state.operationFeedback?.failure)
    }

    private class FakeDocumentRepository : DocumentRepository {
        private val savedDocuments = MutableStateFlow(emptyList<ScannedDocument>())
        var importCount = 0
        var importedUri: Uri? = null
        var importedTitle: String? = null
        var importedPageCount: Int? = null
        var importGate: CompletableDeferred<Unit>? = null
        var importResult: RepositoryResult<ScannedDocument> =
            RepositoryResult.Success(
                ScannedDocument(
                    id = 1L,
                    title = "Saved",
                    createdAtEpochMillis = 1L,
                    pageCount = 1,
                    pdfFilePath = "saved.pdf"
                )
            )

        override suspend fun getDocument(documentId: Long): ScannedDocument? =
            savedDocuments.value.firstOrNull { it.id == documentId }

        override fun observeDocuments(): Flow<List<ScannedDocument>> = savedDocuments

        override fun observeDocument(documentId: Long): Flow<ScannedDocument?> =
            flowOf(savedDocuments.value.firstOrNull { it.id == documentId })

        override suspend fun importScannedPdf(
            sourceUri: Uri,
            title: String,
            pageCount: Int
        ): RepositoryResult<ScannedDocument> {
            importCount += 1
            importedUri = sourceUri
            importedTitle = title
            importedPageCount = pageCount
            importGate?.await()
            return importResult
        }

        override suspend fun renameDocument(
            documentId: Long,
            newTitle: String
        ): RepositoryResult<Unit> = RepositoryResult.Success(Unit)

        override suspend fun deleteDocument(documentId: Long): RepositoryResult<Unit> =
            RepositoryResult.Success(Unit)
    }
}
