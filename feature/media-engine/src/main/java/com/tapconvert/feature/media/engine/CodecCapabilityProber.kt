package com.tapconvert.feature.media.engine

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build

/**
 * Probes and caches device hardware video codec capabilities.
 */
object CodecCapabilityProber {

    data class CodecProfile(
        val isHardwareAccelerated: Boolean,
        val supportsVbr: Boolean,
        val maxSupportedWidth: Int,
        val maxSupportedHeight: Int,
        val encoderName: String
    )

    @Volatile
    private var cachedAvcProfile: CodecProfile? = null

    /**
     * Finds and inspects the best available AVC encoder on this device.
     */
    fun probeAvcEncoder(): CodecProfile {
        cachedAvcProfile?.let { return it }

        val codecList = try {
            MediaCodecList(MediaCodecList.REGULAR_CODECS)
        } catch (_: Throwable) {
            null
        }

        var selectedProfile: CodecProfile? = null
        val infos = try { codecList?.codecInfos } catch (_: Throwable) { null }

        if (infos != null) {
            for (info in infos) {
                if (info == null || !info.isEncoder) continue
                val types = info.supportedTypes
                if (!types.contains(MediaFormat.MIMETYPE_VIDEO_AVC)) continue

                val isHw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try { info.isHardwareAccelerated } catch (_: Throwable) { false }
                } else {
                    val name = info.name.lowercase()
                    !name.startsWith("omx.google.") && !name.startsWith("c2.android.")
                }

                val caps = try {
                    info.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC)
                } catch (_: Throwable) { null }

                val supportsVbr = try {
                    caps?.encoderCapabilities?.isBitrateModeSupported(
                        MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR
                    ) ?: false
                } catch (_: Throwable) { false }

                val videoCaps = caps?.videoCapabilities
                val maxWidth = try { videoCaps?.supportedWidths?.upper ?: 1920 } catch (_: Throwable) { 1920 }
                val maxHeight = try { videoCaps?.supportedHeights?.upper ?: 1080 } catch (_: Throwable) { 1080 }

                val profile = CodecProfile(
                    isHardwareAccelerated = isHw,
                    supportsVbr = supportsVbr,
                    maxSupportedWidth = maxWidth,
                    maxSupportedHeight = maxHeight,
                    encoderName = info.name
                )

                // Prefer hardware encoder if found
                if (isHw) {
                    selectedProfile = profile
                    break
                } else if (selectedProfile == null) {
                    selectedProfile = profile
                }
            }
        }

        val finalProfile = selectedProfile ?: CodecProfile(
            isHardwareAccelerated = false,
            supportsVbr = true,
            maxSupportedWidth = 1920,
            maxSupportedHeight = 1080,
            encoderName = "default"
        )

        cachedAvcProfile = finalProfile
        return finalProfile
    }

    fun clearCache() {
        cachedAvcProfile = null
    }

    /** Returns a software AVC encoder when the device exposes one. */
    fun findSoftwareAvcEncoder(): String? {
        val infos = try {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
        } catch (_: Throwable) {
            emptyArray()
        }
        return infos.firstOrNull { info ->
            if (!info.isEncoder || !info.supportedTypes.contains(MediaFormat.MIMETYPE_VIDEO_AVC)) {
                false
            } else {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        !info.isHardwareAccelerated
                    } else {
                        val name = info.name.lowercase()
                        name.startsWith("c2.android.") || name.startsWith("omx.google.")
                    }
                } catch (_: Throwable) {
                    false
                }
            }
        }?.name
    }
}
