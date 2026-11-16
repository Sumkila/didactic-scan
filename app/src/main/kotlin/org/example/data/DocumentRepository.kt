package org.example.data

import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.example.data.local.database.DocumentDao
import org.example.data.local.files.DocumentFileStore
import org.example.data.model.ScannedDocument
import java.io.FileNotFoundException

interface DocumentRepository {
    suspend fun getDocument(documentId: Long): ScannedDocument?

    fun observeDocuments(): Flow<List<ScannedDocument>>

    fun observeDocument(documentId: Long): Flow<ScannedDocument?>

    suspend fun importScannedPdf(
        sourceUri: Uri,
        title: String,
        pageCount: Int
    ): RepositoryResult<ScannedDocument>

    suspend fun renameDocument(documentId: Long, newTitle: String): RepositoryResult<Unit>

    suspend fun deleteDocument(documentId: Long): RepositoryResult<Unit>
}

enum class DocumentFailure {
    INVALID_TITLE,
    INVALID_PAGE_COUNT,
    SOURCE_UNREADABLE,
    SOURCE_COPY_FAILED,
    DOCUMENT_SAVE_FAILED,
    DOCUMENT_SAVE_CLEANUP_FAILED,
    DOCUMENT_NOT_FOUND,
    DOCUMENT_LOAD_FAILED,
    DOCUMENT_RENAME_FAILED,
    PDF_DELETE_FAILED,
    DOCUMENT_DELETE_FAILED
}

class RoomDocumentRepository(
    private val documentDao: DocumentDao,
    private val fileStore: DocumentFileStore
) : DocumentRepository {
    override suspend fun getDocument(documentId: Long): ScannedDocument? =
        documentDao.getDocument(documentId)

    override fun observeDocuments(): Flow<List<ScannedDocument>> =
        documentDao.observeDocuments()

    override fun observeDocument(documentId: Long): Flow<ScannedDocument?> =
        documentDao.observeDocument(documentId)

    override suspend fun importScannedPdf(
        sourceUri: Uri,
        title: String,
        pageCount: Int
    ): RepositoryResult<ScannedDocument> {
        val trimmedTitle = title.trim()
        if (trimmedTitle.isEmpty()) {
            return RepositoryResult.Failure(DocumentFailure.INVALID_TITLE)
        }
        if (pageCount <= 0) {
            return RepositoryResult.Failure(DocumentFailure.INVALID_PAGE_COUNT)
        }

        val pdfFilePath = try {
            fileStore.copyPdf(sourceUri)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            return RepositoryResult.Failure(
                reason = if (
                    failure is FileNotFoundException || failure is SecurityException
                ) {
                    DocumentFailure.SOURCE_UNREADABLE
                } else {
                    DocumentFailure.SOURCE_COPY_FAILED
                },
                cause = failure
            )
        }

        val document = ScannedDocument(
            title = trimmedTitle,
            createdAtEpochMillis = System.currentTimeMillis(),
            pageCount = pageCount,
            pdfFilePath = pdfFilePath
        )

        return try {
            val insertedId = documentDao.insert(document)
            RepositoryResult.Success(document.copy(id = insertedId))
        } catch (cancellation: CancellationException) {
            removeCopiedFileAfterCancellation(pdfFilePath, cancellation)
            throw cancellation
        } catch (failure: Exception) {
            val reason = try {
                fileStore.deletePdf(pdfFilePath)
                DocumentFailure.DOCUMENT_SAVE_FAILED
            } catch (cleanupFailure: Exception) {
                failure.addSuppressed(cleanupFailure)
                DocumentFailure.DOCUMENT_SAVE_CLEANUP_FAILED
            }
            RepositoryResult.Failure(reason, failure)
        }
    }

    override suspend fun renameDocument(
        documentId: Long,
        newTitle: String
    ): RepositoryResult<Unit> {
        val trimmedTitle = newTitle.trim()
        if (trimmedTitle.isEmpty()) {
            return RepositoryResult.Failure(DocumentFailure.INVALID_TITLE)
        }

        return try {
            if (documentDao.updateTitle(documentId, trimmedTitle) == 0) {
                RepositoryResult.Failure(DocumentFailure.DOCUMENT_NOT_FOUND)
            } else {
                RepositoryResult.Success(Unit)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            RepositoryResult.Failure(DocumentFailure.DOCUMENT_RENAME_FAILED, failure)
        }
    }

    override suspend fun deleteDocument(documentId: Long): RepositoryResult<Unit> {
        val document = try {
            documentDao.getDocument(documentId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            return RepositoryResult.Failure(DocumentFailure.DOCUMENT_LOAD_FAILED, failure)
        } ?: return RepositoryResult.Failure(DocumentFailure.DOCUMENT_NOT_FOUND)

        try {
            fileStore.deletePdf(document.pdfFilePath)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            return RepositoryResult.Failure(DocumentFailure.PDF_DELETE_FAILED, failure)
        }

        return try {
            if (documentDao.delete(documentId) == 0) {
                RepositoryResult.Failure(
                    DocumentFailure.DOCUMENT_DELETE_FAILED
                )
            } else {
                RepositoryResult.Success(Unit)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            RepositoryResult.Failure(DocumentFailure.DOCUMENT_DELETE_FAILED, failure)
        }
    }

    private suspend fun removeCopiedFileAfterCancellation(
        pdfFilePath: String,
        cancellation: CancellationException
    ) {
        try {
            withContext(NonCancellable) {
                fileStore.deletePdf(pdfFilePath)
            }
        } catch (cleanupFailure: Exception) {
            cancellation.addSuppressed(cleanupFailure)
        }
    }
}

sealed interface RepositoryResult<out T> {
    data class Success<T>(val value: T) : RepositoryResult<T>

    data class Failure(
        val reason: DocumentFailure,
        val cause: Throwable? = null
    ) : RepositoryResult<Nothing>
}
