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

data class BobbyUpdateInfo(val version: String, val downloadUrl: String, val sha256: String?)

class BobbyUpdateManager(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .callTimeout(3, TimeUnit.MINUTES)
        .build()

    fun checkLatest(): BobbyUpdateInfo {
        val request = Request.Builder()
            .url("https://api.github.com/repos/di999666blo-ai/JERVIS_AI/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "GitHub release check failed (HTTP ${response.code})." }
            val json = JSONObject(response.body?.string().orEmpty())
            val assets = json.optJSONArray("assets") ?: error("Release has no APK assets.")
            val asset = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.optString("name") == "BOBBY.apk" }
                ?: error("Latest release does not contain BOBBY.apk.")
            val version = json.optString("tag_name", "latest")
            @Suppress("DEPRECATION")
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val installed = if (Build.VERSION.SDK_INT >= 28) packageInfo.longVersionCode else packageInfo.versionCode.toLong()
            val latestCode = Regex("""(?i)Version code:\s*(\d+)""").find(json.optString("body"))?.groupValues?.get(1)?.toLongOrNull() ?: -1L
            if (latestCode > 0L && latestCode <= installed) error("BOBBY is already up to date.")
            return BobbyUpdateInfo(version, asset.getString("browser_download_url"), asset.optString("digest").takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:"))
        }
    }

    fun downloadAndPromptInstall(update: BobbyUpdateInfo): File {
        val request = Request.Builder().url(update.downloadUrl).build()
        val apk = File(context.cacheDir, "BOBBY-update.apk")
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "APK download failed (HTTP ${response.code})." }
            val body = response.body ?: error("GitHub returned an empty APK.")
            body.byteStream().use { input -> apk.outputStream().use { output -> input.copyTo(output) } }
        }
        check(apk.length() > 1_000_000L) { "Downloaded APK is unexpectedly small." }
        update.sha256?.let { expected ->
            val actual = MessageDigest.getInstance("SHA-256").digest(apk.readBytes()).joinToString("") { "%02x".format(it) }
            check(actual.equals(expected, ignoreCase = true)) { "APK checksum verification failed; installation cancelled." }
        }
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
            val pending = PendingIntent.getBroadcast(
                context, sessionId, callback,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            session.commit(pending.intentSender)
        }
        return apk
    }
}
