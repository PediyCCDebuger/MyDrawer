package com.pediycc.mydrawer.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
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

    /** 下载结果：成功给出文件，失败给出可展示的中文原因。 */
    sealed interface DownloadResult {
        data class Ok(val file: File) : DownloadResult
        data class Fail(val reason: String) : DownloadResult
    }

    /** 拉起安装器的结果。 */
    enum class InstallResult {
        /** 系统安装界面已成功拉起。 */
        STARTED,

        /** 缺少「安装未知应用」授权，需要先跳到系统设置。 */
        NEED_PERMISSION,

        /** 文件无效 / 无法拉起安装器。 */
        FAILED,
    }

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

    /** 去掉可能的前导 v / V，便于版本比较与展示。 */
    fun normalize(tag: String): String = tag.trim().removePrefix("v").removePrefix("V")

    /** 把 2.3.0 / v2.3.0-beta 之类拆成数字段，非数字段按 0 处理。 */
    private fun versionParts(version: String): List<Int> =
        normalize(version)
            .split('.', '-', '_', '+')
            .map { part -> part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }

    /**
     * 远端 tag 是否比当前版本新。
     *
     * 之前用「字符串不相等」判断，导致远端 2.2.0 与本地 2.2.0 也被当成「发现新版本」。
     * 现在按数字段逐位比较，只有远端更大才算更新。
     */
    fun isNewer(tag: String, current: String): Boolean {
        val remote = versionParts(tag)
        val local = versionParts(current)
        for (i in 0 until maxOf(remote.size, local.size)) {
            val r = remote.getOrElse(i) { 0 }
            val l = local.getOrElse(i) { 0 }
            if (r != l) return r > l
        }
        return false
    }

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
        conn.instanceFollowRedirects = true
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
                if (name.contains("universal", ignoreCase = true) && name.endsWith(".apk", ignoreCase = true)) {
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

    suspend fun download(context: Context, info: ReleaseInfo, proxy: String): DownloadResult =
        withContext(Dispatchers.IO) {
            var out: File? = null
            try {
                val dir = File(context.cacheDir, "update")
                dir.mkdirs()
                // 清掉上一次的残留，避免把旧包当成本次下载结果。
                dir.listFiles()?.forEach { if (it.isFile && it.extension == "apk") it.delete() }
                out = File(dir, if (info.apkName.isBlank()) "MyDrawer-update.apk" else info.apkName)
                val conn = connect(info.apkUrl, proxy, 120000)
                try {
                    if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                        return@withContext DownloadResult.Fail("下载失败（HTTP ${conn.responseCode}），可更换代理后重试")
                    }
                    val total = conn.contentLengthLong
                    conn.inputStream.use { input ->
                        FileOutputStream(out).use { fos -> input.copyTo(fos, 64 * 1024) }
                    }
                    if (total > 0 && out.length() != total) {
                        out.delete()
                        return@withContext DownloadResult.Fail("下载不完整，请重试")
                    }
                } finally {
                    conn.disconnect()
                }
                if (out.length() <= 0) {
                    out.delete()
                    return@withContext DownloadResult.Fail("下载内容为空，可更换代理后重试")
                }
                // 代理出错时常见返回 200 + HTML 错误页，直接交给安装器只会静默失败，
                // 所以先校验是不是一个包名、版本都正确的 APK。
                val reason = invalidReason(context, out)
                if (reason != null) {
                    out.delete()
                    return@withContext DownloadResult.Fail(reason)
                }
                DownloadResult.Ok(out)
            } catch (e: Exception) {
                runCatching { out?.delete() }
                DownloadResult.Fail("下载出错：${e.message ?: "网络异常"}")
            }
        }

    /** 返回 null 表示文件是有效且可升级的 APK；否则返回中文原因。 */
    private fun invalidReason(context: Context, file: File): String? {
        val header = ByteArray(4)
        val read = runCatching {
            file.inputStream().use { it.read(header) }
        }.getOrDefault(0)
        // ZIP 魔数 PK\x03\x04；HTML 错误页 / 代理提示页会在这里被挡住。
        if (read < 4 || header[0] != 0x50.toByte() || header[1] != 0x4B.toByte()) {
            return "下载到的不是安装包（代理可能返回了错误页），请更换代理重试"
        }
        val archive = runCatching {
            context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
        }.getOrNull() ?: return "安装包解析失败，请更换代理重新下载"
        if (archive.packageName != context.packageName) {
            return "下载到的安装包不属于本应用，已取消安装"
        }
        val newCode = PackageInfoCompat.getLongVersionCode(archive)
        val currentCode = runCatching {
            PackageInfoCompat.getLongVersionCode(
                context.packageManager.getPackageInfo(context.packageName, 0),
            )
        }.getOrDefault(0L)
        if (newCode <= currentCode) return "下载到的版本不比当前版本新，已取消安装"
        return null
    }

    fun canInstall(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else true

    /** 跳到「安装未知应用」授权页；返回是否成功拉起。 */
    fun openInstallPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    fun install(context: Context, file: File): InstallResult {
        if (!canInstall(context)) return InstallResult.NEED_PERMISSION
        return try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider",
                file,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                // 个别 ROM 的安装器只认 ClipData 里的授权。
                clipData = android.content.ClipData.newRawUri("apk", uri)
            }
            context.startActivity(intent)
            InstallResult.STARTED
        } catch (e: Exception) {
            InstallResult.FAILED
        }
    }
}
