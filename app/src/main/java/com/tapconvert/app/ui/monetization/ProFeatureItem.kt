package com.tapconvert.app.ui.monetization

enum class ProFeatureIcon {
    BATCH,
    SPEED,
    AD_FREE,
    WATERMARK_FREE
}

data class ProFeatureItem(
    val title: String,
    val icon: ProFeatureIcon
)

object ProFeatureProvider {

    fun getProFeatures(): List<ProFeatureItem> = listOf(
        ProFeatureItem(
            title = "100 files per batch conversion",
            icon = ProFeatureIcon.BATCH
        ),
        ProFeatureItem(
            title = "Turbo multi-core processing",
            icon = ProFeatureIcon.SPEED
        ),
        ProFeatureItem(
            title = "100% ad-free experience",
            icon = ProFeatureIcon.AD_FREE
        ),
        ProFeatureItem(
            title = "Watermark-free PDF exports",
            icon = ProFeatureIcon.WATERMARK_FREE
        )
    )
}
