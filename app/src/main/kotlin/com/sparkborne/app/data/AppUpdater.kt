package com.sparkborne.app.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.sparkborne.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

@Serializable
private data class GitHubReleaseDto(
    @SerialName("tag_name")
    val tagName: String = "",
    val name: String = "",
    val assets: List<GitHubAssetDto> = emptyList(),
)

@Serializable
private data class GitHubAssetDto(
    val name: String = "",
    @SerialName("browser_download_url")
    val downloadUrl: String = "",
)

data class AppUpdateInfo(
    val tagName: String,
    val buildNumber: Int,
    val apkUrl: String,
    val apkFileName: String,
)

/**
 * Checks GitHub Releases for a newer Sparkborne build, downloads the APK into cache,
 * and launches Android's in-place package installer via [FileProvider].
 */
class AppUpdater(
    private val context: Context,
    private val repoSlug: String = "Flexingg/Sparkborne",
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun checkForUpdate(): Result<AppUpdateInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("https://api.github.com/repos/$repoSlug/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Sparkborne-Android/${BuildConfig.VERSION_NAME}")
                .get()
                .build()

            val body = client.newCall(request).execute().use { res ->
                if (!res.isSuccessful) {
                    throw IllegalStateException("GitHub API HTTP ${res.code}")
                }
                res.body?.string().orEmpty()
            }

            val release = json.decodeFromString<GitHubReleaseDto>(body)
            val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                ?: return@runCatching null

            val remoteBuildNumber = extractBuildNumber(release.tagName) ?: return@runCatching null
            if (remoteBuildNumber > BuildConfig.VERSION_CODE) {
                AppUpdateInfo(
                    tagName = release.tagName,
                    buildNumber = remoteBuildNumber,
                    apkUrl = apkAsset.downloadUrl,
                    apkFileName = apkAsset.name,
                )
            } else {
                null
            }
        }
    }

    suspend fun downloadAndInstall(
        update: AppUpdateInfo,
        onProgress: (Float) -> Unit,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            updatesDir.listFiles()?.forEach { it.delete() }
            val targetFile = File(updatesDir, update.apkFileName)

            val request = Request.Builder()
                .url(update.apkUrl)
                .header("User-Agent", "Sparkborne-Android/${BuildConfig.VERSION_NAME}")
                .get()
                .build()

            client.newCall(request).execute().use { res ->
                if (!res.isSuccessful) {
                    throw IllegalStateException("APK download failed (HTTP ${res.code})")
                }
                val body = res.body ?: throw IllegalStateException("Empty APK response body")
                val totalBytes = body.contentLength().coerceAtLeast(1L)
                body.byteStream().use { input ->
                    FileOutputStream(targetFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var copied = 0L
                        var read = input.read(buffer)
                        while (read >= 0) {
                            output.write(buffer, 0, read)
                            copied += read
                            onProgress((copied.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f))
                            read = input.read(buffer)
                        }
                    }
                }
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                targetFile,
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(installIntent)
        }
    }

    private fun extractBuildNumber(tag: String): Int? {
        // Parses tags like "v0.1.3" -> 3
        return tag.trim().substringAfterLast('.', "").toIntOrNull()
    }
}
