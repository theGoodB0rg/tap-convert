package com.tapconvert.core.ads

import com.google.common.truth.Truth.assertThat
import com.tapconvert.core.model.ConversionType
import org.junit.Test

class TierLimitValidatorTest {

    @Test
    fun `free tier allows up to 5 batch files and 10 PDF images`() {
        val freeState = AdState(isPro = false, isAdFree = false)

        // 5 batch files allowed
        val batch5 = TierLimitValidator.validate(5, ConversionType.IMAGE_COMPRESS, freeState)
        assertThat(batch5).isEqualTo(TierLimitResult.Allowed)

        // 6 batch files exceeded (can unlock with reward)
        val batch6 = TierLimitValidator.validate(6, ConversionType.IMAGE_COMPRESS, freeState)
        assertThat(batch6).isInstanceOf(TierLimitResult.LimitExceeded::class.java)
        val batch6Exceeded = batch6 as TierLimitResult.LimitExceeded
        assertThat(batch6Exceeded.allowedCount).isEqualTo(5)
        assertThat(batch6Exceeded.requestedCount).isEqualTo(6)
        assertThat(batch6Exceeded.freeLimit).isEqualTo(5)
        assertThat(batch6Exceeded.rewardedLimit).isEqualTo(20)
        assertThat(batch6Exceeded.canUnlockWithReward).isTrue()
        assertThat(batch6Exceeded.isPdf).isFalse()
        assertThat(batch6Exceeded.isPro).isFalse()
        assertThat(batch6Exceeded.isFastPassActive).isFalse()

        // 10 PDF images allowed
        val pdf10 = TierLimitValidator.validate(10, ConversionType.IMAGES_TO_PDF, freeState)
        assertThat(pdf10).isEqualTo(TierLimitResult.Allowed)

        // 11 PDF images exceeded
        val pdf11 = TierLimitValidator.validate(11, ConversionType.IMAGES_TO_PDF, freeState)
        assertThat(pdf11).isInstanceOf(TierLimitResult.LimitExceeded::class.java)
        val pdf11Exceeded = pdf11 as TierLimitResult.LimitExceeded
        assertThat(pdf11Exceeded.allowedCount).isEqualTo(10)
        assertThat(pdf11Exceeded.requestedCount).isEqualTo(11)
        assertThat(pdf11Exceeded.freeLimit).isEqualTo(10)
        assertThat(pdf11Exceeded.rewardedLimit).isEqualTo(25)
        assertThat(pdf11Exceeded.canUnlockWithReward).isTrue()
        assertThat(pdf11Exceeded.isPdf).isTrue()
    }

    @Test
    fun `rewarded single-batch pass allows up to 20 batch files and 25 PDF images`() {
        val singleBatchUnlockedState = AdState(
            isPro = false,
            isAdFree = false,
            unlockedBatchTokens = 1
        )

        // 20 batch files allowed
        val batch20 = TierLimitValidator.validate(20, ConversionType.VIDEO_COMPRESS, singleBatchUnlockedState)
        assertThat(batch20).isEqualTo(TierLimitResult.Allowed)

        // 21 batch files exceeded (cannot unlock with simple video, requires Pro)
        val batch21 = TierLimitValidator.validate(21, ConversionType.VIDEO_COMPRESS, singleBatchUnlockedState)
        assertThat(batch21).isInstanceOf(TierLimitResult.LimitExceeded::class.java)
        val batch21Exceeded = batch21 as TierLimitResult.LimitExceeded
        assertThat(batch21Exceeded.allowedCount).isEqualTo(20)
        assertThat(batch21Exceeded.requestedCount).isEqualTo(21)
        assertThat(batch21Exceeded.isFastPassActive).isTrue()
        assertThat(batch21Exceeded.canUnlockWithReward).isFalse()

        // 25 PDF images allowed
        val pdf25 = TierLimitValidator.validate(25, ConversionType.IMAGES_TO_PDF, singleBatchUnlockedState)
        assertThat(pdf25).isEqualTo(TierLimitResult.Allowed)

        // 26 PDF images exceeded
        val pdf26 = TierLimitValidator.validate(26, ConversionType.IMAGES_TO_PDF, singleBatchUnlockedState)
        assertThat(pdf26).isInstanceOf(TierLimitResult.LimitExceeded::class.java)
        val pdf26Exceeded = pdf26 as TierLimitResult.LimitExceeded
        assertThat(pdf26Exceeded.allowedCount).isEqualTo(25)
        assertThat(pdf26Exceeded.requestedCount).isEqualTo(26)
        assertThat(pdf26Exceeded.canUnlockWithReward).isFalse()
    }

    @Test
    fun `pro tier allows up to 100 batch files and 500 PDF images`() {
        val proState = AdState(isPro = true, isAdFree = true)

        // 100 batch files allowed
        val batch100 = TierLimitValidator.validate(100, ConversionType.IMAGE_CONVERT, proState)
        assertThat(batch100).isEqualTo(TierLimitResult.Allowed)

        // 500 PDF images allowed
        val pdf500 = TierLimitValidator.validate(500, ConversionType.IMAGES_TO_PDF, proState)
        assertThat(pdf500).isEqualTo(TierLimitResult.Allowed)

        // 501 PDF images exceeded
        val pdf501 = TierLimitValidator.validate(501, ConversionType.IMAGES_TO_PDF, proState)
        assertThat(pdf501).isInstanceOf(TierLimitResult.LimitExceeded::class.java)
        val pdf501Exceeded = pdf501 as TierLimitResult.LimitExceeded
        assertThat(pdf501Exceeded.allowedCount).isEqualTo(500)
        assertThat(pdf501Exceeded.requestedCount).isEqualTo(501)
        assertThat(pdf501Exceeded.isPro).isTrue()
    }

    @Test
    fun `exhausted tokens revert to free tier limits`() {
        val noTokensState = AdState(
            isPro = false,
            isAdFree = false,
            unlockedBatchTokens = 0
        )

        // 6 batch files should exceed free limit of 5
        val batch6 = TierLimitValidator.validate(6, ConversionType.IMAGE_COMPRESS, noTokensState)
        assertThat(batch6).isInstanceOf(TierLimitResult.LimitExceeded::class.java)
        assertThat((batch6 as TierLimitResult.LimitExceeded).allowedCount).isEqualTo(5)
        assertThat(batch6.isFastPassActive).isFalse()
        assertThat(batch6.canUnlockWithReward).isTrue()
    }
}
