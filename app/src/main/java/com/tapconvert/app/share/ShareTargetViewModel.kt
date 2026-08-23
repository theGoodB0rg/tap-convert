package com.tapconvert.app.share

import android.content.ContentResolver
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tapconvert.core.ads.AdManager
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
import com.tapconvert.core.model.ConversionResult
import com.tapconvert.core.model.ConversionStage
import com.tapconvert.core.model.ConversionType
import com.tapconvert.core.model.MediaCategory
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class ShareTargetViewModel(
    private val imageEngine: ImageEngine = DefaultImageEngine(),
    private val pdfEngine: PdfEngine = DefaultPdfEngine(),
    private val mediaEngine: MediaEngine = DefaultMediaEngine(),
    private val historyRepository: ConversionHistoryRepository = InMemoryConversionHistoryRepository(),
    val adManager: AdManager = DefaultAdManager(),
    private val analyticsTracker: AnalyticsTracker = NoOpAnalyticsTracker()
) : ViewModel() {

    private val _uiState = MutableStateFlow<ShareTargetUiState>(ShareTargetUiState.Loading)
    val uiState: StateFlow<ShareTargetUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null
    private var stagingDirectory: File? = null

    fun loadFromIntent(intent: Intent?, contentResolver: ContentResolver?, cacheDir: File) {
        stagingDirectory = cacheDir
        val payload = ShareIntentParser.parse(intent, contentResolver, cacheDir)
        loadFromPayload(payload)
    }

    fun loadFromPayload(payload: SharePayload?) {
        if (payload == null) {
            _uiState.value = ShareTargetUiState.Error(
                ConversionError.FileNotFound("No supported files found in share request")
            )
            return
        }

        val presets = Preset.presetsFor(payload.category)
        val defaultPreset = presets.firstOrNull()

        val defaultTargetMime = defaultPreset?.targetMimeType ?: when (payload.category) {
            MediaCategory.IMAGE -> MimeType.Image.JPEG
            MediaCategory.VIDEO -> MimeType.Video.MP4
            MediaCategory.DOCUMENT -> MimeType.Image.JPEG
            MediaCategory.AUDIO -> MimeType.Audio.MP3
        }

        _uiState.value = ShareTargetUiState.Ready(
            payload = payload,
            suggestedPresets = presets,
            selectedPreset = defaultPreset,
            customQuality = defaultPreset?.quality ?: ConversionQuality.High,
            customTargetSize = defaultPreset?.targetSize,
            selectedTargetMimeType = defaultTargetMime
        )
    }

    fun selectPreset(preset: Preset) {
        val current = _uiState.value as? ShareTargetUiState.Ready ?: return
        val totalSourceSize = current.payload.sourceUris.sumOf { uri ->
            try {
                val f = File(uri.removePrefix("file://"))
                if (f.exists()) f.length() else 0L
            } catch (_: Throwable) { 0L }
        }

        val presetTarget = preset.targetSize
        val calibratedQuality = if (presetTarget != null && totalSourceSize > 0L) {
            val targetBytes = presetTarget.bytes
            if (totalSourceSize <= targetBytes) {
                ConversionQuality.Custom(90)
            } else {
                val targetRatio = (targetBytes.toDouble() / totalSourceSize.toDouble() * 0.95 * 100.0).toInt()
                ConversionQuality.Custom(targetRatio.coerceIn(10, 95))
            }
        } else {
            preset.quality
        }

        _uiState.value = current.copy(
            selectedPreset = preset,
            selectedTargetMimeType = preset.targetMimeType,
            customQuality = calibratedQuality,
            customTargetSize = preset.targetSize
        )
        analyticsTracker.logPresetSelected(preset.id, preset.category)
    }

    fun updateTargetMimeType(mimeType: MimeType) {
        val current = _uiState.value as? ShareTargetUiState.Ready ?: return
        _uiState.value = current.copy(
            selectedTargetMimeType = mimeType,
            selectedPreset = if (current.selectedPreset?.targetMimeType == mimeType) current.selectedPreset else null
        )
    }

    fun updateQuality(quality: ConversionQuality) {
        val current = _uiState.value as? ShareTargetUiState.Ready ?: return
        _uiState.value = current.copy(customQuality = quality)
    }

    fun updateTargetSize(targetSize: TargetSize?) {
        val current = _uiState.value as? ShareTargetUiState.Ready ?: return
        _uiState.value = current.copy(customTargetSize = targetSize)
    }

    fun startConversion(outputDirectory: File) {
        val current = _uiState.value as? ShareTargetUiState.Ready ?: return
        val payload = current.payload
        val preset = current.selectedPreset

        val conversionType = preset?.conversionType ?: when (payload.category) {
            MediaCategory.IMAGE -> if (current.customTargetSize != null) ConversionType.IMAGE_COMPRESS else ConversionType.IMAGE_CONVERT
            MediaCategory.VIDEO -> ConversionType.VIDEO_COMPRESS
            MediaCategory.DOCUMENT -> ConversionType.PDF_TO_IMAGES
            MediaCategory.AUDIO -> ConversionType.EXTRACT_AUDIO
        }

        val request = ConversionRequest(
            sourceUris = payload.sourceUris,
            conversionType = conversionType,
            targetMimeType = current.selectedTargetMimeType,
            preset = preset,
            targetSize = current.customTargetSize,
            dimensionConstraint = preset?.dimensionConstraint ?: com.tapconvert.core.model.DimensionConstraint.None,
            quality = current.customQuality
        )

        _uiState.value = ShareTargetUiState.Converting(
            stage = ConversionStage.PREPARING,
            percentage = 10,
            statusMessage = "Preparing conversion..."
        )

        activeJob = viewModelScope.launch {
            val flow: Flow<AppResult<ConversionResult>> = when (request.conversionType) {
                ConversionType.IMAGE_COMPRESS, ConversionType.IMAGE_CONVERT -> {
                    imageEngine.process(request, outputDirectory)
                }
                ConversionType.IMAGES_TO_PDF -> {
                    pdfEngine.convertImagesToPdf(request, outputDirectory)
                }
                ConversionType.PDF_TO_IMAGES -> {
                    pdfEngine.extractPdfToImages(request, outputDirectory)
                }
                ConversionType.PDF_COMPRESS -> {
                    pdfEngine.compressPdf(request, outputDirectory)
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
                        _uiState.value = ShareTargetUiState.Converting(
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

                        _uiState.value = ShareTargetUiState.Success(
                            result = result.data,
                            record = record,
                            outputFilePaths = result.data.outputUris
                        )
                    }
                    is AppResult.Error -> {
                        val error = if (result.throwable is ConversionError) {
                            result.throwable as ConversionError
                        } else {
                            ConversionError.Unknown(result.message, result.throwable)
                        }
                        _uiState.value = ShareTargetUiState.Error(error)
                    }
                }
            }
        }
    }

    fun cancelConversion() {
        activeJob?.cancel()
        _uiState.value = ShareTargetUiState.Error(ConversionError.Cancelled)
    }
}
