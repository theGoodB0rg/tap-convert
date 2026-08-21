package com.tapconvert.core.ads

data class AdPolicyConfig(
    val minConversionsBeforeFirstInterstitial: Int = 2,
    val interstitialCooldownMs: Long = 60_000L, // 60 seconds
    val maxInterstitialsPerSession: Int = 5
)
