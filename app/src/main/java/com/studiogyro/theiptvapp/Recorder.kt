package com.studiogyro.theiptvapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import okhttp3.Request
import java.io.File
import java.io.OutputStream
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Keeps the process alive while a recording runs in the background. */
class RecordService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    /** enregistrement programmé : écrit le flux jusqu'à l'heure de fin, puis s'arrête */
    private fun runScheduled(i: Intent) {
        val id = i.getStringExtra("id") ?: return
        val url = i.getStringExtra("url") ?: return
        val name = i.getStringExtra("ch") ?: "chaine"
        val title = i.getStringExtra("t").orEmpty()
        val stop = i.getLongExtra("e", 0L)
        val safe = (title.ifEmpty { name }).replace(Regex("[^A-Za-z0-9À-ÿ _-]"), "").trim().ifEmpty { "emission" }.replace(' ', '_')
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        val ext = Regex("\\.(mp4|mkv|avi|mov|ts)(\\?|$)", RegexOption.IGNORE_CASE).find(url)?.groupValues?.get(1)?.lowercase() ?: "ts"
        val f = File(Recorder.dir(this), "${safe}_$stamp.$ext")
        val ch = Channel(id = id, name = name, url = url, alt = listOf(url))
        scope.launch {
            val job = launch(Dispatchers.IO) { runCatching { Recorder.record(ch, f) { } } }
            val left = stop - System.currentTimeMillis()
            if (left > 0) delay(left)
            Recorder.cancelCurrent()
            job.cancel()
            Scheduler.done(this@RecordService, id)
            if (f.exists() && f.length() < 1024) f.delete()
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("rec", "Enregistrement", NotificationManager.IMPORTANCE_LOW))
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "rec") else Notification.Builder(this)
        val n = b.setContentTitle("theIPTVapp").setContentText("Enregistrement en cours…").setSmallIcon(R.mipmap.ic_launcher).setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else startForeground(1, n)
        if (intent?.hasExtra("url") == true) runScheduled(intent)
        return START_NOT_STICKY
    }
}

/** Records a live channel to a .ts file (progressive TS streams, or HLS playlists segment by segment). */
object Recorder {
    fun dir(ctx: Context): File =
        File(ctx.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES) ?: ctx.filesDir, "theIPTVapp").apply { mkdirs() }

    private fun request(url: String) = Request.Builder().url(url).header("User-Agent", Repo.streamUa).build()

    /** requête réseau en cours : annulée à l'arrêt pour débloquer tout de suite la lecture */
    @Volatile private var active: okhttp3.Call? = null
    fun cancelCurrent() { active?.cancel() }
    private fun newCall(url: String): okhttp3.Call = Repo.client.newCall(request(url)).also { active = it }

    suspend fun record(c: Channel, file: File, onBytes: (Long) -> Unit) {
        val url = c.alt.firstOrNull { it.contains(".ts", ignoreCase = true) } ?: c.url
        file.outputStream().buffered().use { out ->
            if (url.contains(".m3u8", ignoreCase = true)) hls(url, out, onBytes) else progressive(url, out, onBytes)
        }
    }

    private suspend fun progressive(url: String, out: OutputStream, onBytes: (Long) -> Unit) {
        newCall(url).execute().use { r ->
            if (!r.isSuccessful) throw RuntimeException("HTTP ${r.code}")
            val body = r.body ?: throw RuntimeException("réponse vide")
            var total = 0L
            val buf = ByteArray(64 * 1024)
            body.byteStream().use { input ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    total += n
                    onBytes(total)
                }
            }
        }
    }

    private fun text(url: String): String =
        newCall(url).execute().use { r ->
            if (!r.isSuccessful) throw RuntimeException("HTTP ${r.code}")
            r.body?.string() ?: ""
        }

    private suspend fun hls(start: String, out: OutputStream, onBytes: (Long) -> Unit) {
        var playlist = start
        val seen = LinkedHashSet<String>()
        var total = 0L
        var failures = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            try {
                val lines = text(playlist).lines().map { it.trim() }.filter { it.isNotEmpty() }
                val variant = lines.indexOfFirst { it.startsWith("#EXT-X-STREAM-INF") }
                if (variant >= 0) {
                    val next = lines.drop(variant + 1).firstOrNull { !it.startsWith("#") } ?: throw RuntimeException("liste vide")
                    playlist = URI(playlist).resolve(next).toString()
                    continue
                }
                for (l in lines) {
                    if (l.startsWith("#")) continue
                    val seg = URI(playlist).resolve(l).toString()
                    if (!seen.add(seg)) continue
                    newCall(seg).execute().use { r ->
                        if (!r.isSuccessful) throw RuntimeException("HTTP ${r.code}")
                        val b = r.body?.bytes() ?: ByteArray(0)
                        out.write(b)
                        total += b.size
                        onBytes(total)
                    }
                }
                while (seen.size > 300) seen.remove(seen.first())
                failures = 0
                if (lines.any { it.startsWith("#EXT-X-ENDLIST") }) return
            } catch (e: java.io.IOException) {
                if (++failures > 5) throw e
            }
            delay(2000)
        }
    }
}
