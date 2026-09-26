package com.tapconvert.core.common.diagnostics

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DiagnosticSanitizerTest {

    @Test
    fun `sanitizePath masks private app internal directories`() {
        val rawPath = "/data/user/0/com.tapconvert.app/files/conversions/user_secret_passport_2026.jpg"
        val sanitized = DiagnosticSanitizer.sanitizePath(rawPath)

        assertThat(sanitized).doesNotContain("user_secret_passport_2026")
        assertThat(sanitized).doesNotContain("/data/user/0/com.tapconvert.app")
        assertThat(sanitized).endsWith(".jpg")
    }

    @Test
    fun `sanitizePath masks external storage username paths`() {
        val rawPath = "/storage/emulated/0/Download/Personal_Financial_Statement.pdf"
        val sanitized = DiagnosticSanitizer.sanitizePath(rawPath)

        assertThat(sanitized).doesNotContain("Personal_Financial_Statement")
        assertThat(sanitized).endsWith(".pdf")
        assertThat(sanitized).startsWith("[storage]")
    }

    @Test
    fun `sanitizeMessage strips email and url query parameters`() {
        val message = "Failed processing file for user john.doe@example.com at https://example.com/api?token=secret123"
        val sanitized = DiagnosticSanitizer.sanitizeMessage(message)

        assertThat(sanitized).doesNotContain("john.doe@example.com")
        assertThat(sanitized).doesNotContain("secret123")
        assertThat(sanitized).contains("[EMAIL_REDACTED]")
    }
}
