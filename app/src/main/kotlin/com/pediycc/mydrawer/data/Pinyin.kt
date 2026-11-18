package com.pediycc.mydrawer.data

import android.icu.text.Transliterator
import java.text.Normalizer

/**
 * 中文 → 拼音（带空格的小写拉丁字母），供搜索用。
 * 用 Android 自带的 ICU Transliterator（Han-Latin），无需第三方依赖、无需联网。
 * 结果去除声调符号，例如「微信」→ "wei xin"。
 */
object Pinyin {
    // 先转拼音，再 NFD 分解把带调韵母（ā/á/ǎ/à）拆成基字母+组合记号，最后去掉组合记号。
    private val hanToLatin: Transliterator? = runCatching {
        Transliterator.getInstance("Han-Latin")
    }.getOrNull()

    /** 转换失败（极老设备缺 ICU 数据）时返回空串，搜索退化为仅原文匹配，不影响其它功能。 */
    fun romanize(text: String): String {
        val t = hanToLatin ?: return ""
        return runCatching {
            val latin = t.transliterate(text)
            Normalizer.normalize(latin, Normalizer.Form.NFD)
                .replace(Regex("\\p{M}"), "") // 去掉声调等组合记号
                .lowercase()
        }.getOrDefault("")
    }
}
