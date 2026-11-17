package com.pediycc.mydrawer.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 自动更新：直接查询 GitHub Releases 最新版，下载 APK 并拉起系统安装。
 *
 * 检测始终优先直连 api.github.com；下载因为 github.com 直连不稳，可选公共反向代理，
 * 格式为 `https://<proxy>/<原始直连链接>`（把完整 browser_download_url 拼到代理域名后）。
 * 注意：这里不内置任何本机代理（127.0.0.1:1080 之类）——那是开发环境访问 GitHub 用的，
 * 普通用户手机上并不存在。
 */
object Updater {
    private const val REPO = "PediyCCDebuger/MyDrawer"
    private const val API = "https://api.github.com/repos/$REPO/releases/latest"

    const val PROXY_DIRECT = "直连（不使用代理）"

    data class ReleaseInfo(val tag: String, val apkUrl: String, val apkName: String, val body: String)

    /** 第一项为直连，其后为可选公共反向代理。 */
    val PROXIES = listOf(
        PROXY_DIRECT,
        "gh-proxy.org",
        "gh-proxy.com",
        "ghfast.top",
        "gh.acmsz.top",
        "github.chenc.dev",
        "gh.monlor.com",
    )

    /** 去掉可能的前导 v / V，便于版本比较。 */
    fun normalize(tag: String): String = tag.trim().removePrefix("v").removePrefix("V")

    /** 远端 tag 是否与当前版本不同（即存在更新）。 */
    fun isNewer(tag: String, current: String): Boolean = normalize(tag) != normalize(current)

    /**
     * 拼代理地址。只有明确在 [PROXIES] 里的公共代理才加前缀；
     * 直连、空值以及旧版本遗留的未知代理（例如本机 127.0.0.1:1080）一律按直连处理，
     * 避免拼出非法 URL。
     */
    private fun proxied(url: String, proxy: String): String =
        if (proxy == PROXY_DIRECT || proxy.isBlank() || proxy !in PROXIES) url else "https://$proxy/$url"

    private fun connect(url: String, proxy: String, readTimeout: Int): HttpURLConnection {
        val conn = URL(proxied(url, proxy)).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("User-Agent", "MyDrawer")
        conn.connectTimeout = 15000
        conn.readTimeout = readTimeout
        return conn
    }

    suspend fun fetchLatest(proxy: String = PROXY_DIRECT): ReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            val conn = connect(API, proxy, 15000)
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                conn.disconnect()
                return@withContext null
            }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val json = JSONObject(text)
            val tag = json.optString("tag_name", "")
            val body = json.optString("body", "")
            val assets = json.optJSONArray("assets") ?: return@withContext null
            var apkUrl = ""
            var apkName = ""
            // 优先 universal 包，保证任何设备都能装。
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                val name = a.optString("name", "")
                if (name.contains("universal") && name.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = a.optString("browser_download_url", "")
                    apkName = name
                    break
                }
            }
            if (apkUrl.isBlank()) {
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    val name = a.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = a.optString("browser_download_url", "")
                        apkName = name
                        break
                    }
                }
            }
            if (tag.isBlank() || apkUrl.isBlank()) null else ReleaseInfo(tag, apkUrl, apkName, body)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun download(context: Context, info: ReleaseInfo, proxy: String): File? =
        withContext(Dispatchers.IO) {
            try {
                val dir = File(context.cacheDir, "update")
                dir.mkdirs()
                val out = File(dir, if (info.apkName.isBlank()) "MyDrawer-update.apk" else info.apkName)
                val conn = connect(info.apkUrl, proxy, 60000)
                if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                    conn.disconnect()
                    return@withContext null
                }
                conn.inputStream.use { input -> FileOutputStream(out).use { it.write(input.readBytes()) } }
                conn.disconnect()
                if (out.length() > 0) out else null
            } catch (e: Exception) {
                null
            }
        }

    fun install(context: Context, file: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
        }
    }
}
