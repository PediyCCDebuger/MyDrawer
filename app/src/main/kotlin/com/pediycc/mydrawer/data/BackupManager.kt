package com.pediycc.mydrawer.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class BackupFile(
    val format: String = "mydrawer-backup",
    val version: Int = 2,
    val prefs: UserPrefs,
)

/** 配置备份：开放 JSON。v1 备份（只有 5 个字段）仍可导入，新字段自动补默认值。 */
class BackupManager(context: Context) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun export(prefs: UserPrefs): String =
        json.encodeToString(BackupFile(prefs = prefs))

    fun import(raw: String): UserPrefs {
        val parsed = json.decodeFromString<BackupFile>(raw)
        require(parsed.format == "mydrawer-backup") { "不是 MyDrawer 备份文件" }
        return parsed.prefs
    }

    fun fileName(): String =
        "mydrawer-backup-${java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.getDefault()).format(java.util.Date())}.json"
}
