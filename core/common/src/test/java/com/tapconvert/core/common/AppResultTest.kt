package com.tapconvert.core.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppResultTest {

    @Test
    fun `success result holds data correctly`() {
        val result = AppResult.Success("converted_file.pdf")
        assertThat(result.data).isEqualTo("converted_file.pdf")
    }

    @Test
    fun `error result holds throwable and message`() {
        val exception = IllegalArgumentException("Invalid file")
        val result = AppResult.Error(exception)
        assertThat(result.message).isEqualTo("Invalid file")
        assertThat(result.throwable).isEqualTo(exception)
    }

    @Test
    fun `progress result holds percentage and step`() {
        val progress = AppResult.Progress(75, "Compressing frames")
        assertThat(progress.percentage).isEqualTo(75)
        assertThat(progress.currentStep).isEqualTo("Compressing frames")
    }
}
