package com.tapconvert.core.model

enum class ConversionStage(val displayName: String) {
    ANALYZING("Analyzing Media"),
    PREPARING("Preparing Files"),
    PROCESSING("Processing Frames"),
    COMPRESSING("Compressing Stream"),
    FINALIZING("Saving Output")
}

data class ConversionProgress(
    val percentage: Int,
    val stage: ConversionStage = ConversionStage.PROCESSING,
    val currentItemIndex: Int = 1,
    val totalItems: Int = 1,
    val detailMessage: String? = null
) {
    init {
        require(percentage in 0..100) { "Percentage must be between 0 and 100, was $percentage" }
        require(currentItemIndex in 0..totalItems) { "currentItemIndex must be in 0..totalItems, was $currentItemIndex / $totalItems" }
    }

    val isComplete: Boolean
        get() = percentage == 100

    val overallSummary: String
        get() = if (totalItems > 1) {
            "Item $currentItemIndex of $totalItems: ${stage.displayName} ($percentage%)"
        } else {
            "${stage.displayName} ($percentage%)"
        }
}
