package com.pediycc.mydrawer.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class BackupFile(
    val format: String = "mydrawer-backup",
    val version: Int = 1,
    val prefs: UserPrefs,
)

/** 配置备份：把分类覆盖、隐藏列表、排序、外观设置导出为开放 JSON。 */
class BackupManager(context: Context) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    private val appContext = context.applicationContext

    fun export(prefs: UserPrefs): String =
        json.encodeToString(BackupFile(prefs = prefs))

    /** 导入：容错解析，任何字段缺失都回退到默认值。 */
    fun import(raw: String): UserPrefs {
        val parsed = json.decodeFromString<BackupFile>(raw)
        require(parsed.format == "mydrawer-backup") { "不是 MyDrawer 备份文件" }
        return parsed.prefs
    }

    fun fileName(): String =
        "mydrawer-backup-${java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.getDefault()).format(java.util.Date())}.json"
}
