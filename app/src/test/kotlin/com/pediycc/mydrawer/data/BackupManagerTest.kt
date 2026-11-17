package com.pediycc.mydrawer.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupManagerTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `backup round trip preserves prefs`() {
        val prefs = UserPrefs(
            hiddenPackages = setOf("com.foo", "com.bar"),
            categoryOverrides = mapOf("com.foo" to "games"),
            sortMode = SortMode.BY_CATEGORY,
            gridColumns = 5,
            showSystemApps = true,
            categoryLabels = mapOf("games" to "游戏大厅"),
            showAppLabels = false,
            frequentEnabled = false,
            updateProxy = "ghfast.top",
        )
        val backup = BackupFile(prefs = prefs)
        val raw = json.encodeToString(backup)
        val decoded = json.decodeFromString<BackupFile>(raw)
        assertEquals(prefs, decoded.prefs)
    }

    @Test
    fun `missing fields fall back to defaults`() {
        val raw = """{"format":"mydrawer-backup","prefs":{}}"""
        val decoded = json.decodeFromString<BackupFile>(raw)
        assertEquals(UserPrefs(), decoded.prefs)
    }
}
