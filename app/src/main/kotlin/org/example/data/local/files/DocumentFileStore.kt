package org.example.data.local.files

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.util.UUID

enum class DeleteFileResult {
    Deleted,
    Missing
}

class DocumentFileStore(
    private val contentResolver: ContentResolver,
    private val documentsDirectory: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    constructor(context: Context) : this(
        contentResolver = context.applicationContext.contentResolver,
        documentsDirectory = File(context.applicationContext.filesDir, DOCUMENTS_DIRECTORY)
    )

    suspend fun copyPdf(sourceUri: Uri): String = withContext(ioDispatcher) {
        val source = contentResolver.openInputStream(sourceUri)
            ?: throw FileNotFoundException("The selected PDF could not be opened.")
        copyPdfToManagedDirectory(source)
    }

    internal suspend fun copyPdf(source: InputStream): String =
        withContext(ioDispatcher) { copyPdfToManagedDirectory(source) }

    suspend fun deletePdf(relativePath: String): DeleteFileResult =
        withContext(ioDispatcher) {
            val file = resolveFile(relativePath)
            if (!file.exists()) {
                DeleteFileResult.Missing
            } else {
                if (!file.isFile || !file.delete()) {
                    throw IOException("The saved PDF could not be deleted.")
                }
                DeleteFileResult.Deleted
            }
        }

    suspend fun fileExists(relativePath: String): Boolean = withContext(ioDispatcher) {
        resolveFile(relativePath).isFile
    }

    suspend fun existingPdfFile(relativePath: String): File? = withContext(ioDispatcher) {
        resolveFile(relativePath).takeIf { it.isFile }
    }

    fun resolveFile(relativePath: String): File {
        require(relativePath.isNotBlank()) { "A relative PDF path is required." }
        require(!File(relativePath).isAbsolute) { "A relative PDF path is required." }

        val managedDirectory = documentsDirectory.canonicalFile
        val resolvedFile = File(managedDirectory, relativePath).canonicalFile
        val managedPrefix = managedDirectory.path + File.separator
        require(resolvedFile.path.startsWith(managedPrefix)) {
            "The PDF path must stay inside the app's documents directory."
        }
        return resolvedFile
    }

    private fun copyPdfToManagedDirectory(source: InputStream): String {
        val fileName = "${UUID.randomUUID()}.pdf"
        val temporaryFile = File(documentsDirectory, "$fileName.tmp")
        val finalFile = File(documentsDirectory, fileName)
        var promoted = false
        var temporaryFileCreated = false

        try {
            source.use { input ->
                ensureDocumentsDirectory()
                if (!temporaryFile.createNewFile()) {
                    throw IOException("A temporary PDF file could not be created.")
                }
                temporaryFileCreated = true
                temporaryFile.outputStream().buffered().use { output ->
                    input.copyTo(output)
                }
            }
            if (temporaryFile.length() == 0L) {
                throw IOException("The selected PDF is empty.")
            }
            if (finalFile.exists() || !temporaryFile.renameTo(finalFile)) {
                throw IOException("The selected PDF could not be saved.")
            }
            promoted = true
            return fileName
        } catch (cancellation: CancellationException) {
            if (temporaryFileCreated) cleanUpAfterFailure(temporaryFile, cancellation)
            if (promoted) cleanUpAfterFailure(finalFile, cancellation)
            throw cancellation
        } catch (failure: Exception) {
            if (temporaryFileCreated) cleanUpAfterFailure(temporaryFile, failure)
            if (promoted) cleanUpAfterFailure(finalFile, failure)
            throw failure
        }
    }

    private fun ensureDocumentsDirectory() {
        if (!documentsDirectory.isDirectory && !documentsDirectory.mkdirs()) {
            throw IOException("The app's documents directory could not be created.")
        }
    }

    private fun cleanUpAfterFailure(file: File, originalFailure: Throwable) {
        try {
            if (file.exists() && !file.delete()) {
                originalFailure.addSuppressed(
                    IOException("A temporary PDF file could not be removed.")
                )
            }
        } catch (cleanupFailure: Exception) {
            originalFailure.addSuppressed(cleanupFailure)
        }
    }

    private companion object {
        const val DOCUMENTS_DIRECTORY = "documents"
    }
}
