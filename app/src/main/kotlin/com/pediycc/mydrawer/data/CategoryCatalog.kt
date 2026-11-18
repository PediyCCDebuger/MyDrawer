package com.pediycc.mydrawer.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class RuleEntry(val n: String, val fC: String? = null)

/**
 * 自动分类引擎：加载随 APK 打包的 2000 条包名->分类规则，
 * 首次启动时即为全部已安装应用给出分类。
 */
class CategoryCatalog(context: Context) {
    private val rules: Map<String, String>

    init {
        val raw = context.assets.open("categories.json").bufferedReader().use { it.readText() }
        val parsed = Json { ignoreUnknownKeys = true }.decodeFromString<List<RuleEntry>>(raw)
        rules = parsed.associate { entry ->
            entry.n to (entry.fC?.takeIf { Categories.isValid(it) } ?: Categories.OTHER)
        }
    }

    val size: Int get() = rules.size

    /** 按包名精确匹配；未命中进「未分类」。 */
    fun classify(packageName: String): String = rules[packageName] ?: Categories.OTHER
}
