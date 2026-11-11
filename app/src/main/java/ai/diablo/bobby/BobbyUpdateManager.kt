package ai.diablo.bobby

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class BobbyUpdateInfo(
    val version: String,
    val versionCode: Long,
    val downloadUrl: String,
    val checksumUrl: String?,
    val sha256: String?
)

class BobbyUpdateManager(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .callTimeout(3, TimeUnit.MINUTES)
        .build()

    /** Checks the latest published stable release, not an arbitrary Actions artifact. */
    fun checkLatest(): BobbyUpdateInfo {
        val request = Request.Builder()
            .url("https://api.github.com/repos/di999666blo-ai/JERVIS_AI/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "GitHub release check failed (HTTP ${response.code})." }
            val json = JSONObject(response.body?.string().orEmpty())
            val assets = json.optJSONArray("assets") ?: error("The latest stable release has no assets.")
            val rows = (0 until assets.length()).map { assets.getJSONObject(it) }
            val apk = rows.firstOrNull { it.optString("name") == "BOBBY.apk" }
                ?: error("No stable BOBBY.apk is published yet. The signed release build may still be running.")
            val checksum = rows.firstOrNull { it.optString("name") == "BOBBY.apk.sha256" }
            val versionCode = Regex("""(?i)Version code:\s*(\d+)""")
                .find(json.optString("body"))?.groupValues?.get(1)?.toLongOrNull()
                ?: error("Release is missing its build version. It cannot be safely compared with this installation.")
            @Suppress("DEPRECATION")
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val installed = if (Build.VERSION.SDK_INT >= 28) packageInfo.longVersionCode else packageInfo.versionCode.toLong()
            check(versionCode > installed) { "BOBBY is already up to date (installed build $installed, latest build $versionCode)." }
            val downloadUrl = apk.optString("browser_download_url")
            check(downloadUrl.startsWith("https://github.com/di999666blo-ai/JERVIS_AI/releases/download/")) {
                "The release APK is not hosted at the official BOBBY GitHub release location."
            }
            return BobbyUpdateInfo(
                version = "${json.optString("tag_name", "latest")} • build $versionCode",
                versionCode = versionCode,
                downloadUrl = downloadUrl,
                checksumUrl = checksum?.optString("browser_download_url")?.takeIf { it.startsWith("https://github.com/di999666blo-ai/JERVIS_AI/releases/download/") },
                sha256 = apk.optString("digest").takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:")
            )
        }
    }

    /** Downloads, verifies the SHA-256, then invokes Android's protected package installer. */
    fun downloadAndPromptInstall(update: BobbyUpdateInfo): File {
        val apk = File(context.cacheDir, "BOBBY-update.apk")
        client.newCall(Request.Builder().url(update.downloadUrl).build()).execute().use { response ->
            check(response.isSuccessful) { "APK download failed (HTTP ${response.code})." }
            val body = response.body ?: error("GitHub returned an empty APK.")
            body.byteStream().use { input -> apk.outputStream().use { output -> input.copyTo(output) } }
        }
        check(apk.length() > 1_000_000L) { "Downloaded APK is unexpectedly small." }

        val expected = update.sha256 ?: update.checksumUrl?.let { url ->
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                check(response.isSuccessful) { "Checksum download failed (HTTP ${response.code})." }
                Regex("""(?i)\b([a-f0-9]{64})\b""")
                    .find(response.body?.string().orEmpty())?.groupValues?.get(1)
                    ?: error("The published checksum file is invalid.")
            }
        } ?: error("This release has no SHA-256 checksum. Installation cancelled.")
        val actual = MessageDigest.getInstance("SHA-256").digest(apk.readBytes())
            .joinToString("") { "%02x".format(it) }
        check(actual.equals(expected, ignoreCase = true)) { "APK checksum verification failed; installation cancelled." }

        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("bobby.apk", 0, apk.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }
            val callback = Intent(context, BobbyInstallResultReceiver::class.java)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pending = PendingIntent.getBroadcast(context, sessionId, callback, flags)
            session.commit(pending.intentSender)
        }
        return apk
    }
}
