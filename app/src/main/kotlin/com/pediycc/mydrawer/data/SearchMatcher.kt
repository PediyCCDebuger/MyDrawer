package com.pediycc.mydrawer.data

/**
 * 应用搜索匹配逻辑（纯 Kotlin，便于单元测试）。
 *
 * 规则：
 * 1. 含中文的查询词：按原文子串匹配（中文用户习惯直接输片段）。
 * 2. 纯英文 / 数字的查询词：按「单词前缀」匹配，而不是任意子串 ——
 *    旧版用 contains，导致输入 ser 时把 User / Server / com.xxx.service 之类
 *    一堆无关项全翻出来。现在只有某个单词（或包名某一段）以查询词开头才算命中。
 * 3. 拼音：支持全拼与首字母（如「微信」→ weixin / wx），由 [romanize] 提供转换，
 *    平台层用 Android ICU，单元测试注入假实现，互不依赖。
 */
object SearchMatcher {

    /** 判断某个应用是否命中搜索词。[romanize] 把中文转成带空格的小写拼音，如 "wei xin"。 */
    fun matches(
        label: String,
        packageName: String,
        query: String,
        romanize: (String) -> String,
    ): Boolean {
        val raw = query.trim()
        if (raw.isEmpty()) return true
        val q = raw.lowercase()

        // 查询词只含 ASCII 字母/数字/空格 → 走「前缀」匹配
        val isLatin = q.all { it.code < 128 }
        if (!isLatin && label.contains(raw, ignoreCase = true)) return true

        if (isLatin && matchesLatin(label, packageName, q)) return true

        // 拼音全拼 + 首字母
        val spaced = romanize(label).lowercase().replace(Regex("[^a-z0-9 ]+"), " ").trim()
        if (spaced.isNotEmpty()) {
            val compact = spaced.replace(" ", "")
            if (compact.contains(q) || spaced.contains(q)) return true
            val initials = spaced.split(Regex("\\s+")).mapNotNull { it.firstOrNull() }.joinToString("")
            if (initials.isNotEmpty() && (initials.startsWith(q) || (q.length > 1 && initials.contains(q)))) {
                return true
            }
        }
        return false
    }

    /** 英文/数字：每个单词（以及包名每一段）必须以查询词开头，避免子串误命中。 */
    private fun matchesLatin(label: String, packageName: String, q: String): Boolean {
        val words = label.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
        if (words.any { it.startsWith(q) }) return true
        val pkg = packageName.lowercase()
        if (pkg.startsWith(q)) return true
        return pkg.split('.').filter { it.isNotEmpty() }.any { it.startsWith(q) }
    }
}
