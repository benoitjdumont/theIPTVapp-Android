package com.studiogyro.theiptvapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import okhttp3.Request
import org.json.JSONObject
import java.io.File

/** In-app update: looks for a newer GitHub release, downloads the APK and hands it to the Android installer. */
object Updater {
    const val REPO = "benoitjdumont/theIPTVapp-Android"

    data class Update(val code: Int, val url: String)

    /** Returns the newer version, or null when this one is the latest. Throws with a readable message on failure. */
    fun check(): Update? {
        val req = Request.Builder()
            .url("https://api.github.com/repos/$REPO/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", Repo.LIST_UA)
            .build()
        Repo.client.newCall(req).execute().use { r ->
            if (r.code == 404) throw RuntimeException("aucune version publique trouvée (le dépôt est peut-être privé)")
            if (!r.isSuccessful) throw RuntimeException("HTTP ${r.code}")
            val o = JSONObject(r.body?.string() ?: return null)
            val code = o.optString("tag_name").trim().removePrefix("v").toIntOrNull() ?: return null
            val assets = o.optJSONArray("assets") ?: return null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk")) {
                    return if (code > BuildConfig.VERSION_CODE) Update(code, a.optString("browser_download_url")) else null
                }
            }
        }
        return null
    }

    fun download(ctx: Context, url: String, onProgress: (Int) -> Unit): File {
        val dir = File(ctx.cacheDir, "update").apply { mkdirs() }
        val out = File(dir, "theIPTVapp.apk")
        val req = Request.Builder().url(url).header("User-Agent", Repo.LIST_UA).build()
        Repo.client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw RuntimeException("HTTP ${r.code}")
            val body = r.body ?: throw RuntimeException("réponse vide")
            val total = body.contentLength()
            var done = 0L
            body.byteStream().use { input ->
                out.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        o.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress((done * 100 / total).toInt())
                    }
                }
            }
        }
        return out
    }

    /** Opens the Android installer. The first time, Android asks to allow installs from this app. */
    fun install(ctx: Context, apk: File) {
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apk)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
