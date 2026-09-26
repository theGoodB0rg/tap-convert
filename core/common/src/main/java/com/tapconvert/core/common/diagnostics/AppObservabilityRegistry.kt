package com.tapconvert.core.common.diagnostics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class AppObservabilityRegistry(
    private val maxTracesCapacity: Int = 100
) {
    private val activeConversions = AtomicInteger(0)
    private val totalCompleted = AtomicLong(0)
    private val totalFailed = AtomicLong(0)
    private val totalDurationMs = AtomicLong(0)
    private val traces = ConcurrentLinkedDeque<ConversionTraceRecord>()

    private val _healthState = MutableStateFlow(createSnapshot())
    val healthState: StateFlow<SystemHealthSnapshot> = _healthState.asStateFlow()

    fun onConversionStarted(traceId: String): Long {
        activeConversions.incrementAndGet()
        updateSnapshot()
        return System.currentTimeMillis()
    }

    fun onConversionFinished(
        traceId: String,
        conversionType: String,
        inputSizeBytes: Long,
        outputSizeBytes: Long,
        durationMs: Long,
        isSuccess: Boolean,
        errorMessage: String? = null
    ) {
        activeConversions.updateAndGet { current -> (current - 1).coerceAtLeast(0) }
        if (isSuccess) {
            totalCompleted.incrementAndGet()
            totalDurationMs.addAndGet(durationMs)
        } else {
            totalFailed.incrementAndGet()
        }

        val throughput = if (durationMs > 0 && outputSizeBytes > 0) {
            (outputSizeBytes * 1000L) / (durationMs * 1024L) // KB/s
        } else 0L

        val record = ConversionTraceRecord(
            traceId = traceId,
            conversionType = conversionType,
            inputSizeBytes = inputSizeBytes,
            outputSizeBytes = outputSizeBytes,
            durationMs = durationMs,
            throughputKbps = throughput,
            isSuccess = isSuccess,
            sanitizedError = DiagnosticSanitizer.sanitizeMessage(errorMessage)
        )

        traces.addFirst(record)
        while (traces.size > maxTracesCapacity) {
            traces.pollLast()
        }

        updateSnapshot()
    }

    fun resetForTesting() {
        activeConversions.set(0)
        totalCompleted.set(0)
        totalFailed.set(0)
        totalDurationMs.set(0)
        traces.clear()
        updateSnapshot()
    }

    fun clearTraces() {
        traces.clear()
        updateSnapshot()
    }

    fun createSnapshot(): SystemHealthSnapshot {
        val runtime = Runtime.getRuntime()
        val totalMem = runtime.totalMemory() / (1024 * 1024)
        val freeMem = runtime.freeMemory() / (1024 * 1024)
        val maxMem = runtime.maxMemory() / (1024 * 1024)
        val usedMem = totalMem - freeMem

        val pressure = when {
            usedMem >= (maxMem * 0.85f) -> MemoryPressureLevel.CRITICAL
            usedMem >= (maxMem * 0.65f) -> MemoryPressureLevel.MODERATE
            else -> MemoryPressureLevel.NORMAL
        }

        val completed = totalCompleted.get()
        val avgDuration = if (completed > 0) totalDurationMs.get() / completed else 0L

        return SystemHealthSnapshot(
            availableMemoryMb = freeMem,
            totalMemoryMb = totalMem,
            maxHeapMb = maxMem,
            usedHeapMb = usedMem,
            memoryPressureLevel = pressure,
            activeConversionsCount = activeConversions.get().coerceAtLeast(0),
            totalConversionsCompleted = completed,
            totalConversionsFailed = totalFailed.get(),
            averageDurationMs = avgDuration,
            recentTraces = traces.toList(),
            cacheSizeBytes = 0L,
            hardwareEncoders = emptyList()
        )
    }

    private fun updateSnapshot() {
        _healthState.value = createSnapshot()
    }

    fun exportSanitizedReportJson(): String {
        val snap = createSnapshot()
        val tracesJson = snap.recentTraces.take(20).joinToString(separator = ",\n      ") { trace ->
            """{"id":"${trace.traceId}","type":"${trace.conversionType}","in":${trace.inputSizeBytes},"out":${trace.outputSizeBytes},"durationMs":${trace.durationMs},"speedKbps":${trace.throughputKbps},"success":${trace.isSuccess}}"""
        }

        return """
        {
          "system": {
            "usedHeapMb": ${snap.usedHeapMb},
            "maxHeapMb": ${snap.maxHeapMb},
            "pressure": "${snap.memoryPressureLevel}",
            "activeConversions": ${snap.activeConversionsCount}
          },
          "stats": {
            "completed": ${snap.totalConversionsCompleted},
            "failed": ${snap.totalConversionsFailed},
            "avgDurationMs": ${snap.averageDurationMs}
          },
          "recentTraces": [
            $tracesJson
          ]
        }
        """.trimIndent()
    }

    companion object {
        val instance by lazy { AppObservabilityRegistry() }
    }
}
