package org.example.data.local.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.example.data.model.ScannedDocument

@Dao
interface DocumentDao {
    @Query(
        "SELECT * FROM documents " +
            "ORDER BY createdAtEpochMillis DESC, id DESC"
    )
    fun observeDocuments(): Flow<List<ScannedDocument>>

    @Query("SELECT * FROM documents WHERE id = :documentId")
    fun observeDocument(documentId: Long): Flow<ScannedDocument?>

    @Query("SELECT * FROM documents WHERE id = :documentId")
    suspend fun getDocument(documentId: Long): ScannedDocument?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(document: ScannedDocument): Long

    @Query("UPDATE documents SET title = :title WHERE id = :documentId")
    suspend fun updateTitle(documentId: Long, title: String): Int

    @Query("DELETE FROM documents WHERE id = :documentId")
    suspend fun delete(documentId: Long): Int
}
