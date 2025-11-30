package org.nxy.hasstools.utils
/**
 * TODO 这玩意是用AI临时生成的，目前都还没发布版本，不好测试。
 * 先弄一个简单的凑合用 :)
 */

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Request
import org.nxy.hasstools.App
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest

/**
 * GitHub Release 信息
 */
@Suppress("PropertyName")
@Serializable
data class GitHubRelease(
    val tag_name: String,
    val name: String,
    val body: String,
    val html_url: String,
    val assets: List<GitHubAsset>
)

/**
 * GitHub Release Asset 信息
 */
@Suppress("PropertyName")
@Serializable
data class GitHubAsset(
    val name: String,
    val browser_download_url: String,
    val size: Long,
    val content_type: String,
    val digest: String? = null  // 格式: "sha256:xxxx"
)

/**
 * 更新检查结果
 */
sealed class UpdateCheckResult {
    data class UpdateAvailable(
        val currentVersion: String,
        val latestVersion: String,
        val releaseNotes: String,
        val releaseUrl: String,
        val apkAsset: GitHubAsset?
    ) : UpdateCheckResult()
    
    data class UpToDate(val currentVersion: String) : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}

/**
 * 下载进度回调
 */
typealias DownloadProgressCallback = (bytesDownloaded: Long, totalBytes: Long) -> Unit

/**
 * 更新检查器
 * 
 * 用于检查 GitHub 仓库的最新版本并下载更新
 */
object UpdateChecker {
    private const val GITHUB_API_BASE = "https://api.github.com"
    private const val REPO_OWNER = "NXY666"
    private const val REPO_NAME = "HassTools"
    
    const val REPO_URL = "https://github.com/$REPO_OWNER/$REPO_NAME"
    const val RELEASES_URL = "$REPO_URL/releases"
    
    private val json = Json { ignoreUnknownKeys = true }
    
