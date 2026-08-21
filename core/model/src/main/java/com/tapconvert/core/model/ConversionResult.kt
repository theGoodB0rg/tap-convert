package com.tapconvert.core.model

data class ConversionResult(
    val requestId: String,
    val conversionType: ConversionType,
    val outputUris: List<String>,
    val originalSizeBytes: Long,
    val outputSizeBytes: Long,
    val durationMs: Long,
    val metadata: Map<String, String> = emptyMap()
) {
    init {
        require(originalSizeBytes >= 0) { "originalSizeBytes cannot be negative, was $originalSizeBytes" }
        require(outputSizeBytes >= 0) { "outputSizeBytes cannot be negative, was $outputSizeBytes" }
        require(durationMs >= 0) { "durationMs cannot be negative, was $durationMs" }
    }

    val compressionRatio: Float
        get() = if (originalSizeBytes > 0) outputSizeBytes.toFloat() / originalSizeBytes.toFloat() else 1.0f

    val bytesSaved: Long
        get() = (originalSizeBytes - outputSizeBytes).coerceAtLeast(0L)

    val percentageSaved: Int
        get() = if (originalSizeBytes > 0) {
            (((originalSizeBytes - outputSizeBytes).toDouble() / originalSizeBytes.toDouble()) * 100.0)
                .toInt()
                .coerceIn(0, 100)
        } else {
            0
        }
}
