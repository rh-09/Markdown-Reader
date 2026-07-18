package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface AppScreen {
    object Library : AppScreen
    data class Reader(val uri: String) : AppScreen
}

class MarkdownViewModel(
    application: Application,
    private val repository: MarkdownRepository
) : AndroidViewModel(application) {

    // Navigation State
    private val _currentScreen = MutableStateFlow<AppScreen>(AppScreen.Library)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    // Library Lists (Observed from Room)
    val recentDocuments: StateFlow<List<MarkdownDocument>> = repository.recentDocuments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bookmarkedDocuments: StateFlow<List<MarkdownDocument>> = repository.bookmarkedDocuments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active Reader State
    private val _activeDocument = MutableStateFlow<MarkdownDocument?>(null)
    val activeDocument: StateFlow<MarkdownDocument?> = _activeDocument.asStateFlow()

    private val _activeContent = MutableStateFlow("")
    val activeContent: StateFlow<String> = _activeContent.asStateFlow()

    private val _parsedBlocks = MutableStateFlow<List<MarkdownBlock>>(emptyList())
    val parsedBlocks: StateFlow<List<MarkdownBlock>> = _parsedBlocks.asStateFlow()

    private val _isLoadingContent = MutableStateFlow(false)
    val isLoadingContent: StateFlow<Boolean> = _isLoadingContent.asStateFlow()

    private val _errorLoading = MutableStateFlow<String?>(null)
    val errorLoading: StateFlow<String?> = _errorLoading.asStateFlow()

    // Search Inside Document
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Reader Preferences
    private val _fontSize = MutableStateFlow(16f) // in sp
    val fontSize: StateFlow<Float> = _fontSize.asStateFlow()

    private val _fontFamilyType = MutableStateFlow("Serif") // "Sans-Serif", "Serif", "Monospace"
    val fontFamilyType: StateFlow<String> = _fontFamilyType.asStateFlow()

    private val _readerTheme = MutableStateFlow("warm_sepia") // "nord_light", "deep_slate", "warm_sepia", "amoled_black"
    val readerTheme: StateFlow<String> = _readerTheme.asStateFlow()

    init {
        // Automatically pre-load samples if first launch
        viewModelScope.launch {
            repository.initializeSamplesIfNeeded()
        }
    }

    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
        if (screen is AppScreen.Library) {
            // Reset search query and clean up reader state when returning to library
            _searchQuery.value = ""
            _activeDocument.value = null
            _activeContent.value = ""
            _parsedBlocks.value = emptyList()
        } else if (screen is AppScreen.Reader) {
            loadDocument(screen.uri)
        }
    }

    private fun loadDocument(uri: String) {
        viewModelScope.launch {
            _isLoadingContent.value = true
            _errorLoading.value = null
            try {
                // 1. Fetch metadata from DB if it exists, or resolve from file system
                var doc = repository.getDocumentByUri(uri)
                if (doc == null) {
                    val details = repository.resolveDocumentDetails(uri)
                    doc = MarkdownDocument(
                        uri = uri,
                        title = details.name,
                        filePath = if (uri.startsWith("content://")) "Shared System File" else uri,
                        lastOpened = System.currentTimeMillis(),
                        fileSize = details.size,
                        wordCount = 0
                    )
                } else {
                    // Update last opened timestamp
                    doc = doc.copy(lastOpened = System.currentTimeMillis())
                }

                _activeDocument.value = doc

                // 2. Read full content
                val content = repository.readDocumentContent(uri)
                _activeContent.value = content

                // 3. Compute stats
                val words = MarkdownRepository.countWords(content)
                doc = doc.copy(wordCount = words)
                _activeDocument.value = doc

                // Save or update document metadata in DB
                repository.insertOrUpdateDocument(doc)

                // 4. Parse content blocks
                val blocks = MarkdownParser.parse(content)
                _parsedBlocks.value = blocks

            } catch (e: Exception) {
                _errorLoading.value = e.localizedMessage ?: "Failed to read file."
            } finally {
                _isLoadingContent.value = false
            }
        }
    }

    fun toggleBookmark(document: MarkdownDocument) {
        viewModelScope.launch {
            val updatedState = !document.isBookmarked
            repository.updateBookmarkState(document.uri, updatedState)
            if (_activeDocument.value?.uri == document.uri) {
                _activeDocument.value = _activeDocument.value?.copy(isBookmarked = updatedState)
            }
        }
    }

    fun updateReadingProgress(uri: String, position: Int, percentage: Float) {
        viewModelScope.launch {
            repository.updateReadingProgress(uri, position, percentage)
            if (_activeDocument.value?.uri == uri) {
                _activeDocument.value = _activeDocument.value?.copy(
                    scrollPosition = position,
                    scrollPercentage = percentage
                )
            }
        }
    }

    fun deleteDocument(uri: String) {
        viewModelScope.launch {
            repository.deleteDocument(uri)
            if (_currentScreen.value == AppScreen.Reader(uri)) {
                navigateTo(AppScreen.Library)
            }
        }
    }

    // Adjust preferences
    fun setFontSize(size: Float) {
        _fontSize.value = size.coerceIn(12f, 32f)
    }

    fun setFontFamilyType(type: String) {
        _fontFamilyType.value = type
    }

    fun setReaderTheme(theme: String) {
        _readerTheme.value = theme
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // Factory for simple VM instantiation
    class Factory(
        private val application: Application,
        private val repository: MarkdownRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MarkdownViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return MarkdownViewModel(application, repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
