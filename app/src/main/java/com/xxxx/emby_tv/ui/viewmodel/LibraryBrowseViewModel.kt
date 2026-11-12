package com.xxxx.emby_tv.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xxxx.emby_tv.data.model.BaseItemDto
import com.xxxx.emby_tv.data.model.RecommendationDto
import com.xxxx.emby_tv.data.repository.EmbyRepository
import com.xxxx.emby_tv.util.ErrorHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LibraryBrowseViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = EmbyRepository.getInstance(application)

    var items by mutableStateOf<List<BaseItemDto>?>(null)
        private set
    var hasMore by mutableStateOf(true)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var isLoadingMore by mutableStateOf(false)
        private set

    var suggestions by mutableStateOf<List<RecommendationDto>?>(null)
        private set
    var genres by mutableStateOf<List<BaseItemDto>?>(null)
        private set
    var selectedGenre by mutableStateOf<String?>(null)
        private set
    var genreItems by mutableStateOf<List<BaseItemDto>?>(null)
        private set
    var genreHasMore by mutableStateOf(true)
        private set

    private var parentId = ""
    private var itemType = "Movie"

    fun load(parentId: String, type: String) {
        if (parentId == this.parentId && type == itemType && items != null) return
        this.parentId = parentId
        itemType = type.ifBlank { "Movie" }
        items = null
        suggestions = null
        genres = null
        selectedGenre = null
        genreItems = null
        hasMore = true
        fetchPage(reset = true)
    }

    fun loadMore() {
        if (selectedGenre != null) {
            if (isLoadingMore || !genreHasMore || genreItems == null) return
            fetchGenrePage(reset = false)
        } else {
            if (isLoadingMore || !hasMore || items == null) return
            fetchPage(reset = false)
        }
    }

    fun ensureSuggestions() {
        if (suggestions != null || parentId.isBlank()) return
        viewModelScope.launch {
            suggestions = try {
                withContext(Dispatchers.IO) { repository.getLibrarySuggestions(parentId, itemType) }
            } catch (e: Exception) {
                ErrorHandler.logError("LibraryBrowse", "建议加载失败", e)
                emptyList()
            }
        }
    }

    fun ensureGenres() {
        if (genres != null || parentId.isBlank()) return
        viewModelScope.launch {
            genres = try {
                withContext(Dispatchers.IO) { repository.getLibraryGenres(parentId, itemType) }
            } catch (e: Exception) {
                ErrorHandler.logError("LibraryBrowse", "类型加载失败", e)
                emptyList()
            }
        }
    }

    fun openGenre(name: String) {
        if (name.isBlank()) return
        selectedGenre = name
        genreItems = null
        genreHasMore = true
        fetchGenrePage(reset = true)
    }

    fun clearGenre() {
        selectedGenre = null
        genreItems = null
    }

    private fun fetchPage(reset: Boolean) {
        val start = if (reset) 0 else items.orEmpty().size
        viewModelScope.launch {
            if (reset) isLoading = true else isLoadingMore = true
            try {
                val (page, count) = withContext(Dispatchers.IO) {
                    repository.getLibraryList(
                        parentId,
                        itemType,
                        start,
                        PAGE_SIZE,
                        "DateCreated",
                        "Descending",
                    )
                }
                items = if (reset) page else items.orEmpty() + page
                hasMore = (items?.size ?: 0) < count && page.isNotEmpty()
            } catch (e: Exception) {
                ErrorHandler.logError("LibraryBrowse", "列表加载失败", e)
                if (reset) items = emptyList()
                hasMore = false
            } finally {
                isLoading = false
                isLoadingMore = false
            }
        }
    }

    private fun fetchGenrePage(reset: Boolean) {
        val name = selectedGenre ?: return
        val start = if (reset) 0 else genreItems.orEmpty().size
        viewModelScope.launch {
            if (reset) isLoading = true else isLoadingMore = true
            try {
                val (page, count) = withContext(Dispatchers.IO) {
                    repository.getLibraryList(
                        parentId,
                        itemType,
                        start,
                        PAGE_SIZE,
                        "DateCreated",
                        "Descending",
                        genre = name,
                    )
                }
                genreItems = if (reset) page else genreItems.orEmpty() + page
                genreHasMore = (genreItems?.size ?: 0) < count && page.isNotEmpty()
            } catch (e: Exception) {
                ErrorHandler.logError("LibraryBrowse", "类型列表加载失败", e)
                if (reset) genreItems = emptyList()
                genreHasMore = false
            } finally {
                isLoading = false
                isLoadingMore = false
            }
        }
    }

    companion object {
        const val PAGE_SIZE = 36
    }
}
