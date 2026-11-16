package org.example.ui.home

import android.net.Uri
import org.example.ui.model.DocumentUiModel

data class HomeUiState(
    val documents: List<DocumentUiModel> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isOperationInProgress: Boolean = false,
    val operationFeedback: OperationFeedback? = null,
    val isScanning: Boolean = false,
    val pendingScan: PendingScan? = null,
    val hasLoadError: Boolean = false
)

enum class DocumentAction {
    Import,
    Rename,
    Delete,
    Scan
}

data class OperationFeedback(
    val id: Long,
    val action: DocumentAction,
    val succeeded: Boolean,
    val failure: OperationFailure? = null
)

enum class OperationFailure {
    SCANNER_UNAVAILABLE,
    SCANNER_SETUP_FAILED,
    SCANNER_RESULT_INVALID,
    SCANNER_RESULT_MISSING,
    SCANNER_RESULT_UNREADABLE,
    SCAN_RESULT_LOST,
    SCAN_SOURCE_UNREADABLE,
    SCAN_SAVE_FAILED,
    SCAN_SAVE_CLEANUP_FAILED,
    INVALID_TITLE,
    INVALID_PAGE_COUNT,
    DOCUMENT_MISSING,
    DOCUMENT_LOAD_FAILED,
    DOCUMENT_RENAME_FAILED,
    PDF_DELETE_FAILED,
    DOCUMENT_DELETE_FAILED,
    UNKNOWN_OPERATION
}

data class PendingScan(
    val pdfUri: Uri,
    val pageCount: Int
)
