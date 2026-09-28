package com.tapconvert.core.model

import java.util.Locale

data class TargetSize(
    val bytes: Long,
    val tolerancePercent: Double = 0.02,
    /** Optional absolute tolerance for services with fixed upload-size slack. */
    val toleranceBytes: Long = 0L
) : Comparable<TargetSize> {

    init {
        require(bytes > 0) { "Target size bytes must be strictly positive, was $bytes" }
        require(tolerancePercent in 0.0..0.5) { "Tolerance percent must be between 0.0 and 0.5 (0% to 50%), was $tolerancePercent" }
        require(toleranceBytes >= 0L) { "Tolerance bytes must not be negative, was $toleranceBytes" }
    }

    private val effectiveToleranceBytes: Long
        get() = maxOf((bytes * tolerancePercent).toLong(), toleranceBytes)

    val maxAllowedBytes: Long
        get() = bytes + effectiveToleranceBytes

    val minAllowedBytes: Long
        get() = (bytes - effectiveToleranceBytes).coerceAtLeast(0L)

    fun isWithinTolerance(actualBytes: Long): Boolean {
        return actualBytes in minAllowedBytes..maxAllowedBytes
    }

    fun isUnderLimit(actualBytes: Long): Boolean {
        return actualBytes <= bytes
    }

    fun formatted(): String {
        val b = bytes.toDouble()
        val kb = b / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0

        return when {
            gb >= 1.0 -> String.format(Locale.US, "%.1f GB", gb)
            mb >= 1.0 -> {
                if (mb == mb.toLong().toDouble()) {
                    String.format(Locale.US, "%d MB", mb.toLong())
                } else {
                    String.format(Locale.US, "%.1f MB", mb)
                }
            }
            kb >= 1.0 -> {
                if (kb == kb.toLong().toDouble()) {
                    String.format(Locale.US, "%d KB", kb.toLong())
                } else {
                    String.format(Locale.US, "%.1f KB", kb)
                }
            }
            else -> "$bytes B"
        }
    }

    override fun compareTo(other: TargetSize): Int = bytes.compareTo(other.bytes)

    companion object {
        const val DEFAULT_VIDEO_TOLERANCE_BYTES: Long = 768L * 1024L

        fun fromBytes(bytes: Long, tolerancePercent: Double = 0.02, toleranceBytes: Long = 0L): TargetSize =
            TargetSize(bytes, tolerancePercent, toleranceBytes)

        fun fromKilobytes(kb: Long, tolerancePercent: Double = 0.02, toleranceBytes: Long = 0L): TargetSize =
            TargetSize(kb * 1024L, tolerancePercent, toleranceBytes)

        fun fromMegabytes(mb: Long, tolerancePercent: Double = 0.02, toleranceBytes: Long = 0L): TargetSize =
            TargetSize(mb * 1024L * 1024L, tolerancePercent, toleranceBytes)

        fun fromGigabytes(gb: Long, tolerancePercent: Double = 0.02, toleranceBytes: Long = 0L): TargetSize =
            TargetSize(gb * 1024L * 1024L * 1024L, tolerancePercent, toleranceBytes)
    }
}
