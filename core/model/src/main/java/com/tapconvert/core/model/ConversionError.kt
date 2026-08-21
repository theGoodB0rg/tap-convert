package com.tapconvert.core.model

sealed class ConversionError(
    val userReadableMessage: String,
    cause: Throwable? = null
) : Exception(userReadableMessage, cause) {

    data class FileNotFound(val uri: String) :
        ConversionError("The selected file could not be found: $uri")

    data class UnsupportedFormat(val format: String) :
        ConversionError("The file format '$format' is not supported for this conversion.")

    data class CorruptFile(val reason: String, val uri: String? = null) :
        ConversionError(if (uri != null) "Corrupt or unreadable file at $uri: $reason" else "Corrupt or unreadable file: $reason")

    data class TargetSizeUnachievable(
        val minAchievableBytes: Long,
        val targetBytes: Long
    ) : ConversionError("Could not reach target size ${TargetSize(targetBytes).formatted()}. Smallest achievable size was ${TargetSize(minAchievableBytes).formatted()}.")

    data class InsufficientStorage(
        val requiredBytes: Long,
        val availableBytes: Long
    ) : ConversionError("Insufficient storage. Required ${TargetSize(requiredBytes).formatted()}, available ${TargetSize(availableBytes).formatted()}.")

    data class EncryptedPdf(val detail: String = "Password protected PDF documents cannot be converted.") :
        ConversionError(detail)

    data class CodecError(val codec: String, val detail: String) :
        ConversionError("Hardware codec failure ($codec): $detail")

    data object Cancelled :
        ConversionError("Conversion was cancelled.")

    data class IOError(val detail: String, val ioCause: Throwable? = null) :
        ConversionError("Disk I/O error: $detail", ioCause)

    data class Unknown(val detail: String, val unknownCause: Throwable? = null) :
        ConversionError("Unexpected error occurred: $detail", unknownCause)
}
