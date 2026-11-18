package com.pediycc.mydrawer.data

import java.security.MessageDigest

/**
 * 使用密码（替代原「使用 PIN」）：管理隐藏应用前的口令校验。
 * 只保存 SHA-256 摘要，备份/配置里不出现明文口令。
 * 这是本机的应用锁，不是加密，摘要加一层固定盐可避免明文与常见弱口令直接暴露。
 */
object PasswordHash {
    private const val SALT = "mydrawer-hidden-apps-v1"

    fun hash(raw: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest((SALT + raw).toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun verify(raw: String, storedHash: String): Boolean =
        storedHash.isNotBlank() && hash(raw) == storedHash
}
