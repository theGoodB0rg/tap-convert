package com.tapconvert.feature.media.engine

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FfmpegSupportTest {
    @Test
    fun `supports shipped 64 bit ABIs`() {
        assertThat(FfmpegSupport.isSupported(arrayOf("arm64-v8a"))).isTrue()
        assertThat(FfmpegSupport.isSupported(arrayOf("x86_64"))).isTrue()
    }

    @Test
    fun `rejects ABIs not present in the FFmpeg AAR`() {
        assertThat(FfmpegSupport.isSupported(arrayOf("armeabi-v7a"))).isFalse()
        assertThat(FfmpegSupport.isSupported(arrayOf("x86"))).isFalse()
        assertThat(FfmpegSupport.isSupported(emptyArray())).isFalse()
    }
}
