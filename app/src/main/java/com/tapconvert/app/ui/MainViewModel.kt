package com.tapconvert.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tapconvert.core.ads.AdManager
import com.tapconvert.core.ads.AdReward
import com.tapconvert.core.ads.DefaultAdManager
import com.tapconvert.core.ads.TierLimitResult
import com.tapconvert.core.ads.TierLimitValidator
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
import com.tapconvert.core.common.FakeInAppReviewLauncher
import com.tapconvert.core.common.InAppReviewLauncher
import com.tapconvert.core.common.InMemoryLifetimeStatsManager
import com.tapconvert.core.common.InMemoryReviewPromptManager
import com.tapconvert.core.common.LifetimeStatsManager
import com.tapconvert.core.common.ReviewPromptManager
import com.tapconvert.feature.pdf.engine.DefaultPdfEngine
import com.tapconvert.feature.pdf.engine.PdfEngine
import android.content.Context
import android.net.Uri
import com.tapconvert.core.common.intake.DefaultMediaIntakeManager
import com.tapconvert.core.common.intake.IntakeResult
import com.tapconvert.core.common.intake.MediaIntakeManager
import com.tapconvert.core.model.MediaCategory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel(
    private val imageEngine: ImageEngine = DefaultImageEngine(),
    private val pdfEngine: PdfEngine = DefaultPdfEngine(),
    private val mediaEngine: MediaEngine = DefaultMediaEngine(),
    private val historyRepository: ConversionHistoryRepository = InMemoryConversionHistoryRepository(),
    val adManager: AdManager = DefaultAdManager(),
    private val reviewPromptManager: ReviewPromptManager = InMemoryReviewPromptManager(),
    private val reviewLauncher: InAppReviewLauncher = FakeInAppReviewLauncher(),
    private val lifetimeStatsManager: LifetimeStatsManager = InMemoryLifetimeStatsManager(),
    private val analyticsTracker: AnalyticsTracker = NoOpAnalyticsTracker(),
    private val mediaIntakeManager: MediaIntakeManager = DefaultMediaIntakeManager(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    val lifetimeReclaimedBytes: Flow<Long> = lifetimeStatsManager.lifetimeReclaimedBytes
    val lifetimeConversionsCount: Flow<Int> = lifetimeStatsManager.lifetimeConversionsCount

    private val _uiState = MutableStateFlow<ConversionUiState>(ConversionUiState.Idle)
    val uiState: StateFlow<ConversionUiState> = _uiState.asStateFlow()

    private val _shouldShowInterstitial = MutableStateFlow(false)
    val shouldShowInterstitial: StateFlow<Boolean> = _shouldShowInterstitial.asStateFlow()

    private val _shouldShowReviewPrompt = MutableStateFlow(false)
    val shouldShowReviewPrompt: StateFlow<Boolean> = _shouldShowReviewPrompt.asStateFlow()

    private val _tierLimitExceeded = MutableStateFlow<TierLimitResult.LimitExceeded?>(null)
    val tierLimitExceeded: StateFlow<TierLimitResult.LimitExceeded?> = _tierLimitExceeded.asStateFlow()

    private var pendingIntakeAction: ((List<String>) -> Unit)? = null
    private var pendingIntakeUris: List<String> = emptyList()

    private var activeJob: Job? = null

    fun processIntakeUris(
        context: Context,
        rawUris: List<Uri>,
        stagingDirectory: File,
        preset: Preset? = null,
        category: MediaCategory? = null,
        specificType: ConversionType? = null
    ) {
        if (rawUris.isEmpty()) return

        _uiState.value = ConversionUiState.Staging("Preparing selected files...", rawUris.size)

        viewModelScope.launch(ioDispatcher) {
            when (val intakeResult = mediaIntakeManager.stageUris(context, rawUris, stagingDirectory)) {
                is IntakeResult.Success -> {
                    val workingUris = intakeResult.items.map { it.uri }
                    val current = _uiState.value
                    if (current is ConversionUiState.Configuring) {
                        addSourceUris(workingUris)
                        return@launch
                    }

                    if (preset != null) {
                        checkAndExecuteIntake(workingUris, preset.conversionType) { allowed ->
                            selectPreset(preset, allowed)
                        }
                    } else if (specificType != null) {
                        when (specificType) {
                            ConversionType.PDF_COMPRESS -> {
                                checkAndExecuteIntake(workingUris, ConversionType.PDF_COMPRESS) { allowed ->
                                    configureCustom(allowed, ConversionType.PDF_COMPRESS, MimeType.Document.PDF)
                                }
                            }
                            ConversionType.PDF_TO_IMAGES -> {
                                checkAndExecuteIntake(workingUris, ConversionType.PDF_TO_IMAGES) { allowed ->
                                    configureCustom(allowed, ConversionType.PDF_TO_IMAGES, MimeType.Image.JPEG)
                                }
                            }
                            else -> {
                                checkAndExecuteIntake(workingUris, specificType) { allowed ->
                                    configureCustom(allowed, specificType, MimeType.Document.PDF)
                                }
                            }
                        }
                    } else if (category != null) {
                        when (category) {
                            MediaCategory.IMAGE -> {
                                checkAndExecuteIntake(workingUris, ConversionType.IMAGE_COMPRESS) { allowed ->
                                    configureCustom(allowed, ConversionType.IMAGE_COMPRESS, MimeType.Image.WEBP)
                                }
                            }
                            MediaCategory.VIDEO -> {
                                checkAndExecuteIntake(workingUris, ConversionType.VIDEO_COMPRESS) { allowed ->
                                    configureCustom(allowed, ConversionType.VIDEO_COMPRESS, MimeType.Video.MP4)
                                }
                            }
                            MediaCategory.DOCUMENT -> {
                                checkAndExecuteIntake(workingUris, ConversionType.IMAGES_TO_PDF) { allowed ->
                                    configureCustom(allowed, ConversionType.IMAGES_TO_PDF, MimeType.Document.PDF)
                                }
                            }
                            MediaCategory.AUDIO -> {
                                checkAndExecuteIntake(workingUris, ConversionType.EXTRACT_AUDIO) { allowed ->
                                    configureCustom(allowed, ConversionType.EXTRACT_AUDIO, MimeType.Audio.MP3)
                                }
                            }
                        }
                    } else {
                        checkAndExecuteIntake(workingUris, ConversionType.IMAGE_COMPRESS) { allowed ->
                            configureCustom(allowed, ConversionType.IMAGE_COMPRESS, MimeType.Image.WEBP)
                        }
                    }
                }
                is IntakeResult.Empty -> {
                    if (_uiState.value is ConversionUiState.Staging) {
                        _uiState.value = ConversionUiState.Idle
                    }
                }
                is IntakeResult.Error -> {
                    _uiState.value = ConversionUiState.Error(ConversionError.IOError(intakeResult.message, intakeResult.cause))
                }
            }
        }
    }

    fun checkAndExecuteIntake(
        sourceUris: List<String>,
        conversionType: ConversionType,
        onAllowed: (List<String>) -> Unit
    ) {
        val validation = TierLimitValidator.validate(
            fileCount = sourceUris.size,
            conversionType = conversionType,
            adState = adManager.state.value
        )
        when (validation) {
            is TierLimitResult.Allowed -> {
                onAllowed(sourceUris)
            }
            is TierLimitResult.LimitExceeded -> {
                pendingIntakeAction = onAllowed
                pendingIntakeUris = sourceUris
                _tierLimitExceeded.value = validation
            }
        }
    }

    fun dismissTierLimit() {
        _tierLimitExceeded.value = null
        pendingIntakeAction = null
        pendingIntakeUris = emptyList()
    }

    fun proceedWithClampedLimit(allowedCount: Int) {
        val clamped = pendingIntakeUris.take(allowedCount)
        val action = pendingIntakeAction
        dismissTierLimit()
        action?.invoke(clamped)
    }

    fun retryPendingIntakeWithNewTier() {
        val action = pendingIntakeAction
        val uris = pendingIntakeUris
        dismissTierLimit()
        action?.invoke(uris)
    }

    fun selectPreset(preset: Preset, sourceUris: List<String>) {
        val totalSourceSize = sourceUris.sumOf { uri ->
            try {
                val f = File(uri.removePrefix("file://"))
                if (f.exists()) f.length() else 0L
            } catch (_: Throwable) { 0L }
        }

        val presetTarget = preset.targetSize
        val calibratedQuality = if (presetTarget != null && totalSourceSize > 0L) {
            val targetBytes = presetTarget.bytes
            if (totalSourceSize <= targetBytes) {
                // Source is already smaller than or equal to preset limit:
                // Set slider to 90% so compression still happens without size inflation
                ConversionQuality.Custom(90)
            } else {
                // Source is larger than preset limit:
                // Set slider to the exact ratio needed to reach the preset limit with 5% safety margin
                val targetRatio = (targetBytes.toDouble() / totalSourceSize.toDouble() * 0.95 * 100.0).toInt()
                ConversionQuality.Custom(targetRatio.coerceIn(10, 95))
            }
        } else {
            preset.quality
        }

        val request = ConversionRequest(
            sourceUris = sourceUris,
            conversionType = preset.conversionType,
            targetMimeType = preset.targetMimeType,
            preset = preset,
            targetSize = preset.targetSize,
            dimensionConstraint = preset.dimensionConstraint,
            quality = calibratedQuality
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

    /**
     * Updates PDF / document footer branding.
     * Policy: Only Pro subscribers can disable branding (rewarded ads cannot disable it).
     * Returns true if applied, false if blocked by non-Pro tier.
     */
    fun updateIncludeBranding(includeBranding: Boolean): Boolean {
        val current = _uiState.value
        if (current is ConversionUiState.Configuring) {
            if (!includeBranding && !adManager.state.value.isPro) {
                return false
            }
            _uiState.value = current.copy(request = current.request.copy(includeBranding = includeBranding))
            return true
        }
        return false
    }

    fun removeSourceUri(index: Int) {
        val current = _uiState.value as? ConversionUiState.Configuring ?: return
        if (index !in current.request.sourceUris.indices) return

        val newUris = current.request.sourceUris.toMutableList().apply { removeAt(index) }
        if (newUris.isEmpty()) {
            _uiState.value = ConversionUiState.Idle
            return
        }
        val newNames = newUris.map { File(it.removePrefix("file://")).name }
        _uiState.value = current.copy(
            request = current.request.copy(sourceUris = newUris),
            sourceFileNames = newNames
        )
    }

    fun addSourceUris(newUris: List<String>) {
        val current = _uiState.value as? ConversionUiState.Configuring ?: return
        if (newUris.isEmpty()) return

        val combinedUris = (current.request.sourceUris + newUris).distinct()
        checkAndExecuteIntake(combinedUris, current.request.conversionType) { allowedUris ->
            val combinedNames = allowedUris.map { File(it.removePrefix("file://")).name }
            _uiState.value = current.copy(
                request = current.request.copy(sourceUris = allowedUris),
                sourceFileNames = combinedNames
            )
        }
    }

    fun reorderSourceUris(fromIndex: Int, toIndex: Int) {
        val current = _uiState.value as? ConversionUiState.Configuring ?: return
        val uris = current.request.sourceUris.toMutableList()
        if (fromIndex !in uris.indices || toIndex !in uris.indices || fromIndex == toIndex) return

        val item = uris.removeAt(fromIndex)
        uris.add(toIndex, item)
        val names = uris.map { File(it.removePrefix("file://")).name }
        _uiState.value = current.copy(
            request = current.request.copy(sourceUris = uris),
            sourceFileNames = names
        )
    }

    fun startConversion(outputDirectory: File) {
        val current = _uiState.value as? ConversionUiState.Configuring ?: return
        val request = current.request

        // Consume single batch pass token if one was granted
        adManager.consumeBatchToken()

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
                        lifetimeStatsManager.recordConversion(result.data.originalSizeBytes, result.data.outputSizeBytes)

                        reviewPromptManager.recordSuccessfulConversion()
                        if (reviewPromptManager.shouldPromptReview()) {
                            _shouldShowReviewPrompt.value = true
                        }

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

    fun onReviewAccepted(activity: android.app.Activity? = null) {
        _shouldShowReviewPrompt.value = false
        viewModelScope.launch {
            reviewPromptManager.recordReviewCompleted()
            reviewLauncher.launchReview(activity)
        }
    }

    fun onReviewDismissed() {
        _shouldShowReviewPrompt.value = false
        viewModelScope.launch {
            reviewPromptManager.recordReviewDismissed()
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

    fun unlockBatchMode(reward: AdReward = AdReward.SingleBatchUnlock()) {
        adManager.grantReward(reward)
    }

    fun purchasePro(plan: com.tapconvert.core.ads.SubscriptionPlan = com.tapconvert.core.ads.SubscriptionPlan.Lifetime) {
        adManager.setPro(true, plan.tier)
        analyticsTracker.logPaywallPlanSelected(plan.productId, plan.tier.name)
    }
}
