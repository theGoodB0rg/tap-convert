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
}
