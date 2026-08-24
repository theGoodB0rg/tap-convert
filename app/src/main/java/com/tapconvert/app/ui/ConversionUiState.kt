package com.tapconvert.app.ui

import com.tapconvert.core.database.entity.ConversionRecordEntity
import com.tapconvert.core.model.ConversionError
import com.tapconvert.core.model.ConversionRequest
import com.tapconvert.core.model.ConversionResult
import com.tapconvert.core.model.ConversionStage

import com.tapconvert.core.model.MediaCategory

sealed interface ConversionUiState {
    data object Idle : ConversionUiState

    data class Staging(
        val message: String = "Getting your file ready...",
        val fileCount: Int = 1,
        val category: MediaCategory? = null
    ) : ConversionUiState

    data class Configuring(
        val request: ConversionRequest,
        val sourceFileNames: List<String> = emptyList(),
        val totalInputSizeBytes: Long = 0L
    ) : ConversionUiState

    data class Processing(
        val stage: ConversionStage = ConversionStage.PREPARING,
        val percentage: Int = 0,
        val statusMessage: String = ""
    ) : ConversionUiState

    data class Success(
        val result: ConversionResult,
        val record: ConversionRecordEntity
    ) : ConversionUiState

    data class Error(
        val error: ConversionError
    ) : ConversionUiState
}
