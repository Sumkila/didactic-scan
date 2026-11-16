package org.example.ui.scan

import android.net.Uri
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.example.data.DocumentRepository
import org.example.data.DocumentFailure
import org.example.data.RepositoryResult
import org.example.data.model.ScannedDocument
import org.example.ui.home.DocumentAction
import org.example.ui.home.OperationFailure
import org.example.ui.sample.SampleDocuments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ScanViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun repositoryDocumentsAppearInUiState() = runTest {
        val repository = FakeDocumentRepository()
        repository.documents.value = listOf(testDocument())
        val viewModel = ScanViewModel(repository)

        advanceUntilIdle()

        assertEquals("Saved title", viewModel.uiState.value.documents.single().title)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun renameDelegatesToRepository() = runTest {
        val repository = FakeDocumentRepository()
        val viewModel = ScanViewModel(repository)
        advanceUntilIdle()

        viewModel.renameDocument(42L, "Updated title")
        advanceUntilIdle()

        assertEquals(42L, repository.renamedId)
        assertEquals("Updated title", repository.renamedTitle)
        assertEquals(DocumentAction.Rename, viewModel.uiState.value.operationFeedback?.action)
        assertTrue(viewModel.uiState.value.operationFeedback?.succeeded == true)
    }

    @Test
    fun deleteDelegatesToRepository() = runTest {
        val repository = FakeDocumentRepository()
        val viewModel = ScanViewModel(repository)
        advanceUntilIdle()

        viewModel.deleteDocument(42L)
        advanceUntilIdle()

        assertEquals(42L, repository.deletedId)
        assertEquals(DocumentAction.Delete, viewModel.uiState.value.operationFeedback?.action)
    }

    @Test
    fun repositoryFailureAppearsInUiState() = runTest {
        val repository = FakeDocumentRepository().apply {
            renameResult = RepositoryResult.Failure(DocumentFailure.DOCUMENT_RENAME_FAILED)
        }
        val viewModel = ScanViewModel(repository)
        advanceUntilIdle()

        viewModel.renameDocument(42L, "Updated title")
        advanceUntilIdle()

        assertEquals(
            OperationFailure.DOCUMENT_RENAME_FAILED,
            viewModel.uiState.value.operationFeedback?.failure
        )
        assertFalse(viewModel.uiState.value.operationFeedback?.succeeded ?: true)
    }

    @Test
    fun operationProgressPreventsDuplicateActionsAndResets() = runTest {
        val repository = FakeDocumentRepository().apply {
            operationGate = CompletableDeferred()
        }
        val viewModel = ScanViewModel(repository)
        advanceUntilIdle()

        viewModel.renameDocument(42L, "Updated title")
        runCurrent()
        viewModel.renameDocument(42L, "Duplicate")
        runCurrent()

        assertTrue(viewModel.uiState.value.isOperationInProgress)
        assertEquals(1, repository.renameCalls)

        repository.operationGate?.complete(Unit)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isOperationInProgress)
    }

    @Test
    fun retryRestartsFailedDocumentObservation() = runTest {
        val repository = FakeDocumentRepository().apply {
            failNextObservation = true
        }
        val viewModel = ScanViewModel(repository)
        runCurrent()

        assertTrue(viewModel.uiState.value.hasLoadError)

        viewModel.retryObservingDocuments()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.hasLoadError)
        assertNull(viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun scanProgressRejectsDuplicateLaunchAndResetsAfterCancellation() = runTest {
        val viewModel = ScanViewModel(FakeDocumentRepository())
        advanceUntilIdle()

        assertTrue(viewModel.beginScan())
        assertFalse(viewModel.beginScan())
        assertTrue(viewModel.uiState.value.isScanning)

        viewModel.onScannerCancelled()

        assertFalse(viewModel.uiState.value.isScanning)
        assertTrue(viewModel.beginScan())
    }

    @Test
    fun scannerFailureResetsProgressAndAppearsInUiState() = runTest {
        val viewModel = ScanViewModel(FakeDocumentRepository())
        advanceUntilIdle()
        viewModel.beginScan()

        viewModel.onScannerLaunchFailed(OperationFailure.SCANNER_UNAVAILABLE)

        assertFalse(viewModel.uiState.value.isScanning)
        assertEquals(
            OperationFailure.SCANNER_UNAVAILABLE,
            viewModel.uiState.value.operationFeedback?.failure
        )
    }

    @Test
    fun sampleDocumentsAreNotSeededIntoTheSavedLibrary() = runTest {
        val viewModel = ScanViewModel(FakeDocumentRepository())

        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.documents.isEmpty())
        assertTrue(SampleDocuments.all.isNotEmpty())
    }

    private fun testDocument() = ScannedDocument(
        id = 42L,
        title = "Saved title",
        createdAtEpochMillis = 1_700_000_000_000L,
        pageCount = 2,
        pdfFilePath = "test.pdf"
    )
}

private class FakeDocumentRepository : DocumentRepository {
    val documents = MutableStateFlow(emptyList<ScannedDocument>())
    var failNextObservation = false
    var renameCalls = 0
    var renamedId: Long? = null
    var renamedTitle: String? = null
    var deletedId: Long? = null
    var renameResult: RepositoryResult<Unit> = RepositoryResult.Success(Unit)
    var operationGate: CompletableDeferred<Unit>? = null

    override suspend fun getDocument(documentId: Long): ScannedDocument? =
        documents.value.firstOrNull { it.id == documentId }

    override fun observeDocuments(): Flow<List<ScannedDocument>> {
        if (failNextObservation) {
            failNextObservation = false
            return flow { throw IOException("Observation failed.") }
        }
        return documents
    }

    override fun observeDocument(documentId: Long): Flow<ScannedDocument?> =
        flow { emit(documents.value.firstOrNull { it.id == documentId }) }

    override suspend fun importScannedPdf(
        sourceUri: Uri,
        title: String,
        pageCount: Int
    ): RepositoryResult<ScannedDocument> =
        RepositoryResult.Failure(DocumentFailure.SOURCE_COPY_FAILED)

    override suspend fun renameDocument(
        documentId: Long,
        newTitle: String
    ): RepositoryResult<Unit> {
        renameCalls += 1
        renamedId = documentId
        renamedTitle = newTitle
        operationGate?.await()
        return renameResult
    }

    override suspend fun deleteDocument(documentId: Long): RepositoryResult<Unit> {
        deletedId = documentId
        return RepositoryResult.Success(Unit)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule : TestWatcher() {
    private val dispatcher = StandardTestDispatcher()

    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
