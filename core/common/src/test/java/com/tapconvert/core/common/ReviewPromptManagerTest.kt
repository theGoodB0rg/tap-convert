package com.tapconvert.core.common

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.concurrent.TimeUnit

class ReviewPromptManagerTest {

    private val baseTimeMs = 1724338200000L
    private val fortySixDaysMs = TimeUnit.DAYS.toMillis(46)
    private val tenDaysMs = TimeUnit.DAYS.toMillis(10)

    @Test
    fun `shouldPromptReview returns false on 1st successful conversion`() = runTest {
        val manager = InMemoryReviewPromptManager()

        manager.recordSuccessfulConversion()

        val shouldPrompt = manager.shouldPromptReview(baseTimeMs)
        assertThat(shouldPrompt).isFalse()
    }

    @Test
    fun `shouldPromptReview returns true on 2nd successful conversion`() = runTest {
        val manager = InMemoryReviewPromptManager()

        manager.recordSuccessfulConversion()
        manager.recordSuccessfulConversion()

        val shouldPrompt = manager.shouldPromptReview(baseTimeMs)
        assertThat(shouldPrompt).isTrue()
    }

    @Test
    fun `shouldPromptReview enforces 45 day cooldown after dismissal`() = runTest {
        val manager = InMemoryReviewPromptManager()

        // 2 conversions reach threshold
        manager.recordSuccessfulConversion()
        manager.recordSuccessfulConversion()
        assertThat(manager.shouldPromptReview(baseTimeMs)).isTrue()

        // User dismisses ("Maybe Later")
        manager.recordReviewDismissed(currentTimeMs = baseTimeMs)

        // 10 days later -> still in cooldown
        val promptAtDay10 = manager.shouldPromptReview(baseTimeMs + tenDaysMs)
        assertThat(promptAtDay10).isFalse()

        // 46 days later -> cooldown passed, can prompt again
        val promptAtDay46 = manager.shouldPromptReview(baseTimeMs + fortySixDaysMs)
        assertThat(promptAtDay46).isTrue()
    }

    @Test
    fun `shouldPromptReview returns false permanently after user completes review`() = runTest {
        val manager = InMemoryReviewPromptManager()

        manager.recordSuccessfulConversion()
        manager.recordSuccessfulConversion()
        assertThat(manager.shouldPromptReview(baseTimeMs)).isTrue()

        // User rates / reviews
        manager.recordReviewCompleted()

        // Forever false, even after 100 days
        val promptAfter100Days = manager.shouldPromptReview(baseTimeMs + TimeUnit.DAYS.toMillis(100))
        assertThat(promptAfter100Days).isFalse()
    }

    @Test
    fun `resetReviewState resets conversion counter and flags`() = runTest {
        val manager = InMemoryReviewPromptManager()

        manager.recordSuccessfulConversion()
        manager.recordSuccessfulConversion()
        manager.recordReviewCompleted()

        manager.resetReviewState()

        val shouldPrompt = manager.shouldPromptReview(baseTimeMs)
        assertThat(shouldPrompt).isFalse() // Needs 2 new conversions
    }
}
