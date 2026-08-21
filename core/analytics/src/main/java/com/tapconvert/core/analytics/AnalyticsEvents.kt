package com.tapconvert.core.analytics

import com.tapconvert.core.model.ConversionType
import com.tapconvert.core.model.MediaCategory

sealed interface AnalyticsEvent {
    val eventName: String
    fun toParamsMap(): Map<String, Any>

    data class ConversionStarted(
        val conversionType: ConversionType,
        val inputSizeBytes: Long,
        val sourceFormat: String,
        val presetId: String? = null
    ) : AnalyticsEvent {
        override val eventName: String = "conversion_started"

        override fun toParamsMap(): Map<String, Any> = buildMap {
            put("conversion_type", conversionType.name)
            put("input_size_bytes", inputSizeBytes)
            put("source_format", sourceFormat)
            presetId?.let { put("preset_id", it) }
        }
    }

    data class ConversionCompleted(
        val conversionType: ConversionType,
        val durationMs: Long,
        val inputSizeBytes: Long,
        val outputSizeBytes: Long,
        val compressionRatio: Float,
        val presetId: String? = null,
        val isSuccess: Boolean = true
    ) : AnalyticsEvent {
        override val eventName: String = "conversion_completed"

        override fun toParamsMap(): Map<String, Any> = buildMap {
            put("conversion_type", conversionType.name)
            put("duration_ms", durationMs)
            put("input_size_bytes", inputSizeBytes)
            put("output_size_bytes", outputSizeBytes)
            put("compression_ratio", compressionRatio)
            put("is_success", isSuccess)
            presetId?.let { put("preset_id", it) }
        }
    }

    data class ConversionFailed(
        val conversionType: ConversionType,
        val errorType: String,
        val errorMessage: String,
        val presetId: String? = null
    ) : AnalyticsEvent {
        override val eventName: String = "conversion_failed"

        override fun toParamsMap(): Map<String, Any> = buildMap {
            put("conversion_type", conversionType.name)
            put("error_type", errorType)
            put("error_message", errorMessage)
            presetId?.let { put("preset_id", it) }
        }
    }

    data class PresetSelected(
        val presetId: String,
        val category: MediaCategory
    ) : AnalyticsEvent {
        override val eventName: String = "preset_selected"

        override fun toParamsMap(): Map<String, Any> = mapOf(
            "preset_id" to presetId,
            "category" to category.name
        )
    }

    data class AdImpression(
        val adFormat: String,
        val placement: String
    ) : AnalyticsEvent {
        override val eventName: String = "ad_impression"

        override fun toParamsMap(): Map<String, Any> = mapOf(
            "ad_format" to adFormat,
            "placement" to placement
        )
    }

    data class AdRewardGranted(
        val rewardType: String,
        val durationMinutes: Int
    ) : AnalyticsEvent {
        override val eventName: String = "ad_reward_granted"

        override fun toParamsMap(): Map<String, Any> = mapOf(
            "reward_type" to rewardType,
            "duration_minutes" to durationMinutes
        )
    }

    data class ShareAction(
        val format: String,
        val outputSizeBytes: Long
    ) : AnalyticsEvent {
        override val eventName: String = "share_action"

        override fun toParamsMap(): Map<String, Any> = mapOf(
            "format" to format,
            "output_size_bytes" to outputSizeBytes
        )
    }

    data class Custom(
        override val eventName: String,
        val params: Map<String, Any> = emptyMap()
    ) : AnalyticsEvent {
        override fun toParamsMap(): Map<String, Any> = params
    }
}