    /**
     * 获取当前应用版本名称
     */
    fun getCurrentVersionName(context: Context): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        } catch (_: Exception) {
            "unknown"
        }
    }
    
    /**
     * 获取当前应用版本号
     */
    fun getCurrentVersionCode(context: Context): Long {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
        } catch (_: Exception) {
            0L
        }
    }
    
    /**
     * 检查更新
     * 
     * @return 更新检查结果
     */
    fun checkForUpdate(): UpdateCheckResult {
        val currentVersion = getCurrentVersionName(App.context)
        
        return try {
            val latestRelease = fetchLatestRelease() ?: return UpdateCheckResult.Error("无法获取最新版本信息。")

            // 移除版本号前面的 'v' 前缀（如果有的话）
            val latestVersion = latestRelease.tag_name.removePrefix("v")
            
            if (isNewerVersion(currentVersion, latestVersion)) {
                // 查找 APK 文件
                val apkAsset = latestRelease.assets.find { 
                    it.name.endsWith(".apk") && it.content_type == "application/vnd.android.package-archive"
                }
                
                UpdateCheckResult.UpdateAvailable(
                    currentVersion = currentVersion,
                    latestVersion = latestVersion,
                    releaseNotes = latestRelease.body,
                    releaseUrl = latestRelease.html_url,
                    apkAsset = apkAsset
                )
            } else {
                UpdateCheckResult.UpToDate(currentVersion)
            }
        } catch (e: IOException) {
            UpdateCheckResult.Error("网络错误(${e.message})。")
        } catch (e: Exception) {
            UpdateCheckResult.Error("未知错误(${e.message})。")
        }
    }
    
    /**
     * 从 GitHub API 获取最新 release
     */
    private fun fetchLatestRelease(): GitHubRelease? {
        val url = "$GITHUB_API_BASE/repos/$REPO_OWNER/$REPO_NAME/releases/latest"
        
        val request = Request.Builder()
            .url(url)
            .addHeader("Accept", "application/vnd.github.v3+json")
            .addHeader("User-Agent", "HassTools-Android")
            .get()
            .build()
        
        NetworkMonitor.getHttpClient().newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                println("获取最新版本失败: ${response.code}")
                return null
            }
            
            val body = response.body.string()
            return json.decodeFromString<GitHubRelease>(body)
        }
    }
    
    /**
     * 比较版本号，判断是否有新版本
     * 
     * @param current 当前版本
     * @param latest 最新版本
     * @return 如果最新版本更新则返回 true
     */
    private fun isNewerVersion(current: String, latest: String): Boolean {
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
        val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }
        
        val maxLength = maxOf(currentParts.size, latestParts.size)
        
        for (i in 0 until maxLength) {
            val currentPart = currentParts.getOrElse(i) { 0 }
            val latestPart = latestParts.getOrElse(i) { 0 }
            
            when {
                latestPart > currentPart -> return true
                latestPart < currentPart -> return false
            }
        }
        
        return false // 版本相同
    }
    
    /**
     * 计算文件的 SHA256 哈希值
     * 
     * @param file 要计算哈希的文件
     * @return SHA256 哈希值（小写十六进制字符串）
     */
    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    
    /**
     * 验证文件的 digest 是否匹配
     * 
     * @param file 要验证的文件
     * @param expectedDigest 期望的 digest（格式: "sha256:xxxx"）
     * @return 是否匹配
     */
    private fun verifyDigest(file: File, expectedDigest: String?): Boolean {
        if (expectedDigest == null) {
            // 如果没有提供 digest，跳过验证
            println("警告: 未提供 digest，跳过验证")
            return true
        }
        
        val parts = expectedDigest.split(":")
        if (parts.size != 2 || parts[0] != "sha256") {
            println("警告: 不支持的 digest 格式: $expectedDigest")
            return true
        }
        
        val expectedHash = parts[1].lowercase()
        val actualHash = calculateSha256(file)
        
        return if (expectedHash == actualHash) {
            println("digest 验证通过")
            true
        } else {
            println("digest 验证失败: 期望 $expectedHash, 实际 $actualHash")
            false
        }
    }
    
    /**
     * 清理旧的 APK 文件
     * 
     * 删除缓存目录中的所有 APK 文件
     */
    private fun cleanupOldApks() {
        val cacheDir = App.context.cacheDir
        cacheDir.listFiles { file ->
            file.isFile && file.name.endsWith(".apk")
        }?.forEach { file ->
            try {
                file.delete()
                println("已清理旧 APK: ${file.name}")
            } catch (e: Exception) {
                println("清理旧 APK 失败: ${file.name}, ${e.message}")
            }
        }
    }
    
    /**
     * APK 下载结果
     */
    sealed class DownloadResult {
        data class Success(val file: File) : DownloadResult()
        data class DigestMismatch(val message: String) : DownloadResult()
        data class Failed(val message: String) : DownloadResult()
    }
    
    /**
     * 下载 APK 文件
     * 
     * @param asset APK 资源信息
     * @param progressCallback 下载进度回调
     * @return 下载结果
     */
    fun downloadApk(
        asset: GitHubAsset,
        progressCallback: DownloadProgressCallback? = null
    ): DownloadResult {
        // 使用应用 cache 目录
        val downloadDir = App.context.cacheDir
        
        if (!downloadDir.exists()) {
            downloadDir.mkdirs()
        }
        
        val outputFile = File(downloadDir, "update.apk")
        
        // 如果文件已存在，验证 digest
        if (outputFile.exists()) {
            if (verifyDigest(outputFile, asset.digest)) {
                return DownloadResult.Success(outputFile)
            } else {
                // digest 不匹配，删除文件重新下载
                println("已缓存的文件 digest 不匹配，重新下载")
                outputFile.delete()
            }
        }
        
        // 下载前清理所有旧的 APK 文件
        cleanupOldApks()
        
        val request = Request.Builder()
            .url(asset.browser_download_url)
            .addHeader("User-Agent", "HassTools-Android")
            .get()
            .build()
        
        try {
            NetworkMonitor.getHttpClient().newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    println("下载 APK 失败: ${response.code}")
                    return DownloadResult.Failed("下载失败: HTTP ${response.code}")
                }
                
                val body = response.body
                val totalBytes = body.contentLength()
                
                FileOutputStream(outputFile).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(8192)
                        var bytesDownloaded = 0L
                        var bytesRead: Int
                        
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            bytesDownloaded += bytesRead
                            progressCallback?.invoke(bytesDownloaded, totalBytes)
                        }
                    }
                }
                
                // 下载完成后验证 digest
                if (!verifyDigest(outputFile, asset.digest)) {
                    outputFile.delete()
                    return DownloadResult.DigestMismatch("文件校验失败，安装包可能已损坏")
                }
                
                return DownloadResult.Success(outputFile)
            }
        } catch (e: Exception) {
            println("下载 APK 失败: ${e.message}")
            outputFile.delete()
            return DownloadResult.Failed("下载失败: ${e.message}")
        }
    }
    
    /**
     * 安装 APK 文件
     * 
     * @param context 上下文
     * @param apkFile APK 文件
     */
    fun installApk(context: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        context.startActivity(intent)
    }
    
    /**
     * 在浏览器中打开 Release 页面
     */
    fun openReleasesPage(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW, RELEASES_URL.toUri()).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
    
    /**
     * 在浏览器中打开仓库页面
     */
    fun openRepoPage(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW, REPO_URL.toUri()).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
