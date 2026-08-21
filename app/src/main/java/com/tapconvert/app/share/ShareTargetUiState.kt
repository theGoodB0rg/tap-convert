package com.tapconvert.app.share

import com.tapconvert.core.database.entity.ConversionRecordEntity
import com.tapconvert.core.model.ConversionError
import com.tapconvert.core.model.ConversionQuality
import com.tapconvert.core.model.ConversionResult
import com.tapconvert.core.model.ConversionStage
import com.tapconvert.core.model.MimeType
import com.tapconvert.core.model.Preset
import com.tapconvert.core.model.TargetSize

sealed interface ShareTargetUiState {
    data object Loading : ShareTargetUiState

    data class Ready(
        val payload: SharePayload,
        val suggestedPresets: List<Preset>,
        val selectedPreset: Preset? = null,
        val customQuality: ConversionQuality = ConversionQuality.High,
        val customTargetSize: TargetSize? = null,
        val selectedTargetMimeType: MimeType = MimeType.Image.JPEG
    ) : ShareTargetUiState

    data class Converting(
        val stage: ConversionStage = ConversionStage.PROCESSING,
        val percentage: Int = 0,
        val statusMessage: String = ""
    ) : ShareTargetUiState

    data class Success(
        val result: ConversionResult,
        val record: ConversionRecordEntity,
        val outputFilePaths: List<String>
    ) : ShareTargetUiState

    data class Error(
        val error: ConversionError
    ) : ShareTargetUiState
}
