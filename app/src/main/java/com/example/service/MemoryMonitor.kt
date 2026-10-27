package com.example.service

import android.app.ActivityManager
import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

data class DeviceMemorySnapshot(
    val totalRamMB: Int,
    val availRamMB: Int,
    val usedRamMB: Int,
    val thresholdMB: Int,
    val isLowMemory: Boolean,
    val heapAllocatedMB: Int,
    val heapMaxMB: Int
)

class MemoryMonitor(private val context: Context) {

    private val activityManager =
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    fun getSnapshot(): DeviceMemorySnapshot {
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)

        val totalMB = (memInfo.totalMem / (1024 * 1024)).toInt().let { if (it > 0) it else 4096 }
        val availMB = (memInfo.availMem / (1024 * 1024)).toInt().let { if (it > 0) it else 2560 }
        val usedMB = (totalMB - availMB).coerceAtLeast(0)
        val thresholdMB = (memInfo.threshold / (1024 * 1024)).toInt().let { if (it > 0) it else 512 }

        val runtime = Runtime.getRuntime()
        val heapAllocatedMB = ((runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)).toInt()
        val heapMaxMB = (runtime.maxMemory() / (1024 * 1024)).toInt().coerceAtLeast(256)

        return DeviceMemorySnapshot(
            totalRamMB = totalMB,
            availRamMB = availMB,
            usedRamMB = usedMB,
            thresholdMB = thresholdMB,
            isLowMemory = memInfo.lowMemory,
            heapAllocatedMB = heapAllocatedMB,
            heapMaxMB = heapMaxMB
        )
    }

    fun pollMemoryFlow(intervalMs: Long = 1000L): Flow<DeviceMemorySnapshot> = flow {
        while (true) {
            emit(getSnapshot())
            delay(intervalMs)
        }
    }
}
