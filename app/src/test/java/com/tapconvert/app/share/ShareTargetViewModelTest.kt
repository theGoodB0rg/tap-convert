package com.tapconvert.app.share

import android.content.Intent
import android.net.Uri
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
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
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

    private val testDispatcher = StandardTestDispatcher()

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
    fun `loadFromIntent with image intent populates Ready state with presets`() {
        val imageFile = tempFolder.newFile("sample.jpg").apply {
            writeBytes(ByteArray(512) { 0xFF.toByte() })
        }

        val uri = mockk<Uri>()
        every { uri.scheme } returns "file"
        every { uri.path } returns imageFile.absolutePath
        every { uri.lastPathSegment } returns "sample.jpg"

        val intent = mockk<Intent>()
        every { intent.action } returns Intent.ACTION_SEND
        every { intent.type } returns "image/jpeg"
        every { intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) } returns uri
        every { intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) } returns uri
        every { intent.data } returns null
        every { intent.clipData } returns null

        viewModel.loadFromIntent(intent, null, cacheDir)

        val state = viewModel.uiState.value
        assertThat(state).isInstanceOf(ShareTargetUiState.Ready::class.java)

        val ready = state as ShareTargetUiState.Ready
        assertThat(ready.payload.category).isEqualTo(MediaCategory.IMAGE)
        assertThat(ready.suggestedPresets).isNotEmpty()
        assertThat(ready.suggestedPresets).contains(Preset.GovPassport200KB)
    }

    @Test
    fun `selectPreset updates selected preset and logs telemetry`() {
        val imageFile = tempFolder.newFile("sample.jpg").apply {
            writeBytes(ByteArray(512) { 0xFF.toByte() })
        }

        val uri = mockk<Uri>()
        every { uri.scheme } returns "file"
        every { uri.path } returns imageFile.absolutePath
        every { uri.lastPathSegment } returns "sample.jpg"

        val intent = mockk<Intent>()
        every { intent.action } returns Intent.ACTION_SEND
        every { intent.type } returns "image/jpeg"
        every { intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) } returns uri
        every { intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) } returns uri
        every { intent.data } returns null
        every { intent.clipData } returns null

        viewModel.loadFromIntent(intent, null, cacheDir)
        viewModel.selectPreset(Preset.GovPassport200KB)

        val ready = viewModel.uiState.value as ShareTargetUiState.Ready
        assertThat(ready.selectedPreset).isEqualTo(Preset.GovPassport200KB)
        assertThat(fakeAnalytics.hasSelectedPreset(Preset.GovPassport200KB.id)).isTrue()
    }

    @Test
    fun `updateQuality and updateTargetSize adjust custom parameters`() {
        val imageFile = tempFolder.newFile("sample.jpg").apply { writeBytes(ByteArray(512)) }

        val uri = mockk<Uri>()
        every { uri.scheme } returns "file"
        every { uri.path } returns imageFile.absolutePath
        every { uri.lastPathSegment } returns "sample.jpg"

        val intent = mockk<Intent>()
        every { intent.action } returns Intent.ACTION_SEND
        every { intent.type } returns "image/jpeg"
        every { intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) } returns uri
        every { intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) } returns uri
        every { intent.data } returns null
        every { intent.clipData } returns null

        viewModel.loadFromIntent(intent, null, cacheDir)
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
