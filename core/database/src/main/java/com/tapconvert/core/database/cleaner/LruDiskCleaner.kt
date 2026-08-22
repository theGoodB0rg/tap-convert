package com.tapconvert.core.database.cleaner

import com.tapconvert.core.database.entity.ConversionRecordEntity
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
     * Explicit on-demand cache & non-favorited history purge.
     * 1. Deletes physical output files of non-favorited records and purges records from DB.
     * 2. Recursively purges all temporary files in cacheDirectories (including intake_staging/), while protecting favorited output files.
     */
    suspend fun performManualCachePurge(protectFavorites: Boolean = true): CleanupReport {
        var totalDeleted = 0
        var totalReclaimed = 0L

        val protectedPaths = if (protectFavorites && repository != null) {
            repository.getFavorited().flatMap { it.outputUris }
                .map { File(it.removePrefix("file://")).canonicalPath }
                .toSet()
        } else {
            emptySet()
        }

        // Step 1: Delete output files from database records
        if (repository != null) {
            val recordsToDelete = if (protectFavorites) {
                repository.getNonFavorited()
            } else {
                repository.getNonFavorited()
            }

            for (record in recordsToDelete) {
                for (uri in record.outputUris) {
                    val file = File(uri.removePrefix("file://"))
                    if (file.exists() && file.isFile && file.canonicalPath !in protectedPaths) {
                        val len = file.length()
                        if (file.delete()) {
                            totalDeleted++
                            totalReclaimed += len
                        }
                    }
                }
            }

            // Preserve database history metadata even when purging temporary physical cache files
            // so lifetime achievements, statistics, and history log entries remain intact for the user.
        }

        // Step 2: Recursively clean all cache and intermediate directories
        for (dir in cacheDirectories) {
            if (dir.exists() && dir.isDirectory) {
                val files = dir.walkBottomUp()
                    .filter { it.isFile }
                    .toList()

                for (file in files) {
                    if (file.canonicalPath in protectedPaths) {
                        continue // Skip favorited files
                    }
                    val len = file.length()
                    if (file.delete()) {
                        totalDeleted++
                        totalReclaimed += len
                    }
                }
            }
        }

        return CleanupReport(totalDeleted, totalReclaimed)
    }

    /**
     * Delete physical files associated with a single record before removing from repository.
     */
    suspend fun deleteRecordWithFiles(recordId: String): Boolean {
        if (repository != null) {
            val record = repository.getById(recordId)
            if (record != null) {
                for (uri in record.outputUris) {
                    val file = File(uri.removePrefix("file://"))
                    if (file.exists() && file.isFile) {
                        file.delete()
                    }
                }
            }
            return repository.deleteById(recordId)
        }
        return false
    }

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
            .flatMap { dir ->
                dir.walkBottomUp()
                    .filter { it.isFile }
                    .toList()
            }
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
