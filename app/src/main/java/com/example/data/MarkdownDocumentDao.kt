package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MarkdownDocumentDao {

    @Query("SELECT * FROM markdown_documents ORDER BY title ASC")
    fun getAllDocuments(): Flow<List<MarkdownDocument>>

    @Query("SELECT * FROM markdown_documents WHERE lastOpened > 0 ORDER BY lastOpened DESC")
    fun getRecentDocuments(): Flow<List<MarkdownDocument>>

    @Query("SELECT * FROM markdown_documents WHERE isBookmarked = 1 ORDER BY title ASC")
    fun getBookmarkedDocuments(): Flow<List<MarkdownDocument>>

    @Query("SELECT * FROM markdown_documents WHERE uri = :uri LIMIT 1")
    suspend fun getDocumentByUri(uri: String): MarkdownDocument?

    @Query("SELECT * FROM markdown_documents WHERE uri = :uri LIMIT 1")
    fun observeDocumentByUri(uri: String): Flow<MarkdownDocument?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateDocument(document: MarkdownDocument)

    @Query("UPDATE markdown_documents SET isBookmarked = :isBookmarked WHERE uri = :uri")
    suspend fun updateBookmarkState(uri: String, isBookmarked: Boolean)

    @Query("UPDATE markdown_documents SET scrollPosition = :position, scrollPercentage = :percentage WHERE uri = :uri")
    suspend fun updateReadingProgress(uri: String, position: Int, percentage: Float)

    @Query("DELETE FROM markdown_documents WHERE uri = :uri")
    suspend fun deleteDocument(uri: String)

    @Query("DELETE FROM markdown_documents WHERE isSample = 1")
    suspend fun deleteSamples()
}
