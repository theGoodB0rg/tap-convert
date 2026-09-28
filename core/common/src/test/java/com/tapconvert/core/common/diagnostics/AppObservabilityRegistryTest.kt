package com.tapconvert.core.common.diagnostics

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppObservabilityRegistryTest {

    @Test
    fun `observability tracks completed and failed conversions correctly`() {
        val registry = AppObservabilityRegistry()

        val start = registry.onConversionStarted("trace-1")
        assertThat(registry.createSnapshot().activeConversionsCount).isEqualTo(1)

        registry.onConversionFinished(
            traceId = "trace-1",
            conversionType = "VIDEO_COMPRESS",
            inputSizeBytes = 10_000_000L,
            outputSizeBytes = 4_000_000L,
            durationMs = 2000L,
            isSuccess = true
        )

        val snap = registry.createSnapshot()
        assertThat(snap.activeConversionsCount).isEqualTo(0)
        assertThat(snap.totalConversionsCompleted).isEqualTo(1L)
        assertThat(snap.recentTraces).isNotEmpty()
        assertThat(snap.recentTraces.first().traceId).isEqualTo("trace-1")
        assertThat(snap.recentTraces.first().throughputKbps).isGreaterThan(0L)
    }

    @Test
    fun `exportSanitizedReportJson produces valid json without user paths`() {
        val registry = AppObservabilityRegistry()
        registry.onConversionFinished(
            traceId = "trace-json",
            conversionType = "IMAGE_COMPRESS",
            inputSizeBytes = 5_000_000L,
            outputSizeBytes = 500_000L,
            durationMs = 500L,
            isSuccess = true
        )

        val json = registry.exportSanitizedReportJson()
        assertThat(json).contains("\"trace-json\"")
        assertThat(json).contains("\"system\"")
        assertThat(json).contains("\"stats\"")
        assertThat(json).doesNotContain("/data/user")
    }

    @Test
    fun `recordEvent stores bounded sanitized observations`() {
        val registry = AppObservabilityRegistry(maxTracesCapacity = 2)
        registry.recordEvent(
            "video_compression_contract",
            mapOf(
                "outputPath" to "/data/user/0/com.tapconvert.app/files/private.mp4",
                "bytes" to 123L,
                "duration_ms" to 1200L,
                "input_throughput_kbps" to 5120L,
                "output_throughput_kbps" to 1024L
            )
        )

        val event = registry.createSnapshot().recentEvents.single()
        assertThat(event.name).isEqualTo("video_compression_contract")
        assertThat(event.params["outputPath"]).doesNotContain("/data/user")
        assertThat(event.params["duration_ms"]).isEqualTo("1200")
        assertThat(event.params["input_throughput_kbps"]).isEqualTo("5120")
        assertThat(event.params["output_throughput_kbps"]).isEqualTo("1024")
        assertThat(registry.exportSanitizedReportJson()).contains("recentEvents")
    }
}
