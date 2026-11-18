package com.pediycc.mydrawer.data

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings

/**
 * 读取系统「使用情况访问权限」，得到本机真实的应用使用频率。
 * 原版 Smart Drawer 的「最常用」来自自身启动计数；这里改为系统层面统计，
 * 这样即使应用不是从 MyDrawer 启动的，也会计入常用。
 */
object UsageStatsHelper {
    /** 应用内保存的启动次数只是没授权时的兜底。 */
    const val FALLBACK_WEIGHT = 1

    fun hasPermission(context: Context): Boolean {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** 打开系统的「Usage access / 使用情况访问权限」设置页。 */
    fun openPermissionSettings(context: Context): Boolean = runCatching {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    }.getOrDefault(false)

    /**
     * 返回最近 [days] 天每个包的使用权重：前台分钟数 + 活跃天数。
     * 未授权或系统没有统计数据时返回空表，调用方应回落到应用内启动计数。
     */
    fun load(context: Context, days: Int = 30): Map<String, Int> {
        if (!hasPermission(context)) return emptyMap()
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyMap()
        val end = System.currentTimeMillis()
        val start = end - days.toLong() * 24L * 60L * 60L * 1000L
        val stats = runCatching {
            manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
        }.getOrNull() ?: return emptyMap()
        if (stats.isEmpty()) return emptyMap()
        return stats
            .groupBy { it.packageName }
            .mapValues { (_, entries) ->
                val minutes = entries.sumOf { it.totalTimeInForeground.coerceAtLeast(0L) } / 60_000L
                // 前台分钟数为主，活跃天数作为次要权重（getLaunchCount 是隐藏 API，不用）。
                val activeDays = entries.count { it.totalTimeInForeground > 0L }.toLong()
                (minutes + activeDays).coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
            }
            .filterKeys { it.isNotBlank() }
    }
}
