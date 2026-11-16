package org.example

import android.content.Context
import androidx.room.Room
import org.example.data.RoomDocumentRepository
import org.example.data.local.database.ScannerDatabase
import org.example.data.local.files.DocumentFileStore

class AppContainer(context: Context) {
    private val applicationContext = context.applicationContext

    val database: ScannerDatabase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        Room.databaseBuilder(
            applicationContext,
            ScannerDatabase::class.java,
            DATABASE_NAME
        ).build()
    }

    val documentFileStore: DocumentFileStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        DocumentFileStore(applicationContext)
    }

    val documentRepository: RoomDocumentRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        RoomDocumentRepository(database.documentDao(), documentFileStore)
    }

    private companion object {
        const val DATABASE_NAME = "scanner_database"
    }
}
