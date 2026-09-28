package com.tapconvert.app.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.tapconvert.core.ads.DefaultAdManager
import com.tapconvert.core.common.AppResult
import com.tapconvert.core.database.repository.InMemoryConversionHistoryRepository
import com.tapconvert.core.model.ConversionRequest
import com.tapconvert.core.model.ConversionResult
import com.tapconvert.core.model.ConversionType
import com.tapconvert.core.model.MimeType
import com.tapconvert.core.analytics.NoOpAnalyticsTracker
import com.tapconvert.feature.media.engine.MediaEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.yield
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class MainViewModelDeviceTest {

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun uiProgressRemainsMonotonicOnDeviceWhenEngineRegresses() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val source = File(context.cacheDir, "ui-progress-regression.mp4").apply {
                writeBytes(ByteArray(1024) { 0x22 })
            }
            val fakeMediaEngine = object : MediaEngine {
                override fun compressVideo(
                    request: ConversionRequest,
                    outputDirectory: File
                ): Flow<AppResult<ConversionResult>> = flow {
                    emit(AppResult.Progress(89, "encoding"))
                    yield()
                    emit(AppResult.Progress(49, "fallback encoding"))
                    yield()
                    emit(AppResult.Progress(100, "finalizing"))
                    yield()
                    emit(
                        AppResult.Success(
                            ConversionResult(
                                requestId = request.id,
                                conversionType = ConversionType.VIDEO_COMPRESS,
                                outputUris = listOf(File(outputDirectory, "result.mp4").absolutePath),
                                originalSizeBytes = source.length(),
                                outputSizeBytes = 512L,
                                durationMs = 1L
                            )
                        )
                    )
                }

                override fun extractAudio(
                    request: ConversionRequest,
                    outputDirectory: File
                ): Flow<AppResult<ConversionResult>> = flow { }
            }
            val viewModel = MainViewModel(
                mediaEngine = fakeMediaEngine,
                historyRepository = InMemoryConversionHistoryRepository(),
                adManager = DefaultAdManager(),
                analyticsTracker = NoOpAnalyticsTracker()
            )
            viewModel.uiState.test {
                awaitItem() // Idle
                viewModel.configureCustom(
                    sourceUris = listOf("file://${source.absolutePath}"),
                    conversionType = ConversionType.VIDEO_COMPRESS,
                    targetMimeType = MimeType.Video.MP4
                )
                awaitItem() // Configuring
                viewModel.startConversion(context.cacheDir)
                assertThat((awaitItem() as ConversionUiState.Processing).percentage).isEqualTo(10)
                assertThat((awaitItem() as ConversionUiState.Processing).percentage).isEqualTo(89)
                assertThat((awaitItem() as ConversionUiState.Processing).percentage).isEqualTo(89)
                assertThat((awaitItem() as ConversionUiState.Processing).percentage).isEqualTo(100)
                assertThat(awaitItem()).isInstanceOf(ConversionUiState.Success::class.java)
                cancelAndIgnoreRemainingEvents()
            }
        } finally {
            Dispatchers.resetMain()
        }
    }
}
