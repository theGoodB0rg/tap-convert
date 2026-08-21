package com.tapconvert.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tapconvert.core.ads.AdManager
import com.tapconvert.core.ads.AdReward
import com.tapconvert.core.ads.DefaultAdManager
import com.tapconvert.core.analytics.AnalyticsTracker
import com.tapconvert.core.analytics.NoOpAnalyticsTracker
import com.tapconvert.core.common.AppResult
import com.tapconvert.core.database.entity.ConversionRecordEntity
import com.tapconvert.core.database.repository.ConversionHistoryRepository
import com.tapconvert.core.database.repository.InMemoryConversionHistoryRepository
import com.tapconvert.core.model.ConversionError
import com.tapconvert.core.model.ConversionQuality
import com.tapconvert.core.model.ConversionRequest
import com.tapconvert.core.model.ConversionStage
import com.tapconvert.core.model.ConversionType
import com.tapconvert.core.model.DimensionConstraint
import com.tapconvert.core.model.MimeType
import com.tapconvert.core.model.Preset
import com.tapconvert.core.model.TargetSize
import com.tapconvert.feature.image.engine.DefaultImageEngine
import com.tapconvert.feature.image.engine.ImageEngine
import com.tapconvert.feature.media.engine.DefaultMediaEngine
import com.tapconvert.feature.media.engine.MediaEngine
import com.tapconvert.feature.pdf.engine.DefaultPdfEngine
import com.tapconvert.feature.pdf.engine.PdfEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel(
    private val imageEngine: ImageEngine = DefaultImageEngine(),
    private val pdfEngine: PdfEngine = DefaultPdfEngine(),
    private val mediaEngine: MediaEngine = DefaultMediaEngine(),
    private val historyRepository: ConversionHistoryRepository = InMemoryConversionHistoryRepository(),
    val adManager: AdManager = DefaultAdManager(),
    private val analyticsTracker: AnalyticsTracker = NoOpAnalyticsTracker()
) : ViewModel() {

    private val _uiState = MutableStateFlow<ConversionUiState>(ConversionUiState.Idle)
    val uiState: StateFlow<ConversionUiState> = _uiState.asStateFlow()

    private val _shouldShowInterstitial = MutableStateFlow(false)
    val shouldShowInterstitial: StateFlow<Boolean> = _shouldShowInterstitial.asStateFlow()

    private var activeJob: Job? = null

    fun selectPreset(preset: Preset, sourceUris: List<String>) {
        val request = ConversionRequest(
            sourceUris = sourceUris,
            conversionType = preset.conversionType,
            targetMimeType = preset.targetMimeType,
            preset = preset,
            targetSize = preset.targetSize,
            dimensionConstraint = preset.dimensionConstraint,
            quality = preset.quality
        )
        val fileNames = sourceUris.map { File(it.removePrefix("file://")).name }
        _uiState.value = ConversionUiState.Configuring(request, fileNames)
        analyticsTracker.logPresetSelected(preset.id, preset.category)
    }

    fun configureCustom(
        sourceUris: List<String>,
        conversionType: ConversionType,
        targetMimeType: MimeType
    ) {
        val request = ConversionRequest(
            sourceUris = sourceUris,
            conversionType = conversionType,
            targetMimeType = targetMimeType
        )
        val fileNames = sourceUris.map { File(it.removePrefix("file://")).name }
        _uiState.value = ConversionUiState.Configuring(request, fileNames)
    }

    fun updateQuality(quality: ConversionQuality) {
        val current = _uiState.value
        if (current is ConversionUiState.Configuring) {
            _uiState.value = current.copy(request = current.request.copy(quality = quality))
        }
    }

    fun updateTargetSize(targetSize: TargetSize?) {
        val current = _uiState.value
        if (current is ConversionUiState.Configuring) {
            _uiState.value = current.copy(request = current.request.copy(targetSize = targetSize))
        }
    }

    fun updateDimensionConstraint(constraint: DimensionConstraint) {
        val current = _uiState.value
        if (current is ConversionUiState.Configuring) {
            _uiState.value = current.copy(request = current.request.copy(dimensionConstraint = constraint))
        }
    }

    fun startConversion(outputDirectory: File) {
        val current = _uiState.value as? ConversionUiState.Configuring ?: return
        val request = current.request

        _uiState.value = ConversionUiState.Processing(ConversionStage.PREPARING, 10, "Initializing conversion...")

        activeJob = viewModelScope.launch {
            val flow = when (request.conversionType) {
                ConversionType.IMAGE_COMPRESS, ConversionType.IMAGE_CONVERT -> {
                    imageEngine.process(request, outputDirectory)
                }
                ConversionType.IMAGES_TO_PDF -> {
                    pdfEngine.convertImagesToPdf(request, outputDirectory)
                }
                ConversionType.PDF_TO_IMAGES -> {
                    pdfEngine.extractPdfToImages(request, outputDirectory)
                }
                ConversionType.VIDEO_COMPRESS -> {
                    mediaEngine.compressVideo(request, outputDirectory)
                }
                ConversionType.EXTRACT_AUDIO -> {
                    mediaEngine.extractAudio(request, outputDirectory)
                }
            }

            flow.collect { result ->
                when (result) {
                    is AppResult.Progress -> {
                        _uiState.value = ConversionUiState.Processing(
                            stage = ConversionStage.PROCESSING,
                            percentage = result.percentage,
                            statusMessage = result.currentStep
                        )
                    }
                    is AppResult.Success -> {
                        val record = ConversionRecordEntity.fromDomain(
                            result = result.data,
                            inputUris = request.sourceUris,
                            presetId = request.preset?.id
                        )
                        historyRepository.save(record)
                        adManager.recordConversion()

                        if (adManager.shouldShowInterstitial()) {
                            _shouldShowInterstitial.value = true
                        }

                        _uiState.value = ConversionUiState.Success(result.data, record)
                    }
                    is AppResult.Error -> {
                        val error = if (result.throwable is ConversionError) {
                            result.throwable as ConversionError
                        } else {
                            ConversionError.Unknown(result.message, result.throwable)
                        }
                        _uiState.value = ConversionUiState.Error(error)
                    }
                }
            }
        }
    }

    fun onInterstitialConsumed() {
        _shouldShowInterstitial.value = false
        adManager.onInterstitialShown()
    }

    fun cancelConversion() {
        activeJob?.cancel()
        _uiState.value = ConversionUiState.Idle
    }

    fun resetToIdle() {
        _uiState.value = ConversionUiState.Idle
    }

    fun toggleFavorite(recordId: String, isFavorited: Boolean) {
        viewModelScope.launch {
            historyRepository.setFavorited(recordId, isFavorited)
        }
    }

    fun unlockBatchMode(reward: AdReward = AdReward.BatchModeUnlock()) {
        adManager.grantReward(reward)
    }
}
