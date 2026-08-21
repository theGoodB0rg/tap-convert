package com.tapconvert.core.database.cleaner

import com.tapconvert.core.database.repository.ConversionHistoryRepository
import java.io.File

class LruDiskCleaner(
    private val cacheDirectories: List<File>,
    private val repository: ConversionHistoryRepository? = null,
    private val maxAgeMs: Long = DEFAULT_MAX_AGE_MS,
    private val maxStorageBudgetBytes: Long = DEFAULT_MAX_STORAGE_BUDGET_BYTES
) {

    data class CleanupReport(
        val filesDeleted: Int,
        val bytesReclaimed: Long
    )

    /**
     * Evicts files older than maxAgeMs and files exceeding maxStorageBudgetBytes.
     * Records with is_favorited = true are protected from automated deletion.
     */
    suspend fun performFullCleanup(currentTimeMs: Long = System.currentTimeMillis()): CleanupReport {
        var totalDeleted = 0
        var totalReclaimed = 0L

        // Step 1: Evict expired records (> 48h)
        val cutoffTime = currentTimeMs - maxAgeMs
        if (repository != null) {
            val expiredRecords = repository.getExpiredNonFavorited(cutoffTime)
            for (record in expiredRecords) {
                for (uri in record.outputUris) {
                    val file = File(uri.removePrefix("file://"))
                    if (file.exists() && file.isFile) {
                        val len = file.length()
                        if (file.delete()) {
                            totalDeleted++
                            totalReclaimed += len
                        }
                    }
                }
            }
            repository.deleteExpiredNonFavorited(cutoffTime)
        }

        // Step 2: Enforce storage budget on cache directories
        val quotaReport = enforceStorageQuota(maxStorageBudgetBytes)
        totalDeleted += quotaReport.filesDeleted
        totalReclaimed += quotaReport.bytesReclaimed

        return CleanupReport(totalDeleted, totalReclaimed)
    }

    /**
     * Enforces storage budget by deleting the oldest files until usage drops to 80% watermark.
     */
    fun enforceStorageQuota(storageBudgetBytes: Long): CleanupReport {
        var deletedCount = 0
        var reclaimedBytes = 0L

        val allFiles = cacheDirectories
            .filter { it.exists() && it.isDirectory }
            .flatMap { it.listFiles()?.toList().orEmpty() }
            .filter { it.isFile }
            .sortedBy { it.lastModified() } // Oldest first

        var currentTotalSize = allFiles.sumOf { it.length() }
        val targetWatermark = (storageBudgetBytes * 0.8).toLong()

        if (currentTotalSize > storageBudgetBytes) {
            for (file in allFiles) {
                if (currentTotalSize <= targetWatermark) break
                val len = file.length()
                if (file.delete()) {
                    deletedCount++
                    reclaimedBytes += len
                    currentTotalSize -= len
                }
            }
        }

        return CleanupReport(deletedCount, reclaimedBytes)
    }

    companion object {
        const val DEFAULT_MAX_AGE_MS = 48 * 60 * 60 * 1000L // 48 hours
        const val DEFAULT_MAX_STORAGE_BUDGET_BYTES = 500 * 1024 * 1024L // 500 MB
    }
}
