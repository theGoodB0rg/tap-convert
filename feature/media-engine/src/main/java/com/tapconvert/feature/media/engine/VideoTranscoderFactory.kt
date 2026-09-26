package com.tapconvert.feature.media.engine

import android.content.Context

/**
 * Factory providing the optimal VideoTranscoder implementation for the current device and runtime environment.
 */
object VideoTranscoderFactory {

    /**
     * Creates the optimal VideoTranscoder for production or test environments.
     * In Android environments, returns [HardwarePipelinedTranscoder] with full hardware acceleration.
     * In non-Android JVM environments without [context], returns a JVM-compatible transcoder.
     */
    fun createOptimalTranscoder(context: Context? = null): VideoTranscoder {
        return if (context != null) {
            HardwarePipelinedTranscoder()
        } else {
            // For unit tests / mock environments running off-device
            Media3VideoTranscoder(context = null)
        }
    }
}
