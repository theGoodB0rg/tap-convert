package com.tapconvert.core.common.diagnostics

enum class MemoryPressureLevel {
    NORMAL,
    MODERATE,
    CRITICAL
}

data class ConversionTraceRecord(
    val traceId: String,
    val conversionType: String,
    val inputSizeBytes: Long,
    val outputSizeBytes: Long,
    val durationMs: Long,
    val throughputKbps: Long,
    val isSuccess: Boolean,
    val sanitizedError: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class SystemHealthSnapshot(
    val availableMemoryMb: Long,
    val totalMemoryMb: Long,
    val maxHeapMb: Long,
    val usedHeapMb: Long,
    val memoryPressureLevel: MemoryPressureLevel,
    val activeConversionsCount: Int,
    val totalConversionsCompleted: Long,
    val totalConversionsFailed: Long,
    val averageDurationMs: Long,
    val recentTraces: List<ConversionTraceRecord>,
    val cacheSizeBytes: Long,
    val hardwareEncoders: List<String>,
    val timestamp: Long = System.currentTimeMillis()
)
