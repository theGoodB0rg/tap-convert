package com.tapconvert.core.ads

sealed interface AdReward {
    val durationMs: Long
    val rewardName: String

    data class BatchModeUnlock(
        override val durationMs: Long = DEFAULT_BATCH_DURATION_MS,
        override val rewardName: String = "Batch Mode Fast Pass"
    ) : AdReward {
        companion object {
            const val DEFAULT_BATCH_DURATION_MS = 30 * 60 * 1000L // 30 minutes
        }
    }

    data class UltraFastUnlock(
        override val durationMs: Long = DEFAULT_ULTRA_FAST_DURATION_MS,
        override val rewardName: String = "Ultra Fast Processing"
    ) : AdReward {
        companion object {
            const val DEFAULT_ULTRA_FAST_DURATION_MS = 15 * 60 * 1000L // 15 minutes
        }
    }
}
