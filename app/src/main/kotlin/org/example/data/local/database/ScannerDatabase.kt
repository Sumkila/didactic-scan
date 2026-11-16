package org.example.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import org.example.data.model.ScannedDocument

@Database(
    entities = [ScannedDocument::class],
    version = 1,
    exportSchema = true
)
abstract class ScannerDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
}
