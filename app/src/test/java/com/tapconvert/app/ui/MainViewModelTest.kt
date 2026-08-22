package com.tapconvert.app.ui

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.tapconvert.core.ads.AdReward
import com.tapconvert.core.ads.DefaultAdManager
import com.tapconvert.core.database.repository.InMemoryConversionHistoryRepository
import com.tapconvert.core.model.ConversionQuality
import com.tapconvert.core.model.DimensionConstraint
import com.tapconvert.core.model.Preset
import com.tapconvert.core.model.TargetSize
import com.tapconvert.core.testing.FakeAnalyticsTracker
import com.tapconvert.feature.image.engine.DefaultImageEngine
import com.tapconvert.feature.media.engine.DefaultMediaEngine
import com.tapconvert.feature.pdf.engine.DefaultPdfEngine
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MainViewModelTest {

    private val fakeAnalytics = FakeAnalyticsTracker()
    private val historyRepo = InMemoryConversionHistoryRepository()
    private val adManager = DefaultAdManager(analyticsTracker = fakeAnalytics)

    private val viewModel = MainViewModel(
        imageEngine = DefaultImageEngine(fakeAnalytics),
        pdfEngine = DefaultPdfEngine(fakeAnalytics),
        mediaEngine = DefaultMediaEngine(fakeAnalytics),
        historyRepository = historyRepo,
        adManager = adManager,
        analyticsTracker = fakeAnalytics
    )

    @Test
    fun `initial uiState is Idle`() {
        assertThat(viewModel.uiState.value).isEqualTo(ConversionUiState.Idle)
    }

    @Test
    fun `selectPreset transitions uiState to Configuring with preset parameters`() {
        viewModel.selectPreset(Preset.GovPassport200KB, listOf("file:///passport.jpg"))

        val state = viewModel.uiState.value
        assertThat(state).isInstanceOf(ConversionUiState.Configuring::class.java)

        val config = state as ConversionUiState.Configuring
        assertThat(config.request.preset).isEqualTo(Preset.GovPassport200KB)
        assertThat(config.sourceFileNames).containsExactly("passport.jpg")
        assertThat(fakeAnalytics.hasSelectedPreset(Preset.GovPassport200KB.id)).isTrue()
    }

    @Test
    fun `configuration updates modify active request`() {
        viewModel.selectPreset(Preset.GovPassport200KB, listOf("file:///photo.jpg"))

        viewModel.updateQuality(ConversionQuality.High)
        var config = viewModel.uiState.value as ConversionUiState.Configuring
        assertThat(config.request.quality).isEqualTo(ConversionQuality.High)

        viewModel.updateTargetSize(TargetSize.fromKilobytes(500))
        config = viewModel.uiState.value as ConversionUiState.Configuring
        assertThat(config.request.targetSize?.bytes).isEqualTo(500 * 1024L)

        viewModel.updateDimensionConstraint(DimensionConstraint.MaxDimension(1080))
        config = viewModel.uiState.value as ConversionUiState.Configuring
        assertThat(config.request.dimensionConstraint).isEqualTo(DimensionConstraint.MaxDimension(1080))
    }

    @Test
    fun `cancelConversion resets state to Idle`() {
        viewModel.selectPreset(Preset.WhatsAppVideo16MB, listOf("file:///video.mp4"))
        assertThat(viewModel.uiState.value).isInstanceOf(ConversionUiState.Configuring::class.java)

        viewModel.cancelConversion()
        assertThat(viewModel.uiState.value).isEqualTo(ConversionUiState.Idle)
    }

    @Test
    fun `unlockBatchMode grants reward to adManager`() {
        viewModel.unlockBatchMode(AdReward.BatchModeUnlock())
        assertThat(adManager.state.value.isBatchModeUnlocked()).isTrue()
        assertThat(fakeAnalytics.rewardsGranted).hasSize(1)
    }

    @Test
    fun `removeSourceUri removes element and updates state`() {
        viewModel.configureCustom(
            sourceUris = listOf("file:///p1.jpg", "file:///p2.jpg", "file:///p3.jpg"),
            conversionType = com.tapconvert.core.model.ConversionType.IMAGES_TO_PDF,
            targetMimeType = com.tapconvert.core.model.MimeType.Document.PDF
        )

        viewModel.removeSourceUri(1) // Remove p2.jpg
        val state = viewModel.uiState.value as ConversionUiState.Configuring
        assertThat(state.request.sourceUris).containsExactly("file:///p1.jpg", "file:///p3.jpg").inOrder()
        assertThat(state.sourceFileNames).containsExactly("p1.jpg", "p3.jpg").inOrder()
    }

    @Test
    fun `removeSourceUri on last remaining item resets state to Idle`() {
        viewModel.selectPreset(Preset.GovPassport200KB, listOf("file:///single.jpg"))
        viewModel.removeSourceUri(0)
        assertThat(viewModel.uiState.value).isEqualTo(ConversionUiState.Idle)
    }

    @Test
    fun `addSourceUris appends new files to active configuration`() {
        viewModel.configureCustom(
            sourceUris = listOf("file:///p1.jpg"),
            conversionType = com.tapconvert.core.model.ConversionType.IMAGES_TO_PDF,
            targetMimeType = com.tapconvert.core.model.MimeType.Document.PDF
        )

        viewModel.addSourceUris(listOf("file:///p2.jpg", "file:///p3.jpg"))
        val state = viewModel.uiState.value as ConversionUiState.Configuring
        assertThat(state.request.sourceUris).containsExactly("file:///p1.jpg", "file:///p2.jpg", "file:///p3.jpg").inOrder()
        assertThat(state.sourceFileNames).containsExactly("p1.jpg", "p2.jpg", "p3.jpg").inOrder()
    }

    @Test
    fun `reorderSourceUris changes element ordering correctly`() {
        viewModel.configureCustom(
            sourceUris = listOf("file:///p1.jpg", "file:///p2.jpg", "file:///p3.jpg"),
            conversionType = com.tapconvert.core.model.ConversionType.IMAGES_TO_PDF,
            targetMimeType = com.tapconvert.core.model.MimeType.Document.PDF
        )

        viewModel.reorderSourceUris(0, 2) // Move p1.jpg to the end
        val state = viewModel.uiState.value as ConversionUiState.Configuring
        assertThat(state.request.sourceUris).containsExactly("file:///p2.jpg", "file:///p3.jpg", "file:///p1.jpg").inOrder()
        assertThat(state.sourceFileNames).containsExactly("p2.jpg", "p3.jpg", "p1.jpg").inOrder()
    }

    @Test
    fun `checkAndExecuteIntake intercepts files exceeding free limits and allows clamping`() {
        var executedUris: List<String>? = null
        val sixPdfUris = (1..6).map { "file:///img$it.jpg" }

        viewModel.checkAndExecuteIntake(sixPdfUris, com.tapconvert.core.model.ConversionType.IMAGES_TO_PDF) { uris ->
            executedUris = uris
        }

        // Free limit is 5 for PDF -> should trigger tier limit exceeded
        assertThat(executedUris).isNull()
        val limitExceeded = viewModel.tierLimitExceeded.value
        assertThat(limitExceeded).isNotNull()
        assertThat(limitExceeded?.requestedCount).isEqualTo(6)
        assertThat(limitExceeded?.allowedCount).isEqualTo(5)
        assertThat(limitExceeded?.isPdf).isTrue()

        // Proceeding with clamped limit
        viewModel.proceedWithClampedLimit(5)
        assertThat(executedUris).hasSize(5)
        assertThat(viewModel.tierLimitExceeded.value).isNull()
    }

    @Test
    fun `checkAndExecuteIntake retries with full list when fast pass unlocked`() {
        var executedUris: List<String>? = null
        val eightUris = (1..8).map { "file:///img$it.jpg" }

        viewModel.checkAndExecuteIntake(eightUris, com.tapconvert.core.model.ConversionType.IMAGES_TO_PDF) { uris ->
            executedUris = uris
        }

        assertThat(executedUris).isNull()
        assertThat(viewModel.tierLimitExceeded.value).isNotNull()

        // Unlock Fast Pass (up to 15 PDF images)
        viewModel.unlockBatchMode(AdReward.BatchModeUnlock())
        viewModel.retryPendingIntakeWithNewTier()

        assertThat(executedUris).hasSize(8)
        assertThat(viewModel.tierLimitExceeded.value).isNull()
    }

    @Test
    fun `updateIncludeBranding is blocked for free tier users`() {
        viewModel.configureCustom(
            sourceUris = listOf("file:///p1.jpg"),
            conversionType = com.tapconvert.core.model.ConversionType.IMAGES_TO_PDF,
            targetMimeType = com.tapconvert.core.model.MimeType.Document.PDF
        )

        val applied = viewModel.updateIncludeBranding(false)
        assertThat(applied).isFalse()

        val state = viewModel.uiState.value as ConversionUiState.Configuring
        assertThat(state.request.includeBranding).isTrue()
    }

    @Test
    fun `updateIncludeBranding is blocked even with rewarded ad unlocks`() {
        viewModel.configureCustom(
            sourceUris = listOf("file:///p1.jpg"),
            conversionType = com.tapconvert.core.model.ConversionType.IMAGES_TO_PDF,
            targetMimeType = com.tapconvert.core.model.MimeType.Document.PDF
        )

        viewModel.unlockBatchMode(AdReward.BatchModeUnlock())
        val applied = viewModel.updateIncludeBranding(false)
        assertThat(applied).isFalse()

        val state = viewModel.uiState.value as ConversionUiState.Configuring
        assertThat(state.request.includeBranding).isTrue()
    }

    @Test
    fun `updateIncludeBranding succeeds for Pro subscribers`() {
        viewModel.configureCustom(
            sourceUris = listOf("file:///p1.jpg"),
            conversionType = com.tapconvert.core.model.ConversionType.IMAGES_TO_PDF,
            targetMimeType = com.tapconvert.core.model.MimeType.Document.PDF
        )

        viewModel.purchasePro()
        val applied = viewModel.updateIncludeBranding(false)
        assertThat(applied).isTrue()

        val state = viewModel.uiState.value as ConversionUiState.Configuring
        assertThat(state.request.includeBranding).isFalse()
    }

    @Test
    fun `onReviewAccepted marks review completed and dismisses prompt`() {
        viewModel.onReviewAccepted(null)
        assertThat(viewModel.shouldShowReviewPrompt.value).isFalse()
    }

    @Test
    fun `onReviewDismissed dismisses prompt`() {
        viewModel.onReviewDismissed()
        assertThat(viewModel.shouldShowReviewPrompt.value).isFalse()
    }
}


