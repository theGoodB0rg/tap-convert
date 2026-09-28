package com.tapconvert.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ConversionErrorTest {

    @Test
    fun `FileNotFound formats readable message with URI`() {
        val error = ConversionError.FileNotFound("content://media/external/images/42")
        assertThat(error.userReadableMessage).contains("content://media/external/images/42")
        assertThat(error.message).isEqualTo(error.userReadableMessage)
    }

    @Test
    fun `UnsupportedFormat indicates rejected format`() {
        val error = ConversionError.UnsupportedFormat("image/tiff")
        assertThat(error.userReadableMessage).contains("image/tiff")
    }

    @Test
    fun `TargetSizeUnachievable includes readable byte numbers`() {
        val error = ConversionError.TargetSizeUnachievable(
            minAchievableBytes = 300 * 1024L,
            targetBytes = 200 * 1024L
        )
        assertThat(error.userReadableMessage).contains("200 KB")
        assertThat(error.userReadableMessage).contains("300 KB")
    }

    @Test
    fun `CodecBudgetUnachievable gives actionable encoder guidance`() {
        val error = ConversionError.CodecBudgetUnachievable(
            encoder = "c2.android.avc.encoder",
            targetBytes = 16 * 1024L * 1024L
        )

        assertThat(error.userReadableMessage).contains("c2.android.avc.encoder")
        assertThat(error.userReadableMessage).contains("16 MB")
        assertThat(error.userReadableMessage.lowercase()).contains("less aggressive")
    }

    @Test
    fun `OutputBudgetExceeded explains that oversized output was not published`() {
        val error = ConversionError.OutputBudgetExceeded(
            actualBytes = 160 * 1024L * 1024L,
            limitBytes = 16 * 1024L * 1024L
        )

        assertThat(error.userReadableMessage).contains("160 MB")
        assertThat(error.userReadableMessage).contains("16 MB")
        assertThat(error.userReadableMessage.lowercase()).contains("no output was published")
    }

    @Test
    fun `InsufficientStorage includes required and available numbers`() {
        val error = ConversionError.InsufficientStorage(
            requiredBytes = 50 * 1024L * 1024L,
            availableBytes = 10 * 1024L * 1024L
        )
        assertThat(error.userReadableMessage).contains("50 MB")
        assertThat(error.userReadableMessage).contains("10 MB")
    }

    @Test
    fun `EncryptedPdf has user guidance`() {
        val error = ConversionError.EncryptedPdf()
        assertThat(error.userReadableMessage.lowercase()).contains("password")
    }

    @Test
    fun `Cancelled error is singleton object`() {
        val error = ConversionError.Cancelled
        assertThat(error.userReadableMessage).isEqualTo("Conversion was cancelled.")
    }
}
