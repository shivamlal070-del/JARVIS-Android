package com.jarvis.assistant.core.device

import android.content.Context
import android.os.Environment
import android.os.StatFs
import java.io.File

data class StorageSpaceInfo(
    val totalBytes: Long,
    val freeBytes: Long,
    val usedBytes: Long,
    val totalGb: Double,
    val freeGb: Double,
    val usedGb: Double,
    val usedPercentage: Int,
    val isStorageLow: Boolean
)

/**
 * StorageMonitor queries physical flash partition metrics via StatFs.
 * Does not delete user files automatically; deletion requires explicit confirmation.
 */
class StorageMonitor(private val context: Context) {

    fun getInternalStorageInfo(): StorageSpaceInfo {
        val path: File = Environment.getDataDirectory()
        val stat = StatFs(path.path)
        val blockSize = stat.blockSizeLong
        val totalBlocks = stat.blockCountLong
        val availableBlocks = stat.availableBlocksLong

        val totalBytes = totalBlocks * blockSize
        val freeBytes = availableBlocks * blockSize
        val usedBytes = totalBytes - freeBytes

        val totalGb = totalBytes / (1024.0 * 1024.0 * 1024.0)
        val freeGb = freeBytes / (1024.0 * 1024.0 * 1024.0)
        val usedGb = usedBytes / (1024.0 * 1024.0 * 1024.0)

        val usedPercent = if (totalBytes > 0) ((usedBytes.toDouble() / totalBytes) * 100).toInt() else 0
        val isLow = freeGb < 2.0 // Less than 2 GB remaining

        return StorageSpaceInfo(
            totalBytes = totalBytes,
            freeBytes = freeBytes,
            usedBytes = usedBytes,
            totalGb = Math.round(totalGb * 10.0) / 10.0,
            freeGb = Math.round(freeGb * 10.0) / 10.0,
            usedGb = Math.round(usedGb * 10.0) / 10.0,
            usedPercentage = usedPercent,
            isStorageLow = isLow
        )
    }

    fun getSpokenStorageStatus(): String {
        val info = getInternalStorageInfo()
        return if (info.isStorageLow) {
            "Warning: Storage is almost full. You have only ${info.freeGb} Gigabytes free out of ${info.totalGb} Gigabytes (${info.usedPercentage} percent used)."
        } else {
            "You have ${info.freeGb} Gigabytes available out of ${info.totalGb} Gigabytes total storage (${info.usedPercentage} percent used)."
        }
    }
}
