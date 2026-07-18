package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "markdown_documents")
data class MarkdownDocument(
    @PrimaryKey val uri: String, // Unique identifier (File path or SAF content URI)
    val title: String,
    val filePath: String,
    val lastOpened: Long = 0L, // Timestamp, 0 if never opened
    val isBookmarked: Boolean = false,
    val scrollPosition: Int = 0, // Saved scroll index or pixel offset
    val scrollPercentage: Float = 0f, // From 0.0 to 1.0 representing reading progress
    val fileSize: Long = 0L, // File size in bytes
    val wordCount: Int = 0,
    val isSample: Boolean = false
)
