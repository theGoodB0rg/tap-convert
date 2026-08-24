package com.tapconvert.core.ads

sealed interface AdReward {
    val rewardName: String

    data class SingleBatchUnlock(
        val maxBatchFiles: Int = DEFAULT_MAX_BATCH_FILES,
        val maxPdfImages: Int = DEFAULT_MAX_PDF_IMAGES,
        override val rewardName: String = "Single Batch Unlock"
    ) : AdReward {
        companion object {
            const val DEFAULT_MAX_BATCH_FILES = 20
            const val DEFAULT_MAX_PDF_IMAGES = 25
        }
    }

    data class BatchModeUnlock(
        val durationMs: Long = DEFAULT_BATCH_DURATION_MS,
        override val rewardName: String = "Batch Mode Fast Pass"
    ) : AdReward {
        companion object {
            const val DEFAULT_BATCH_DURATION_MS = 30 * 60 * 1000L // 30 minutes
        }
    }

    data class UltraFastUnlock(
        val durationMs: Long = DEFAULT_ULTRA_FAST_DURATION_MS,
        override val rewardName: String = "Ultra Fast Processing"
    ) : AdReward {
        companion object {
            const val DEFAULT_ULTRA_FAST_DURATION_MS = 15 * 60 * 1000L // 15 minutes
        }
    }
}
