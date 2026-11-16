package org.example.data

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.example.data.local.database.DocumentDao
import org.example.data.local.database.ScannerDatabase
import org.example.data.local.files.DeleteFileResult
import org.example.data.local.files.DocumentFileStore
import org.example.data.model.ScannedDocument
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ScannerDataLayerTest {
    private lateinit var context: Context
    private lateinit var database: ScannerDatabase
    private lateinit var dao: DocumentDao
    private lateinit var documentsDirectory: File
    private lateinit var fileStore: DocumentFileStore
    private lateinit var repository: RoomDocumentRepository
    private lateinit var sourceDirectory: File
    private lateinit var sourcePdf: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, ScannerDatabase::class.java).build()
        dao = database.documentDao()
        val testDirectory = File(context.cacheDir, "data-layer-${UUID.randomUUID()}")
        documentsDirectory = File(testDirectory, "managed")
        sourceDirectory = File(testDirectory, "sources").apply { mkdirs() }
        sourcePdf = File(sourceDirectory, "test-fixture.pdf").apply {
            writeBytes(createMinimalPdf())
        }
        fileStore = DocumentFileStore(
            contentResolver = context.contentResolver,
            documentsDirectory = documentsDirectory
        )
        repository = RoomDocumentRepository(dao, fileStore)
    }

    @After
    fun tearDown() {
        database.close()
        sourceDirectory.parentFile?.deleteRecursively()
    }

    @Test
    fun roomInsertAndRetrieve() = runBlocking {
        val input = document(title = "Inserted")
        val insertedId = dao.insert(input)

        assertEquals(input.copy(id = insertedId), dao.getDocument(insertedId))
    }

    @Test
    fun roomObservesNewestFirstAndOrdersEqualTimestampsById() = runBlocking {
        dao.insert(document(title = "First", createdAt = 100L))
        val secondId = dao.insert(document(title = "Second", createdAt = 100L))
        dao.insert(document(title = "Older", createdAt = 99L))

        assertEquals(
            listOf("Second", "First", "Older"),
            dao.observeDocuments().first().map { it.title }
        )
        assertEquals(secondId, dao.observeDocuments().first().first().id)
    }

    @Test
    fun roomTitleUpdatePreservesOtherFields() = runBlocking {
        val insertedId = dao.insert(document(title = "Before"))
        val before = dao.getDocument(insertedId)!!

        assertEquals(1, dao.updateTitle(insertedId, "After"))

        assertEquals(before.copy(title = "After"), dao.getDocument(insertedId))
    }

    @Test
    fun roomDeleteAndUnknownIdsAreSafe() = runBlocking {
        val insertedId = dao.insert(document(title = "Delete me"))

        assertEquals(1, dao.delete(insertedId))
        assertEquals(0, dao.delete(-1L))
        assertEquals(0, dao.updateTitle(-1L, "Missing"))
        assertEquals(null, dao.getDocument(insertedId))
        assertEquals(null, dao.observeDocument(-1L).first())
    }

    @Test
    fun fileStoreCopiesReadablePdfToManagedDirectory() = runBlocking {
        val storedPath = fileStore.copyPdf(Uri.fromFile(sourcePdf))
        val storedFile = fileStore.resolveFile(storedPath)

        assertTrue(storedPath.endsWith(".pdf"))
        assertTrue(storedFile.isFile)
        assertArrayEquals(sourcePdf.readBytes(), storedFile.readBytes())
    }

    @Test
    fun fileStoreRejectsMissingAndEmptySourcesWithoutLeavingFiles() = runBlocking {
        val missing = File(sourceDirectory, "missing.pdf")
        val missingResult = runCatching { fileStore.copyPdf(Uri.fromFile(missing)) }
        val empty = File(sourceDirectory, "empty.pdf").apply { writeBytes(byteArrayOf()) }
        val emptyResult = runCatching { fileStore.copyPdf(Uri.fromFile(empty)) }

        assertTrue(missingResult.isFailure)
        assertTrue(emptyResult.isFailure)
        assertTrue(documentsDirectory.listFiles().isNullOrEmpty())
    }

    @Test
    fun fileStoreRemovesPartialCopyAfterStreamFailure() = runBlocking {
        val brokenInput = object : InputStream() {
            private var hasReturnedByte = false

            override fun read(): Int {
                if (!hasReturnedByte) {
                    hasReturnedByte = true
                    return 'x'.code
                }
                throw IOException("Test stream failure.")
            }
        }

        val result = runCatching { fileStore.copyPdf(brokenInput) }

        assertTrue(result.isFailure)
        assertTrue(documentsDirectory.listFiles().isNullOrEmpty())
    }

    @Test
    fun fileStoreDeleteHandlesExistingMissingAndOutOfDirectoryPaths() = runBlocking {
        val storedPath = fileStore.copyPdf(Uri.fromFile(sourcePdf))

        assertEquals(DeleteFileResult.Deleted, fileStore.deletePdf(storedPath))
        assertEquals(DeleteFileResult.Missing, fileStore.deletePdf(storedPath))
        assertTrue(runCatching { fileStore.resolveFile("../outside.pdf") }.isFailure)
        assertTrue(runCatching { fileStore.resolveFile(sourcePdf.absolutePath) }.isFailure)
    }

    @Test
    fun repositoryImportCreatesMetadataAndManagedPdf() = runBlocking {
        val result = repository.importScannedPdf(
            Uri.fromFile(sourcePdf),
            "  Imported notes  ",
            2
        )
        val saved = assertSuccess(result)

        assertEquals("Imported notes", saved.title)
        assertEquals(2, saved.pageCount)
        assertTrue(saved.createdAtEpochMillis > 0L)
        assertTrue(fileStore.fileExists(saved.pdfFilePath))
        assertEquals(saved, dao.getDocument(saved.id))
    }

    @Test
    fun repositoryRejectsInvalidMetadataAndCopyFailureWithoutMetadata() = runBlocking {
        val blankTitle = repository.importScannedPdf(Uri.fromFile(sourcePdf), "  ", 1)
        val invalidPages = repository.importScannedPdf(Uri.fromFile(sourcePdf), "Valid", 0)
        val emptyPdf = File(sourceDirectory, "empty-repository.pdf").apply {
            writeBytes(byteArrayOf())
        }
        val copyFailure = repository.importScannedPdf(Uri.fromFile(emptyPdf), "Valid", 1)

        assertTrue(blankTitle is RepositoryResult.Failure)
        assertTrue(invalidPages is RepositoryResult.Failure)
        assertTrue(copyFailure is RepositoryResult.Failure)
        assertTrue(dao.observeDocuments().first().isEmpty())
        assertTrue(documentsDirectory.listFiles().isNullOrEmpty())
    }

    @Test
    fun repositoryCleansCopiedFileWhenMetadataInsertFails() = runBlocking {
        val failingRepository = RoomDocumentRepository(
            FailingInsertDao(dao, IOException("Insert failed.")),
            fileStore
        )

        val result = failingRepository.importScannedPdf(Uri.fromFile(sourcePdf), "Import", 1)

        assertTrue(result is RepositoryResult.Failure)
        assertTrue(dao.observeDocuments().first().isEmpty())
        assertTrue(documentsDirectory.listFiles().isNullOrEmpty())
    }

    @Test
    fun repositoryPropagatesCancellationAndCleansCopiedFile() = runBlocking {
        val cancellingRepository = RoomDocumentRepository(
            FailingInsertDao(dao, CancellationException("Test cancellation.")),
            fileStore
        )

        val result = runCatching {
            cancellingRepository.importScannedPdf(Uri.fromFile(sourcePdf), "Import", 1)
        }

        assertTrue(result.exceptionOrNull() is CancellationException)
        assertTrue(documentsDirectory.listFiles().isNullOrEmpty())
    }

    @Test
    fun repositoryRenamePreservesIdTimestampPageCountAndFilePath() = runBlocking {
        val path = fileStore.copyPdf(Uri.fromFile(sourcePdf))
        val insertedId = dao.insert(document(title = "Before", pdfFilePath = path))
        val before = dao.getDocument(insertedId)!!

        assertTrue(repository.renameDocument(insertedId, "  After  ") is RepositoryResult.Success)

        assertEquals(before.copy(title = "After"), dao.getDocument(insertedId))
        assertTrue(fileStore.fileExists(path))
    }

    @Test
    fun repositoryDeleteRemovesFileAndMetadataAndHandlesMissingFile() = runBlocking {
        val path = fileStore.copyPdf(Uri.fromFile(sourcePdf))
        val insertedId = dao.insert(document(title = "Delete", pdfFilePath = path))

        assertTrue(repository.deleteDocument(insertedId) is RepositoryResult.Success)
        assertFalse(fileStore.fileExists(path))
        assertEquals(null, dao.getDocument(insertedId))

        val missingPdfDocumentId = dao.insert(document(title = "Missing PDF"))
        assertTrue(repository.deleteDocument(missingPdfDocumentId) is RepositoryResult.Success)
        assertEquals(null, dao.getDocument(missingPdfDocumentId))
    }

    @Test
    fun repositoryPreservesMetadataIfFileDeletionFails() = runBlocking {
        val path = "not-a-file.pdf"
        val directoryAtPdfPath = File(documentsDirectory, path).apply {
            mkdirs()
            File(this, "keep.txt").writeText("prevent directory deletion")
        }
        val insertedId = dao.insert(document(title = "Keep metadata", pdfFilePath = path))

        val result = repository.deleteDocument(insertedId)

        assertTrue(result is RepositoryResult.Failure)
        assertNotNull(dao.getDocument(insertedId))
        assertTrue(directoryAtPdfPath.isDirectory)
    }

    @Test
    fun persistenceCheckImportsRenamesSurvivesDatabaseReopenAndDeletes() = runBlocking {
        val databaseName = "persistence-test-${UUID.randomUUID()}.db"
        database.close()
        val persistentDatabase = Room.databaseBuilder(
            context,
            ScannerDatabase::class.java,
            databaseName
        ).build()
        val persistentDao = persistentDatabase.documentDao()
        val persistentRepository = RoomDocumentRepository(persistentDao, fileStore)

        try {
            val saved = assertSuccess(
                persistentRepository.importScannedPdf(
                    Uri.fromFile(sourcePdf),
                    "Persistence fixture",
                    1
                )
            )
            assertTrue(fileStore.fileExists(saved.pdfFilePath))
            persistentDatabase.close()

            val reopenedDatabase = Room.databaseBuilder(
                context,
                ScannerDatabase::class.java,
                databaseName
            ).build()
            try {
                val reopenedDao = reopenedDatabase.documentDao()
                val reopenedRepository = RoomDocumentRepository(reopenedDao, fileStore)
                assertEquals(saved, reopenedDao.getDocument(saved.id))
                assertTrue(
                    reopenedRepository.renameDocument(saved.id, "Renamed fixture")
                        is RepositoryResult.Success
                )
                val renamed = reopenedDao.getDocument(saved.id)!!
                assertEquals(saved.copy(title = "Renamed fixture"), renamed)
                assertTrue(reopenedRepository.deleteDocument(saved.id) is RepositoryResult.Success)
                assertEquals(null, reopenedDao.getDocument(saved.id))
                assertFalse(fileStore.fileExists(saved.pdfFilePath))
            } finally {
                reopenedDatabase.close()
            }
        } finally {
            context.deleteDatabase(databaseName)
        }
    }

    private fun document(
        id: Long = 0L,
        title: String,
        createdAt: Long = 100L,
        pageCount: Int = 1,
        pdfFilePath: String = "fixture-${UUID.randomUUID()}.pdf"
    ) = ScannedDocument(
        id = id,
        title = title,
        createdAtEpochMillis = createdAt,
        pageCount = pageCount,
        pdfFilePath = pdfFilePath
    )

    private fun <T> assertSuccess(result: RepositoryResult<T>): T {
        assertTrue("Expected success, got $result", result is RepositoryResult.Success)
        return (result as RepositoryResult.Success<T>).value
    }

    private fun createMinimalPdf(): ByteArray {
        val output = ByteArrayOutputStream()
        fun write(value: String) = output.write(value.toByteArray(StandardCharsets.US_ASCII))

        write("%PDF-1.4\n")
        val objectOffsets = mutableListOf<Int>()
        val objects = listOf(
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << >> >>"
        )
        objects.forEachIndexed { index, body ->
            objectOffsets += output.size()
            write("${index + 1} 0 obj\n$body\nendobj\n")
        }

        val xrefOffset = output.size()
        write("xref\n0 ${objects.size + 1}\n")
        write("0000000000 65535 f \n")
        objectOffsets.forEach { offset -> write("%010d 00000 n \n".format(offset)) }
        write("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\n")
        write("startxref\n$xrefOffset\n%%EOF\n")
        return output.toByteArray()
    }

    private class FailingInsertDao(
        private val delegate: DocumentDao,
        private val failure: Exception
    ) : DocumentDao {
        override fun observeDocuments(): Flow<List<ScannedDocument>> =
            delegate.observeDocuments()

        override fun observeDocument(documentId: Long): Flow<ScannedDocument?> =
            delegate.observeDocument(documentId)

        override suspend fun getDocument(documentId: Long): ScannedDocument? =
            delegate.getDocument(documentId)

        override suspend fun insert(document: ScannedDocument): Long {
            throw failure
        }

        override suspend fun updateTitle(documentId: Long, title: String): Int =
            delegate.updateTitle(documentId, title)

        override suspend fun delete(documentId: Long): Int =
            delegate.delete(documentId)
    }
}
