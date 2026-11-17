package com.pediycc.mydrawer.data

import android.graphics.drawable.Drawable

/** 一个已安装应用的展示模型。 */
data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val isSystem: Boolean,
    val categoryId: String,
)

/** 自动分类规则表中的一条：包名 -> 分类 id。 */
data class CategoryRule(
    val packageName: String,
    val categoryId: String,
)

/** 内置分类定义。id 与原版 fC 值保持一致，便于复用规则表。 */
data class CategoryDef(
    val id: String,
    val label: String,
)

object Categories {
    // 顺序即抽屉中的分类顺序；OTHER 必须放最后作为兜底。
    val ALL = listOf(
        CategoryDef("games", "游戏"),
        CategoryDef("internet", "上网"),
        CategoryDef("media", "影音"),
        CategoryDef("phone", "通讯"),
        CategoryDef("utility", "工具"),
        CategoryDef("settings", "设置"),
        CategoryDef("other", "其他"),
    )
    const val OTHER = "other"

    private val labelById = ALL.associateBy { it.id }

    fun labelOf(id: String): String = labelById[id]?.label ?: "其他"

    fun isValid(id: String): Boolean = labelById.containsKey(id)
}
