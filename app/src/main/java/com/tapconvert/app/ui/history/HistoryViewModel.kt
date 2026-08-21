package com.tapconvert.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tapconvert.core.database.cleaner.LruDiskCleaner
import com.tapconvert.core.database.entity.ConversionRecordEntity
import com.tapconvert.core.database.repository.ConversionHistoryRepository
import com.tapconvert.core.database.repository.InMemoryConversionHistoryRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoryViewModel(
    private val repository: ConversionHistoryRepository = InMemoryConversionHistoryRepository(),
    private val diskCleaner: LruDiskCleaner? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main
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
        viewModelScope.launch(mainDispatcher) {
            repository.setFavorited(recordId, isFavorited)
        }
    }

    fun deleteRecord(recordId: String) {
        viewModelScope.launch(ioDispatcher) {
            if (diskCleaner != null) {
                diskCleaner.deleteRecordWithFiles(recordId)
            } else {
                repository.deleteById(recordId)
            }
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch(ioDispatcher) {
            if (diskCleaner != null) {
                diskCleaner.performManualCachePurge(protectFavorites = false)
            } else {
                repository.clearAll()
            }
        }
    }

    fun triggerDiskCleanup(onComplete: ((LruDiskCleaner.CleanupReport) -> Unit)? = null) {
        viewModelScope.launch(ioDispatcher) {
            val report = diskCleaner?.performManualCachePurge(protectFavorites = true)
                ?: LruDiskCleaner.CleanupReport(0, 0L)
            withContext(mainDispatcher) {
                onComplete?.invoke(report)
            }
        }
    }
}
