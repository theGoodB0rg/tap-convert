package com.tapconvert.core.model

import java.util.UUID

data class ConversionRequest(
    val id: String = UUID.randomUUID().toString(),
    val sourceUris: List<String>,
    val conversionType: ConversionType,
    val targetMimeType: MimeType,
    val preset: Preset? = null,
    val targetSize: TargetSize? = null,
    val dimensionConstraint: DimensionConstraint = DimensionConstraint.None,
    val quality: ConversionQuality = ConversionQuality.High,
    val preserveExif: Boolean = true,
    val customAudioBitrateKbps: Int? = null,
    val includeBranding: Boolean = true,
    val outputFileName: String? = null
) {
    init {
        require(sourceUris.isNotEmpty()) { "ConversionRequest must have at least one source URI" }
    }

    companion object {
        fun fromPreset(
            sourceUris: List<String>,
            preset: Preset,
            customOutputFileName: String? = null
        ): ConversionRequest {
            return ConversionRequest(
                sourceUris = sourceUris,
                conversionType = preset.conversionType,
                targetMimeType = preset.targetMimeType,
                preset = preset,
                targetSize = preset.targetSize,
                dimensionConstraint = preset.dimensionConstraint,
                quality = preset.quality,
                outputFileName = customOutputFileName
            )
        }
    }
}
