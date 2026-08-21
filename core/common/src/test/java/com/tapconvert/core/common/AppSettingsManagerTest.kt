package com.tapconvert.core.common

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AppSettingsManagerTest {

    private val settingsManager: AppSettingsManager = InMemoryAppSettingsManager()

    @Test
    fun `default settings values are set correctly`() = runTest {
        settingsManager.autoSaveToGallery.test {
            assertThat(awaitItem()).isTrue()
        }

        settingsManager.customStorageUri.test {
            assertThat(awaitItem()).isNull()
        }

        settingsManager.customStorageDisplayPath.test {
            assertThat(awaitItem()).isNull()
        }

        settingsManager.hapticFeedbackEnabled.test {
            assertThat(awaitItem()).isTrue()
        }
    }

    @Test
    fun `setCustomStorageLocation updates storage flow and path`() = runTest {
        settingsManager.setCustomStorageLocation(
            uri = "content://com.android.externalstorage.documents/tree/primary%3AMyConversions",
            displayPath = "Internal Storage/MyConversions"
        )

        settingsManager.customStorageUri.test {
            assertThat(awaitItem()).isEqualTo("content://com.android.externalstorage.documents/tree/primary%3AMyConversions")
        }

        settingsManager.customStorageDisplayPath.test {
            assertThat(awaitItem()).isEqualTo("Internal Storage/MyConversions")
        }
    }

    @Test
    fun `resetToDefaultStorage clears custom location`() = runTest {
        settingsManager.setCustomStorageLocation("content://test", "MyFolder")
        settingsManager.resetToDefaultStorage()

        settingsManager.customStorageUri.test {
            assertThat(awaitItem()).isNull()
        }

        settingsManager.customStorageDisplayPath.test {
            assertThat(awaitItem()).isNull()
        }
    }

    @Test
    fun `toggle auto save to gallery persists accurately`() = runTest {
        settingsManager.setAutoSaveToGallery(false)

        settingsManager.autoSaveToGallery.test {
            assertThat(awaitItem()).isFalse()
        }

        settingsManager.setAutoSaveToGallery(true)

        settingsManager.autoSaveToGallery.test {
            assertThat(awaitItem()).isTrue()
        }
    }
}
