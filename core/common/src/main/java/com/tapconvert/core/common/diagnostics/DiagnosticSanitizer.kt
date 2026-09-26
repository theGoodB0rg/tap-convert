package com.tapconvert.core.common.diagnostics

import java.io.File
import java.security.MessageDigest

/**
 * Ensures diagnostic and observability data never leaks PII, full device directory
 * structures, username traces, or credentials into debug logs or telemetry endpoints.
 */
object DiagnosticSanitizer {

    private val EMAIL_REGEX = Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
    private val URL_QUERY_PARAM_REGEX = Regex("([?&][a-zA-Z0-9_]+)=([^&#\\s]+)")

    fun sanitizePath(path: String?): String {
        if (path.isNullOrBlank()) return "[empty]"
        val normalized = path.replace('\\', '/')
        val ext = normalized.substringAfterLast('.', "")
        val extSuffix = if (ext.isNotEmpty()) ".$ext" else ""

        val prefix = when {
            normalized.contains("/com.tapconvert.app/files/conversions") -> "[app_conversions]"
            normalized.contains("/cache") -> "[app_cache]"
            normalized.contains("/storage/emulated") || normalized.contains("/sdcard") -> "[storage]"
            else -> "[local]"
        }

        // Generate short stable hash of filename to trace operations without revealing filename
        val fileName = File(normalized).nameWithoutExtension
        val hash = shortSha256(fileName)
        return "$prefix/$hash$extSuffix"
    }

    fun sanitizeMessage(message: String?): String {
        if (message.isNullOrBlank()) return ""
        var result = EMAIL_REGEX.replace(message, "[EMAIL_REDACTED]")
        result = URL_QUERY_PARAM_REGEX.replace(result, "$1=[REDACTED]")
        return result
    }

    private fun shortSha256(input: String): String {
        if (input.isBlank()) return "anon"
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
            hashBytes.take(4).joinToString("") { "%02x".format(it) }
        } catch (_: Throwable) {
            "anon"
        }
    }
}
