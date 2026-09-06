package com.shikeji.reminder.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 应用内更新：启动/手动检查 GitHub Releases 最新版本，
 * 有新版时弹窗（含更新说明），经系统 DownloadManager 下载后拉起安装器。
 * 仓库为公开仓库，无需鉴权；国内网络直连 GitHub 可能较慢或失败，失败会给出提示。
 */
object UpdateChecker {
    private const val RELEASES_API = "https://api.github.com/repos/l1Ha/shikeji/releases/latest"

    data class UpdateInfo(
        val version: String,   // 如 v1.3.0
        val notes: String,     // Release 说明（Markdown 源文本）
        val downloadUrl: String
    )

    var latest by mutableStateOf<UpdateInfo?>(null)
        private set
    var checking by mutableStateOf(false)
        private set
    var checkMessage by mutableStateOf<String?>(null)
        private set
    var dialogVisible by mutableStateOf(false)

    var downloading by mutableStateOf(false)
        private set
    var downloadProgress by mutableStateOf(0) // 0-100
        private set
    var downloadedApk by mutableStateOf<File?>(null)
        private set

    private var downloadId: Long? = null

    fun currentVersionName(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    } catch (e: Exception) {
        "?"
    }

    /** 检查更新；同一次会话内已检查过则直接复用结果 */
    fun check(context: Context) {
        if (checking) return
        checking = true
        checkMessage = null
        val appContext = context.applicationContext
        Thread {
            try {
                val conn = (URL(RELEASES_API).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "shikeji-app")
                }
                val code = conn.responseCode
                if (code == 200) {
                    val body = conn.inputStream.bufferedReader().readText()
                    val json = JSONObject(body)
                    val tag = json.optString("tag_name")
                    val notes = json.optString("body")
                    val assets = json.optJSONArray("assets")
                    var downloadUrl: String? = null
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                                downloadUrl = asset.optString("browser_download_url")
                                break
                            }
                        }
                    }
                    val currentVersion = currentVersionName(appContext)
                    if (downloadUrl == null) {
                        checkMessage = "最新发布缺少安装包"
                    } else if (isNewer(tag, currentVersion)) {
                        latest = UpdateInfo(tag, notes, downloadUrl)
                        dialogVisible = true
                    } else {
                        checkMessage = "已是最新版本 v$currentVersion"
                    }
                } else {
                    checkMessage = "检查失败（HTTP $code）"
                }
                conn.disconnect()
            } catch (e: Exception) {
                checkMessage = "检查失败：网络不可用或无法访问 GitHub"
            } finally {
                checking = false
            }
        }.start()
    }

    /** 经系统 DownloadManager 下载，系统通知栏可见进度 */
    fun startDownload(context: Context) {
        val info = latest ?: return
        val appContext = context.applicationContext
        if (downloading) return

        val request = DownloadManager.Request(Uri.parse(info.downloadUrl))
            .setTitle("时刻计 ${info.version}")
            .setDescription("正在下载更新包")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(
                appContext,
                Environment.DIRECTORY_DOWNLOADS,
                "shikeji-${info.version}.apk"
            )
        val dm = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadId = dm.enqueue(request)
        downloading = true
        downloadProgress = 0
    }

    /** 下载中由 UI 每 500ms 轮询一次，刷新进度并捕获完成/失败 */
    fun pollProgress(context: Context) {
        val id = downloadId ?: return
        val appContext = context.applicationContext
        val dm = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val cursor: Cursor? = dm.query(DownloadManager.Query().setFilterById(id))
        cursor?.use { c ->
            if (c.moveToFirst()) {
                val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                downloadProgress = if (total > 0) ((done * 100) / total).toInt().coerceIn(0, 100) else 0

                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        downloading = false
                        val info = latest
                        if (info != null) {
                            downloadedApk = File(
                                appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                                "shikeji-${info.version}.apk"
                            )
                        }
                    }
                    DownloadManager.STATUS_FAILED -> {
                        downloading = false
                        downloadId = null
                        Toast.makeText(appContext, "下载失败，请重试", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    /** 下载完成后拉起系统安装器；Android 8+ 需要用户先允许「安装未知应用」 */
    fun install(context: Context) {
        val apk = downloadedApk ?: return
        val appContext = context.applicationContext

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !appContext.packageManager.canRequestPackageInstalls()
        ) {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${appContext.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                appContext.startActivity(intent)
                Toast.makeText(appContext, "请允许「安装未知应用」后返回，再点击安装", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(appContext, "请在系统设置中允许本应用安装未知应用", Toast.LENGTH_LONG).show()
            }
            return
        }

        val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(intent)
    }

    /** 语义化版本比较：v1.10.0 > v1.9.0；非法段按 0 处理 */
    private fun isNewer(latest: String, current: String): Boolean {
        val a = parseVersion(latest)
        val b = parseVersion(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun parseVersion(version: String): List<Int> =
        version.trim().removePrefix("v").removePrefix("V")
            .split('.')
            .map { segment -> segment.filter { it.isDigit() }.ifEmpty { "0" }.toInt() }
}
