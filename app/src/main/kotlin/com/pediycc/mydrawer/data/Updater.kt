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
 * 自动更新：查询 GitHub Releases 最新版，下载 APK 并拉起系统安装。
 * 直连 GitHub 不稳定，提供一组反向代理；另外支持本机 HTTP 代理 127.0.0.1:1080。
 */
object Updater {
    private const val REPO = "PediyCCDebuger/MyDrawer"
    private const val API = "https://api.github.com/repos/$REPO/releases/latest"

    const val PROXY_DIRECT = "直连（不使用代理）"
    const val PROXY_LOCAL = "本机代理 127.0.0.1:1080"

    data class ReleaseInfo(val tag: String, val apkUrl: String, val apkName: String, val body: String)

    /** 第一项为直连，第二项为用户本机代理，其后为公共反向代理。 */
    val PROXIES = listOf(
        PROXY_DIRECT,
        PROXY_LOCAL,
        "gh-proxy.org",
        "gh-proxy.com",
        "ghfast.top",
        "gh.acmsz.top",
        "github.chenc.dev",
        "gh.monlor.com",
    )

    fun normalize(tag: String): String = tag.trim().removePrefix("v").removePrefix("V")

    fun isNewer(tag: String, current: String): Boolean = normalize(tag) != normalize(current)

    private fun proxied(url: String, proxy: String): String =
        if (proxy == PROXY_DIRECT || proxy == PROXY_LOCAL) url else "https://$proxy/$url"

    private fun connect(url: String, proxy: String): HttpURLConnection {
        val conn = URL(proxied(url, proxy)).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("User-Agent", "MyDrawer")
        conn.connectTimeout = 15000
        conn.readTimeout = 20000
        return conn
    }

    suspend fun fetchLatest(proxy: String = PROXY_DIRECT): ReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            val conn = connect(API, proxy)
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
                val conn = connect(info.apkUrl, proxy)
                conn.readTimeout = 60000
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
