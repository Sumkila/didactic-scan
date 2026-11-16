package org.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "documents",
    indices = [Index(value = ["createdAtEpochMillis"])]
)
data class ScannedDocument(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    val createdAtEpochMillis: Long,
    val pageCount: Int,
    val pdfFilePath: String
)
