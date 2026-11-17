package com.pediycc.mydrawer.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class UserPrefs(
    val hiddenPackages: Set<String> = emptySet(),
    val categoryOverrides: Map<String, String> = emptyMap(),
    val sortMode: String = SortMode.BY_NAME,
    val gridColumns: Int = 4,
    val showSystemApps: Boolean = false,
)

object SortMode {
    const val BY_NAME = "by_name"
    const val BY_INSTALL_TIME = "by_install_time"
    const val BY_CATEGORY = "by_category"
}

/** 用 SharedPreferences 存储用户设置，键名与原版语义区分，避免冲突。 */
class Preferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("mydrawer_prefs", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    var current: UserPrefs = load()
        private set

    private fun load(): UserPrefs {
        val raw = prefs.getString(KEY_PREFS, null) ?: return UserPrefs()
        return try {
            json.decodeFromString<UserPrefs>(raw)
        } catch (_: Exception) {
            UserPrefs()
        }
    }

    private fun save() {
        prefs.edit().putString(KEY_PREFS, json.encodeToString(current)).apply()
    }

    fun update(transform: (UserPrefs) -> UserPrefs) {
        current = transform(current)
        save()
    }

    fun isHidden(packageName: String): Boolean = packageName in current.hiddenPackages

    fun toggleHidden(packageName: String) {
        update { p ->
            val next = p.hiddenPackages.toMutableSet()
            if (!next.add(packageName)) next.remove(packageName)
            p.copy(hiddenPackages = next)
        }
    }

    fun overrideCategory(packageName: String, categoryId: String) {
        update { p -> p.copy(categoryOverrides = p.categoryOverrides + (packageName to categoryId)) }
    }

    fun replaceAll(prefs: UserPrefs) {
        current = prefs
        save()
    }

    companion object {
        private const val KEY_PREFS = "user_prefs_v1"
    }
}
