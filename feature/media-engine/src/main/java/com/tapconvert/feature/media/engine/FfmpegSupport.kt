package com.tapconvert.feature.media.engine

import android.os.Build

/**
 * The maintained FFmpeg AAR currently ships arm64-v8a and x86_64 binaries.
 * Keep the decision explicit so unsupported ABIs stay on the platform path
 * instead of failing later with an opaque native-library load error.
 */
object FfmpegSupport {
    private val supportedAbis = setOf("arm64-v8a", "x86_64")

    fun isSupported(abis: Array<String> = Build.SUPPORTED_ABIS): Boolean =
        abis.any(supportedAbis::contains)
}
