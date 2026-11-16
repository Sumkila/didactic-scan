package org.example.ui.scan

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.data.DocumentRepository
import org.example.data.DocumentFailure
import org.example.data.RepositoryResult
import org.example.data.model.ScannedDocument
import org.example.ui.home.DocumentAction
import org.example.ui.home.HomeUiState
import org.example.ui.home.OperationFailure
import org.example.ui.home.OperationFeedback
import org.example.ui.home.PendingScan
import org.example.ui.model.DocumentUiModel
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.atomic.AtomicLong

class ScanViewModel(
    private val documentRepository: DocumentRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState(isLoading = true))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val nextFeedbackId = AtomicLong(0L)
    private var observationJob: Job? = null

    init {
        observeDocuments()
    }
    fun retryObservingDocuments() {
        observeDocuments()
    }

    fun beginScan(): Boolean {
        val state = _uiState.value
        if (state.isScanning || state.isOperationInProgress || state.pendingScan != null) {
            return false
        }
        _uiState.value = state.copy(isScanning = true, operationFeedback = null)
        return true
    }

    fun onScannerCancelled() {
        _uiState.update { it.copy(isScanning = false) }
    }

    fun onScannerLaunchFailed(failure: OperationFailure) {
        _uiState.update { it.copy(isScanning = false) }
        publishFeedback(
            action = DocumentAction.Scan,
            succeeded = false,
            failure = failure
        )
    }

    fun onScanResult(pdfUri: Uri?, pageCount: Int): Boolean {
        val state = _uiState.value
        if (
            !state.isScanning ||
            pdfUri == null ||
            pdfUri == Uri.EMPTY ||
            pageCount <= 0
        ) {
            _uiState.update { it.copy(isScanning = false) }
            val failure = when {
                !state.isScanning -> OperationFailure.SCAN_RESULT_LOST
                pdfUri == null || pdfUri == Uri.EMPTY -> OperationFailure.SCANNER_RESULT_MISSING
                else -> OperationFailure.SCANNER_RESULT_INVALID
            }
            publishFeedback(
                action = DocumentAction.Scan,
                succeeded = false,
                failure = failure
            )
            return false
        }
        _uiState.value = state.copy(
            isScanning = false,
            pendingScan = PendingScan(pdfUri, pageCount)
        )
        return true
    }

    fun cancelPendingScan() {
        if (!_uiState.value.isOperationInProgress) {
            _uiState.update { it.copy(pendingScan = null) }
        }
    }

    fun importScannedPdf(title: String) {
        val pendingScan = _uiState.value.pendingScan ?: return
        runOperation(DocumentAction.Import) {
            documentRepository.importScannedPdf(
                sourceUri = pendingScan.pdfUri,
                title = title,
                pageCount = pendingScan.pageCount
            )
        }
    }

    fun renameDocument(documentId: Long, newTitle: String) {
        runOperation(DocumentAction.Rename) {
            documentRepository.renameDocument(documentId, newTitle)
        }
    }

    fun deleteDocument(documentId: Long) {
        runOperation(DocumentAction.Delete) {
            documentRepository.deleteDocument(documentId)
        }
    }

    fun clearOperationFeedback(feedbackId: Long) {
        _uiState.update { state ->
            if (state.operationFeedback?.id == feedbackId) {
                state.copy(operationFeedback = null)
            } else {
                state
            }
        }
    }

    private fun observeDocuments() {
        observationJob?.cancel()
        observationJob = viewModelScope.launch {
            _uiState.update {
                it.copy(isLoading = true, errorMessage = null, hasLoadError = false)
            }
            try {
                documentRepository.observeDocuments().collect { documents ->
                    _uiState.update {
                        it.copy(
                            documents = documents.map { document -> document.toUiModel() },
                            isLoading = false,
                            errorMessage = null,
                            hasLoadError = false
                        )
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = null,
                        hasLoadError = true
                    )
                }
            }
        }
    }

    private fun runOperation(
        action: DocumentAction,
        operation: suspend () -> RepositoryResult<*>
    ) {
        if (_uiState.value.isOperationInProgress || _uiState.value.isScanning) return

        _uiState.update {
            it.copy(isOperationInProgress = true, operationFeedback = null)
        }
        viewModelScope.launch {
            try {
                when (val result = operation()) {
                    is RepositoryResult.Success -> {
                        if (action == DocumentAction.Import) {
                            _uiState.update { it.copy(pendingScan = null) }
                        }
                        publishFeedback(action, succeeded = true)
                    }
                    is RepositoryResult.Failure -> {
                        if (result.reason == DocumentFailure.SOURCE_UNREADABLE) {
                            _uiState.update { it.copy(pendingScan = null) }
                        }
                        publishFeedback(
                            action = action,
                            succeeded = false,
                            failure = result.reason.toOperationFailure()
                        )
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                publishFeedback(
                    action = action,
                    succeeded = false,
                    failure = OperationFailure.UNKNOWN_OPERATION
                )
            } finally {
                _uiState.update { it.copy(isOperationInProgress = false) }
            }
        }
    }

    private fun publishFeedback(
        action: DocumentAction,
        succeeded: Boolean,
        failure: OperationFailure? = null
    ) {
        val feedback = OperationFeedback(
            id = nextFeedbackId.incrementAndGet(),
            action = action,
            succeeded = succeeded,
            failure = failure
        )
        _uiState.update { it.copy(operationFeedback = feedback) }
    }

    private fun DocumentFailure.toOperationFailure(): OperationFailure =
        when (this) {
            DocumentFailure.INVALID_TITLE -> OperationFailure.INVALID_TITLE
            DocumentFailure.INVALID_PAGE_COUNT -> OperationFailure.INVALID_PAGE_COUNT
            DocumentFailure.SOURCE_UNREADABLE -> OperationFailure.SCAN_SOURCE_UNREADABLE
            DocumentFailure.SOURCE_COPY_FAILED -> OperationFailure.SCAN_SAVE_FAILED
            DocumentFailure.DOCUMENT_SAVE_FAILED -> OperationFailure.SCAN_SAVE_FAILED
            DocumentFailure.DOCUMENT_SAVE_CLEANUP_FAILED -> OperationFailure.SCAN_SAVE_CLEANUP_FAILED
            DocumentFailure.DOCUMENT_NOT_FOUND -> OperationFailure.DOCUMENT_MISSING
            DocumentFailure.DOCUMENT_LOAD_FAILED -> OperationFailure.DOCUMENT_LOAD_FAILED
            DocumentFailure.DOCUMENT_RENAME_FAILED -> OperationFailure.DOCUMENT_RENAME_FAILED
            DocumentFailure.PDF_DELETE_FAILED -> OperationFailure.PDF_DELETE_FAILED
            DocumentFailure.DOCUMENT_DELETE_FAILED -> OperationFailure.DOCUMENT_DELETE_FAILED
        }

    private fun ScannedDocument.toUiModel(): DocumentUiModel =
        DocumentUiModel(
            id = id,
            title = title,
            dateLabel = DateFormat.getDateInstance(DateFormat.MEDIUM)
                .format(Date(createdAtEpochMillis)),
            pageCount = pageCount
        )

}
