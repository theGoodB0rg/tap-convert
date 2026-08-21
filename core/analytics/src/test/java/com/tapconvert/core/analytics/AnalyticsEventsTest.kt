package com.tapconvert.core.analytics

import com.google.common.truth.Truth.assertThat
import com.tapconvert.core.model.ConversionType
import com.tapconvert.core.model.MediaCategory
import org.junit.Test

class AnalyticsEventsTest {

    @Test
    fun `ConversionStarted event maps parameters correctly`() {
        val event = AnalyticsEvent.ConversionStarted(
            conversionType = ConversionType.IMAGE_COMPRESS,
            inputSizeBytes = 2048L,
            sourceFormat = "image/jpeg",
            presetId = "gov_passport_200kb"
        )

        assertThat(event.eventName).isEqualTo("conversion_started")
        val params = event.toParamsMap()
        assertThat(params["conversion_type"]).isEqualTo("IMAGE_COMPRESS")
        assertThat(params["input_size_bytes"]).isEqualTo(2048L)
        assertThat(params["source_format"]).isEqualTo("image/jpeg")
        assertThat(params["preset_id"]).isEqualTo("gov_passport_200kb")
    }

    @Test
    fun `ConversionCompleted event maps parameters correctly`() {
        val event = AnalyticsEvent.ConversionCompleted(
            conversionType = ConversionType.VIDEO_COMPRESS,
            durationMs = 3500L,
            inputSizeBytes = 100_000_000L,
            outputSizeBytes = 15_000_000L,
            compressionRatio = 0.15f,
            presetId = "whatsapp_video_16mb",
            isSuccess = true
        )

        assertThat(event.eventName).isEqualTo("conversion_completed")
        val params = event.toParamsMap()
        assertThat(params["conversion_type"]).isEqualTo("VIDEO_COMPRESS")
        assertThat(params["duration_ms"]).isEqualTo(3500L)
        assertThat(params["input_size_bytes"]).isEqualTo(100_000_000L)
        assertThat(params["output_size_bytes"]).isEqualTo(15_000_000L)
        assertThat(params["compression_ratio"]).isEqualTo(0.15f)
        assertThat(params["preset_id"]).isEqualTo("whatsapp_video_16mb")
        assertThat(params["is_success"]).isEqualTo(true)
    }

    @Test
    fun `ConversionFailed event maps parameters correctly`() {
        val event = AnalyticsEvent.ConversionFailed(
            conversionType = ConversionType.EXTRACT_AUDIO,
            errorType = "CodecError",
            errorMessage = "Hardware encoder unavailable",
            presetId = "mp3_hq_320"
        )

        assertThat(event.eventName).isEqualTo("conversion_failed")
        val params = event.toParamsMap()
        assertThat(params["conversion_type"]).isEqualTo("EXTRACT_AUDIO")
        assertThat(params["error_type"]).isEqualTo("CodecError")
        assertThat(params["error_message"]).isEqualTo("Hardware encoder unavailable")
        assertThat(params["preset_id"]).isEqualTo("mp3_hq_320")
    }

    @Test
    fun `PresetSelected, AdImpression, AdReward and ShareAction map parameters`() {
        val preset = AnalyticsEvent.PresetSelected("whatsapp_video_16mb", MediaCategory.VIDEO)
        assertThat(preset.eventName).isEqualTo("preset_selected")
        assertThat(preset.toParamsMap()["category"]).isEqualTo("VIDEO")

        val ad = AnalyticsEvent.AdImpression("INTERSTITIAL", "PostConversion")
        assertThat(ad.eventName).isEqualTo("ad_impression")
        assertThat(ad.toParamsMap()["ad_format"]).isEqualTo("INTERSTITIAL")

        val reward = AnalyticsEvent.AdRewardGranted("BATCH_UNLOCK_24H", 1440)
        assertThat(reward.eventName).isEqualTo("ad_reward_granted")
        assertThat(reward.toParamsMap()["duration_minutes"]).isEqualTo(1440)

        val share = AnalyticsEvent.ShareAction("image/jpeg", 150_000L)
        assertThat(share.eventName).isEqualTo("share_action")
        assertThat(share.toParamsMap()["output_size_bytes"]).isEqualTo(150_000L)
    }
}
