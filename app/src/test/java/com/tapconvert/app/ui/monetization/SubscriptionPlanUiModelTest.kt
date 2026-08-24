package com.tapconvert.app.ui.monetization

import com.google.common.truth.Truth.assertThat
import com.tapconvert.core.ads.SubscriptionPlan
import org.junit.Test

class SubscriptionPlanUiModelTest {

    @Test
    fun `annual plan maps to correct title, price, and best value badge`() {
        val uiModel = SubscriptionPlan.Annual.toUiModel()

        assertThat(uiModel.title).isEqualTo("Annual")
        assertThat(uiModel.priceFormatted).isEqualTo("$9.99 / yr")
        assertThat(uiModel.badge).isEqualTo("BEST VALUE")
        assertThat(uiModel.isBestValue).isTrue()
    }

    @Test
    fun `monthly plan maps to correct title and price without badge`() {
        val uiModel = SubscriptionPlan.Monthly.toUiModel()

        assertThat(uiModel.title).isEqualTo("Monthly")
        assertThat(uiModel.priceFormatted).isEqualTo("$0.99 / mo")
        assertThat(uiModel.badge).isNull()
        assertThat(uiModel.isBestValue).isFalse()
    }

    @Test
    fun `all subscription plan UI models contain strictly zero emoji characters`() {
        val plans = listOf(SubscriptionPlan.Annual.toUiModel(), SubscriptionPlan.Monthly.toUiModel())
        val emojiRegex = Regex("[\\p{So}\\p{Cn}\\uD83C-\\uDBFF\\uDC00-\\uDFFF]")

        for (plan in plans) {
            assertThat(plan.title).doesNotMatch(emojiRegex.pattern)
            assertThat(plan.priceFormatted).doesNotMatch(emojiRegex.pattern)
            plan.badge?.let { assertThat(it).doesNotMatch(emojiRegex.pattern) }
        }
    }
}
