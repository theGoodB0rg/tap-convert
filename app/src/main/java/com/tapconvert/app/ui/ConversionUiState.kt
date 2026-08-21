package com.tapconvert.app.ui

import com.tapconvert.core.database.entity.ConversionRecordEntity
import com.tapconvert.core.model.ConversionError
import com.tapconvert.core.model.ConversionRequest
import com.tapconvert.core.model.ConversionResult
import com.tapconvert.core.model.ConversionStage

sealed interface ConversionUiState {
    data object Idle : ConversionUiState

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
