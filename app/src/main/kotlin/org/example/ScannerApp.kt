package org.example

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.example.platform.documents.DocumentIntents
import org.example.platform.documents.PdfUriResolution
import org.example.platform.scanning.DocumentScanResult
import org.example.platform.scanning.DocumentScannerResultParser
import org.example.platform.scanning.ScanFailure
import org.example.ui.components.DeleteDocumentDialog
import org.example.ui.components.DocumentNameDialog
import org.example.ui.home.DocumentAction
import org.example.ui.model.DocumentUiModel
import org.example.ui.home.OperationFailure
import org.example.ui.navigation.ScannerNavHost
import org.example.ui.scan.ScanViewModel
import org.example.ui.scan.ScanViewModelFactory

private data class NamingDocument(val id: Long, val initialName: String)

@Composable
fun ScannerApp() {
    val context = LocalContext.current
    val application = context.applicationContext as ScannerApplication
    val viewModelFactory = remember(application) {
        ScanViewModelFactory(application.container.documentRepository)
    }
    val scanViewModel: ScanViewModel = viewModel(factory = viewModelFactory)
    val uiState by scanViewModel.uiState.collectAsStateWithLifecycle()
    var namingDocument by remember { mutableStateOf<NamingDocument?>(null) }
    var documentToDelete by remember { mutableStateOf<DocumentUiModel?>(null) }
    var deletionCompletion by remember { mutableStateOf<(() -> Unit)?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val feedback = uiState.operationFeedback
    val scannerOptions = remember {
        GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(false)
            .setPageLimit(DocumentScannerResultParser.MAX_PAGE_COUNT)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
    }
    val documentScanner = remember(scannerOptions) {
        GmsDocumentScanning.getClient(scannerOptions)
    }
    val scanResultLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        when (
            val scanResult = DocumentScannerResultParser.parse(
                result.resultCode,
                result.data
            )
        ) {
            DocumentScanResult.Cancelled -> scanViewModel.onScannerCancelled()
            is DocumentScanResult.Ready -> scanViewModel.onScanResult(
                scanResult.pdfUri,
                scanResult.pageCount
            )
            is DocumentScanResult.Failed -> {
                scanResult.cause?.let {
                    Log.e(TAG, "Could not read the document scanner result.", it)
                }
                scanViewModel.onScannerLaunchFailed(scanResult.reason.toOperationFailure())
            }
        }
    }

    LaunchedEffect(feedback?.id) {
        if (feedback != null) {
            if (feedback.action == DocumentAction.Delete && feedback.succeeded) {
                deletionCompletion?.invoke()
            }
            val messageId = if (feedback.succeeded) {
                when (feedback.action) {
                    DocumentAction.Import -> R.string.import_success
                    DocumentAction.Rename -> R.string.rename_success
                    DocumentAction.Delete -> R.string.delete_success
                    DocumentAction.Scan -> R.string.scan_result_ready
                }
            } else {
                feedback.failure?.toStringResource()
                    ?: R.string.document_operation_failed
            }
            snackbarHostState.showSnackbar(context.getString(messageId))
            deletionCompletion = null
            scanViewModel.clearOperationFeedback(feedback.id)
        }
    }

    fun launchScanner() {
        if (!scanViewModel.beginScan()) return

        val activity = context.findActivity()
        if (activity == null) {
            scanViewModel.onScannerLaunchFailed(OperationFailure.SCANNER_SETUP_FAILED)
            return
        }

        try {
            documentScanner.getStartScanIntent(activity)
                .addOnSuccessListener { intentSender ->
                    if (activity.isFinishing || activity.isDestroyed) {
                        scanViewModel.onScannerLaunchFailed(
                            OperationFailure.SCANNER_SETUP_FAILED
                        )
                        return@addOnSuccessListener
                    }
                    try {
                        scanResultLauncher.launch(
                            IntentSenderRequest.Builder(intentSender).build()
                        )
                    } catch (failure: Exception) {
                        Log.e(TAG, "Could not launch the document scanner.", failure)
                        scanViewModel.onScannerLaunchFailed(
                            OperationFailure.SCANNER_SETUP_FAILED
                        )
                    }
                }
                .addOnFailureListener { failure ->
                    Log.e(TAG, "Document scanner preparation failed.", failure)
                    scanViewModel.onScannerLaunchFailed(
                        OperationFailure.SCANNER_UNAVAILABLE
                    )
                }
        } catch (failure: Exception) {
            Log.e(TAG, "Could not prepare the document scanner.", failure)
            scanViewModel.onScannerLaunchFailed(OperationFailure.SCANNER_SETUP_FAILED)
        }
    }

    suspend fun resolveDocumentUri(documentId: Long): android.net.Uri? {
        val repository = application.container.documentRepository
        val document = try {
            repository.getDocument(documentId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            Log.e(TAG, "Could not load a document for an external action.", failure)
            snackbarHostState.showSnackbar(context.getString(R.string.document_load_failed))
            return null
        }
        if (document == null) {
            snackbarHostState.showSnackbar(context.getString(R.string.document_not_found))
            return null
        }

        return when (
            val resolution = DocumentIntents.resolvePdfUri(
                context = context,
                fileStore = application.container.documentFileStore,
                relativePath = document.pdfFilePath
            )
        ) {
            is PdfUriResolution.Available -> resolution.uri
            PdfUriResolution.MissingFile -> {
                snackbarHostState.showSnackbar(context.getString(R.string.saved_pdf_missing))
                null
            }
            PdfUriResolution.Failed -> {
                snackbarHostState.showSnackbar(context.getString(R.string.pdf_access_failed))
                null
            }
        }
    }

    fun openDocument(documentId: Long) {
        scope.launch {
            val uri = resolveDocumentUri(documentId) ?: return@launch
            try {
                context.startActivity(DocumentIntents.createOpenIntent(uri))
            } catch (_: ActivityNotFoundException) {
                snackbarHostState.showSnackbar(context.getString(R.string.no_pdf_viewer))
            } catch (failure: Exception) {
                Log.e(TAG, "Could not open the saved PDF.", failure)
                snackbarHostState.showSnackbar(context.getString(R.string.pdf_open_failed))
            }
        }
    }

    fun shareDocument(documentId: Long) {
        scope.launch {
            val uri = resolveDocumentUri(documentId) ?: return@launch
            try {
                context.startActivity(
                    DocumentIntents.createShareChooser(
                        uri = uri,
                        chooserTitle = context.getString(R.string.share_chooser_title),
                        contentResolver = context.contentResolver
                    )
                )
            } catch (failure: Exception) {
                Log.e(TAG, "Could not open the PDF share sheet.", failure)
                snackbarHostState.showSnackbar(context.getString(R.string.pdf_share_failed))
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            if (uiState.isOperationInProgress || uiState.isScanning) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Box(modifier = Modifier.weight(1f)) {
                ScannerNavHost(
                    uiState = uiState,
                    onScanClick = ::launchScanner,
                    onRetryClick = scanViewModel::retryObservingDocuments,
                    onOpenDocument = ::openDocument,
                    onShareDocument = ::shareDocument,
                    onRenameDocument = { document ->
                        namingDocument = NamingDocument(document.id, document.title)
                    },
                    onDeleteDocument = { document, onDeleted ->
                        documentToDelete = document
                        deletionCompletion = onDeleted
                    }
                )
            }
        }
    }

    val pendingScan = uiState.pendingScan
    if (pendingScan != null && namingDocument == null) {
        DocumentNameDialog(
            title = stringResource(R.string.name_scanned_document_title),
            initialName = stringResource(R.string.suggested_document_name),
            cancelLabel = stringResource(R.string.cancel_action),
            confirmLabel = stringResource(R.string.save_action),
            isBusy = uiState.isOperationInProgress,
            onDismiss = scanViewModel::cancelPendingScan,
            onConfirm = scanViewModel::importScannedPdf
        )
    }

    namingDocument?.let { naming ->
        DocumentNameDialog(
            title = stringResource(R.string.rename_document_title),
            initialName = naming.initialName,
            cancelLabel = stringResource(R.string.cancel_action),
            confirmLabel = stringResource(R.string.rename_action),
            isBusy = uiState.isOperationInProgress,
            onDismiss = { namingDocument = null },
            onConfirm = { newName ->
                scanViewModel.renameDocument(naming.id, newName)
                namingDocument = null
            }
        )
    }

    documentToDelete?.let { document ->
        DeleteDocumentDialog(
            documentTitle = document.title,
            onDismiss = {
                documentToDelete = null
                deletionCompletion = null
            },
            onConfirm = {
                documentToDelete = null
                scanViewModel.deleteDocument(document.id)
            }
        )
    }
}

private fun Context.findActivity(): Activity? {
    var currentContext: Context = this
    while (currentContext is ContextWrapper) {
        if (currentContext is Activity) return currentContext
        val nextContext = currentContext.baseContext
        if (nextContext === currentContext) break
        currentContext = nextContext
    }
    return currentContext as? Activity
}

private fun ScanFailure.toOperationFailure(): OperationFailure =
    when (this) {
        ScanFailure.UNAVAILABLE -> OperationFailure.SCANNER_UNAVAILABLE
        ScanFailure.LAUNCH_FAILED -> OperationFailure.SCANNER_SETUP_FAILED
        ScanFailure.INVALID_RESULT -> OperationFailure.SCANNER_RESULT_INVALID
        ScanFailure.MISSING_PDF -> OperationFailure.SCANNER_RESULT_MISSING
        ScanFailure.INVALID_PAGE_COUNT -> OperationFailure.SCANNER_RESULT_INVALID
        ScanFailure.UNREADABLE_RESULT -> OperationFailure.SCANNER_RESULT_UNREADABLE
    }

private fun OperationFailure.toStringResource(): Int =
    when (this) {
        OperationFailure.SCANNER_UNAVAILABLE -> R.string.scanner_unavailable
        OperationFailure.SCANNER_SETUP_FAILED -> R.string.scanner_setup_failed
        OperationFailure.SCANNER_RESULT_INVALID -> R.string.scanner_result_invalid
        OperationFailure.SCANNER_RESULT_MISSING -> R.string.scanner_result_missing
        OperationFailure.SCANNER_RESULT_UNREADABLE -> R.string.scanner_result_unreadable
        OperationFailure.SCAN_RESULT_LOST -> R.string.scan_result_lost
        OperationFailure.SCAN_SOURCE_UNREADABLE -> R.string.scan_source_unreadable
        OperationFailure.SCAN_SAVE_FAILED -> R.string.scan_save_failed
        OperationFailure.SCAN_SAVE_CLEANUP_FAILED -> R.string.scan_save_cleanup_failed
        OperationFailure.INVALID_TITLE -> R.string.name_required
        OperationFailure.INVALID_PAGE_COUNT -> R.string.invalid_page_count
        OperationFailure.DOCUMENT_MISSING -> R.string.document_not_found
        OperationFailure.DOCUMENT_LOAD_FAILED -> R.string.document_load_failed
        OperationFailure.DOCUMENT_RENAME_FAILED -> R.string.document_rename_failed
        OperationFailure.PDF_DELETE_FAILED -> R.string.pdf_delete_failed
        OperationFailure.DOCUMENT_DELETE_FAILED -> R.string.document_delete_failed
        OperationFailure.UNKNOWN_OPERATION -> R.string.document_operation_failed
    }

private const val TAG = "QuickScan"
