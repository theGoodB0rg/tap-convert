package com.tapconvert.app.share

import com.google.common.truth.Truth.assertThat
import com.tapconvert.core.ads.DefaultAdManager
import com.tapconvert.core.database.repository.InMemoryConversionHistoryRepository
import com.tapconvert.core.model.ConversionQuality
import com.tapconvert.core.model.MediaCategory
import com.tapconvert.core.model.MimeType
import com.tapconvert.core.model.Preset
import com.tapconvert.core.model.TargetSize
import com.tapconvert.core.testing.FakeAnalyticsTracker
import com.tapconvert.feature.image.engine.DefaultImageEngine
import com.tapconvert.feature.media.engine.DefaultMediaEngine
import com.tapconvert.feature.pdf.engine.DefaultPdfEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ShareTargetViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var cacheDir: File
    private lateinit var outDir: File

    private val fakeAnalytics = FakeAnalyticsTracker()
    private val historyRepo = InMemoryConversionHistoryRepository()
    private val adManager = DefaultAdManager(analyticsTracker = fakeAnalytics)

    private val viewModel = ShareTargetViewModel(
        imageEngine = DefaultImageEngine(fakeAnalytics),
        pdfEngine = DefaultPdfEngine(fakeAnalytics),
        mediaEngine = DefaultMediaEngine(fakeAnalytics),
        historyRepository = historyRepo,
        adManager = adManager,
        analyticsTracker = fakeAnalytics
    )

    @Before
    fun setup() {
        cacheDir = tempFolder.newFolder("cache")
        outDir = tempFolder.newFolder("out")
    }

    @Test
    fun `initial uiState is Loading`() {
        assertThat(viewModel.uiState.value).isEqualTo(ShareTargetUiState.Loading)
    }

    @Test
    fun `loadFromIntent with null intent sets Error state`() {
        viewModel.loadFromIntent(null, null, cacheDir)
        val state = viewModel.uiState.value
        assertThat(state).isInstanceOf(ShareTargetUiState.Error::class.java)
    }

    @Test
    fun `loadFromPayload with image payload populates Ready state with presets`() {
        val payload = SharePayload(
            sourceUris = listOf("file:///path/sample.jpg"),
            fileNames = listOf("sample.jpg"),
            mimeType = "image/jpeg",
            category = MediaCategory.IMAGE,
            isMultiple = false,
            totalSizeBytes = 1024L
        )

        viewModel.loadFromPayload(payload)

        val state = viewModel.uiState.value
        assertThat(state).isInstanceOf(ShareTargetUiState.Ready::class.java)

        val ready = state as ShareTargetUiState.Ready
        assertThat(ready.payload.category).isEqualTo(MediaCategory.IMAGE)
        assertThat(ready.suggestedPresets).isNotEmpty()
        assertThat(ready.suggestedPresets).contains(Preset.GovPassport200KB)
    }

    @Test
    fun `selectPreset updates selected preset and logs telemetry`() {
        val payload = SharePayload(
            sourceUris = listOf("file:///path/sample.jpg"),
            fileNames = listOf("sample.jpg"),
            mimeType = "image/jpeg",
            category = MediaCategory.IMAGE,
            isMultiple = false,
            totalSizeBytes = 1024L
        )

        viewModel.loadFromPayload(payload)
        viewModel.selectPreset(Preset.GovPassport200KB)

        val ready = viewModel.uiState.value as ShareTargetUiState.Ready
        assertThat(ready.selectedPreset).isEqualTo(Preset.GovPassport200KB)
        assertThat(fakeAnalytics.hasSelectedPreset(Preset.GovPassport200KB.id)).isTrue()
    }

    @Test
    fun `updateQuality and updateTargetSize adjust custom parameters`() {
        val payload = SharePayload(
            sourceUris = listOf("file:///path/sample.jpg"),
            fileNames = listOf("sample.jpg"),
            mimeType = "image/jpeg",
            category = MediaCategory.IMAGE,
            isMultiple = false,
            totalSizeBytes = 1024L
        )

        viewModel.loadFromPayload(payload)
        viewModel.updateQuality(ConversionQuality.Low)
        viewModel.updateTargetSize(TargetSize.fromKilobytes(100))
        viewModel.updateTargetMimeType(MimeType.Image.WEBP)

        val ready = viewModel.uiState.value as ShareTargetUiState.Ready
        assertThat(ready.customQuality).isEqualTo(ConversionQuality.Low)
        assertThat(ready.customTargetSize?.bytes).isEqualTo(100 * 1024L)
        assertThat(ready.selectedTargetMimeType).isEqualTo(MimeType.Image.WEBP)
    }

    @Test
    fun `cancelConversion transitions state to Error Cancelled`() {
        viewModel.cancelConversion()
        val state = viewModel.uiState.value
        assertThat(state).isInstanceOf(ShareTargetUiState.Error::class.java)
    }
}
