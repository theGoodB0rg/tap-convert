package com.tapconvert.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tapconvert.core.database.cleaner.LruDiskCleaner
import com.tapconvert.core.database.entity.ConversionRecordEntity
import com.tapconvert.core.database.repository.ConversionHistoryRepository
import com.tapconvert.core.database.repository.InMemoryConversionHistoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(
    private val repository: ConversionHistoryRepository = InMemoryConversionHistoryRepository(),
    private val diskCleaner: LruDiskCleaner? = null
) : ViewModel() {

    private val _onlyFavoritesFilter = MutableStateFlow(false)
    val onlyFavoritesFilter: StateFlow<Boolean> = _onlyFavoritesFilter

    val records: StateFlow<List<ConversionRecordEntity>> = combine(
        repository.getAll(),
        _onlyFavoritesFilter
    ) { allRecords, onlyFavs ->
        if (onlyFavs) allRecords.filter { it.isFavorited } else allRecords
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val totalStorageUsageBytes: StateFlow<Long> = repository.getTotalStorageUsage()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    fun toggleFavoritesFilter() {
        _onlyFavoritesFilter.value = !_onlyFavoritesFilter.value
    }

    fun toggleFavorite(recordId: String, isFavorited: Boolean) {
        viewModelScope.launch {
            repository.setFavorited(recordId, isFavorited)
        }
    }

    fun deleteRecord(recordId: String) {
        viewModelScope.launch {
            repository.deleteById(recordId)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }

    fun triggerDiskCleanup() {
        viewModelScope.launch {
            diskCleaner?.performFullCleanup()
        }
    }
}
