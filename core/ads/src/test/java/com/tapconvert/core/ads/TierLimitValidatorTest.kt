package com.tapconvert.core.ads

import com.google.common.truth.Truth.assertThat
import com.tapconvert.core.model.ConversionType
import org.junit.Test

class TierLimitValidatorTest {

    @Test
    fun `free tier allows up to 2 batch files and 5 PDF images`() {
        val freeState = AdState(isPro = false, isAdFree = false)

        // 2 batch files allowed
        val batch2 = TierLimitValidator.validate(2, ConversionType.IMAGE_COMPRESS, freeState)
        assertThat(batch2).isEqualTo(TierLimitResult.Allowed)

        // 3 batch files exceeded
        val batch3 = TierLimitValidator.validate(3, ConversionType.IMAGE_COMPRESS, freeState)
        assertThat(batch3).isInstanceOf(TierLimitResult.LimitExceeded::class.java)
        val batch3Exceeded = batch3 as TierLimitResult.LimitExceeded
        assertThat(batch3Exceeded.allowedCount).isEqualTo(2)
        assertThat(batch3Exceeded.requestedCount).isEqualTo(3)
        assertThat(batch3Exceeded.isPdf).isFalse()
        assertThat(batch3Exceeded.isPro).isFalse()
        assertThat(batch3Exceeded.isFastPassActive).isFalse()

        // 5 PDF images allowed
        val pdf5 = TierLimitValidator.validate(5, ConversionType.IMAGES_TO_PDF, freeState)
        assertThat(pdf5).isEqualTo(TierLimitResult.Allowed)

        // 6 PDF images exceeded
        val pdf6 = TierLimitValidator.validate(6, ConversionType.IMAGES_TO_PDF, freeState)
        assertThat(pdf6).isInstanceOf(TierLimitResult.LimitExceeded::class.java)
        val pdf6Exceeded = pdf6 as TierLimitResult.LimitExceeded
        assertThat(pdf6Exceeded.allowedCount).isEqualTo(5)
        assertThat(pdf6Exceeded.requestedCount).isEqualTo(6)
        assertThat(pdf6Exceeded.isPdf).isTrue()
    }

    @Test
    fun `fast pass allows up to 10 batch files and 15 PDF images`() {
        val now = 1_000_000L
        val fastPassState = AdState(
            isPro = false,
            isAdFree = false,
            batchModeExpiryTime = now + 86_400_000L
        )

        // 10 batch files allowed
        val batch10 = TierLimitValidator.validate(10, ConversionType.VIDEO_COMPRESS, fastPassState, now)
        assertThat(batch10).isEqualTo(TierLimitResult.Allowed)

        // 11 batch files exceeded
        val batch11 = TierLimitValidator.validate(11, ConversionType.VIDEO_COMPRESS, fastPassState, now)
        assertThat(batch11).isInstanceOf(TierLimitResult.LimitExceeded::class.java)
        val batch11Exceeded = batch11 as TierLimitResult.LimitExceeded
        assertThat(batch11Exceeded.allowedCount).isEqualTo(10)
        assertThat(batch11Exceeded.requestedCount).isEqualTo(11)
        assertThat(batch11Exceeded.isFastPassActive).isTrue()

        // 15 PDF images allowed
        val pdf15 = TierLimitValidator.validate(15, ConversionType.IMAGES_TO_PDF, fastPassState, now)
        assertThat(pdf15).isEqualTo(TierLimitResult.Allowed)

        // 16 PDF images exceeded
        val pdf16 = TierLimitValidator.validate(16, ConversionType.IMAGES_TO_PDF, fastPassState, now)
        assertThat(pdf16).isInstanceOf(TierLimitResult.LimitExceeded::class.java)
        val pdf16Exceeded = pdf16 as TierLimitResult.LimitExceeded
        assertThat(pdf16Exceeded.allowedCount).isEqualTo(15)
        assertThat(pdf16Exceeded.requestedCount).isEqualTo(16)
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
    fun `expired fast pass reverts to free tier limits`() {
        val now = 2_000_000L
        val expiredFastPassState = AdState(
            isPro = false,
            isAdFree = false,
            batchModeExpiryTime = now - 1000L // In the past
        )

        // 3 batch files should exceed free limit of 2
        val batch3 = TierLimitValidator.validate(3, ConversionType.IMAGE_COMPRESS, expiredFastPassState, now)
        assertThat(batch3).isInstanceOf(TierLimitResult.LimitExceeded::class.java)
        assertThat((batch3 as TierLimitResult.LimitExceeded).allowedCount).isEqualTo(2)
        assertThat(batch3.isFastPassActive).isFalse()
    }
}
