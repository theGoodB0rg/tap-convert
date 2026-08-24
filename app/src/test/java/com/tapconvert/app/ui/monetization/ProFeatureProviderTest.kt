package com.tapconvert.app.ui.monetization

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProFeatureProviderTest {

    @Test
    fun `pro features list contains exactly 4 core value propositions`() {
        val features = ProFeatureProvider.getProFeatures()
        assertThat(features).hasSize(4)

        val icons = features.map { it.icon }
        assertThat(icons).containsExactly(
            ProFeatureIcon.BATCH,
            ProFeatureIcon.SPEED,
            ProFeatureIcon.AD_FREE,
            ProFeatureIcon.WATERMARK_FREE
        ).inOrder()
    }

    @Test
    fun `all pro feature titles are concise and contain strictly zero emojis`() {
        val features = ProFeatureProvider.getProFeatures()
        val emojiRegex = Regex("[\\p{So}\\p{Cn}\\uD83C-\\uDBFF\\uDC00-\\uDFFF]")

        for (feature in features) {
            assertThat(feature.title).doesNotMatch(emojiRegex.pattern)
            // Assert concise copy (under 35 characters for zero-overflow)
            assertThat(feature.title.length).isAtMost(35)
        }
    }

    @Test
    fun `feature titles match expected scannable copy`() {
        val features = ProFeatureProvider.getProFeatures()
        assertThat(features[0].title).isEqualTo("Unlimited batch conversions")
        assertThat(features[1].title).isEqualTo("Maximum processing speed")
        assertThat(features[2].title).isEqualTo("100% ad-free experience")
        assertThat(features[3].title).isEqualTo("Watermark-free PDF exports")
    }
}
