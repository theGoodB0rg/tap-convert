package com.tapconvert.core.model

import java.util.Locale

data class TargetSize(
    val bytes: Long,
    val tolerancePercent: Double = 0.02
) : Comparable<TargetSize> {

    init {
        require(bytes > 0) { "Target size bytes must be strictly positive, was $bytes" }
        require(tolerancePercent in 0.0..0.5) { "Tolerance percent must be between 0.0 and 0.5 (0% to 50%), was $tolerancePercent" }
    }

    val maxAllowedBytes: Long
        get() = (bytes * (1.0 + tolerancePercent)).toLong()

    val minAllowedBytes: Long
        get() = (bytes * (1.0 - tolerancePercent)).toLong()

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
        fun fromBytes(bytes: Long, tolerancePercent: Double = 0.02): TargetSize =
            TargetSize(bytes, tolerancePercent)

        fun fromKilobytes(kb: Long, tolerancePercent: Double = 0.02): TargetSize =
            TargetSize(kb * 1024L, tolerancePercent)

        fun fromMegabytes(mb: Long, tolerancePercent: Double = 0.02): TargetSize =
            TargetSize(mb * 1024L * 1024L, tolerancePercent)

        fun fromGigabytes(gb: Long, tolerancePercent: Double = 0.02): TargetSize =
            TargetSize(gb * 1024L * 1024L * 1024L, tolerancePercent)
    }
}
