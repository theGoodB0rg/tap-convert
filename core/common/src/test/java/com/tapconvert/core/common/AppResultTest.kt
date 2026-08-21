package com.tapconvert.core.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppResultTest {

    @Test
    fun `success result holds data correctly and flags match`() {
        val result = AppResult.Success("converted_file.pdf")
        assertThat(result.data).isEqualTo("converted_file.pdf")
        assertThat(result.isSuccess).isTrue()
        assertThat(result.isError).isFalse()
        assertThat(result.isProgress).isFalse()
    }

    @Test
    fun `error result holds throwable and message and flags match`() {
        val exception = IllegalArgumentException("Invalid file")
        val result = AppResult.Error(exception)
        assertThat(result.message).isEqualTo("Invalid file")
        assertThat(result.throwable).isEqualTo(exception)
        assertThat(result.isSuccess).isFalse()
        assertThat(result.isError).isTrue()
        assertThat(result.isProgress).isFalse()
    }

    @Test
    fun `progress result holds percentage and step and flags match`() {
        val progress = AppResult.Progress(75, "Compressing frames")
        assertThat(progress.percentage).isEqualTo(75)
        assertThat(progress.currentStep).isEqualTo("Compressing frames")
        assertThat(progress.isSuccess).isFalse()
        assertThat(progress.isError).isFalse()
        assertThat(progress.isProgress).isTrue()
    }

    @Test
    fun `map transforms success value and leaves error or progress unchanged`() {
        val success = AppResult.Success(100)
        val mappedSuccess = success.map { it * 2 }
        assertThat(mappedSuccess.getOrNull()).isEqualTo(200)

        val error = AppResult.Error(RuntimeException("fail"))
        val mappedError = error.map { "ignored" }
        assertThat(mappedError.isError).isTrue()

        val progress = AppResult.Progress(50, "working")
        val mappedProgress = progress.map { "ignored" }
        assertThat(mappedProgress.isProgress).isTrue()
    }

    @Test
    fun `flatMap chains AppResult transformations correctly`() {
        val success = AppResult.Success("input.jpg")
        val flatMapped = success.flatMap { AppResult.Success("$it.pdf") }
        assertThat(flatMapped.getOrNull()).isEqualTo("input.jpg.pdf")

        val error = AppResult.Error(RuntimeException("disk full"))
        val flatMappedError = error.flatMap { AppResult.Success("unused") }
        assertThat(flatMappedError.isError).isTrue()
    }

    @Test
    fun `getOrNull and getOrElse behave properly across states`() {
        val success: AppResult<String> = AppResult.Success("output")
        val error: AppResult<String> = AppResult.Error(RuntimeException("err"))

        assertThat(success.getOrNull()).isEqualTo("output")
        assertThat(error.getOrNull()).isNull()

        assertThat(success.getOrElse { "fallback" }).isEqualTo("output")
        assertThat(error.getOrElse { "fallback" }).isEqualTo("fallback")
    }

    @Test
    fun `onSuccess, onError and onProgress fire respective callbacks`() {
        var successFired = false
        var errorFired = false
        var progressFired = false

        AppResult.Success("data").onSuccess { successFired = true }
        AppResult.Error(RuntimeException()).onError { errorFired = true }
        AppResult.Progress(10, "start").onProgress { _, _ -> progressFired = true }

        assertThat(successFired).isTrue()
        assertThat(errorFired).isTrue()
        assertThat(progressFired).isTrue()
    }

    @Test
    fun `fold reduces all 3 states into desired result`() {
        val success: AppResult<Int> = AppResult.Success(42)
        val error: AppResult<Int> = AppResult.Error(RuntimeException(), "Failed")
        val progress: AppResult<Int> = AppResult.Progress(60, "Running")

        val foldSuccess = success.fold(
            onSuccess = { "Value: $it" },
            onError = { it.message },
            onProgress = { pct, step -> "$pct% $step" }
        )
        val foldError = error.fold(
            onSuccess = { "Value: $it" },
            onError = { it.message },
            onProgress = { pct, step -> "$pct% $step" }
        )
        val foldProgress = progress.fold(
            onSuccess = { "Value: $it" },
            onError = { it.message },
            onProgress = { pct, step -> "$pct% $step" }
        )

        assertThat(foldSuccess).isEqualTo("Value: 42")
        assertThat(foldError).isEqualTo("Failed")
        assertThat(foldProgress).isEqualTo("60% Running")
    }
}

