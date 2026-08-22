package com.tapconvert.core.common

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExportFileNameGenerator {

    private const val DEFAULT_PREFIX = "TapConvert"
    private const val DEFAULT_FALLBACK_NAME = "File"
    private const val MAX_BASE_NAME_LENGTH = 30

    /**
     * Generates a standardized, branded export filename conforming to:
     * TapConvert_[SanitizedOriginalName]_[Timestamp].[extension]
     * Or for batch items:
     * TapConvert_[SanitizedOriginalName]_[BatchIndex]_[Timestamp].[extension]
     */
    fun generate(
        originalName: String?,
        extension: String,
        timestampMs: Long = System.currentTimeMillis(),
        batchIndex: Int? = null,
        prefix: String = DEFAULT_PREFIX,
        fallbackName: String = DEFAULT_FALLBACK_NAME
    ): String {
        val sanitizedBase = sanitizeBaseName(originalName, fallbackName)
        val formattedTimestamp = formatTimestamp(timestampMs)
        val cleanExtension = extension.removePrefix(".").trim().lowercase()

        val nameBuilder = StringBuilder(prefix).append("_").append(sanitizedBase)
        if (batchIndex != null) {
            nameBuilder.append("_").append(batchIndex)
        }
        nameBuilder.append("_").append(formattedTimestamp).append(".").append(cleanExtension)

        return nameBuilder.toString()
    }

    /**
     * Extracts basename without extension, strips path separators,
     * sanitizes non-alphanumeric chars to underscore, and clamps length.
     */
    fun sanitizeBaseName(rawName: String?, fallbackName: String = DEFAULT_FALLBACK_NAME): String {
        if (rawName.isNullOrBlank()) {
            return fallbackName
        }

        // Strip path directory components if full path was passed
        val fileNameOnly = File(rawName.trim()).name
        // Strip extension if present
        val withoutExt = if (fileNameOnly.contains(".")) {
            fileNameOnly.substringBeforeLast(".")
        } else {
            fileNameOnly
        }

        // Replace illegal/special chars with underscores (allow letters, digits, dashes, and underscores)
        val sanitized = withoutExt.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')

        val finalName = if (sanitized.isBlank()) fallbackName else sanitized
        return finalName.take(MAX_BASE_NAME_LENGTH).trimEnd('_')
    }

    fun formatTimestamp(timestampMs: Long): String {
        val formatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        return formatter.format(Date(timestampMs))
    }
}
