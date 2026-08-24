package com.tapconvert.app.ui

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.tapconvert.core.ads.AdReward
import com.tapconvert.core.ads.DefaultAdManager
import com.tapconvert.core.common.intake.IntakeResult
import com.tapconvert.core.common.intake.MediaIntakeManager
import com.tapconvert.core.common.intake.StagedMediaItem
import com.tapconvert.core.database.repository.InMemoryConversionHistoryRepository
import com.tapconvert.core.model.ConversionQuality
import com.tapconvert.core.model.DimensionConstraint
import com.tapconvert.core.model.Preset
import com.tapconvert.core.model.TargetSize
import com.tapconvert.core.testing.FakeAnalyticsTracker
import com.tapconvert.feature.image.engine.DefaultImageEngine
import com.tapconvert.feature.media.engine.DefaultMediaEngine
import com.tapconvert.feature.pdf.engine.DefaultPdfEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class MainViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

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
    fun `selectPreset with known large file size dynamically calibrates initial slider percentage`() {
        val largeFile = tempFolder.newFile("large_video.mp4").apply {
            writeBytes(ByteArray(40 * 1024 * 1024) { 0x11 }) // 40 MB file
        }

        // WhatsApp preset is 16 MB -> 16 / 40 * 0.95 * 100 ≈ 38%
        viewModel.selectPreset(Preset.WhatsAppVideo16MB, listOf("file://${largeFile.absolutePath}"))

        val config = viewModel.uiState.value as ConversionUiState.Configuring
        assertThat(config.request.quality.qualityPercent).isIn(35..45)
    }

    @Test
    fun `selectPreset with known small file size sets slider to 90 percent to prevent inflation`() {
        val smallFile = tempFolder.newFile("small_video.mp4").apply {
            writeBytes(ByteArray(5 * 1024 * 1024) { 0x22 }) // 5 MB file
        }

        // WhatsApp preset is 16 MB -> since 5MB <= 16MB, slider calibrated to 90%
        viewModel.selectPreset(Preset.WhatsAppVideo16MB, listOf("file://${smallFile.absolutePath}"))

        val config = viewModel.uiState.value as ConversionUiState.Configuring
        assertThat(config.request.quality.qualityPercent).isEqualTo(90)
    }

    @Test
    fun `selectPreset with PdfCompressGov500KB initializes PDF_COMPRESS configuration`() {
        val pdfFile = tempFolder.newFile("document.pdf").apply {
            writeBytes(ByteArray(2 * 1024 * 1024) { 0x33 }) // 2 MB file
        }

        viewModel.selectPreset(Preset.PdfCompressGov500KB, listOf("file://${pdfFile.absolutePath}"))

        val state = viewModel.uiState.value as ConversionUiState.Configuring
        assertThat(state.request.conversionType).isEqualTo(com.tapconvert.core.model.ConversionType.PDF_COMPRESS)
        assertThat(state.request.targetSize?.bytes).isEqualTo(500 * 1024L)
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
        val elevenPdfUris = (1..11).map { "file:///img$it.jpg" }

        viewModel.checkAndExecuteIntake(elevenPdfUris, com.tapconvert.core.model.ConversionType.IMAGES_TO_PDF) { uris ->
            executedUris = uris
        }

        // Free limit is 10 for PDF -> should trigger tier limit exceeded
        assertThat(executedUris).isNull()
        val limitExceeded = viewModel.tierLimitExceeded.value
        assertThat(limitExceeded).isNotNull()
        assertThat(limitExceeded?.requestedCount).isEqualTo(11)
        assertThat(limitExceeded?.allowedCount).isEqualTo(10)
        assertThat(limitExceeded?.freeLimit).isEqualTo(10)
        assertThat(limitExceeded?.rewardedLimit).isEqualTo(25)
        assertThat(limitExceeded?.canUnlockWithReward).isTrue()
        assertThat(limitExceeded?.isPdf).isTrue()

        // Proceeding with clamped limit
        viewModel.proceedWithClampedLimit(10)
        assertThat(executedUris).hasSize(10)
        assertThat(viewModel.tierLimitExceeded.value).isNull()
    }

    @Test
    fun `checkAndExecuteIntake retries with full list when single batch pass unlocked`() {
        var executedUris: List<String>? = null
        val twelveUris = (1..12).map { "file:///img$it.jpg" }

        viewModel.checkAndExecuteIntake(twelveUris, com.tapconvert.core.model.ConversionType.IMAGES_TO_PDF) { uris ->
            executedUris = uris
        }

        assertThat(executedUris).isNull()
        assertThat(viewModel.tierLimitExceeded.value).isNotNull()

        // Unlock Single Batch Pass (up to 25 PDF images)
        viewModel.unlockBatchMode(AdReward.SingleBatchUnlock())
        viewModel.retryPendingIntakeWithNewTier()

        assertThat(executedUris).hasSize(12)
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

    @Test
    fun `lifetimeReclaimedBytes and lifetimeConversionsCount reflect injected LifetimeStatsManager state`() = runTest {
        val statsManager = com.tapconvert.core.common.InMemoryLifetimeStatsManager(
            initialReclaimedBytes = 5_000_000L,
            initialCount = 3
        )
        val vm = MainViewModel(
            historyRepository = historyRepo,
            adManager = adManager,
            analyticsTracker = fakeAnalytics,
            lifetimeStatsManager = statsManager
        )

        assertThat(vm.lifetimeReclaimedBytes.first()).isEqualTo(5_000_000L)
        assertThat(vm.lifetimeConversionsCount.first()).isEqualTo(3)

        statsManager.recordConversion(10_000_000L, 4_000_000L)
        assertThat(vm.lifetimeReclaimedBytes.first()).isEqualTo(11_000_000L)
        assertThat(vm.lifetimeConversionsCount.first()).isEqualTo(4)
    }

    @Test
    fun `processIntakeUris with empty URIs does not change Idle state`() {
        viewModel.processIntakeUris(
            context = object : ContextWrapper(null) {},
            rawUris = emptyList(),
            stagingDirectory = tempFolder.root
        )
        assertThat(viewModel.uiState.value).isEqualTo(ConversionUiState.Idle)
    }

    @Test
    fun `processIntakeUris with success transitions to Configuring state`() = runTest {
        val fakeIntake = object : MediaIntakeManager {
            override suspend fun stageUris(
                context: Context,
                uris: List<Uri>,
                stagingDirectory: File
            ): IntakeResult {
                return IntakeResult.Success(
                    items = listOf(
                        StagedMediaItem(
                            uri = "file:///staged/image1.jpg",
                            originalName = "image1.jpg",
                            mimeType = "image/jpeg",
                            sizeBytes = 2048L
                        )
                    ),
                    totalSizeBytes = 2048L
                )
            }
        }

        val vm = MainViewModel(
            historyRepository = historyRepo,
            adManager = adManager,
            analyticsTracker = fakeAnalytics,
            mediaIntakeManager = fakeIntake,
            ioDispatcher = Dispatchers.Unconfined
        )

        vm.processIntakeUris(
            context = object : ContextWrapper(null) {},
            rawUris = listOf(Uri.EMPTY),
            stagingDirectory = tempFolder.root,
            preset = Preset.GovPassport200KB
        )

        val state = vm.uiState.value
        assertThat(state).isInstanceOf(ConversionUiState.Configuring::class.java)
        val config = state as ConversionUiState.Configuring
        assertThat(config.request.preset).isEqualTo(Preset.GovPassport200KB)
        assertThat(config.request.sourceUris).containsExactly("file:///staged/image1.jpg")
    }

    @Test
    fun `processIntakeUris with IntakeResult Error sets Error state`() = runTest {
        val fakeIntake = object : MediaIntakeManager {
            override suspend fun stageUris(
                context: Context,
                uris: List<Uri>,
                stagingDirectory: File
            ): IntakeResult {
                return IntakeResult.Error("Disk full error")
            }
        }

        val vm = MainViewModel(
            historyRepository = historyRepo,
            adManager = adManager,
            analyticsTracker = fakeAnalytics,
            mediaIntakeManager = fakeIntake,
            ioDispatcher = Dispatchers.Unconfined
        )

        vm.processIntakeUris(
            context = object : ContextWrapper(null) {},
            rawUris = listOf(Uri.EMPTY),
            stagingDirectory = tempFolder.root
        )

        val state = vm.uiState.value
        assertThat(state).isInstanceOf(ConversionUiState.Error::class.java)
    }

    @Test
    fun `processIntakeUris with universal intake video file dynamically configures VIDEO_COMPRESS`() = runTest {
        val fakeIntake = object : MediaIntakeManager {
            override suspend fun stageUris(
                context: Context,
                uris: List<Uri>,
                stagingDirectory: File
            ): IntakeResult {
                return IntakeResult.Success(
                    items = listOf(
                        StagedMediaItem(
                            uri = "file:///staged/1787583442306_4059ce_1000003432.mp4",
                            originalName = "1000003432.mp4",
                            mimeType = "video/mp4",
                            sizeBytes = 66_000_000L
                        )
                    ),
                    totalSizeBytes = 66_000_000L
                )
            }
        }

        val vm = MainViewModel(
            historyRepository = historyRepo,
            adManager = adManager,
            analyticsTracker = fakeAnalytics,
            mediaIntakeManager = fakeIntake,
            ioDispatcher = Dispatchers.Unconfined
        )

        // Universal dropzone intake: preset, category, and specificType are all null
        vm.processIntakeUris(
            context = object : ContextWrapper(null) {},
            rawUris = listOf(Uri.EMPTY),
            stagingDirectory = tempFolder.root
        )

        val state = vm.uiState.value
        assertThat(state).isInstanceOf(ConversionUiState.Configuring::class.java)
        val config = state as ConversionUiState.Configuring
        assertThat(config.request.conversionType).isEqualTo(com.tapconvert.core.model.ConversionType.VIDEO_COMPRESS)
        assertThat(config.request.targetMimeType).isEqualTo(com.tapconvert.core.model.MimeType.Video.MP4)
        assertThat(config.request.sourceUris).containsExactly("file:///staged/1787583442306_4059ce_1000003432.mp4")
    }

    @Test
    fun `processIntakeUris with universal intake PDF document dynamically configures PDF_COMPRESS`() = runTest {
        val fakeIntake = object : MediaIntakeManager {
            override suspend fun stageUris(
                context: Context,
                uris: List<Uri>,
                stagingDirectory: File
            ): IntakeResult {
                return IntakeResult.Success(
                    items = listOf(
                        StagedMediaItem(
                            uri = "file:///staged/report.pdf",
                            originalName = "report.pdf",
                            mimeType = "application/pdf",
                            sizeBytes = 5_000_000L
                        )
                    ),
                    totalSizeBytes = 5_000_000L
                )
            }
        }

        val vm = MainViewModel(
            historyRepository = historyRepo,
            adManager = adManager,
            analyticsTracker = fakeAnalytics,
            mediaIntakeManager = fakeIntake,
            ioDispatcher = Dispatchers.Unconfined
        )

        vm.processIntakeUris(
            context = object : ContextWrapper(null) {},
            rawUris = listOf(Uri.EMPTY),
            stagingDirectory = tempFolder.root
        )

        val state = vm.uiState.value
        assertThat(state).isInstanceOf(ConversionUiState.Configuring::class.java)
        val config = state as ConversionUiState.Configuring
        assertThat(config.request.conversionType).isEqualTo(com.tapconvert.core.model.ConversionType.PDF_COMPRESS)
        assertThat(config.request.targetMimeType).isEqualTo(com.tapconvert.core.model.MimeType.Document.PDF)
    }

    @Test
    fun `processIntakeUris with universal intake audio file dynamically configures EXTRACT_AUDIO`() = runTest {
        val fakeIntake = object : MediaIntakeManager {
            override suspend fun stageUris(
                context: Context,
                uris: List<Uri>,
                stagingDirectory: File
            ): IntakeResult {
                return IntakeResult.Success(
                    items = listOf(
                        StagedMediaItem(
                            uri = "file:///staged/podcast.mp3",
                            originalName = "podcast.mp3",
                            mimeType = "audio/mpeg",
                            sizeBytes = 12_000_000L
                        )
                    ),
                    totalSizeBytes = 12_000_000L
                )
            }
        }

        val vm = MainViewModel(
            historyRepository = historyRepo,
            adManager = adManager,
            analyticsTracker = fakeAnalytics,
            mediaIntakeManager = fakeIntake,
            ioDispatcher = Dispatchers.Unconfined
        )

        vm.processIntakeUris(
            context = object : ContextWrapper(null) {},
            rawUris = listOf(Uri.EMPTY),
            stagingDirectory = tempFolder.root
        )

        val state = vm.uiState.value
        assertThat(state).isInstanceOf(ConversionUiState.Configuring::class.java)
        val config = state as ConversionUiState.Configuring
        assertThat(config.request.conversionType).isEqualTo(com.tapconvert.core.model.ConversionType.EXTRACT_AUDIO)
        assertThat(config.request.targetMimeType).isEqualTo(com.tapconvert.core.model.MimeType.Audio.MP3)
    }
}



