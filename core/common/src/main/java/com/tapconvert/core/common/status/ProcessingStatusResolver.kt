package com.tapconvert.core.common.status

import com.tapconvert.core.model.ConversionStage

object ProcessingStatusResolver {

    /**
     * Resolves human-friendly, non-technical micro-copy based on stage and progress percentage.
     */
    fun resolveFriendlyStatus(stage: ConversionStage, percentage: Int): String {
        return when {
            percentage >= 100 -> "Done!"
            percentage >= 80 || stage == ConversionStage.FINALIZING -> "Saving your new file..."
            percentage >= 20 || stage == ConversionStage.COMPRESSING || stage == ConversionStage.PROCESSING -> "Shrinking size, keeping quality..."
            else -> "Analyzing media..."
        }
    }

    /**
     * Resolves clean, non-technical intake headline copy.
     */
    fun resolveIntakeTitle(fileCount: Int): String {
        return when (fileCount) {
            1 -> "Getting your file ready..."
            in 2..Int.MAX_VALUE -> "Getting $fileCount files ready..."
            else -> "Getting your files ready..."
        }
    }
}
