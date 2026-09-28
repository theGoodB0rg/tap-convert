@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.tapconvert.feature.media.engine

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.C
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Converts PCM input to mono 8 kHz PCM before low-bitrate AAC encoding. */
internal class LowRateMonoAudioProcessor : BaseAudioProcessor() {
    private var inputRate = 48_000
    private var inputChannels = 2
    private var phase = 0

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        inputRate = inputAudioFormat.sampleRate.coerceAtLeast(8_000)
        inputChannels = inputAudioFormat.channelCount.coerceIn(1, 2)
        phase = 0
        return AudioProcessor.AudioFormat(8_000, 1, C.ENCODING_PCM_16BIT)
    }

    override fun queueInput(input: ByteBuffer) {
        val inputBytes = input.remaining()
        val bytesPerFrame = inputChannels * 2
        if (bytesPerFrame <= 0) {
            input.position(input.limit())
            return
        }
        val frameCount = inputBytes / bytesPerFrame
        val output = replaceOutputBuffer(frameCount * 2)
        val pcm = input.order(ByteOrder.LITTLE_ENDIAN)
        repeat(frameCount) {
            var sum = 0
            repeat(inputChannels) { sum += pcm.getShort().toInt() }
            phase += 8_000
            if (phase >= inputRate) {
                phase -= inputRate
                output.putShort((sum / inputChannels).toShort())
            }
        }
        input.position(input.limit())
        output.flip()
    }

    override fun onReset() {
        inputRate = 48_000
        inputChannels = 2
        phase = 0
    }
}
